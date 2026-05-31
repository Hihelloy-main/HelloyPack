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
import java.util.List;

public class CrystalNeedles extends WaterAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private int needleCount;
    private double spreadAngle;
    private double needleSpeed;
    private double needleRange;
    private long maxLifetime;
    private int slowDuration;

    private enum NeedleState {
        FORMING,
        LAUNCHED
    }
    private NeedleState state;

    private List<Needle> needles = new ArrayList<>();
    private Transition formTransition;
    private long formStart;
    private static final long FORM_DURATION = 600;

    public CrystalNeedles(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        if (!hasWaterSource()) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Water.CrystalNeedles.Cooldown", 5000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Water.CrystalNeedles.Damage", 2.0);
        this.needleCount = config.getInt("ExtraAbilities.Hihelloy.Water.CrystalNeedles.NeedleCount", 7);
        this.spreadAngle = config.getDouble("ExtraAbilities.Hihelloy.Water.CrystalNeedles.SpreadAngle", 20.0);
        this.needleSpeed = config.getDouble("ExtraAbilities.Hihelloy.Water.CrystalNeedles.NeedleSpeed", 1.7);
        this.needleRange = config.getDouble("ExtraAbilities.Hihelloy.Water.CrystalNeedles.NeedleRange", 22.0);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Water.CrystalNeedles.MaxLifetime", 5000);
        this.slowDuration = config.getInt("ExtraAbilities.Hihelloy.Water.CrystalNeedles.SlowDuration", 40);

        this.state = NeedleState.FORMING;
        this.formStart = System.currentTimeMillis();

        Location handLoc = GeneralMethods.getMainHandLocation(player);
        Vector forward = player.getLocation().getDirection().clone();
        double yaw = -player.getLocation().getYaw();

        for (int i = 0; i < needleCount; i++) {
            double t = (needleCount > 1) ? ((double) i / (needleCount - 1) - 0.5) : 0;
            double spreadRad = Math.toRadians(t * spreadAngle * 2);
            Vector dir = forward.clone().rotateAroundY(spreadRad).normalize();
            needles.add(new Needle(player, handLoc.clone(), dir, yaw, i, needleCount));
        }

        this.formTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(FORM_DURATION / 50.0, 0, 0),
                new Vector(0, 0, 0));

        player.getWorld().playSound(handLoc, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.8f);
        player.getWorld().playSound(handLoc, Sound.ENTITY_ARROW_SHOOT, 1.2f, 1.4f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        if (state == NeedleState.FORMING) {
            formTransition.update();
            double progress = formTransition.getX();
            Location handLoc = GeneralMethods.getMainHandLocation(player);

            for (int i = 0; i < needles.size(); i++) {
                Needle n = needles.get(i);
                double t = (needleCount > 1) ? ((double) i / (needleCount - 1) - 0.5) : 0;
                double spreadRad = Math.toRadians(t * spreadAngle * 2 * progress);
                Vector dir = player.getLocation().getDirection().clone().rotateAroundY(spreadRad).normalize();
                double fanOffset = t * progress * 0.6;
                Location fanLoc = handLoc.clone().add(
                        new Vector(1, 0, 0).rotateAroundY(Math.toRadians(-player.getLocation().getYaw())).multiply(fanOffset));
                fanLoc.add(new Vector(0, 0, 0));
                n.updateForm(fanLoc, dir, progress, i);
            }

            for (Needle n : needles) n.display();

            if (progress >= 0.99) {
                state = NeedleState.LAUNCHED;
                player.getWorld().playSound(GeneralMethods.getMainHandLocation(player),
                        Sound.ENTITY_ARROW_SHOOT, 2.0f, 1.5f);
            }
        } else if (state == NeedleState.LAUNCHED) {
            for (int i = needles.size() - 1; i >= 0; i--) {
                Needle n = needles.get(i);
                n.updateFlight(needleSpeed);
                n.display();
                if (n.checkHit(damage, slowDuration, this) || n.isExpired(needleRange)) {
                    n.spawnImpact();
                    n.destroy();
                    needles.remove(i);
                }
            }
            if (needles.isEmpty()) {
                remove();
            }
        }
    }


    private boolean hasWaterSource() {
        org.bukkit.block.Block src = getWaterSourceBlock(player, 4, true);
        if (src != null) return true;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() == Material.POTION) {
                org.bukkit.inventory.meta.PotionMeta meta =
                        (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
                if (meta != null && meta.getBasePotionType() == org.bukkit.potion.PotionType.WATER)
                    return true;
            }
        }
        return false;
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
        return player.getLocation();
    }
    @Override
    public String getName() {
        return "CrystalNeedles";
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
        return "Crystallize water into a fan of ice needles that impale and slow targets.";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Fire crystal needles in a spread.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        for (Needle n : needles) n.destroy();
    }

    @Override
    public void load() {
        abilityListener = new CrystalNeedlesListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.CrystalNeedles.Cooldown", 5000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.CrystalNeedles.Damage", 2.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.CrystalNeedles.NeedleCount", 7);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.CrystalNeedles.SpreadAngle", 20.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.CrystalNeedles.NeedleSpeed", 1.7);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.CrystalNeedles.NeedleRange", 22.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.CrystalNeedles.MaxLifetime", 5000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.CrystalNeedles.SlowDuration", 40);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }

    private static class Needle {
        private final Player player;
        private Location location;
        private final Location startLocation;
        private Vector direction;
        private final double needleYaw;
        private double rollAngle;

        private GameObject shaftObj;
        private GameObject tipObj;

        Needle(Player player, Location startLoc, Vector direction, double yaw, int index, int total) {
            this.player = player;
            this.location = startLoc.clone();
            this.startLocation = startLoc.clone();
            this.direction = direction.clone();
            this.needleYaw = yaw;
            this.rollAngle = (360.0 / total) * index;

            this.shaftObj = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    this.location, new Vector(0.07, 0.07, 0.45),
                    new Vector(0, Math.toRadians(yaw), 0), new Vector());
            this.shaftObj.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
            this.shaftObj.setBlockMaterial(Material.PACKED_ICE);

            this.tipObj = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    this.shaftObj, new Vector(0.12, 0.12, 0.18),
                    new Vector(0, 0, 0), new Vector(0, 0, 0.45));
            this.tipObj.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
            this.tipObj.setBlockMaterial(Material.BLUE_ICE);
        }

        void updateForm(Location loc, Vector dir, double progress, int index) {
            this.location = loc.clone();
            this.direction = dir.clone().normalize();
            double pitch = -Math.toDegrees(Math.asin(direction.getY()));
            double yaw = Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
            this.shaftObj.setLocation(this.location);
            this.shaftObj.setRotation(new Vector(Math.toRadians(pitch), Math.toRadians(yaw), 0));
            this.shaftObj.setScale(new Vector(0.07, 0.07, 0.45 * progress));
            this.tipObj.setTranslation(new Vector(0, 0, 0.45 * progress));
        }

        void updateFlight(double speed) {
            this.direction.add(new Vector(0, -0.015, 0));
            if (this.direction.length() > speed) this.direction.normalize().multiply(speed);
            this.location.add(this.direction);
            this.rollAngle += 10;

            double pitch = -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, this.direction.clone().normalize().getY()))));
            double yaw = Math.toDegrees(Math.atan2(-this.direction.getX(), this.direction.getZ()));
            this.shaftObj.setLocation(this.location);
            this.shaftObj.setRotation(new Vector(Math.toRadians(pitch), Math.toRadians(yaw), Math.toRadians(rollAngle)));
            this.tipObj.setTranslation(new Vector(0, 0, 0.45));
        }

        boolean checkHit(double damage, int slowDuration, CrystalNeedles ability) {
            if (this.location.getBlock().isSolid()) return true;
            for (Entity e : GeneralMethods.getEntitiesAroundPoint(this.location, 0.55)) {
                if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                    DamageHandler.damageEntity((LivingEntity) e, damage, ability);
                    ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDuration, 0));
                    return true;
                }
            }
            return false;
        }

        boolean isExpired(double range) {
            return this.location.distanceSquared(startLocation) > range * range;
        }

        void spawnImpact() {
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, this.location, 8, 0.1, 0.1, 0.1, 0.04);
            player.getWorld().spawnParticle(Particle.DUST, this.location, 4, 0.1, 0.1, 0.1,
                    0, new Particle.DustOptions(Color.fromRGB(200, 230, 255), 0.8f));
            player.getWorld().playSound(this.location, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.7f);
        }

        void display() {
            this.shaftObj.updateAndDisplay();
            this.tipObj.updateAndDisplay();
        }

        void destroy() {
            if (shaftObj != null) shaftObj.destroy();
            if (tipObj != null) tipObj.destroy();
        }
    }
}
