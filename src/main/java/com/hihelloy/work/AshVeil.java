package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
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

public class AshVeil extends FireAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double tickDamage;
    private double maxRadius;
    private double expandSpeed;
    private long duration;
    private long maxLifetime;
    private int blindDuration;
    private int poisonDuration;

    private enum VeilState {
        EXHALING,
        SETTLED,
        DISPERSING
    }
    private VeilState state;

    private Location veilCenter;
    private double currentRadius;
    private double veilHeight;
    private long settleStartTime;
    private Transition exhaleTransition;
    private Transition disperseTransition;

    private long lastDamageTick;
    private Set<Entity> inVeil = new HashSet<>();
    private long lastParticleTick;

    public AshVeil(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Fire.AshVeil.Cooldown", 12000);
        this.tickDamage = config.getDouble("ExtraAbilities.Hihelloy.Fire.AshVeil.TickDamage", 0.8);
        this.maxRadius = config.getDouble("ExtraAbilities.Hihelloy.Fire.AshVeil.MaxRadius", 6.0);
        this.expandSpeed = config.getDouble("ExtraAbilities.Hihelloy.Fire.AshVeil.ExpandSpeed", 0.12);
        this.duration = config.getLong("ExtraAbilities.Hihelloy.Fire.AshVeil.Duration", 8000);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Fire.AshVeil.MaxLifetime", 14000);
        this.blindDuration = config.getInt("ExtraAbilities.Hihelloy.Fire.AshVeil.BlindDuration", 40);
        this.poisonDuration = config.getInt("ExtraAbilities.Hihelloy.Fire.AshVeil.PoisonDuration", 60);

        this.state = VeilState.EXHALING;
        this.veilCenter = player.getLocation().clone();
        this.currentRadius = 0.2;
        this.veilHeight = 2.5;
        this.lastDamageTick = System.currentTimeMillis();
        this.lastParticleTick = System.currentTimeMillis();

        this.exhaleTransition = new Transition(
                new Vector(0.2, 0, 0),
                new Vector(maxRadius, 0, 0),
                new Vector(maxRadius / expandSpeed, 0, 0),
                new Vector(0, 0, 0));

        player.getWorld().playSound(veilCenter, Sound.BLOCK_FIRE_AMBIENT, 1.5f, 0.4f);
        player.getWorld().playSound(veilCenter, Sound.ENTITY_WITHER_AMBIENT, 0.7f, 1.5f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        long now = System.currentTimeMillis();

        if (state == VeilState.EXHALING) {
            exhaleTransition.update();
            currentRadius = exhaleTransition.getX();

            spawnVeilParticles(now);

            if (currentRadius >= maxRadius * 0.99) {
                state = VeilState.SETTLED;
                settleStartTime = System.currentTimeMillis();
            }

            applyVeilEffects(now);

        } else if (state == VeilState.SETTLED) {
            spawnVeilParticles(now);
            applyVeilEffects(now);

            if (System.currentTimeMillis() > settleStartTime + duration) {
                state = VeilState.DISPERSING;
                disperseTransition = new Transition(
                        new Vector(currentRadius, 0, 0),
                        new Vector(0, 0, 0),
                        new Vector(40, 0, 0),
                        new Vector(0, 0, 0));
            }
        } else if (state == VeilState.DISPERSING) {
            disperseTransition.update();
            currentRadius = disperseTransition.getX();

            spawnVeilParticles(now);

            if (currentRadius <= 0.1) {
                remove();
            }
        }
    }

    private void applyVeilEffects(long now) {
        if (now - lastDamageTick < 1000) return;
        lastDamageTick = now;
        inVeil.clear();

        for (Entity e : GeneralMethods.getEntitiesAroundPoint(veilCenter.clone().add(0, veilHeight * 0.5, 0), currentRadius + 1)) {
            if (!(e instanceof LivingEntity)) continue;
            if (e.getUniqueId().equals(player.getUniqueId())) continue;
            Location eLoc = e.getLocation();
            double dx = eLoc.getX() - veilCenter.getX();
            double dz = eLoc.getZ() - veilCenter.getZ();
            double flatDist = Math.sqrt(dx * dx + dz * dz);
            double dy = Math.abs(eLoc.getY() - veilCenter.getY());
            if (flatDist <= currentRadius && dy <= veilHeight) {
                inVeil.add(e);
                LivingEntity le = (LivingEntity) e;
                DamageHandler.damageEntity(le, tickDamage, this);
                le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindDuration, 0));
                le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonDuration, 0));
                e.getWorld().playSound(e.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.6f);
            }
        }
    }

    private void spawnVeilParticles(long now) {
        if (now - lastParticleTick < 50) return;
        lastParticleTick = now;

        int ringCount = 3;
        for (int ring = 0; ring < ringCount; ring++) {
            double r = currentRadius * (ring + 1.0) / ringCount;
            int points = Math.max(8, (int) (r * 6));
            for (int i = 0; i < points; i++) {
                double a = (2 * Math.PI / points) * i + (now * 0.0005 * (ring % 2 == 0 ? 1 : -1));
                double h = Math.random() * veilHeight;
                Location p = veilCenter.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
                player.getWorld().spawnParticle(Particle.DUST, p, 1, 0.1, 0.1, 0.1,
                        0, new Particle.DustOptions(Color.fromRGB(60 + (int)(Math.random()*40), 55, 50), 1.2f));
                if (Math.random() < 0.08) {
                    player.getWorld().spawnParticle(Particle.SMOKE, p, 1, 0.1, 0.05, 0.1, 0.01);
                }
            }
        }

        int edgePoints = Math.max(12, (int) (currentRadius * 8));
        for (int i = 0; i < edgePoints; i++) {
            double a = (2 * Math.PI / edgePoints) * i;
            double h = Math.random() * veilHeight;
            Location edge = veilCenter.clone().add(
                    Math.cos(a) * currentRadius, h,
                    Math.sin(a) * currentRadius);
            player.getWorld().spawnParticle(Particle.SMOKE, edge, 1, 0.05, 0.05, 0.05, 0.005);
        }

        int floatPoints = (int) (currentRadius * 3);
        for (int i = 0; i < floatPoints; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = Math.random() * currentRadius;
            Location p = veilCenter.clone().add(Math.cos(a) * r, Math.random() * veilHeight, Math.sin(a) * r);
            if (Math.random() < 0.3)
                player.getWorld().spawnParticle(Particle.SMALL_FLAME, p, 1, 0.05, 0.05, 0.05, 0.005);
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
        return veilCenter;
    }
    @Override
    public String getName() {
        return "AshVeil";
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
        return "Release a spreading veil of hot ash that blinds and poisons anyone who enters.";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Exhale ash veil at your feet.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
    }

    @Override
    public void load() {
        abilityListener = new AshVeilListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.AshVeil.Cooldown", 12000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.AshVeil.TickDamage", 0.8);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.AshVeil.MaxRadius", 6.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.AshVeil.ExpandSpeed", 0.12);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.AshVeil.Duration", 8000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.AshVeil.MaxLifetime", 14000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.AshVeil.BlindDuration", 40);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.AshVeil.PoisonDuration", 60);
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
