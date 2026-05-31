package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.BloodAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.MovementHandler;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

public class BloodSnare extends BloodAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double snareDamagePerTick;
    private long snareTickInterval;
    private long maxSnareDuration;
    private double selectRange;
    private long maxLifetime;

    private LivingEntity target;
    private long snareStartTime;
    private long lastSnareTick;
    private long lastParticleTick;
    private double pulseAngle;
    private boolean active;

    public BloodSnare(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        if (!active) {
            remove();
            return;
        }
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Blood.BloodSnare.Cooldown", 10000);
        this.snareDamagePerTick = config.getDouble("ExtraAbilities.Hihelloy.Blood.BloodSnare.DamagePerTick", 2.0);
        this.snareTickInterval = config.getLong("ExtraAbilities.Hihelloy.Blood.BloodSnare.TickInterval", 800);
        this.maxSnareDuration = config.getLong("ExtraAbilities.Hihelloy.Blood.BloodSnare.MaxDuration", 5000);
        this.selectRange = config.getDouble("ExtraAbilities.Hihelloy.Blood.BloodSnare.SelectRange", 15.0);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Blood.BloodSnare.MaxLifetime", 10000);
        this.pulseAngle = 0;
        this.active = false;

        LivingEntity aimed = getAimedTarget();
        if (aimed == null) return;

        this.target = aimed;
        this.active = true;
        this.snareStartTime = System.currentTimeMillis();
        this.lastSnareTick = 0;
        this.lastParticleTick = 0;

        new MovementHandler(target, this).stopWithDuration(maxSnareDuration, "BloodSnare");
        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.7f);
        player.getWorld().spawnParticle(Particle.DUST,
                target.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3,
                0, new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.0f));
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }
        if (target == null || target.isDead()) {
            remove();
            return;
        }
        if (System.currentTimeMillis() > snareStartTime + maxSnareDuration) {
            remove();
            return;
        }
        if (!player.isSneaking()) {
            remove();
            return;
        }

        pulseAngle += 10;

        long now = System.currentTimeMillis();
        if (now - lastSnareTick >= snareTickInterval) {
            lastSnareTick = now;
            DamageHandler.damageEntity(target, snareDamagePerTick, this);
            player.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 0.6f);
        }

        if (now - lastParticleTick >= 50) {
            lastParticleTick = now;
            spawnSnareParticles(target);
        }
    }

    private LivingEntity getAimedTarget() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(player.getEyeLocation(), selectRange)) {
            if (!(e instanceof LivingEntity)) continue;
            if (e.getUniqueId().equals(player.getUniqueId())) continue;
            Vector toTarget = e.getLocation().add(0, 1, 0).toVector()
                    .subtract(player.getEyeLocation().toVector());
            double dot = toTarget.clone().normalize().dot(player.getLocation().getDirection().normalize());
            if (dot > 0.95 && e.getLocation().distance(player.getLocation()) <= selectRange) {
                return (LivingEntity) e;
            }
        }
        return null;
    }

    private void spawnSnareParticles(LivingEntity t) {
        Location base = t.getLocation();
        int points = 12;
        double height = 2.0;
        for (int i = 0; i < points; i++) {
            double a = Math.toRadians(pulseAngle + (360.0 / points) * i);
            double h = (Math.sin(Math.toRadians(pulseAngle * 0.5 + i * 30)) * 0.5 + 0.5) * height;
            Location p = base.clone().add(Math.cos(a) * 0.5, h, Math.sin(a) * 0.5);
            player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0,
                    0, new Particle.DustOptions(Color.fromRGB(160, 0, 20), 0.9f));
        }
        if (Math.random() < 0.2) {
            player.getWorld().spawnParticle(Particle.DUST,
                    base.clone().add(0, 1, 0), 3, 0.2, 0.4, 0.2,
                    0, new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.2f));
        }
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
        return target != null ? target.getLocation() : player.getLocation();
    }
    @Override
    public String getName() {
        return "BloodSnare";
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
        return "Seize the blood of a targeted entity, rooting them in place and draining their health over time.";
    }
    @Override
    public String getInstructions() {
        return "\nHold Sneak while aiming at a target: Root them and drain their health.\nRelease Sneak: Release.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (target != null) {
            MovementHandler handler = MovementHandler.getFromEntityAndAbility(target, this);
            if (handler != null) handler.reset();
        }
    }

    @Override
    public void load() {
        abilityListener = new BloodSnareListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Blood.BloodSnare.Cooldown", 10000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Blood.BloodSnare.DamagePerTick", 2.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Blood.BloodSnare.TickInterval", 800L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Blood.BloodSnare.MaxDuration", 5000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Blood.BloodSnare.SelectRange", 15.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Blood.BloodSnare.MaxLifetime", 10000L);
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
