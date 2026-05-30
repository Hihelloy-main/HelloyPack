package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
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

import java.util.HashSet;
import java.util.Set;

public class EmberStaff extends FireAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double sweepDamage;
    private double fireBallDamage;
    private double fireBallSpeed;
    private double sweepRadius;
    private long chargeTime;
    private long maxLifetime;

    private enum StaffState {
        IDLE,
        CHARGING,
        SWEEPING,
        FIREBALL
    }
    private StaffState state;

    private GameObject staffCore;
    private GameObject staffHead;
    private GameObject staffTail;
    private GameObject emberRing1;
    private GameObject emberRing2;

    private double staffYaw;
    private double staffRoll;
    private double ringAngle;
    private double ringSpeed;

    private Transition chargeTransition;
    private Transition sweepTransition;
    private long chargeStartTime;
    private long sweepStartTime;

    private Location fireballLoc;
    private Vector fireballVelocity;
    private Location fireballStartLoc;
    private double fireballRange;

    private Set<Entity> sweepHit = new HashSet<>();

    public EmberStaff(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Fire.EmberStaff.Cooldown", 8000);
        this.sweepDamage = config.getDouble("ExtraAbilities.Hihelloy.Fire.EmberStaff.SweepDamage", 2.5);
        this.fireBallDamage = config.getDouble("ExtraAbilities.Hihelloy.Fire.EmberStaff.FireballDamage", 5.0);
        this.fireBallSpeed = config.getDouble("ExtraAbilities.Hihelloy.Fire.EmberStaff.FireballSpeed", 1.4);
        this.sweepRadius = config.getDouble("ExtraAbilities.Hihelloy.Fire.EmberStaff.SweepRadius", 3.0);
        this.chargeTime = config.getLong("ExtraAbilities.Hihelloy.Fire.EmberStaff.ChargeTime", 1500);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Fire.EmberStaff.MaxLifetime", 15000);
        this.fireballRange = 20.0;

        this.state = StaffState.IDLE;
        this.staffYaw = -player.getLocation().getYaw();
        this.staffRoll = 0;
        this.ringAngle = 0;
        this.ringSpeed = 4;

        Location loc = GeneralMethods.getMainHandLocation(player);

        this.staffCore = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                loc, new Vector(0.1, 0.1, 0.9),
                new Vector(0, Math.toRadians(staffYaw), 0), new Vector());
        this.staffCore.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
        this.staffCore.setBlockMaterial(Material.BASALT);

        this.staffHead = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.staffCore, new Vector(0.25, 0.25, 0.25),
                new Vector(0, 0, 0), new Vector(0, 0, 0.9));
        this.staffHead.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.staffHead.setBlockMaterial(Material.MAGMA_BLOCK);

        this.staffTail = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.staffCore, new Vector(0.15, 0.15, 0.15),
                new Vector(0, 0, 0), new Vector(0, 0, -0.05));
        this.staffTail.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.staffTail.setBlockMaterial(Material.BLACKSTONE);

        this.emberRing1 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.staffCore, new Vector(0.4, 0.06, 0.4),
                new Vector(0, 0, 0), new Vector(0, 0, 0.7));
        this.emberRing1.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.emberRing1.setBlockMaterial(Material.SHROOMLIGHT);

        this.emberRing2 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.staffCore, new Vector(0.3, 0.06, 0.3),
                new Vector(0, 0, 0), new Vector(0, 0, 0.5));
        this.emberRing2.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.emberRing2.setBlockMaterial(Material.GLOWSTONE);

        player.getWorld().playSound(loc, Sound.BLOCK_CAMPFIRE_CRACKLE, 1.0f, 1.2f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        ringAngle += ringSpeed;
        staffYaw = -player.getLocation().getYaw();

        if (state == StaffState.IDLE) {
            Location handLoc = GeneralMethods.getMainHandLocation(player);
            staffCore.setLocation(handLoc);
            staffCore.setRotation(new Vector(0, Math.toRadians(staffYaw), Math.toRadians(staffRoll)));
            staffCore.updateAndDisplay();
            staffHead.setRotation(new Vector(Math.toRadians(ringAngle * 0.5), 0, 0));
            staffHead.updateAndDisplay();
            staffTail.updateAndDisplay();
            emberRing1.setRotation(new Vector(0, Math.toRadians(ringAngle), 0));
            emberRing1.updateAndDisplay();
            emberRing2.setRotation(new Vector(0, Math.toRadians(-ringAngle * 1.3), 0));
            emberRing2.updateAndDisplay();
            spawnIdleParticles(handLoc);
        } else if (state == StaffState.CHARGING) {
            Location handLoc = GeneralMethods.getMainHandLocation(player);
            chargeTransition.update();
            ringSpeed = 4 + chargeTransition.getX() * 20;
            double chargeScale = 1.0 + chargeTransition.getX() * 0.6;

            staffCore.setLocation(handLoc);
            staffCore.setRotation(new Vector(0, Math.toRadians(staffYaw), Math.toRadians(staffRoll)));
            staffCore.updateAndDisplay();
            staffHead.setRotation(new Vector(Math.toRadians(ringAngle), Math.toRadians(ringAngle * 0.5), 0));
            staffHead.updateAndDisplay();
            staffTail.updateAndDisplay();
            emberRing1.setRotation(new Vector(0, Math.toRadians(ringAngle), 0));
            emberRing1.updateAndDisplay();
            emberRing2.setRotation(new Vector(Math.toRadians(ringAngle * 0.7), Math.toRadians(-ringAngle * 1.3), 0));
            emberRing2.updateAndDisplay();

            spawnChargeParticles(handLoc);

            if (!player.isSneaking()) {
                long elapsed = System.currentTimeMillis() - chargeStartTime;
                if (elapsed >= chargeTime) {
                    beginFireball(handLoc);
                } else {
                    beginSweep(handLoc);
                }
            }
        } else if (state == StaffState.SWEEPING) {
            sweepTransition.update();
            double sweepProgress = sweepTransition.getX();
            staffRoll = sweepProgress * 360;
            ringSpeed = 25;

            Location handLoc = GeneralMethods.getMainHandLocation(player);
            staffCore.setLocation(handLoc);
            staffCore.setRotation(new Vector(0, Math.toRadians(staffYaw), Math.toRadians(staffRoll)));
            staffCore.updateAndDisplay();
            staffHead.setRotation(new Vector(Math.toRadians(ringAngle), 0, 0));
            staffHead.updateAndDisplay();
            staffTail.updateAndDisplay();
            emberRing1.setRotation(new Vector(0, Math.toRadians(ringAngle), 0));
            emberRing1.updateAndDisplay();
            emberRing2.setRotation(new Vector(0, Math.toRadians(-ringAngle), 0));
            emberRing2.updateAndDisplay();

            checkSweepHits(handLoc);
            spawnSweepParticles(handLoc, sweepProgress);

            if (sweepTransition.getX() >= 0.99) {
                staffRoll = 0;
                ringSpeed = 4;
                sweepHit.clear();
                state = StaffState.IDLE;
            }
        } else if (state == StaffState.FIREBALL) {
            fireballLoc.add(fireballVelocity);

            if (fireballLoc.distanceSquared(fireballStartLoc) > fireballRange * fireballRange) {
                spawnFireballImpact(fireballLoc);
                remove();
                return;
            }
            if (fireballLoc.getBlock().isSolid()) {
                spawnFireballImpact(fireballLoc);
                remove();
                return;
            }

            checkFireballHit();

            staffCore.setLocation(fireballLoc);
            staffCore.setRotation(new Vector(0, Math.toRadians(staffYaw + ringAngle), Math.toRadians(ringAngle * 2)));
            staffCore.updateAndDisplay();
            staffHead.setRotation(new Vector(Math.toRadians(ringAngle), Math.toRadians(ringAngle), 0));
            staffHead.updateAndDisplay();
            staffTail.updateAndDisplay();
            emberRing1.setRotation(new Vector(0, Math.toRadians(ringAngle), 0));
            emberRing1.updateAndDisplay();
            emberRing2.setRotation(new Vector(0, Math.toRadians(-ringAngle * 1.5), 0));
            emberRing2.updateAndDisplay();

            player.getWorld().spawnParticle(Particle.FLAME, fireballLoc, 4, 0.15, 0.15, 0.15, 0.02);
            player.getWorld().spawnParticle(Particle.LAVA, fireballLoc, 1, 0.1, 0.1, 0.1, 0);
        }
    }

    public void onSneak() {
        if (state == StaffState.IDLE) {
            state = StaffState.CHARGING;
            chargeStartTime = System.currentTimeMillis();
            chargeTransition = new Transition(
                    new Vector(0, 0, 0),
                    new Vector(1, 0, 0),
                    new Vector((double) chargeTime / 50.0, 0, 0),
                    new Vector(0, 0, 0));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1.5f, 1.0f);
        }
    }

    public void onLeftClick() {
        if (state == StaffState.IDLE) {
            beginSweep(GeneralMethods.getMainHandLocation(player));
        }
    }

    private void beginSweep(Location from) {
        state = StaffState.SWEEPING;
        sweepStartTime = System.currentTimeMillis();
        sweepTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(16, 0, 0),
                new Vector(0, 0, 0));
        player.getWorld().playSound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.8f);
        player.getWorld().playSound(from, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.5f);
    }

    private void beginFireball(Location from) {
        state = StaffState.FIREBALL;
        fireballLoc = from.clone();
        fireballStartLoc = from.clone();
        fireballVelocity = player.getLocation().getDirection().clone().multiply(fireBallSpeed);
        ringSpeed = 30;
        player.getWorld().playSound(from, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.7f);
        player.getWorld().playSound(from, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.5f);
    }

    private void checkSweepHits(Location center) {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center, sweepRadius)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                if (!sweepHit.contains(e)) {
                    sweepHit.add(e);
                    DamageHandler.damageEntity((LivingEntity) e, sweepDamage, this);
                    e.setFireTicks(80);
                    Vector kb = e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.9);
                    e.setVelocity(e.getVelocity().add(kb));
                    player.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.2f);
                }
            }
        }
    }

    private void checkFireballHit() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(fireballLoc, 1.0)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                DamageHandler.damageEntity((LivingEntity) e, fireBallDamage, this);
                ((LivingEntity) e).setFireTicks(120);
                spawnFireballImpact(fireballLoc);
                remove();
                return;
            }
        }
    }

    private void spawnIdleParticles(Location loc) {
        if (Math.random() < 0.4) {
            player.getWorld().spawnParticle(Particle.SMALL_FLAME, loc, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private void spawnChargeParticles(Location loc) {
        player.getWorld().spawnParticle(Particle.FLAME, loc, 2, 0.2, 0.2, 0.2, 0.03);
        player.getWorld().spawnParticle(Particle.LAVA, loc, 1, 0.05, 0.05, 0.05, 0);
    }

    private void spawnSweepParticles(Location center, double progress) {
        double arcAngle = Math.toRadians(progress * 360 + staffYaw);
        for (int i = 0; i < 3; i++) {
            double a = arcAngle + Math.toRadians(i * 30);
            double r = sweepRadius * (0.5 + Math.random() * 0.5);
            Location p = center.clone().add(Math.cos(a) * r, 0.2 + Math.random() * 1.2, Math.sin(a) * r);
            player.getWorld().spawnParticle(Particle.FLAME, p, 1, 0.05, 0.05, 0.05, 0.04);
        }
    }

    private void spawnFireballImpact(Location loc) {
        player.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
        player.getWorld().spawnParticle(Particle.FLAME, loc, 30, 0.5, 0.5, 0.5, 0.12);
        player.getWorld().spawnParticle(Particle.LAVA, loc, 12, 0.3, 0.3, 0.3, 0);
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
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
        return "EmberStaff";
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
        return "Conjure a staff of living fire — sweep it to blast nearby foes, or charge and hurl a magma fireball.";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Flame sweep\nHold Sneak: Charge\nRelease Sneak (full charge): Launch fireball\nRelease Sneak (partial): Short sweep";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (staffCore != null) staffCore.destroy();
        if (staffHead != null) staffHead.destroy();
        if (staffTail != null) staffTail.destroy();
        if (emberRing1 != null) emberRing1.destroy();
        if (emberRing2 != null) emberRing2.destroy();
    }

    @Override
    public void load() {
        abilityListener = new EmberStaffListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.EmberStaff.Cooldown", 8000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.EmberStaff.SweepDamage", 2.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.EmberStaff.FireballDamage", 5.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.EmberStaff.FireballSpeed", 1.4);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.EmberStaff.SweepRadius", 3.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.EmberStaff.ChargeTime", 1500L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.EmberStaff.MaxLifetime", 15000L);
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
