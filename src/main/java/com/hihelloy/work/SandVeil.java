package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.SandAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
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

public class SandVeil extends SandAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double radius;
    private long duration;
    private int blindDuration;
    private double abrasionDamage;
    private long maxLifetime;

    private enum VeilState {
        RISING,
        ACTIVE,
        FALLING
    }
    private VeilState state;

    private Location center;
    private double currentRadius;
    private double particleAngle;
    private long activatedTime;
    private long lastDamageTick;
    private Set<Entity> currentlyInside = new HashSet<>();

    public SandVeil(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Sand.SandVeil.Cooldown", 9000);
        this.radius = config.getDouble("ExtraAbilities.Hihelloy.Sand.SandVeil.Radius", 4.0);
        this.duration = config.getLong("ExtraAbilities.Hihelloy.Sand.SandVeil.Duration", 5000);
        this.blindDuration = config.getInt("ExtraAbilities.Hihelloy.Sand.SandVeil.BlindDuration", 30);
        this.abrasionDamage = config.getDouble("ExtraAbilities.Hihelloy.Sand.SandVeil.AbrasionDamage", 0.5);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Sand.SandVeil.MaxLifetime", 12000);

        this.state = VeilState.RISING;
        this.center = player.getLocation().clone();
        this.currentRadius = 0.2;
        this.particleAngle = 0;
        this.activatedTime = System.currentTimeMillis();
        this.lastDamageTick = System.currentTimeMillis();

        player.getWorld().playSound(center, Sound.BLOCK_SAND_HIT, 1.5f, 0.7f);
        player.getWorld().playSound(center, Sound.BLOCK_SAND_STEP, 1.2f, 1.4f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        particleAngle += 6;
        center = player.getLocation().clone();

        if (state == VeilState.RISING) {
            currentRadius = Math.min(currentRadius + 0.18, radius);
            spawnVeilParticles();
            if (currentRadius >= radius) {
                state = VeilState.ACTIVE;
                activatedTime = System.currentTimeMillis();
            }

        } else if (state == VeilState.ACTIVE) {
            spawnVeilParticles();
            applyEffects();
            if (System.currentTimeMillis() > activatedTime + duration) {
                state = VeilState.FALLING;
            }

        } else if (state == VeilState.FALLING) {
            currentRadius = Math.max(currentRadius - 0.14, 0);
            spawnVeilParticles();
            if (currentRadius <= 0) {
                remove();
            }
        }
    }

    private void applyEffects() {
        long now = System.currentTimeMillis();
        currentlyInside.clear();

        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center.clone().add(0, 1, 0), currentRadius)) {
            if (!(e instanceof LivingEntity)) continue;
            if (e.getUniqueId().equals(player.getUniqueId())) continue;
            double dx = e.getLocation().getX() - center.getX();
            double dz = e.getLocation().getZ() - center.getZ();
            if (Math.sqrt(dx * dx + dz * dz) > currentRadius) continue;

            currentlyInside.add(e);
            LivingEntity le = (LivingEntity) e;
            le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindDuration, 0, true, false));

            if (now - lastDamageTick >= 800) {
                DamageHandler.damageEntity(le, abrasionDamage, this);
            }
        }

        if (now - lastDamageTick >= 800) {
            lastDamageTick = now;
        }
    }

    private void spawnVeilParticles() {
        int rings = 3;
        for (int ring = 0; ring < rings; ring++) {
            double r = currentRadius * (ring + 1.0) / rings;
            int points = Math.max(8, (int) (r * 8));
            for (int i = 0; i < points; i++) {
                double a = Math.toRadians(particleAngle * (ring % 2 == 0 ? 1 : -1) + (360.0 / points) * i);
                double h = 0.1 + Math.random() * 2.2;
                Location p = center.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
                player.getWorld().spawnParticle(Particle.DUST, p, 1, 0.05, 0.1, 0.05,
                        0, new Particle.DustOptions(Color.fromRGB(210 + (int)(Math.random()*30), 190 + (int)(Math.random()*20), 130), 1.0f));
            }
        }

        int floaters = (int) (currentRadius * 4);
        for (int i = 0; i < floaters; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = Math.random() * currentRadius;
            Location p = center.clone().add(Math.cos(a) * r, Math.random() * 2.5, Math.sin(a) * r);
            if (Math.random() < 0.3)
                player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0,
                        0, new Particle.DustOptions(Color.fromRGB(200, 180, 120), 1.3f));
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
        return center;
    }

    @Override
    public String getName() {
        return "SandVeil";
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
        return "Raise a swirling veil of sand around yourself that blinds and abrades all who enter.";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Raise the sand veil around your position.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
    }

    @Override
    public void load() {
        abilityListener = new SandVeilListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandVeil.Cooldown", 9000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandVeil.Radius", 4.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandVeil.Duration", 5000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandVeil.BlindDuration", 30);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandVeil.AbrasionDamage", 0.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandVeil.MaxLifetime", 12000L);
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
