package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.MetalAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.hihelloy.work.lib.GameObject;
import com.hihelloy.work.lib.Transition;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class MetalShot extends MetalAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private int pierceCount;
    private double speed;
    private double range;
    private long chargeTime;
    private long maxLifetime;

    private enum ShotState {
        CHARGING,
        FLYING
    }
    private ShotState state;

    private GameObject boltBody;
    private GameObject boltTip;
    private Transition chargeTransition;
    private long chargeStartTime;

    private Location boltLoc;
    private Location startLoc;
    private Vector velocity;
    private double spinAngle;
    private int piercesLeft;
    private List<Entity> pierced = new ArrayList<>();

    public MetalShot(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Metal.MetalShot.Cooldown", 4000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Metal.MetalShot.Damage", 5.0);
        this.pierceCount = config.getInt("ExtraAbilities.Hihelloy.Metal.MetalShot.PierceCount", 2);
        this.speed = config.getDouble("ExtraAbilities.Hihelloy.Metal.MetalShot.Speed", 2.5);
        this.range = config.getDouble("ExtraAbilities.Hihelloy.Metal.MetalShot.Range", 28.0);
        this.chargeTime = config.getLong("ExtraAbilities.Hihelloy.Metal.MetalShot.ChargeTime", 600);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Metal.MetalShot.MaxLifetime", 6000);

        this.state = ShotState.CHARGING;
        this.spinAngle = 0;
        this.piercesLeft = pierceCount;
        this.chargeStartTime = System.currentTimeMillis();

        Location hand = GeneralMethods.getMainHandLocation(player);
        double yaw = Math.toRadians(-player.getLocation().getYaw());

        this.boltBody = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                hand, new Vector(0.1, 0.1, 0.5),
                new Vector(0, yaw, 0), new Vector());
        this.boltBody.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
        this.boltBody.setBlockMaterial(Material.IRON_BLOCK);

        this.boltTip = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                hand, new Vector(0.15, 0.15, 0.2),
                new Vector(0, yaw, 0), new Vector());
        this.boltTip.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
        this.boltTip.setBlockMaterial(Material.POLISHED_DEEPSLATE);

        this.chargeTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(chargeTime / 50.0, 0, 0),
                new Vector(0, 0, 0));

        player.getWorld().playSound(hand, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.0f, 1.4f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        spinAngle += 18;

        if (state == ShotState.CHARGING) {
            chargeTransition.update();
            Location hand = GeneralMethods.getMainHandLocation(player);
            double yaw = Math.toRadians(-player.getLocation().getYaw());
            double pitch = Math.toRadians(player.getLocation().getPitch());

            boltBody.setLocation(hand);
            boltBody.setRotation(new Vector(pitch, yaw, Math.toRadians(spinAngle)));
            boltBody.updateAndDisplay();
            boltTip.setLocation(hand);
            boltTip.setRotation(new Vector(pitch, yaw, Math.toRadians(spinAngle)));
            boltTip.updateAndDisplay();

            spawnChargeParticles(hand, chargeTransition.getX());

            if (!player.isSneaking() && System.currentTimeMillis() >= chargeStartTime + chargeTime) {
                beginFlight(hand, yaw, pitch);
            }

        } else if (state == ShotState.FLYING) {
            boltLoc.add(velocity);

            double yaw = Math.toRadians(-player.getLocation().getYaw());
            double pitch = Math.toRadians(Math.toDegrees(Math.asin(-velocity.clone().normalize().getY())));

            boltBody.setLocation(boltLoc);
            boltBody.setRotation(new Vector(pitch, Math.atan2(-velocity.getX(), velocity.getZ()), Math.toRadians(spinAngle)));
            boltBody.updateAndDisplay();
            boltTip.setLocation(boltLoc);
            boltTip.setRotation(new Vector(pitch, Math.atan2(-velocity.getX(), velocity.getZ()), Math.toRadians(spinAngle)));
            boltTip.updateAndDisplay();

            player.getWorld().spawnParticle(Particle.CRIT, boltLoc, 2, 0.05, 0.05, 0.05, 0.01);

            if (boltLoc.distanceSquared(startLoc) > range * range) {
                remove();
                return;
            }
            if (boltLoc.getBlock().isSolid()) {
                spawnImpact(boltLoc);
                remove();
                return;
            }

            for (Entity e : GeneralMethods.getEntitiesAroundPoint(boltLoc, 0.6)) {
                if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId()) && !pierced.contains(e)) {
                    pierced.add(e);
                    DamageHandler.damageEntity((LivingEntity) e, damage, this);
                    player.getWorld().spawnParticle(Particle.CRIT, boltLoc, 10, 0.15, 0.15, 0.15, 0.05);
                    player.getWorld().playSound(boltLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 1.4f);
                    piercesLeft--;
                    if (piercesLeft <= 0) {
                        spawnImpact(boltLoc);
                        remove();
                        return;
                    }
                }
            }
        }
    }

    public void onSneak() {
        if (state == ShotState.CHARGING && System.currentTimeMillis() >= chargeStartTime + chargeTime) {
            Location hand = GeneralMethods.getMainHandLocation(player);
            double yaw = Math.toRadians(-player.getLocation().getYaw());
            double pitch = Math.toRadians(player.getLocation().getPitch());
            beginFlight(hand, yaw, pitch);
        }
    }

    private void beginFlight(Location from, double yaw, double pitch) {
        state = ShotState.FLYING;
        boltLoc = from.clone();
        startLoc = from.clone();
        velocity = player.getLocation().getDirection().clone().normalize().multiply(speed);
        player.getWorld().playSound(from, Sound.ENTITY_ARROW_SHOOT, 2.0f, 1.8f);
        player.getWorld().playSound(from, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.0f, 1.6f);
    }

    private void spawnChargeParticles(Location loc, double charge) {
        if (Math.random() < charge * 0.5) {
            player.getWorld().spawnParticle(Particle.CRIT, loc, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private void spawnImpact(Location loc) {
        player.getWorld().spawnParticle(Particle.CRIT, loc, 15, 0.2, 0.2, 0.2, 0.05);
        player.getWorld().spawnParticle(Particle.BLOCK, loc, 10, 0.15, 0.15, 0.15, 0, Material.IRON_BLOCK.createBlockData());
        player.getWorld().playSound(loc, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.5f, 0.7f);
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
        return boltLoc != null ? boltLoc : player.getLocation();
    }

    @Override
    public String getName() {
        return "MetalShot";
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
        return "Fire a dense metal bolt that pierces through multiple enemies before stopping.";
    }

    @Override
    public String getInstructions() {
        return "\nHold Sneak: Charge\nRelease Sneak: Fire — pierces up to 2 targets.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (boltBody != null) boltBody.destroy();
        if (boltTip != null) boltTip.destroy();
    }

    @Override
    public void load() {
        abilityListener = new MetalShotListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Metal.MetalShot.Cooldown", 4000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Metal.MetalShot.Damage", 5.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Metal.MetalShot.PierceCount", 2);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Metal.MetalShot.Speed", 2.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Metal.MetalShot.Range", 28.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Metal.MetalShot.ChargeTime", 600L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Metal.MetalShot.MaxLifetime", 6000L);
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
