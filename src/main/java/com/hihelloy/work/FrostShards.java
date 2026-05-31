package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.IceAbility;
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

public class FrostShards extends IceAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double shardRange;
    private double orbitRadius;
    private int maxShards;
    private long buildInterval;
    private long maxLifetime;

    private enum ShardState {
        BUILDING,
        ARMED,
        LAUNCHED
    }
    private ShardState state;

    private List<ShardProjectile> shards = new ArrayList<>();
    private double orbitAngle;
    private long lastBuildTime;
    private Transition armTransition;

    public FrostShards(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Ice.FrostShards.Cooldown", 6000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Ice.FrostShards.Damage", 2.5);
        this.shardRange = config.getDouble("ExtraAbilities.Hihelloy.Ice.FrostShards.ShardRange", 20.0);
        this.orbitRadius = config.getDouble("ExtraAbilities.Hihelloy.Ice.FrostShards.OrbitRadius", 0.9);
        this.maxShards = config.getInt("ExtraAbilities.Hihelloy.Ice.FrostShards.MaxShards", 6);
        this.buildInterval = config.getLong("ExtraAbilities.Hihelloy.Ice.FrostShards.BuildInterval", 400);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Ice.FrostShards.MaxLifetime", 12000);

        this.state = ShardState.BUILDING;
        this.orbitAngle = 0;
        this.lastBuildTime = System.currentTimeMillis();

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 1.6f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        orbitAngle += 7;

        if (state == ShardState.BUILDING) {
            if (shards.size() < maxShards && System.currentTimeMillis() - lastBuildTime >= buildInterval) {
                lastBuildTime = System.currentTimeMillis();
                double spawnAngle = (360.0 / maxShards) * shards.size();
                shards.add(new ShardProjectile(player, spawnAngle));
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 1.8f);
            }

            for (int i = 0; i < shards.size(); i++) {
                double angle = orbitAngle + (360.0 / maxShards) * i;
                shards.get(i).updateOrbit(player.getLocation().add(0, 1.2, 0), orbitRadius, angle);
                shards.get(i).display();
            }

            if (shards.size() >= maxShards) {
                state = ShardState.ARMED;
                armTransition = new Transition(
                        new Vector(orbitRadius, 0, 0),
                        new Vector(orbitRadius * 1.3, 0, 0),
                        new Vector(10, 0, 0),
                        new Vector(0, 0, 0));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.7f, 1.8f);
            }

        } else if (state == ShardState.ARMED) {
            armTransition.update();
            double currentRadius = armTransition.getX();

            for (int i = 0; i < shards.size(); i++) {
                double angle = orbitAngle + (360.0 / shards.size()) * i;
                shards.get(i).updateOrbit(player.getLocation().add(0, 1.2, 0), currentRadius, angle);
                shards.get(i).display();
            }

            spawnArmedParticles();

        } else if (state == ShardState.LAUNCHED) {
            for (int i = shards.size() - 1; i >= 0; i--) {
                ShardProjectile sp = shards.get(i);
                sp.updateFlight();
                sp.display();
                if (sp.checkHit(damage, this) || sp.isExpired(shardRange)) {
                    sp.spawnImpact();
                    sp.destroy();
                    shards.remove(i);
                }
            }
            if (shards.isEmpty()) {
                remove();
            }
        }
    }

    public void onLeftClick() {
        if (state == ShardState.ARMED) {
            state = ShardState.LAUNCHED;
            Vector forward = player.getLocation().getDirection().clone();
            double spread = Math.toRadians(30.0);
            for (int i = 0; i < shards.size(); i++) {
                double angleOffset = (shards.size() > 1) ? spread * ((double) i / (shards.size() - 1) - 0.5) * 2 : 0;
                Vector dir = forward.clone().rotateAroundY(angleOffset).normalize();
                shards.get(i).launch(dir);
            }
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.5f, 1.5f);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 0.8f);
        }
    }

    private void spawnArmedParticles() {
        for (ShardProjectile sp : shards) {
            if (Math.random() < 0.3) {
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, sp.getLocation(), 1, 0.05, 0.05, 0.05, 0.01);
            }
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
        return "FrostShards";
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
        return "Summon orbiting ice shards that build up around you, then fling them all in a wide spread.";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Activate — shards build up automatically.\nLeft Click again when fully armed: Launch all shards.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        for (ShardProjectile sp : shards) sp.destroy();
    }

    @Override
    public void load() {
        abilityListener = new FrostShardsListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostShards.Cooldown", 6000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostShards.Damage", 2.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostShards.ShardRange", 20.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostShards.OrbitRadius", 0.9);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostShards.MaxShards", 6);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostShards.BuildInterval", 400L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostShards.MaxLifetime", 12000L);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }

    private static class ShardProjectile {
        private final Player player;
        private Location location;
        private final Location startLocation;
        private Vector velocity;
        private boolean launched;
        private GameObject shardObj;
        private double spinAngle;

        ShardProjectile(Player player, double phaseOffset) {
            this.player = player;
            this.location = player.getLocation().clone();
            this.startLocation = this.location.clone();
            this.launched = false;
            this.spinAngle = phaseOffset;

            this.shardObj = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    this.location, new Vector(0.08, 0.08, 0.35),
                    new Vector(0, 0, 0), new Vector());
            this.shardObj.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
            this.shardObj.setBlockMaterial(Material.BLUE_ICE);
        }

        void updateOrbit(Location center, double radius, double angle) {
            double rad = Math.toRadians(angle);
            this.location = center.clone().add(Math.cos(rad) * radius, 0, Math.sin(rad) * radius);
            this.spinAngle = angle;
        }

        void launch(Vector direction) {
            this.launched = true;
            this.velocity = direction.clone().multiply(1.7);
        }

        void updateFlight() {
            this.velocity.add(new Vector(0, -0.02, 0));
            this.location.add(velocity);
            this.spinAngle += 15;
        }

        boolean checkHit(double damage, FrostShards ability) {
            if (this.location.getBlock().isSolid()) return true;
            for (Entity e : GeneralMethods.getEntitiesAroundPoint(this.location, 0.55)) {
                if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                    DamageHandler.damageEntity((LivingEntity) e, damage, ability);
                    ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
                    return true;
                }
            }
            return false;
        }

        boolean isExpired(double range) {
            return this.location.distanceSquared(startLocation) > range * range;
        }

        void display() {
            double yaw = launched
                    ? Math.toDegrees(Math.atan2(-velocity.getX(), velocity.getZ()))
                    : this.spinAngle;
            double pitch = launched
                    ? -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, velocity.clone().normalize().getY()))))
                    : 0;
            this.shardObj.setLocation(this.location);
            this.shardObj.setRotation(new Vector(Math.toRadians(pitch), Math.toRadians(yaw), Math.toRadians(spinAngle)));
            this.shardObj.updateAndDisplay();
        }

        void spawnImpact() {
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, this.location, 6, 0.1, 0.1, 0.1, 0.03);
            player.getWorld().spawnParticle(Particle.DUST, this.location, 3, 0.1, 0.1, 0.1,
                    0, new Particle.DustOptions(Color.fromRGB(180, 220, 255), 0.8f));
            player.getWorld().playSound(this.location, Sound.BLOCK_GLASS_BREAK, 0.5f, 1.8f);
        }

        Location getLocation() {
            return this.location;
        }

        void destroy() {
            if (shardObj != null) shardObj.destroy();
        }
    }
}
