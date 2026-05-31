package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.hihelloy.work.lib.GameObject;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TorrentStep extends WaterAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double bulletDamage;
    private double bulletSpeed;
    private double bulletRange;
    private double wakeRadius;
    private int wakeSlow;
    private long wakeSlowDuration;
    private long maxLifetime;

    private enum StepState { BULLET_FLYING, DASHING, WAKE_LINGERING }
    private StepState state;

    private GameObject bulletBody;
    private Location bulletLoc;
    private Location bulletStart;
    private Vector bulletVelocity;
    private double bulletSpin;

    private Location dashStart;
    private Location dashTarget;
    private Vector dashVelocity;
    private boolean dashReachedTarget;
    private static final long WAKE_LINGER = 2500;

    private List<WakeNode> wakeNodes = new ArrayList<>();
    private long wakeStartTime;
    private Set<Entity> wakeHit = new HashSet<>();
    private long lastWakeHitClear;

    public TorrentStep(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        if (!hasWaterSource(player)) return;
        setFields();
        start();
    }

    private boolean hasWaterSource(Player player) {
        Block src = getWaterSourceBlock(player, 4, true);
        if (src != null) return true;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() == Material.POTION) {
                org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
                if (meta != null && meta.getBasePotionType() == PotionType.WATER) return true;
            }
        }
        return false;
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Water.TorrentStep.Cooldown", 4500);
        this.bulletDamage = config.getDouble("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletDamage", 3.0);
        this.bulletSpeed = config.getDouble("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletSpeed", 1.8);
        this.bulletRange = config.getDouble("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletRange", 20.0);
        this.wakeRadius = config.getDouble("ExtraAbilities.Hihelloy.Water.TorrentStep.WakeRadius", 1.2);
        this.wakeSlow = config.getInt("ExtraAbilities.Hihelloy.Water.TorrentStep.WakeSlow", 1);
        this.wakeSlowDuration = config.getLong("ExtraAbilities.Hihelloy.Water.TorrentStep.WakeSlowDuration", 1000);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Water.TorrentStep.MaxLifetime", 6000);

        this.state = StepState.BULLET_FLYING;
        this.bulletSpin = 0;
        this.dashReachedTarget = false;
        this.lastWakeHitClear = System.currentTimeMillis();

        Location eye = player.getEyeLocation();
        this.bulletLoc = eye.clone();
        this.bulletStart = eye.clone();
        this.bulletVelocity = player.getLocation().getDirection().clone().normalize().multiply(bulletSpeed);

        this.bulletBody = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                bulletLoc, new Vector(0.22, 0.22, 0.22), new Vector(0, 0, 0), new Vector());
        this.bulletBody.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.bulletBody.setBlockMaterial(Material.BLUE_ICE);

        player.getWorld().playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1.5f, 1.4f);
        player.getWorld().playSound(eye, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.6f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) { remove(); return; }

        if (state == StepState.BULLET_FLYING) {
            bulletSpin += 20;
            bulletVelocity.add(new Vector(0, -0.025, 0));
            if (bulletVelocity.length() > bulletSpeed) bulletVelocity.normalize().multiply(bulletSpeed);
            bulletLoc.add(bulletVelocity);

            double yaw = Math.atan2(-bulletVelocity.getX(), bulletVelocity.getZ());
            bulletBody.setLocation(bulletLoc);
            bulletBody.setRotation(new Vector(0, yaw, Math.toRadians(bulletSpin)));
            bulletBody.updateAndDisplay();

            player.getWorld().spawnParticle(Particle.SPLASH, bulletLoc, 2, 0.06, 0.06, 0.06, 0.03);
            if (Math.random() < 0.3)
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, bulletLoc, 1, 0.04, 0.04, 0.04, 0.01);

            if (bulletLoc.distanceSquared(bulletStart) > bulletRange * bulletRange
                    || bulletLoc.getBlock().isSolid()) {
                beginDash(bulletLoc.clone(), false);
                return;
            }

            for (Entity e : GeneralMethods.getEntitiesAroundPoint(bulletLoc, 0.7)) {
                if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                    DamageHandler.damageEntity((LivingEntity) e, bulletDamage, this);
                    ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0));
                    spawnImpact(bulletLoc, true);
                    beginDash(bulletLoc.clone(), true);
                    return;
                }
            }

        } else if (state == StepState.DASHING) {
            Vector toTarget = dashTarget.toVector().subtract(player.getLocation().toVector());
            double dist = toTarget.length();

            if (dist < 1.2 || dashReachedTarget) {
                dashReachedTarget = true;
                player.setVelocity(new Vector(0, player.getVelocity().getY() < 0 ? 0 : player.getVelocity().getY(), 0));
                state = StepState.WAKE_LINGERING;
                wakeStartTime = System.currentTimeMillis();
                spawnWakeNodes();
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.8f);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
                return;
            }

            toTarget.normalize().multiply(Math.min(dist, 1.2));
            toTarget.setY(0);
            player.setVelocity(toTarget);
            player.setFallDistance(0);

            player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation().add(0, 0.5, 0),
                    2, 0.15, 0.1, 0.15, 0.04);
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 0.3, 0),
                    1, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(160, 210, 255), 0.7f));

        } else if (state == StepState.WAKE_LINGERING) {
            long elapsed = System.currentTimeMillis() - wakeStartTime;
            if (elapsed > WAKE_LINGER) { remove(); return; }
            double fade = 1.0 - (elapsed / (double) WAKE_LINGER);

            long now = System.currentTimeMillis();
            if (now - lastWakeHitClear > 800) { wakeHit.clear(); lastWakeHitClear = now; }

            for (WakeNode node : wakeNodes) {
                node.display(fade);
                checkWakeHit(node.location);
                if (Math.random() < 0.18 * fade)
                    player.getWorld().spawnParticle(Particle.SNOWFLAKE, node.location, 1, 0.3, 0.05, 0.3, 0.01);
            }
        }
    }

    public void onSneak() {
        if (state == StepState.BULLET_FLYING) {
            spawnImpact(bulletLoc, false);
            beginDash(bulletLoc.clone(), false);
        }
    }

    private void beginDash(Location target, boolean hitEntity) {
        if (!hitEntity) spawnImpact(target, false);
        if (bulletBody != null) { bulletBody.destroy(); bulletBody = null; }

        this.dashStart = player.getLocation().clone();
        this.dashTarget = target.clone();
        this.dashTarget.setY(dashStart.getY());

        double flatDist = Math.sqrt(
                Math.pow(dashTarget.getX() - dashStart.getX(), 2) +
                        Math.pow(dashTarget.getZ() - dashStart.getZ(), 2));

        if (flatDist < 1.5) {
            state = StepState.WAKE_LINGERING;
            wakeStartTime = System.currentTimeMillis();
            spawnWakeNodes();
            return;
        }

        state = StepState.DASHING;
        player.getWorld().playSound(dashStart, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 1.2f);
    }

    private void spawnWakeNodes() {
        Vector dashDir = dashTarget.toVector().subtract(dashStart.toVector());
        double dist = dashDir.length();
        if (dist < 0.01) return;
        dashDir.normalize();
        int count = Math.max(4, (int) (dist * 2.5));
        for (int i = 0; i < count; i++) {
            double t = (double) i / (count - 1);
            Location loc = dashStart.clone().add(dashDir.clone().multiply(t * dist));
            loc.setY(loc.getY() + 0.05);
            wakeNodes.add(new WakeNode(player, loc, t));
        }
    }

    private void checkWakeHit(Location loc) {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(loc, wakeRadius)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())
                    && !wakeHit.contains(e)) {
                wakeHit.add(e);
                ((LivingEntity) e).addPotionEffect(
                        new PotionEffect(PotionEffectType.SLOWNESS, (int) (wakeSlowDuration / 50), wakeSlow));
                player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        e.getLocation().add(0, 0.5, 0), 6, 0.2, 0.2, 0.2, 0.03);
            }
        }
    }

    private void spawnImpact(Location loc, boolean hitEntity) {
        player.getWorld().spawnParticle(Particle.SPLASH, loc, 20, 0.3, 0.3, 0.3, 0.1);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 12, 0.2, 0.2, 0.2, 0.06);
        player.getWorld().spawnParticle(Particle.DUST, loc, 8, 0.2, 0.2, 0.2,
                0, new Particle.DustOptions(Color.fromRGB(180, 220, 255), 1.2f));
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 1.0f);
        if (hitEntity) player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 1.3f);
    }

    @Override
    public boolean isSneakAbility() {
        return false;
    }
    @Override
    public boolean isHarmlessAbility() {
        return false;
    }
    @Override
    public long getCooldown() {
        return cooldown;
    }
    @Override
    public Location getLocation() {
        return bulletLoc != null ? bulletLoc : player.getLocation();
    }
    @Override
    public String getName() {
        return "TorrentStep";
    }
    @Override
    public String getAuthor() {
        return "Hihelloy";
    }
    @Override
    public String getVersion() {
        return "1.0";
    }
    @Override
    public String getDescription() {
        return "Fire a water bullet then dash to its impact point, leaving a freezing wake behind you. Requires a nearby water source or water bottle.";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Fire bullet — dash to impact automatically.\nSneak while bullet is flying: Recall early and dash to current bullet position.\nRequires a water source nearby or a water bottle in your inventory.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (bulletBody != null) bulletBody.destroy();
        for (WakeNode node : wakeNodes) node.destroy();
    }

    @Override
    public void load() {
        abilityListener = new TorrentStepListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.Cooldown", 4500L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletDamage", 3.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletSpeed", 1.8);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletRange", 20.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.WakeRadius", 1.2);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.WakeSlow", 1);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.WakeSlowDuration", 1000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.MaxLifetime", 6000L);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }

    private static class WakeNode {
        final Player player;
        final Location location;
        final double tValue;
        GameObject iceObj;

        WakeNode(Player player, Location loc, double t) {
            this.player = player;
            this.location = loc.clone();
            this.tValue = t;
            this.iceObj = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    loc, new Vector(0.4, 0.06, 0.4),
                    new Vector(0, Math.toRadians(t * 180), 0), new Vector());
            this.iceObj.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
            this.iceObj.setBlockMaterial(t < 0.5 ? Material.BLUE_ICE : Material.PACKED_ICE);
        }

        void display(double fade) {
            double s = 0.4 * fade;
            iceObj.setScale(new Vector(s, 0.06 * fade, s));
            iceObj.updateAndDisplay();
        }

        void destroy() { if (iceObj != null) iceObj.destroy(); }
    }
}
