package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.FlightAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.hihelloy.work.lib.GameObject;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

public class GaleRing extends FlightAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double thrustStrength;
    private double repelRadius;
    private double repelStrength;
    private long maxDuration;
    private long maxLifetime;

    private enum RingState {
        ACTIVE,
        DONE
    }
    private RingState state;

    private GameObject outerRing;
    private GameObject innerRing;
    private double ringAngle;
    private long activationTime;
    private long lastRepelTick;

    public GaleRing(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Flight.GaleRing.Cooldown", 8000);
        this.thrustStrength = config.getDouble("ExtraAbilities.Hihelloy.Flight.GaleRing.ThrustStrength", 0.6);
        this.repelRadius = config.getDouble("ExtraAbilities.Hihelloy.Flight.GaleRing.RepelRadius", 4.0);
        this.repelStrength = config.getDouble("ExtraAbilities.Hihelloy.Flight.GaleRing.RepelStrength", 0.9);
        this.maxDuration = config.getLong("ExtraAbilities.Hihelloy.Flight.GaleRing.MaxDuration", 4000);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Flight.GaleRing.MaxLifetime", 8000);

        this.state = RingState.ACTIVE;
        this.ringAngle = 0;
        this.activationTime = System.currentTimeMillis();
        this.lastRepelTick = System.currentTimeMillis();

        Location loc = player.getLocation();
        this.outerRing = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                loc, new Vector(1.2, 0.06, 1.2),
                new Vector(0, 0, 0), new Vector());
        this.outerRing.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.outerRing.setBlockMaterial(Material.WHITE_STAINED_GLASS);

        this.innerRing = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                loc, new Vector(0.8, 0.06, 0.8),
                new Vector(0, 0, 0), new Vector());
        this.innerRing.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.innerRing.setBlockMaterial(Material.LIGHT_BLUE_STAINED_GLASS);

        player.setAllowFlight(true);
        player.setFlying(true);
        playAirbendingSound(loc);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }
        if (System.currentTimeMillis() > activationTime + maxDuration && state == RingState.ACTIVE) {
            remove();
            return;
        }

        ringAngle += 9;

        if (state == RingState.ACTIVE) {
            if (!player.isSneaking()) {
                remove();
                return;
            }

            Vector dir = player.getLocation().getDirection().clone();
            dir.setY(Math.max(dir.getY(), 0));
            dir.normalize().multiply(thrustStrength);
            Vector vel = player.getVelocity().add(dir);
            if (vel.length() > thrustStrength * 3) vel.normalize().multiply(thrustStrength * 3);
            player.setVelocity(vel);

            Location center = player.getLocation().add(0, 0.5, 0);
            outerRing.setLocation(center);
            outerRing.setRotation(new Vector(0, Math.toRadians(ringAngle), 0));
            outerRing.updateAndDisplay();
            innerRing.setLocation(center);
            innerRing.setRotation(new Vector(0, Math.toRadians(-ringAngle * 1.4), 0));
            innerRing.updateAndDisplay();

            spawnRingParticles(center);
            repelNearby(center);
        }
    }

    private void repelNearby(Location center) {
        long now = System.currentTimeMillis();
        if (now - lastRepelTick < 200) return;
        lastRepelTick = now;

        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center, repelRadius)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                Vector away = e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(repelStrength);
                away.setY(Math.max(away.getY(), 0.2));
                e.setVelocity(e.getVelocity().add(away));
            }
        }
    }

    private void spawnRingParticles(Location center) {
        int points = 12;
        for (int i = 0; i < points; i++) {
            double a = Math.toRadians(ringAngle + (360.0 / points) * i);
            Location p = center.clone().add(Math.cos(a) * 1.1, 0, Math.sin(a) * 1.1);
            player.getWorld().spawnParticle(Particle.CLOUD, p, 1, 0.03, 0.03, 0.03, 0.01);
        }
        if (Math.random() < 0.3)
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 1, 0.3, 0.1, 0.3, 0);
    }

    @Override
    public boolean isSneakAbility() {
        return true;
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
        return "GaleRing";
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
        return "Generate a powerful air ring beneath you — hold sneak to fly forward and repel nearby enemies.";
    }

    @Override
    public String getInstructions() {
        return "\nHold Sneak: Activate gale ring and fly in your look direction.\nRelease Sneak: Deactivate.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (outerRing != null) outerRing.destroy();
        if (innerRing != null) innerRing.destroy();
        if (player.isOnline() && player.getAllowFlight() && !player.getGameMode().equals(org.bukkit.GameMode.CREATIVE)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    @Override
    public void load() {
        abilityListener = new GaleRingListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Flight.GaleRing.Cooldown", 8000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Flight.GaleRing.ThrustStrength", 0.6);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Flight.GaleRing.RepelRadius", 4.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Flight.GaleRing.RepelStrength", 0.9);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Flight.GaleRing.MaxDuration", 4000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Flight.GaleRing.MaxLifetime", 8000L);
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
