package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.CombustionAbility;
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

public class CombustionRound extends CombustionAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double blastRadius;
    private double projectileSpeed;
    private long chargeTime;
    private long maxLifetime;

    private enum RoundState {
        CHARGING,
        FLYING
    }
    private RoundState state;

    private GameObject outerShell;
    private GameObject innerCore;
    private GameObject chargeRing;

    private double shellSpin;
    private double coreSpin;
    private Transition chargeTransition;
    private long chargeStartTime;

    private Location roundLoc;
    private Vector roundVelocity;
    private Location roundStart;

    public CombustionRound(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Combustion.CombustionRound.Cooldown", 9000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Combustion.CombustionRound.Damage", 7.0);
        this.blastRadius = config.getDouble("ExtraAbilities.Hihelloy.Combustion.CombustionRound.BlastRadius", 4.0);
        this.projectileSpeed = config.getDouble("ExtraAbilities.Hihelloy.Combustion.CombustionRound.ProjectileSpeed", 1.1);
        this.chargeTime = config.getLong("ExtraAbilities.Hihelloy.Combustion.CombustionRound.ChargeTime", 1500);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Combustion.CombustionRound.MaxLifetime", 10000);

        this.state = RoundState.CHARGING;
        this.shellSpin = 0;
        this.coreSpin = 0;
        this.chargeStartTime = System.currentTimeMillis();

        Location hand = GeneralMethods.getMainHandLocation(player);

        this.outerShell = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                hand, new Vector(0.4, 0.4, 0.4),
                new Vector(0, 0, 0), new Vector());
        this.outerShell.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.outerShell.setBlockMaterial(Material.BLACKSTONE);

        this.innerCore = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                hand, new Vector(0.22, 0.22, 0.22),
                new Vector(0, 0, 0), new Vector());
        this.innerCore.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.innerCore.setBlockMaterial(Material.SHROOMLIGHT);

        this.chargeRing = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                hand, new Vector(0.6, 0.04, 0.6),
                new Vector(0, 0, 0), new Vector());
        this.chargeRing.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.chargeRing.setBlockMaterial(Material.MAGMA_BLOCK);

        this.chargeTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(chargeTime / 50.0, 0, 0),
                new Vector(0, 0, 0));

        player.getWorld().playSound(hand, Sound.ENTITY_BLAZE_AMBIENT, 1.2f, 0.5f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        shellSpin += 8;
        coreSpin -= 12;

        if (state == RoundState.CHARGING) {
            chargeTransition.update();
            double charge = chargeTransition.getX();
            Location hand = GeneralMethods.getMainHandLocation(player);

            outerShell.setLocation(hand);
            outerShell.setRotation(new Vector(Math.toRadians(shellSpin), Math.toRadians(shellSpin * 0.7), 0));
            outerShell.updateAndDisplay();

            innerCore.setLocation(hand);
            innerCore.setRotation(new Vector(Math.toRadians(coreSpin), Math.toRadians(coreSpin * 0.5), 0));
            innerCore.updateAndDisplay();

            chargeRing.setLocation(hand);
            chargeRing.setRotation(new Vector(0, Math.toRadians(shellSpin * 1.5), 0));
            chargeRing.setScale(new Vector(0.3 + charge * 0.4, 0.04, 0.3 + charge * 0.4));
            chargeRing.updateAndDisplay();

            spawnChargeParticles(hand, charge);

            if (!player.isSneaking() && System.currentTimeMillis() >= chargeStartTime + chargeTime) {
                beginFlight(hand);
            }

        } else if (state == RoundState.FLYING) {
            roundVelocity.add(new Vector(0, -0.015, 0));
            roundLoc.add(roundVelocity);

            outerShell.setLocation(roundLoc);
            outerShell.setRotation(new Vector(Math.toRadians(shellSpin), Math.toRadians(shellSpin * 0.7), 0));
            outerShell.updateAndDisplay();

            innerCore.setLocation(roundLoc);
            innerCore.setRotation(new Vector(Math.toRadians(coreSpin), Math.toRadians(coreSpin * 0.5), 0));
            innerCore.updateAndDisplay();

            player.getWorld().spawnParticle(Particle.FLAME, roundLoc, 2, 0.1, 0.1, 0.1, 0.02);

            if (roundLoc.distanceSquared(roundStart) > 35 * 35) {
                detonate(roundLoc);
                return;
            }
            if (roundLoc.getBlock().isSolid()) {
                detonate(roundLoc);
                return;
            }
            for (Entity e : GeneralMethods.getEntitiesAroundPoint(roundLoc, 0.7)) {
                if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                    detonate(roundLoc);
                    return;
                }
            }
        }
    }

    public void onSneak() {
        if (state == RoundState.CHARGING && System.currentTimeMillis() >= chargeStartTime + chargeTime) {
            beginFlight(GeneralMethods.getMainHandLocation(player));
        }
    }

    private void beginFlight(Location from) {
        state = RoundState.FLYING;
        chargeRing.destroy();
        chargeRing = null;
        roundLoc = from.clone();
        roundStart = from.clone();
        roundVelocity = player.getLocation().getDirection().clone().multiply(projectileSpeed);
        player.getWorld().playSound(from, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.8f);
    }

    private void detonate(Location loc) {
        player.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.4, 0.4, 0.4, 0);
        player.getWorld().spawnParticle(Particle.FLAME, loc, 40, 0.6, 0.6, 0.6, 0.15);
        player.getWorld().spawnParticle(Particle.LAVA, loc, 15, 0.4, 0.4, 0.4, 0);
        player.getWorld().spawnParticle(Particle.SMOKE, loc, 20, 0.5, 0.5, 0.5, 0.04);
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.9f);

        Set<Entity> damaged = new HashSet<>();
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(loc, blastRadius)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId()) && !damaged.contains(e)) {
                damaged.add(e);
                double dist = e.getLocation().distance(loc);
                double falloff = 1.0 - (dist / blastRadius) * 0.5;
                DamageHandler.damageEntity((LivingEntity) e, damage * falloff, this);
                Vector kb = e.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.2);
                kb.setY(0.6);
                e.setVelocity(e.getVelocity().add(kb));
            }
        }
        remove();
    }

    private void spawnChargeParticles(Location hand, double charge) {
        if (Math.random() < charge * 0.8) {
            player.getWorld().spawnParticle(Particle.FLAME, hand, 1, 0.15, 0.15, 0.15, 0.03);
            player.getWorld().spawnParticle(Particle.LAVA, hand, 1, 0.05, 0.05, 0.05, 0);
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
        return roundLoc != null ? roundLoc : player.getLocation();
    }

    @Override
    public String getName() {
        return "CombustionRound";
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
        return "Charge a volatile combustion orb then launch it — detonates on impact with a powerful blast.";
    }

    @Override
    public String getInstructions() {
        return "\nHold Sneak: Charge orb\nRelease Sneak: Fire — hits walls or entities to explode.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (outerShell != null) outerShell.destroy();
        if (innerCore != null) innerCore.destroy();
        if (chargeRing != null) chargeRing.destroy();
    }

    @Override
    public void load() {
        abilityListener = new CombustionRoundListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Combustion.CombustionRound.Cooldown", 9000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Combustion.CombustionRound.Damage", 7.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Combustion.CombustionRound.BlastRadius", 4.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Combustion.CombustionRound.ProjectileSpeed", 1.1);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Combustion.CombustionRound.ChargeTime", 1500L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Combustion.CombustionRound.MaxLifetime", 10000L);
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
