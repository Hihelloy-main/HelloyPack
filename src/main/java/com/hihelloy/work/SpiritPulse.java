package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.SpiritualAbility;
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

public class SpiritPulse extends SpiritualAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double maxRadius;
    private double expandSpeed;
    private long maxLifetime;
    private int disorientDuration;

    private double currentRadius;
    private Location origin;
    private Set<Entity> hit = new HashSet<>();
    private double pulseAngle;

    public SpiritPulse(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.Cooldown", 7000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.Damage", 2.0);
        this.maxRadius = config.getDouble("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.MaxRadius", 10.0);
        this.expandSpeed = config.getDouble("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.ExpandSpeed", 0.5);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.MaxLifetime", 5000);
        this.disorientDuration = config.getInt("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.DisorientDuration", 60);

        this.currentRadius = 0.1;
        this.origin = player.getEyeLocation().clone();
        this.pulseAngle = 0;

        player.getWorld().playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
        player.getWorld().playSound(origin, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.5f, 1.8f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        currentRadius += expandSpeed;
        pulseAngle += 8;

        spawnPulseParticles();
        checkHits();

        if (currentRadius >= maxRadius) {
            remove();
        }
    }

    private void checkHits() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(origin, currentRadius + 0.5)) {
            if (!(e instanceof LivingEntity)) continue;
            if (e.getUniqueId().equals(player.getUniqueId())) continue;
            if (hit.contains(e)) continue;

            double dist = e.getLocation().distance(origin);
            if (Math.abs(dist - currentRadius) < 1.2) {
                hit.add(e);
                LivingEntity le = (LivingEntity) e;
                DamageHandler.damageEntity(le, damage, this);
                le.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, disorientDuration, 0));
                le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, disorientDuration / 2, 0));
                Vector kb = e.getLocation().toVector().subtract(origin.toVector()).normalize().multiply(0.6);
                e.setVelocity(e.getVelocity().add(kb));
                player.getWorld().spawnParticle(Particle.CRIT, e.getLocation().add(0, 1, 0), 3, 0.1, 0.1, 0.1, 0);
            }
        }
    }

    private void spawnPulseParticles() {
        int latitudes = 5;
        int longitudePoints = 20;
        for (int lat = 0; lat < latitudes; lat++) {
            double phi = Math.PI * lat / (latitudes - 1);
            double sinPhi = Math.sin(phi);
            double cosPhi = Math.cos(phi);
            for (int lon = 0; lon < longitudePoints; lon++) {
                double theta = Math.toRadians(pulseAngle + (360.0 / longitudePoints) * lon);
                double x = currentRadius * sinPhi * Math.cos(theta);
                double y = currentRadius * cosPhi;
                double z = currentRadius * sinPhi * Math.sin(theta);
                Location p = origin.clone().add(x, y, z);

                float alpha = (float) (0.6 + Math.sin(Math.toRadians(pulseAngle + lon * 18)) * 0.4);
                int r = (int) (180 * alpha);
                int g = (int) (150 * alpha);
                int b = (int) (255 * alpha);
                player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0,
                        0, new Particle.DustOptions(Color.fromRGB(Math.min(r, 255), Math.min(g, 255), Math.min(b, 255)), 0.9f));
            }
        }

        if (Math.random() < 0.2) {
            player.getWorld().spawnParticle(Particle.CRIT, origin.clone().add(
                    (Math.random() - 0.5) * currentRadius * 0.5,
                    (Math.random() - 0.5) * currentRadius * 0.5,
                    (Math.random() - 0.5) * currentRadius * 0.5), 3, 0.1, 0.1, 0.1, 0);
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
        return origin;
    }

    @Override
    public String getName() {
        return "SpiritPulse";
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
        return "Release an expanding sphere of spirit energy that passes through walls and disorients all it touches.";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Emit spirit pulse from your position.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
    }

    @Override
    public void load() {
        abilityListener = new SpiritPulseListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.Cooldown", 7000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.Damage", 2.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.MaxRadius", 10.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.ExpandSpeed", 0.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.MaxLifetime", 5000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Spiritual.SpiritPulse.DisorientDuration", 60);
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
