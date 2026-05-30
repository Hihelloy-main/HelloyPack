package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.hihelloy.work.lib.GameObject;
import com.hihelloy.work.lib.Transition;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private double dashSpeed;
    private double wakeRadius;
    private int wakeSlow;
    private long wakeSlowDuration;
    private long maxLifetime;

    private enum StepState {
        BULLET_FLYING,
        DASHING,
        WAKE_LINGERING,
        DONE
    }
    private StepState state;

    private GameObject bulletBody;
    private GameObject bulletTrail;
    private Location bulletLoc;
    private Location bulletStart;
    private Vector bulletVelocity;
    private double bulletSpin;
    private boolean bulletHitEntity;
    private LivingEntity bulletHitTarget;
    private Location bulletImpactLoc;

    private Location dashStart;
    private Location dashTarget;
    private double dashProgress;
    private double dashTotalDist;
    private boolean dashDone;

    private List<WakeNode> wakeNodes = new ArrayList<>();
    private long wakeStartTime;
    private static final long WAKE_LINGER = 2500;
    private static final int WAKE_NODE_COUNT = 12;
    private Set<Entity> wakeHit = new HashSet<>();
    private long lastWakeHitClear;

    public TorrentStep(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Water.TorrentStep.Cooldown", 4500);
        this.bulletDamage = config.getDouble("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletDamage", 3.0);
        this.bulletSpeed = config.getDouble("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletSpeed", 2.0);
        this.bulletRange = config.getDouble("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletRange", 20.0);
        this.dashSpeed = config.getDouble("ExtraAbilities.Hihelloy.Water.TorrentStep.DashSpeed", 0.7);
        this.wakeRadius = config.getDouble("ExtraAbilities.Hihelloy.Water.TorrentStep.WakeRadius", 1.2);
        this.wakeSlow = config.getInt("ExtraAbilities.Hihelloy.Water.TorrentStep.WakeSlow", 1);
        this.wakeSlowDuration = config.getLong("ExtraAbilities.Hihelloy.Water.TorrentStep.WakeSlowDuration", 1000);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Water.TorrentStep.MaxLifetime", 6000);

        this.state = StepState.BULLET_FLYING;
        this.bulletSpin = 0;
        this.bulletHitEntity = false;
        this.dashDone = false;
        this.dashProgress = 0;
        this.lastWakeHitClear = System.currentTimeMillis();

        Location eye = player.getEyeLocation();
        this.bulletLoc = eye.clone();
        this.bulletStart = eye.clone();
        this.bulletVelocity = player.getLocation().getDirection().clone().normalize().multiply(bulletSpeed);

        this.bulletBody = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                bulletLoc, new Vector(0.22, 0.22, 0.22),
                new Vector(0, 0, 0), new Vector());
        this.bulletBody.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.bulletBody.setBlockMaterial(Material.BLUE_ICE);

        this.bulletTrail = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                bulletLoc, new Vector(0.12, 0.12, 0.38),
                new Vector(0, 0, 0), new Vector());
        this.bulletTrail.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
        this.bulletTrail.setBlockMaterial(Material.PACKED_ICE);

        player.getWorld().playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1.5f, 1.4f);
        player.getWorld().playSound(eye, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 1.6f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        if (state == StepState.BULLET_FLYING) {
            bulletSpin += 20;
            bulletVelocity.add(new Vector(0, -0.025, 0));
            if (bulletVelocity.length() > bulletSpeed) bulletVelocity.normalize().multiply(bulletSpeed);
            bulletLoc.add(bulletVelocity);

            double yaw = Math.atan2(-bulletVelocity.getX(), bulletVelocity.getZ());
            double pitch = Math.asin(Math.max(-1, Math.min(1, bulletVelocity.clone().normalize().getY())));

            bulletBody.setLocation(bulletLoc);
            bulletBody.setRotation(new Vector(0, yaw, Math.toRadians(bulletSpin)));
            bulletBody.updateAndDisplay();

            bulletTrail.setLocation(bulletLoc);
            bulletTrail.setRotation(new Vector(-pitch, yaw, Math.toRadians(bulletSpin * 0.5)));
            bulletTrail.updateAndDisplay();

            spawnBulletParticles(bulletLoc);

            if (bulletLoc.distanceSquared(bulletStart) > bulletRange * bulletRange) {
                bulletImpactLoc = bulletLoc.clone();
                beginDash(bulletImpactLoc, null);
                return;
            }
            if (bulletLoc.getBlock().isSolid()) {
                bulletImpactLoc = bulletLoc.clone();
                spawnBulletImpact(bulletImpactLoc, false);
                beginDash(bulletImpactLoc, null);
                return;
            }

            for (Entity e : GeneralMethods.getEntitiesAroundPoint(bulletLoc, 0.7)) {
                if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                    bulletHitTarget = (LivingEntity) e;
                    bulletHitEntity = true;
                    bulletImpactLoc = bulletLoc.clone();
                    DamageHandler.damageEntity(bulletHitTarget, bulletDamage, this);
                    bulletHitTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0));
                    spawnBulletImpact(bulletImpactLoc, true);
                    beginDash(bulletImpactLoc, bulletHitTarget);
                    return;
                }
            }

        } else if (state == StepState.DASHING) {
            dashProgress += dashSpeed;
            double t = Math.min(dashProgress / dashTotalDist, 1.0);
            double easedT = t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;

            Location newPos = dashStart.clone().add(
                    dashTarget.toVector().subtract(dashStart.toVector()).multiply(easedT));
            newPos.setYaw(player.getLocation().getYaw());
            newPos.setPitch(player.getLocation().getPitch());
            player.teleport(newPos);

            player.setVelocity(dashTarget.toVector().subtract(dashStart.toVector()).normalize().multiply(0.4));

            spawnDashTrail(player.getLocation());

            if (t >= 1.0) {
                dashDone = true;
                state = StepState.WAKE_LINGERING;
                wakeStartTime = System.currentTimeMillis();
                spawnWakeNodes();
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1.5f, 0.8f);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.5f);
            }

        } else if (state == StepState.WAKE_LINGERING) {
            long elapsed = System.currentTimeMillis() - wakeStartTime;
            double fade = 1.0 - (elapsed / (double) WAKE_LINGER);

            if (elapsed > WAKE_LINGER) {
                remove();
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastWakeHitClear > 800) {
                wakeHit.clear();
                lastWakeHitClear = now;
            }

            for (WakeNode node : wakeNodes) {
                node.display(fade);
                checkWakeHit(node.location);
                spawnWakeParticles(node.location, fade);
            }
        }
    }

    public void onSneak() {
        if (state == StepState.BULLET_FLYING) {
            bulletImpactLoc = bulletLoc.clone();
            spawnBulletImpact(bulletImpactLoc, false);
            beginDash(bulletImpactLoc, null);
        }
    }

    private void beginDash(Location target, LivingEntity hitTarget) {
        state = StepState.DASHING;

        if (bulletBody != null) bulletBody.destroy();
        if (bulletTrail != null) bulletTrail.destroy();
        bulletBody = null;
        bulletTrail = null;

        dashStart = player.getLocation().clone();
        dashTarget = target.clone();
        dashTarget.setY(dashStart.getY());

        double flatDist = Math.sqrt(
                Math.pow(dashTarget.getX() - dashStart.getX(), 2) +
                        Math.pow(dashTarget.getZ() - dashStart.getZ(), 2));

        if (flatDist < 1.0) {
            state = StepState.WAKE_LINGERING;
            wakeStartTime = System.currentTimeMillis();
            spawnWakeNodes();
            return;
        }

        dashTotalDist = flatDist;
        dashProgress = 0;

        player.getWorld().playSound(dashStart, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 1.2f);
    }

    private void spawnWakeNodes() {
        Vector dashDir = dashTarget.toVector().subtract(dashStart.toVector()).normalize();
        for (int i = 0; i < WAKE_NODE_COUNT; i++) {
            double t = (double) i / (WAKE_NODE_COUNT - 1);
            Location nodeLoc = dashStart.clone().add(dashDir.clone().multiply(t * dashTotalDist));
            nodeLoc.setY(nodeLoc.getY() + 0.05);
            wakeNodes.add(new WakeNode(player, nodeLoc, t));
        }
    }

    private void checkWakeHit(Location loc) {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(loc, wakeRadius)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())
                    && !wakeHit.contains(e)) {
                wakeHit.add(e);
                LivingEntity le = (LivingEntity) e;
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        (int) (wakeSlowDuration / 50), wakeSlow));
                player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        e.getLocation().add(0, 0.5, 0), 6, 0.2, 0.2, 0.2, 0.03);
            }
        }
    }

    private void spawnBulletParticles(Location loc) {
        player.getWorld().spawnParticle(Particle.SPLASH, loc, 2, 0.06, 0.06, 0.06, 0.04);
        if (Math.random() < 0.4)
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 1, 0.04, 0.04, 0.04, 0.01);
    }

    private void spawnBulletImpact(Location loc, boolean hitEntity) {
        player.getWorld().spawnParticle(Particle.SPLASH, loc, 20, 0.3, 0.3, 0.3, 0.1);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 12, 0.2, 0.2, 0.2, 0.06);
        player.getWorld().spawnParticle(Particle.DUST, loc, 8, 0.2, 0.2, 0.2,
                0, new Particle.DustOptions(Color.fromRGB(180, 220, 255), 1.2f));
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 1.5f, 1.0f);
        if (hitEntity)
            player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 1.3f);
    }

    private void spawnDashTrail(Location loc) {
        player.getWorld().spawnParticle(Particle.SPLASH, loc, 3, 0.2, 0.1, 0.2, 0.06);
        player.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.1, 0.1, 0.1,
                0, new Particle.DustOptions(Color.fromRGB(160, 210, 255), 0.8f));
    }

    private void spawnWakeParticles(Location loc, double fade) {
        if (Math.random() > 0.25 * fade) return;
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 1, 0.3, 0.05, 0.3, 0.01);
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
        return "Fire a fast water bullet then dash through its impact point, leaving a freezing wake that slows enemies who walk through it.";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Fire the bullet — you automatically dash to its impact point.\n" +
                "Sneak while bullet is flying: Recall early and dash to current position.\n" +
                "The icy wake lingers at the dash path for 2.5 seconds.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (bulletBody != null) bulletBody.destroy();
        if (bulletTrail != null) bulletTrail.destroy();
        for (WakeNode node : wakeNodes) node.destroy();
    }

    @Override
    public void load() {
        abilityListener = new TorrentStepListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.Cooldown", 4500L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletDamage", 3.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletSpeed", 2.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.BulletRange", 20.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TorrentStep.DashSpeed", 0.7);
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

        void destroy() {
            if (iceObj != null) iceObj.destroy();
        }
    }
}