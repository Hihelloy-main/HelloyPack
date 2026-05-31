package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.AirAbility;
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

import java.util.HashSet;
import java.util.Set;

public class Thunderclap extends AirAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double coneLength;
    private double coneAngle;
    private long chargeTime;
    private long maxLifetime;
    private int nauseaDuration;

    private enum ThunderclapState {
        WINDUP,
        CLAP,
        SHOCKWAVE,
        DONE
    }
    private ThunderclapState state;

    private GameObject leftPalm;
    private GameObject rightPalm;
    private GameObject shockwaveDisc;

    private Transition windupTransition;
    private Transition clapTransition;
    private Transition shockwaveTransition;

    private double shockwaveRadius;
    private long clapTime;
    private long windupStartTime;

    private Set<Entity> shockwaveHit = new HashSet<>();

    public Thunderclap(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Air.Thunderclap.Cooldown", 10000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Air.Thunderclap.Damage", 4.0);
        this.coneLength = config.getDouble("ExtraAbilities.Hihelloy.Air.Thunderclap.ConeLength", 8.0);
        this.coneAngle = config.getDouble("ExtraAbilities.Hihelloy.Air.Thunderclap.ConeAngle", 35.0);
        this.chargeTime = config.getLong("ExtraAbilities.Hihelloy.Air.Thunderclap.ChargeTime", 800);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Air.Thunderclap.MaxLifetime", 5000);
        this.nauseaDuration = config.getInt("ExtraAbilities.Hihelloy.Air.Thunderclap.NauseaDuration", 80);

        this.state = ThunderclapState.WINDUP;
        this.shockwaveRadius = 0;
        this.windupStartTime = System.currentTimeMillis();

        Location leftLoc = GeneralMethods.getOffHandLocation(player);
        Location rightLoc = GeneralMethods.getMainHandLocation(player);
        double yaw = Math.toRadians(-player.getLocation().getYaw());

        this.leftPalm = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                leftLoc, new Vector(0.3, 0.3, 0.12),
                new Vector(0, yaw, 0), new Vector());
        this.leftPalm.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.leftPalm.setBlockMaterial(Material.LIGHT_BLUE_STAINED_GLASS);

        this.rightPalm = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                rightLoc, new Vector(0.3, 0.3, 0.12),
                new Vector(0, yaw, 0), new Vector());
        this.rightPalm.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.rightPalm.setBlockMaterial(Material.LIGHT_BLUE_STAINED_GLASS);

        Location eyeLoc = player.getEyeLocation();
        this.shockwaveDisc = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                eyeLoc, new Vector(0.01, 0.01, 0.01),
                new Vector(0, yaw, 0), new Vector());
        this.shockwaveDisc.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.shockwaveDisc.setBlockMaterial(Material.WHITE_STAINED_GLASS);

        this.windupTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector((double) chargeTime / 50.0, 0, 0),
                new Vector(0, 0, 0));

        playAirbendingSound(player.getLocation());
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        double yaw = Math.toRadians(-player.getLocation().getYaw());
        double pitch = Math.toRadians(player.getLocation().getPitch());
        Location leftLoc = GeneralMethods.getOffHandLocation(player);
        Location rightLoc = GeneralMethods.getMainHandLocation(player);

        if (state == ThunderclapState.WINDUP) {
            windupTransition.update();
            double charge = windupTransition.getX();

            Vector rightDir = new Vector(1, 0, 0).rotateAroundY(-yaw);
            Location lOffset = leftLoc.clone().subtract(rightDir.clone().multiply(charge * 0.6));
            Location rOffset = rightLoc.clone().add(rightDir.clone().multiply(charge * 0.6));

            leftPalm.setLocation(lOffset);
            leftPalm.setRotation(new Vector(0, yaw, 0));
            leftPalm.updateAndDisplay();

            rightPalm.setLocation(rOffset);
            rightPalm.setRotation(new Vector(0, yaw, 0));
            rightPalm.updateAndDisplay();

            spawnWindupParticles(lOffset, rOffset, charge);

            if (!player.isSneaking() && System.currentTimeMillis() > windupStartTime + chargeTime) {
                triggerClap();
            }
        } else if (state == ThunderclapState.CLAP) {
            clapTransition.update();
            double clapP = clapTransition.getX();

            Vector rightDir = new Vector(1, 0, 0).rotateAroundY(-yaw);
            Location lMid = leftLoc.clone().subtract(rightDir.clone().multiply((1 - clapP) * 0.6));
            Location rMid = rightLoc.clone().add(rightDir.clone().multiply((1 - clapP) * 0.6));

            leftPalm.setLocation(lMid);
            leftPalm.setRotation(new Vector(0, yaw, 0));
            leftPalm.updateAndDisplay();

            rightPalm.setLocation(rMid);
            rightPalm.setRotation(new Vector(0, yaw, 0));
            rightPalm.updateAndDisplay();

            if (clapTransition.getX() >= 0.99) {
                state = ThunderclapState.SHOCKWAVE;
                clapTime = System.currentTimeMillis();
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.4f);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.8f);
                shockwaveTransition = new Transition(
                        new Vector(0, 0, 0),
                        new Vector(coneLength, 0, 0),
                        new Vector(12, 0, 0),
                        new Vector(0, 0, 0));
                leftPalm.destroy();
                rightPalm.destroy();
                leftPalm = null;
                rightPalm = null;
            }
        } else if (state == ThunderclapState.SHOCKWAVE) {
            shockwaveTransition.update();
            shockwaveRadius = shockwaveTransition.getX();

            Location origin = player.getEyeLocation();
            Vector forward = player.getLocation().getDirection().clone();

            spawnConeParticles(origin, forward, shockwaveRadius);
            checkConeHits(origin, forward, shockwaveRadius);

            if (shockwaveRadius > 0.2) {
                double discScale = shockwaveRadius * 0.4;
                shockwaveDisc.setLocation(origin.clone().add(forward.clone().multiply(shockwaveRadius)));
                shockwaveDisc.setScale(new Vector(discScale, discScale, 0.04));
                shockwaveDisc.setRotation(new Vector(pitch, yaw, 0));
                shockwaveDisc.updateAndDisplay();
            }

            if (shockwaveTransition.getX() >= coneLength * 0.99) {
                remove();
            }
        }
    }

    public void onSneak() {
        if (state == ThunderclapState.WINDUP && System.currentTimeMillis() > windupStartTime + chargeTime) {
            triggerClap();
        }
    }

    private void triggerClap() {
        state = ThunderclapState.CLAP;
        clapTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(4, 0, 0),
                new Vector(0, 0, 0));
        playAirbendingSound(player.getLocation());
    }

    private void checkConeHits(Location origin, Vector forward, double radius) {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(origin.clone().add(forward.clone().multiply(radius * 0.5)), radius + 1)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                if (shockwaveHit.contains(e)) continue;
                Vector toTarget = e.getLocation().toVector().subtract(origin.toVector());
                double dot = toTarget.normalize().dot(forward.clone().normalize());
                double angleDeg = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
                if (angleDeg <= coneAngle && toTarget.length() <= radius + 1.0) {
                    shockwaveHit.add(e);
                    DamageHandler.damageEntity((LivingEntity) e, damage, this);
                    Vector kb = forward.clone().normalize().multiply(1.2);
                    kb.setY(0.5);
                    e.setVelocity(e.getVelocity().add(kb));
                    ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDuration, 0));
                    player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, e.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0);
                }
            }
        }
    }

    private void spawnConeParticles(Location origin, Vector forward, double radius) {
        int rings = 4;
        for (int r = 0; r < rings; r++) {
            double ringRadius = radius * (r + 1.0) / rings;
            double ringWidth = Math.tan(Math.toRadians(coneAngle)) * ringRadius;
            int points = Math.max(6, (int) (ringWidth * 8));
            for (int i = 0; i < points; i++) {
                double a = (2 * Math.PI / points) * i;
                Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
                if (right.lengthSquared() < 0.001) right = new Vector(1, 0, 0);
                Vector up = forward.clone().crossProduct(right).normalize();
                Vector offset = right.clone().multiply(Math.cos(a) * ringWidth)
                        .add(up.clone().multiply(Math.sin(a) * ringWidth));
                Location p = origin.clone().add(forward.clone().multiply(ringRadius)).add(offset);
                player.getWorld().spawnParticle(Particle.CLOUD, p, 1, 0, 0, 0, 0.01);
                if (Math.random() < 0.15)
                    player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0, 0, 0, 0);
            }
        }
    }

    private void spawnWindupParticles(Location left, Location right, double charge) {
        if (Math.random() < charge * 0.6) {
            player.getWorld().spawnParticle(Particle.CLOUD, left, 1, 0.05, 0.05, 0.05, 0.02);
            player.getWorld().spawnParticle(Particle.CLOUD, right, 1, 0.05, 0.05, 0.05, 0.02);
        }
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
        return "Thunderclap";
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
        return "Wind up a mighty clap that releases a concussive air cone, launching and nauseating everything in its path.";
    }
    @Override
    public String getInstructions() {
        return "\nHold Sneak to wind up, release to clap.\nFull charge is required before releasing.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (leftPalm != null) leftPalm.destroy();
        if (rightPalm != null) rightPalm.destroy();
        if (shockwaveDisc != null) shockwaveDisc.destroy();
    }

    @Override
    public void load() {
        abilityListener = new ThunderclapListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.Thunderclap.Cooldown", 10000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.Thunderclap.Damage", 4.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.Thunderclap.ConeLength", 8.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.Thunderclap.ConeAngle", 35.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.Thunderclap.ChargeTime", 800L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.Thunderclap.MaxLifetime", 5000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.Thunderclap.NauseaDuration", 80);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }
}
