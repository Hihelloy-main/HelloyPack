package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.HealingAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.hihelloy.work.lib.verlet.VerletHandler;
import com.hihelloy.work.lib.verlet.VerletRope;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

public class MendingStream extends HealingAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double healPerTick;
    private long healTickInterval;
    private double selectRange;
    private long maxDuration;
    private long maxLifetime;

    private LivingEntity healTarget;
    private VerletHandler vh;
    private VerletRope streamRope;
    private long channelStartTime;
    private long lastHealTick;
    private boolean channeling;

    public MendingStream(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        if (!channeling) {
            remove();
            return;
        }
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Healing.MendingStream.Cooldown", 8000);
        this.healPerTick = config.getDouble("ExtraAbilities.Hihelloy.Healing.MendingStream.HealPerTick", 1.0);
        this.healTickInterval = config.getLong("ExtraAbilities.Hihelloy.Healing.MendingStream.HealTickInterval", 600);
        this.selectRange = config.getDouble("ExtraAbilities.Hihelloy.Healing.MendingStream.SelectRange", 12.0);
        this.maxDuration = config.getLong("ExtraAbilities.Hihelloy.Healing.MendingStream.MaxDuration", 6000);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Healing.MendingStream.MaxLifetime", 10000);
        this.channeling = false;

        LivingEntity target = findHealTarget();
        if (target == null) return;

        this.healTarget = target;
        this.channeling = true;
        this.channelStartTime = System.currentTimeMillis();
        this.lastHealTick = System.currentTimeMillis();

        Location handLoc = GeneralMethods.getMainHandLocation(player);
        Location targetLoc = healTarget.getLocation().add(0, 1.2, 0);

        this.vh = new VerletHandler(player);
        int nodes = 20;
        double ropeLen = Math.max(handLoc.distance(targetLoc) + 1, 2.0);
        Vector maxScale = new Vector(0.07, ropeLen / nodes, 0.07);
        Vector minScale = new Vector(0.04, ropeLen / nodes, 0.04);
        this.streamRope = new VerletRope(vh, player, handLoc, ropeLen + 4, nodes, 1,
                Color.fromRGB(0, 200, 120), 1.0f, true, true, maxScale, minScale, Material.PRISMARINE_SLAB);
        this.vh.setGravity(new Vector(0, -0.02, 0));
        player.getWorld().playSound(handLoc, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.6f, 1.8f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }
        if (healTarget == null || healTarget.isDead()) {
            remove();
            return;
        }
        if (!player.isSneaking()) {
            remove();
            return;
        }
        if (System.currentTimeMillis() > channelStartTime + maxDuration) {
            remove();
            return;
        }

        Location handLoc = GeneralMethods.getMainHandLocation(player);
        Location targetLoc = healTarget.getLocation().add(0, 1.2, 0);

        if (handLoc.distance(targetLoc) > selectRange) {
            remove();
            return;
        }

        streamRope.moveStartPoint(handLoc);
        streamRope.moveEndPoint(targetLoc);
        vh.update();
        vh.display();

        long now = System.currentTimeMillis();
        if (now - lastHealTick >= healTickInterval) {
            lastHealTick = now;
            double maxHealth = healTarget.getMaxHealth();
            healTarget.setHealth(Math.min(healTarget.getHealth() + healPerTick, maxHealth));
            player.getWorld().spawnParticle(Particle.HEART,
                    healTarget.getLocation().add(0, 2.2, 0), 3, 0.2, 0.1, 0.2, 0);
            player.getWorld().playSound(healTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.8f);
        }

        spawnStreamParticles(handLoc, targetLoc);
    }

    private LivingEntity findHealTarget() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(player.getEyeLocation(), selectRange)) {
            if (!(e instanceof LivingEntity)) continue;
            if (e.getUniqueId().equals(player.getUniqueId())) continue;
            Vector toTarget = e.getLocation().add(0, 1, 0).toVector().subtract(player.getEyeLocation().toVector());
            double dot = toTarget.clone().normalize().dot(player.getLocation().getDirection().normalize());
            if (dot > 0.92 && e.getLocation().distance(player.getLocation()) <= selectRange) {
                return (LivingEntity) e;
            }
        }
        return null;
    }

    private void spawnStreamParticles(Location from, Location to) {
        if (Math.random() > 0.5) return;
        Vector dir = to.toVector().subtract(from.toVector());
        double dist = from.distance(to);
        if (dist < 0.01) return;
        dir.normalize();
        double t = Math.random() * dist;
        Location p = from.clone().add(dir.multiply(t));
        p.add(new Vector((Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2, (Math.random() - 0.5) * 0.2));
        player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0,
                0, new Particle.DustOptions(Color.fromRGB(0, 210, 140), 0.7f));
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }
    @Override
    public boolean isHarmlessAbility() {
        return true;
    }
    @Override
    public long getCooldown() {
        return cooldown;
    }
    @Override
    public Location getLocation() {
        return healTarget != null ? healTarget.getLocation() : player.getLocation();
    }
    @Override
    public String getName() {
        return "MendingStream";
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
        return "Channel a glowing stream of healing water toward an ally, restoring their health over time.";
    }
    @Override
    public String getInstructions() {
        return "\nHold Sneak while aiming at an ally: Channel healing stream.\nRelease Sneak: Stop.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (streamRope != null) streamRope.destroy();
    }

    @Override
    public void load() {
        abilityListener = new MendingStreamListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Healing.MendingStream.Cooldown", 8000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Healing.MendingStream.HealPerTick", 1.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Healing.MendingStream.HealTickInterval", 600L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Healing.MendingStream.SelectRange", 12.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Healing.MendingStream.MaxDuration", 6000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Healing.MendingStream.MaxLifetime", 10000L);
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