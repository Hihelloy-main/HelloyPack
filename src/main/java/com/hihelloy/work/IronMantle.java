package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IronMantle extends EarthAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double contactDamage;
    private double orbitRadius;
    private long duration;
    private int plateCount;
    private double rotationSpeed;
    private double verticalBob;

    private enum MantleState {
        FORMING,
        ORBITING,
        DISPERSING
    }
    private MantleState state;

    private List<OrbitalPlate> plates = new ArrayList<>();
    private double globalAngle;
    private double globalBobAngle;
    private Transition formTransition;
    private long formStartTime;
    private long disperseStartTime;

    private Set<Entity> recentlyDamaged = new HashSet<>();
    private long lastDamageCleanup = 0;

    public IronMantle(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Earth.IronMantle.Cooldown", 10000);
        this.contactDamage = config.getDouble("ExtraAbilities.Hihelloy.Earth.IronMantle.ContactDamage", 2.5);
        this.orbitRadius = config.getDouble("ExtraAbilities.Hihelloy.Earth.IronMantle.OrbitRadius", 1.2);
        this.duration = config.getLong("ExtraAbilities.Hihelloy.Earth.IronMantle.Duration", 8000);
        this.plateCount = config.getInt("ExtraAbilities.Hihelloy.Earth.IronMantle.PlateCount", 4);
        this.rotationSpeed = config.getDouble("ExtraAbilities.Hihelloy.Earth.IronMantle.RotationSpeed", 6.0);
        this.verticalBob = config.getDouble("ExtraAbilities.Hihelloy.Earth.IronMantle.VerticalBob", 0.3);

        this.state = MantleState.FORMING;
        this.globalAngle = 0;
        this.globalBobAngle = 0;
        this.formStartTime = System.currentTimeMillis();

        Material[] plateMats = {Material.IRON_BLOCK, Material.CHISELED_STONE_BRICKS, Material.POLISHED_ANDESITE, Material.DEEPSLATE_BRICKS};

        for (int i = 0; i < plateCount; i++) {
            double startAngle = (360.0 / plateCount) * i;
            Material mat = plateMats[i % plateMats.length];
            plates.add(new OrbitalPlate(player, startAngle, mat));
        }

        this.formTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(orbitRadius, 1, 0),
                new Vector(20, 20, 0),
                new Vector(0, 0, 0));

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.5f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + duration && state == MantleState.ORBITING) {
            beginDispersal();
        }

        if (System.currentTimeMillis() - lastDamageCleanup > 800) {
            recentlyDamaged.clear();
            lastDamageCleanup = System.currentTimeMillis();
        }

        globalAngle += rotationSpeed;
        globalBobAngle += 4.0;

        double bobOffset = Math.sin(Math.toRadians(globalBobAngle)) * verticalBob;

        if (state == MantleState.FORMING) {
            formTransition.update();
            double currentRadius = formTransition.getX() * orbitRadius;
            double heightOffset = formTransition.getY();

            if (formTransition.getX() >= 0.98) {
                state = MantleState.ORBITING;
            }

            for (OrbitalPlate plate : plates) {
                plate.updateOrbit(player.getLocation(), currentRadius, globalAngle, bobOffset);
            }
        } else if (state == MantleState.ORBITING) {
            for (OrbitalPlate plate : plates) {
                plate.updateOrbit(player.getLocation(), orbitRadius, globalAngle, bobOffset);
            }
            checkContactDamage();
        } else if (state == MantleState.DISPERSING) {
            double elapsed = System.currentTimeMillis() - disperseStartTime;
            double progress = elapsed / 800.0;
            if (progress >= 1.0) {
                remove();
                return;
            }
            for (OrbitalPlate plate : plates) {
                plate.updateDispersal(player.getLocation(), orbitRadius + progress * 3.0, globalAngle, bobOffset + progress * 2.0);
            }
        }

        for (OrbitalPlate plate : plates) {
            plate.display();
        }
    }

    public void onSneak() {
        if (state == MantleState.ORBITING) {
            beginDispersal();
        }
    }

    private void beginDispersal() {
        state = MantleState.DISPERSING;
        disperseStartTime = System.currentTimeMillis();
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.5f, 1.4f);
    }

    private void checkContactDamage() {
        Location center = player.getLocation().add(0, 1, 0);
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center, orbitRadius + 0.8)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                for (OrbitalPlate plate : plates) {
                    if (plate.getLocation().distanceSquared(e.getLocation()) < 0.8 * 0.8) {
                        if (!recentlyDamaged.contains(e)) {
                            recentlyDamaged.add(e);
                            DamageHandler.damageEntity((LivingEntity) e, contactDamage, this);
                            Vector knockback = e.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.7);
                            e.setVelocity(e.getVelocity().add(knockback));
                            player.getWorld().spawnParticle(Particle.BLOCK,
                                    plate.getLocation(), 10, 0.1, 0.1, 0.1,
                                    0, Material.IRON_BLOCK.createBlockData());
                            player.getWorld().playSound(plate.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.2f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean isSneakAbility() { return true; }
    @Override
    public boolean isHarmlessAbility() { return false; }
    @Override
    public long getCooldown() { return this.cooldown; }
    @Override
    public Location getLocation() { return player.getLocation(); }
    @Override
    public String getName() { return "IronMantle"; }
    @Override
    public String getAuthor() { return "Hihelloy"; }
    @Override
    public String getDescription() { return "Rip stone plates from the earth and orbit them around yourself as a spinning shield!"; }
    @Override
    public String getInstructions() { return "\nSneak: Activate/deactivate orbiting stone mantle."; }
    @Override
    public String getVersion() { return "1.0"; }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        for (OrbitalPlate plate : plates) plate.destroy();
    }

    @Override
    public void load() {
        abilityListener = new IronMantleListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.IronMantle.Cooldown", 10000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.IronMantle.ContactDamage", 2.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.IronMantle.OrbitRadius", 1.2);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.IronMantle.Duration", 8000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.IronMantle.PlateCount", 4);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.IronMantle.RotationSpeed", 6.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.IronMantle.VerticalBob", 0.3);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }

    private static class OrbitalPlate {
        private final Player player;
        private final double phaseOffset;
        private GameObject plateObject;
        private Location currentLocation;

        OrbitalPlate(Player player, double phaseOffset, Material mat) {
            this.player = player;
            this.phaseOffset = phaseOffset;
            this.currentLocation = player.getLocation().clone();

            this.plateObject = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    this.currentLocation,
                    new Vector(0.5, 0.08, 0.5),
                    new Vector(0, 0, 0),
                    new Vector());
            this.plateObject.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
            this.plateObject.setBlockMaterial(mat);
        }

        void updateOrbit(Location center, double radius, double globalAngle, double bobOffset) {
            double angle = Math.toRadians(globalAngle + phaseOffset);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            this.currentLocation = center.clone().add(x, 1.0 + bobOffset, z);
            this.plateObject.setLocation(this.currentLocation);
            double tiltAngle = Math.toRadians(globalAngle + phaseOffset + 90);
            this.plateObject.setRotation(new Vector(Math.toRadians(20), tiltAngle, 0));
        }

        void updateDispersal(Location center, double radius, double globalAngle, double heightOffset) {
            double angle = Math.toRadians(globalAngle + phaseOffset);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            this.currentLocation = center.clone().add(x, 1.0 + heightOffset, z);
            this.plateObject.setLocation(this.currentLocation);
        }

        void display() {
            this.plateObject.updateAndDisplay();
        }

        Location getLocation() {
            return this.currentLocation;
        }

        void destroy() {
            if (plateObject != null) plateObject.destroy();
        }
    }
}
