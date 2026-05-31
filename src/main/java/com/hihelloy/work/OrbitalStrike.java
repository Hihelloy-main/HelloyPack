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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class OrbitalStrike extends AirAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double projectileSpeed;
    private double chargeRadius;
    private int maxBlades;
    private long chargeTime;
    private long maxLifetime;

    private enum StrikeState {
        CHARGING,
        LAUNCHING,
        DONE
    }
    private StrikeState state;

    private List<AirBlade> blades = new ArrayList<>();
    private double chargeOrbitAngle;
    private double chargeOrbitSpeed;
    private long chargeStartTime;
    private int bladesLaunched;

    private Transition chargeGrowTransition;

    public OrbitalStrike(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Air.OrbitalStrike.Cooldown", 9000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Air.OrbitalStrike.Damage", 3.0);
        this.projectileSpeed = config.getDouble("ExtraAbilities.Hihelloy.Air.OrbitalStrike.ProjectileSpeed", 1.5);
        this.chargeRadius = config.getDouble("ExtraAbilities.Hihelloy.Air.OrbitalStrike.ChargeRadius", 1.5);
        this.maxBlades = config.getInt("ExtraAbilities.Hihelloy.Air.OrbitalStrike.MaxBlades", 5);
        this.chargeTime = config.getLong("ExtraAbilities.Hihelloy.Air.OrbitalStrike.ChargeTime", 2500);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Air.OrbitalStrike.MaxLifetime", 12000);

        this.state = StrikeState.CHARGING;
        this.chargeOrbitAngle = 0;
        this.chargeOrbitSpeed = 8.0;
        this.chargeStartTime = System.currentTimeMillis();
        this.bladesLaunched = 0;

        this.chargeGrowTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(this.maxBlades, 0, 0),
                new Vector((double) this.chargeTime / 50.0, 0, 0),
                new Vector(0, 0, 0));

        playAirbendingSound(player.getLocation());
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        chargeOrbitAngle += chargeOrbitSpeed;

        if (state == StrikeState.CHARGING) {
            chargeGrowTransition.update();
            chargeOrbitSpeed = 8.0 + (chargeGrowTransition.getX() / maxBlades) * 16.0;

            int targetBladeCount = (int) Math.ceil(chargeGrowTransition.getX());
            while (blades.size() < targetBladeCount && blades.size() < maxBlades) {
                double spawnAngle = (360.0 / maxBlades) * blades.size();
                blades.add(new AirBlade(player, spawnAngle));
            }

            for (int i = 0; i < blades.size(); i++) {
                double orbitAngle = chargeOrbitAngle + (360.0 / blades.size()) * i;
                blades.get(i).updateOrbit(player.getLocation(), chargeRadius, orbitAngle);
            }

            spawnChargeParticles();

            if (!player.isSneaking() && System.currentTimeMillis() > chargeStartTime + chargeTime) {
                state = StrikeState.LAUNCHING;
                bladesLaunched = 0;
                playAirbendingSound(player.getLocation());
            }
        } else if (state == StrikeState.LAUNCHING) {
            if (bladesLaunched < blades.size()) {
                AirBlade blade = blades.get(bladesLaunched);
                if (!blade.isLaunched()) {
                    blade.launch(player.getLocation().getDirection().clone());
                    bladesLaunched++;
                }
            }

            for (int i = blades.size() - 1; i >= 0; i--) {
                AirBlade blade = blades.get(i);
                if (blade.isLaunched()) {
                    blade.updateProjectile(projectileSpeed);
                    if (blade.checkHit(damage, this) || blade.isExpired()) {
                        blade.destroy();
                        blades.remove(i);
                    }
                } else {
                    double orbitAngle = chargeOrbitAngle + (360.0 / blades.size()) * i;
                    blade.updateOrbit(player.getLocation(), chargeRadius, orbitAngle);
                }
            }

            if (blades.isEmpty()) {
                remove();
                return;
            }
        }

        for (AirBlade blade : blades) {
            blade.display();
        }
    }

    public void onSneak() {
        if (state == StrikeState.CHARGING && System.currentTimeMillis() > chargeStartTime + 400) {
            remove();
        }
    }

    private void spawnChargeParticles() {
        Location center = player.getLocation().add(0, 1, 0);
        for (int i = 0; i < 3; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = chargeRadius * Math.random();
            Location particleLoc = center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public boolean isSneakAbility() { return false; }
    @Override
    public boolean isHarmlessAbility() { return false; }
    @Override
    public long getCooldown() { return this.cooldown; }
    @Override
    public Location getLocation() { return player.getLocation(); }
    @Override
    public String getName() { return "OrbitalStrike"; }
    @Override
    public String getAuthor() { return "Hihelloy"; }
    @Override
    public String getDescription() { return "Charge rotating air blades around yourself, then release them as projectiles in a forward fan!"; }
    @Override
    public String getInstructions() { return "\nHold Sneak: Charge air blades (release to fire them)\nSneak briefly and release: Cancel."; }
    @Override
    public String getVersion() { return "1.0"; }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        for (AirBlade blade : blades) blade.destroy();
    }

    @Override
    public void load() {
        abilityListener = new OrbitalStrikeListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.OrbitalStrike.Cooldown", 9000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.OrbitalStrike.Damage", 3.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.OrbitalStrike.ProjectileSpeed", 1.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.OrbitalStrike.ChargeRadius", 1.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.OrbitalStrike.MaxBlades", 5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.OrbitalStrike.ChargeTime", 2500L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.OrbitalStrike.MaxLifetime", 12000L);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }

    private static class AirBlade {
        private final Player player;
        private final double phaseOffset;
        private boolean launched;
        private boolean expired;
        private Location location;
        private Vector projectileVelocity;
        private Location startLocation;
        private double maxRange = 20.0;

        private GameObject bladeObj;
        private double displayYaw;
        private double displayRoll;

        AirBlade(Player player, double phaseOffset) {
            this.player = player;
            this.phaseOffset = phaseOffset;
            this.launched = false;
            this.expired = false;
            this.location = player.getLocation().clone();
            this.displayYaw = phaseOffset;
            this.displayRoll = 0;

            this.bladeObj = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    this.location,
                    new Vector(0.08, 0.5, 0.08),
                    new Vector(0, 0, 0),
                    new Vector());
            this.bladeObj.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
            this.bladeObj.setBlockMaterial(Material.LIGHT_BLUE_STAINED_GLASS);
        }

        void updateOrbit(Location playerLoc, double radius, double angle) {
            double rad = Math.toRadians(angle);
            double x = Math.cos(rad) * radius;
            double z = Math.sin(rad) * radius;
            this.location = playerLoc.clone().add(x, 1.2, z);
            this.displayYaw = angle;
            this.displayRoll += 8;
        }

        void launch(Vector direction) {
            this.launched = true;
            this.startLocation = this.location.clone();
            double spread = (Math.random() - 0.5) * 0.4;
            this.projectileVelocity = direction.clone().rotateAroundY(spread).normalize();
        }

        void updateProjectile(double speed) {
            this.projectileVelocity.add(new Vector(0, -0.02, 0));
            if (this.projectileVelocity.length() > speed) this.projectileVelocity.normalize().multiply(speed);
            this.location.add(projectileVelocity);
            this.displayYaw += 12;
            this.displayRoll += 20;

            if (this.location.distanceSquared(startLocation) > maxRange * maxRange) expired = true;
            if (this.location.getBlock().isSolid()) {
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 3, 0.1, 0.1, 0.1, 0);
                expired = true;
            }

            player.getWorld().spawnParticle(Particle.CLOUD, location, 1, 0.05, 0.05, 0.05, 0.02);
        }

        boolean checkHit(double damage, OrbitalStrike ability) {
            for (Entity e : GeneralMethods.getEntitiesAroundPoint(location, 0.7)) {
                if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                    DamageHandler.damageEntity((LivingEntity) e, damage, ability);
                    Vector knockback = projectileVelocity.clone().normalize().multiply(0.5);
                    e.setVelocity(e.getVelocity().add(knockback));
                    player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, location, 5, 0.2, 0.2, 0.2, 0);
                    playAirbendingSound(location);
                    return true;
                }
            }
            return false;
        }

        void display() {
            this.bladeObj.setLocation(this.location);
            this.bladeObj.setRotation(new Vector(Math.toRadians(displayRoll), Math.toRadians(displayYaw), 0));
            this.bladeObj.updateAndDisplay();
        }

        boolean isLaunched() { return launched; }
        boolean isExpired() { return expired; }

        void destroy() {
            if (bladeObj != null) bladeObj.destroy();
        }
    }
}
