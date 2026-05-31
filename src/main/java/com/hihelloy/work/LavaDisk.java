package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.LavaAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.hihelloy.work.lib.GameObject;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class LavaDisk extends LavaAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double range;
    private double speed;
    private int fireTicks;
    private long maxLifetime;

    private Location diskLoc;
    private Location startLoc;
    private Vector velocity;
    private double spinAngle;

    private GameObject diskBody;
    private GameObject diskRim;
    private GameObject diskCore;

    private Set<Entity> hit = new HashSet<>();

    public LavaDisk(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Lava.LavaDisk.Cooldown", 6000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Lava.LavaDisk.Damage", 4.5);
        this.range = config.getDouble("ExtraAbilities.Hihelloy.Lava.LavaDisk.Range", 22.0);
        this.speed = config.getDouble("ExtraAbilities.Hihelloy.Lava.LavaDisk.Speed", 1.3);
        this.fireTicks = config.getInt("ExtraAbilities.Hihelloy.Lava.LavaDisk.FireTicks", 100);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Lava.LavaDisk.MaxLifetime", 6000);

        this.diskLoc = GeneralMethods.getMainHandLocation(player).clone();
        this.startLoc = this.diskLoc.clone();
        this.spinAngle = -player.getLocation().getYaw();
        this.velocity = player.getLocation().getDirection().clone().multiply(speed);

        this.diskBody = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                diskLoc, new Vector(0.55, 0.07, 0.55),
                new Vector(0, Math.toRadians(spinAngle), 0), new Vector());
        this.diskBody.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.diskBody.setBlockMaterial(Material.MAGMA_BLOCK);

        this.diskRim = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                diskLoc, new Vector(0.7, 0.05, 0.7),
                new Vector(0, Math.toRadians(spinAngle + 22.5), 0), new Vector());
        this.diskRim.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.diskRim.setBlockMaterial(Material.BLACKSTONE);

        this.diskCore = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                diskLoc, new Vector(0.25, 0.1, 0.25),
                new Vector(0, Math.toRadians(spinAngle), 0), new Vector());
        this.diskCore.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.diskCore.setBlockMaterial(Material.SHROOMLIGHT);

        player.getWorld().playSound(diskLoc, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
        player.getWorld().playSound(diskLoc, Sound.BLOCK_LAVA_AMBIENT, 1.0f, 1.4f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        spinAngle += 22;
        velocity.add(new Vector(0, -0.03, 0));
        if (velocity.length() > speed) velocity.normalize().multiply(speed);
        diskLoc.add(velocity);

        if (diskLoc.distanceSquared(startLoc) > range * range) {
            remove();
            return;
        }
        if (diskLoc.getBlock().isSolid()) {
            spawnImpact(diskLoc);
            remove();
            return;
        }

        checkHits();
        updateDisplay();
        spawnTrailParticles();
    }

    private void checkHits() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(diskLoc, 0.95)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId()) && !hit.contains(e)) {
                hit.add(e);
                DamageHandler.damageEntity((LivingEntity) e, damage, this);
                e.setFireTicks(fireTicks);
                Vector kb = diskLoc.toVector().subtract(e.getLocation().toVector()).normalize().multiply(-0.5);
                e.setVelocity(e.getVelocity().add(kb));
                player.getWorld().spawnParticle(Particle.LAVA, diskLoc, 8, 0.2, 0.2, 0.2, 0);
                player.getWorld().playSound(diskLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
            }
        }
    }

    private void updateDisplay() {
        double yaw = Math.toRadians(spinAngle);
        diskBody.setLocation(diskLoc);
        diskBody.setRotation(new Vector(0, yaw, 0));
        diskBody.updateAndDisplay();
        diskRim.setLocation(diskLoc);
        diskRim.setRotation(new Vector(0, yaw + Math.toRadians(22.5), 0));
        diskRim.updateAndDisplay();
        diskCore.setLocation(diskLoc);
        diskCore.setRotation(new Vector(Math.toRadians(spinAngle * 0.5), yaw, 0));
        diskCore.updateAndDisplay();
    }

    private void spawnTrailParticles() {
        player.getWorld().spawnParticle(Particle.LAVA, diskLoc, 1, 0.1, 0.1, 0.1, 0);
        player.getWorld().spawnParticle(Particle.FLAME, diskLoc, 2, 0.15, 0.05, 0.15, 0.02);
        if (Math.random() < 0.3)
            player.getWorld().spawnParticle(Particle.SMOKE, diskLoc, 1, 0.1, 0.05, 0.1, 0.01);
    }

    private void spawnImpact(Location loc) {
        player.getWorld().spawnParticle(Particle.LAVA, loc, 20, 0.4, 0.2, 0.4, 0);
        player.getWorld().spawnParticle(Particle.FLAME, loc, 12, 0.3, 0.3, 0.3, 0.08);
        player.getWorld().spawnParticle(Particle.SMOKE, loc, 10, 0.2, 0.2, 0.2, 0.03);
        player.getWorld().playSound(loc, Sound.BLOCK_LAVA_EXTINGUISH, 1.5f, 1.0f);
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
        return diskLoc;
    }

    @Override
    public String getName() {
        return "LavaDisk";
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
        return "Hurl a spinning disk of molten rock that burns through anything in its path.";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Launch lava disk.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (diskBody != null) diskBody.destroy();
        if (diskRim != null) diskRim.destroy();
        if (diskCore != null) diskCore.destroy();
    }

    @Override
    public void load() {
        abilityListener = new LavaDiskListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.LavaDisk.Cooldown", 6000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.LavaDisk.Damage", 4.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.LavaDisk.Range", 22.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.LavaDisk.Speed", 1.3);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.LavaDisk.FireTicks", 100);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.LavaDisk.MaxLifetime", 6000L);
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
