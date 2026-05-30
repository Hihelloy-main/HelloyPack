package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.hihelloy.work.lib.verlet.VerletHandler;
import com.hihelloy.work.lib.verlet.VerletRope;
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

public class SeismicWhip extends WaterAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double whipRange;
    private long maxLifetime;
    private int slowLevel;

    private enum WhipState {
        COILED,
        CRACKING,
        RETRACTING
    }
    private WhipState state;

    private VerletHandler vh;
    private VerletRope rope;

    private Location tipLocation;
    private Vector crackDirection;
    private long crackStartTime;
    private long crackDuration;

    private Set<Entity> hitEntities = new HashSet<>();

    public SeismicWhip(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Water.SeismicWhip.Cooldown", 6000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Water.SeismicWhip.Damage", 3.5);
        this.whipRange = config.getDouble("ExtraAbilities.Hihelloy.Water.SeismicWhip.Range", 12.0);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Water.SeismicWhip.MaxLifetime", 6000);
        this.slowLevel = config.getInt("ExtraAbilities.Hihelloy.Water.SeismicWhip.SlowLevel", 1);
        this.crackDuration = 600;

        this.state = WhipState.COILED;

        this.vh = new VerletHandler(player);
        Location startLoc = GeneralMethods.getMainHandLocation(player);

        double maxRopeLength = whipRange;
        int verletObjectAmount = (int) (maxRopeLength * 4);

        Vector maxScale = new Vector(0.09, maxRopeLength / verletObjectAmount, 0.09);
        Vector minScale = new Vector(0.04, maxRopeLength / verletObjectAmount, 0.04);

        this.rope = new VerletRope(this.vh, player, startLoc, maxRopeLength, verletObjectAmount, 1,
                Color.fromRGB(150, 220, 255), 1.0f, true, false, maxScale, minScale, Material.PACKED_ICE);
        this.rope.setRopeLength(1.5, false);
        this.vh.setGravity(new Vector(0, -0.015, 0));
        this.tipLocation = startLoc.clone();
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        Location hand = GeneralMethods.getMainHandLocation(player);

        if (state == WhipState.COILED) {
            if (rope.getRopeLength() < whipRange * 0.4) {
                rope.unfurl(0.4, whipRange * 0.4, false);
            }
            rope.moveStartPoint(hand);
            rope.moveEndPoint(hand.clone().add(
                    player.getLocation().getDirection().clone().setY(0).normalize().multiply(rope.getRopeLength())));
            vh.update();
            vh.display();
            tipLocation = rope.getEndLocation();
            spawnWhipParticles(tipLocation, 2);

        } else if (state == WhipState.CRACKING) {
            double elapsed = System.currentTimeMillis() - crackStartTime;
            double progress = elapsed / (double) crackDuration;

            if (progress >= 1.0) {
                state = WhipState.RETRACTING;
                return;
            }

            double targetLen = whipRange * Math.sin(progress * Math.PI);
            rope.setRopeLength(Math.max(1.5, targetLen), false);

            double liftHeight = Math.sin(progress * Math.PI) * 3.0;
            Location crackTip = hand.clone()
                    .add(crackDirection.clone().multiply(targetLen))
                    .add(new Vector(0, liftHeight, 0));

            rope.moveStartPoint(hand);
            rope.moveEndPoint(crackTip);

            vh.update();
            vh.display();

            tipLocation = crackTip;
            spawnWhipParticles(tipLocation, 5);
            checkWhipHits();

            if (progress > 0.4 && progress < 0.6) {
                player.getWorld().playSound(tipLocation, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.5f);
            }

        } else if (state == WhipState.RETRACTING) {
            rope.moveStartPoint(hand);
            rope.furl(0.5, 1.5, false);
            rope.moveEndPoint(hand.clone().add(
                    player.getLocation().getDirection().clone().setY(0).normalize().multiply(rope.getRopeLength())));
            vh.update();
            vh.display();
            if (rope.getRopeLength() <= 1.5) {
                state = WhipState.COILED;
                hitEntities.clear();
            }
        }
    }

    public void onLeftClick() {
        if (state == WhipState.COILED) {
            state = WhipState.CRACKING;
            crackStartTime = System.currentTimeMillis();
            crackDirection = player.getLocation().getDirection().clone();
            crackDirection.setY(0);
            if (crackDirection.lengthSquared() < 0.001) crackDirection.setX(1);
            crackDirection.normalize();
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.5f, 0.5f);
        }
    }

    private void checkWhipHits() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(tipLocation, 1.2)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                if (!hitEntities.contains(e)) {
                    hitEntities.add(e);
                    LivingEntity le = (LivingEntity) e;
                    DamageHandler.damageEntity(le, damage, this);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, slowLevel));
                    Vector knockback = crackDirection.clone().multiply(0.8);
                    e.setVelocity(e.getVelocity().add(knockback));
                    player.getWorld().spawnParticle(Particle.SNOWFLAKE, tipLocation, 20, 0.3, 0.3, 0.3, 0.05);
                    player.getWorld().playSound(tipLocation, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 1.3f);
                }
            }
        }
    }

    private void spawnWhipParticles(Location loc, int count) {
        player.getWorld().spawnParticle(Particle.DUST, loc, count, 0.05, 0.05, 0.05,
                0, new Particle.DustOptions(Color.fromRGB(180, 230, 255), 0.6f));
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
        return tipLocation;
    }

    @Override
    public String getName() {
        return "SeismicWhip";
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
        return "Crack an ice whip that shatters on impact, slowing and damaging targets.";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Crack the whip toward your crosshair.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (rope != null) rope.destroy();
    }

    @Override
    public void load() {
        abilityListener = new SeismicWhipListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.SeismicWhip.Cooldown", 6000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.SeismicWhip.Damage", 3.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.SeismicWhip.Range", 12.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.SeismicWhip.MaxLifetime", 6000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.SeismicWhip.SlowLevel", 1);
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
