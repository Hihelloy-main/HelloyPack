package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.LightningAbility;
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

public class LightningCoil extends LightningAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double chainRange;
    private int chainJumps;
    private double boltSpeed;
    private long chargeTime;
    private long maxLifetime;

    private enum CoilState {
        CHARGING,
        FIRING,
        CHAINING
    }
    private CoilState state;

    private List<GameObject> coilRings = new ArrayList<>();
    private double coilAngle;
    private double coilRadius;
    private Transition chargeTransition;
    private long chargeStartTime;

    private Location boltLoc;
    private Vector boltVelocity;
    private Location boltStart;
    private LivingEntity currentTarget;
    private List<LivingEntity> chainedTargets = new ArrayList<>();
    private int chainsLeft;
    private long lastArcTime;

    public LightningCoil(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Lightning.LightningCoil.Cooldown", 7000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Lightning.LightningCoil.Damage", 4.0);
        this.chainRange = config.getDouble("ExtraAbilities.Hihelloy.Lightning.LightningCoil.ChainRange", 6.0);
        this.chainJumps = config.getInt("ExtraAbilities.Hihelloy.Lightning.LightningCoil.ChainJumps", 3);
        this.boltSpeed = config.getDouble("ExtraAbilities.Hihelloy.Lightning.LightningCoil.BoltSpeed", 2.2);
        this.chargeTime = config.getLong("ExtraAbilities.Hihelloy.Lightning.LightningCoil.ChargeTime", 1200);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Lightning.LightningCoil.MaxLifetime", 8000);

        this.state = CoilState.CHARGING;
        this.coilAngle = 0;
        this.coilRadius = 0;
        this.chainsLeft = chainJumps;
        this.chargeStartTime = System.currentTimeMillis();

        this.chargeTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(chargeTime / 50.0, 0, 0),
                new Vector(0, 0, 0));

        for (int i = 0; i < 3; i++) {
            Location loc = GeneralMethods.getMainHandLocation(player);
            GameObject ring = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    loc, new Vector(0.06, 0.06, 0.3),
                    new Vector(0, 0, 0), new Vector());
            ring.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
            ring.setBlockMaterial(Material.SHROOMLIGHT);
            coilRings.add(ring);
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.8f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        coilAngle += 14;

        if (state == CoilState.CHARGING) {
            chargeTransition.update();
            coilRadius = chargeTransition.getX() * 0.5;
            Location hand = GeneralMethods.getMainHandLocation(player);

            for (int i = 0; i < coilRings.size(); i++) {
                double a = Math.toRadians(coilAngle + (120.0 * i));
                double yaw = Math.toRadians(-player.getLocation().getYaw());
                Vector right = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
                Vector up = new Vector(0, 1, 0);
                Location ringLoc = hand.clone()
                        .add(right.multiply(Math.cos(a) * coilRadius))
                        .add(up.multiply(Math.sin(a) * coilRadius));
                coilRings.get(i).setLocation(ringLoc);
                coilRings.get(i).setRotation(new Vector(Math.toRadians(coilAngle * 2 + i * 60), yaw, 0));
                coilRings.get(i).updateAndDisplay();
            }

            spawnChargeParticles(hand);

            if (!player.isSneaking()) {
                if (System.currentTimeMillis() >= chargeStartTime + chargeTime) {
                    beginFiring(hand);
                }
            }

        } else if (state == CoilState.FIRING) {
            boltVelocity.add(new Vector(0, -0.03, 0));
            boltLoc.add(boltVelocity);
            spawnBoltParticles(boltLoc);

            if (boltLoc.distanceSquared(boltStart) > 30 * 30 || boltLoc.getBlock().isSolid()) {
                remove();
                return;
            }

            LivingEntity hit = checkBoltHit(boltLoc);
            if (hit != null) {
                DamageHandler.damageEntity(hit, damage, this);
                spawnStrikeEffect(boltLoc);
                currentTarget = hit;
                chainedTargets.add(hit);
                if (chainsLeft > 0) {
                    state = CoilState.CHAINING;
                    lastArcTime = System.currentTimeMillis();
                } else {
                    remove();
                }
            }

        } else if (state == CoilState.CHAINING) {
            if (System.currentTimeMillis() - lastArcTime < 120) return;

            LivingEntity next = findChainTarget(currentTarget);
            if (next == null || chainsLeft <= 0) {
                remove();
                return;
            }

            spawnChainArc(currentTarget.getLocation().add(0, 1, 0), next.getLocation().add(0, 1, 0));
            DamageHandler.damageEntity(next, damage * 0.6, this);
            spawnStrikeEffect(next.getLocation().add(0, 1, 0));
            chainedTargets.add(next);
            currentTarget = next;
            chainsLeft--;
            lastArcTime = System.currentTimeMillis();

            if (chainsLeft <= 0) {
                remove();
            }
        }
    }

    public void onSneak() {
        if (state == CoilState.CHARGING && System.currentTimeMillis() >= chargeStartTime + chargeTime) {
            beginFiring(GeneralMethods.getMainHandLocation(player));
        }
    }

    private void beginFiring(Location from) {
        state = CoilState.FIRING;
        for (GameObject r : coilRings) r.destroy();
        coilRings.clear();
        boltLoc = from.clone();
        boltStart = from.clone();
        boltVelocity = player.getLocation().getDirection().clone().multiply(boltSpeed);
        player.getWorld().playSound(from, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.5f);
    }

    private LivingEntity checkBoltHit(Location loc) {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(loc, 0.8)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                return (LivingEntity) e;
            }
        }
        return null;
    }

    private LivingEntity findChainTarget(LivingEntity from) {
        LivingEntity nearest = null;
        double nearestDist = chainRange * chainRange;
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(from.getLocation(), chainRange)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId()) && !chainedTargets.contains(e)) {
                double d = e.getLocation().distanceSquared(from.getLocation());
                if (d < nearestDist) {
                    nearestDist = d;
                    nearest = (LivingEntity) e;
                }
            }
        }
        return nearest;
    }

    private void spawnChainArc(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double len = dir.length();
        int segments = (int) (len * 4);
        dir.normalize();
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Location p = from.clone().add(dir.clone().multiply(len * t));
            p.add(new Vector((Math.random() - 0.5) * 0.4, (Math.random() - 0.5) * 0.4, (Math.random() - 0.5) * 0.4));
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p, 1, 0, 0, 0, 0);
        }
        player.getWorld().playSound(from, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.6f);
    }

    private void spawnBoltParticles(Location loc) {
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 3, 0.1, 0.1, 0.1, 0.02);
        player.getWorld().spawnParticle(Particle.DUST, loc, 2, 0.05, 0.05, 0.05,
                0, new Particle.DustOptions(Color.fromRGB(200, 220, 255), 0.6f));
    }

    private void spawnChargeParticles(Location hand) {
        if (Math.random() < 0.5)
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, hand, 1, 0.15, 0.15, 0.15, 0.01);
    }

    private void spawnStrikeEffect(Location loc) {
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 20, 0.3, 0.3, 0.3, 0.05);
        player.getWorld().spawnParticle(Particle.CRIT, loc, 3, 0.1, 0.1, 0.1, 0);
        player.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.3f);
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
        return "LightningCoil";
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
        return "Charge a coil of lightning around your hand then fire a bolt that chains between nearby enemies.";
    }

    @Override
    public String getInstructions() {
        return "\nHold Sneak: Charge\nRelease Sneak: Fire bolt — it chains up to 3 times.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        for (GameObject r : coilRings) r.destroy();
    }

    @Override
    public void load() {
        abilityListener = new LightningCoilListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lightning.LightningCoil.Cooldown", 7000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lightning.LightningCoil.Damage", 4.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lightning.LightningCoil.ChainRange", 6.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lightning.LightningCoil.ChainJumps", 3);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lightning.LightningCoil.BoltSpeed", 2.2);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lightning.LightningCoil.ChargeTime", 1200L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lightning.LightningCoil.MaxLifetime", 8000L);
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
