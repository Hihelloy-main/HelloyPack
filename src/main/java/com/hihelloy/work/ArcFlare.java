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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ArcFlare extends FireAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double arcDamage;
    private double flareSpeed;
    private double flareRange;
    private int maxArcs;
    private long maxLifetime;

    private List<FireArc> orbitingArcs = new ArrayList<>();
    private List<FireArc> launchedArcs = new ArrayList<>();
    private double orbitAngle;
    private Transition formTransition;

    public ArcFlare(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Fire.ArcFlare.Cooldown", 7000);
        this.arcDamage = config.getDouble("ExtraAbilities.Hihelloy.Fire.ArcFlare.Damage", 3.5);
        this.flareSpeed = config.getDouble("ExtraAbilities.Hihelloy.Fire.ArcFlare.FlareSpeed", 1.6);
        this.flareRange = config.getDouble("ExtraAbilities.Hihelloy.Fire.ArcFlare.FlareRange", 22.0);
        this.maxArcs = config.getInt("ExtraAbilities.Hihelloy.Fire.ArcFlare.MaxArcs", 3);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Fire.ArcFlare.MaxLifetime", 10000);

        this.orbitAngle = 0;
        this.formTransition = new Transition(
                new Vector(0, 0, 0), new Vector(1, 0, 0),
                new Vector(15, 0, 0), new Vector(0, 0, 0));

        for (int i = 0; i < maxArcs; i++) {
            double phase = (360.0 / maxArcs) * i;
            orbitingArcs.add(new FireArc(player, phase));
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1.2f, 0.7f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) { remove(); return; }

        orbitAngle += 9;
        formTransition.update();
        double form = formTransition.getX();
        double orbitR = 1.0 * form;
        Location center = player.getLocation().add(0, 1.2, 0);

        for (int i = 0; i < orbitingArcs.size(); i++) {
            FireArc arc = orbitingArcs.get(i);
            double angle = orbitAngle + arc.phaseOffset;
            arc.updateOrbit(center, orbitR, angle, form);
            arc.display();
        }

        Iterator<FireArc> it = launchedArcs.iterator();
        while (it.hasNext()) {
            FireArc arc = it.next();
            arc.updateFlight(flareSpeed);
            arc.display();
            if (arc.checkHit(arcDamage, this) || arc.isExpired(flareRange)) {
                arc.spawnImpact(player);
                arc.destroy();
                it.remove();
            }
        }

        if (orbitingArcs.isEmpty() && launchedArcs.isEmpty()) {
            remove();
        }
    }

    public void onLeftClick() {
        if (orbitingArcs.isEmpty()) return;
        FireArc arc = orbitingArcs.remove(0);
        arc.launch(player.getLocation().getDirection().clone());
        launchedArcs.add(arc);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.0f);
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
        return "ArcFlare";
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
        return "Conjure three spinning fire arcs that orbit you. Each left click launches one as a fast fire streak.";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Activate — three arcs orbit you.\nLeft Click again (up to 3 times): Launch each arc as a projectile.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        for (FireArc arc : orbitingArcs) arc.destroy();
        for (FireArc arc : launchedArcs) arc.destroy();
    }

    @Override
    public void load() {
        abilityListener = new ArcFlareListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.ArcFlare.Cooldown", 7000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.ArcFlare.Damage", 3.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.ArcFlare.FlareSpeed", 1.6);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.ArcFlare.FlareRange", 22.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.ArcFlare.MaxArcs", 3);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.ArcFlare.MaxLifetime", 10000L);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }

    private static class FireArc {
        final Player player;
        final double phaseOffset;
        private boolean launched = false;
        private Location location;
        private Location startLocation;
        private Vector velocity;
        private double spinAngle;

        private GameObject bodyA;
        private GameObject bodyB;
        private GameObject headGlow;

        FireArc(Player player, double phaseOffset) {
            this.player = player;
            this.phaseOffset = phaseOffset;
            this.location = player.getLocation().clone();
            this.spinAngle = phaseOffset;

            this.bodyA = makeObj(player, Material.MAGMA_BLOCK, new Vector(0.28, 0.09, 0.5));
            this.bodyB = makeObj(player, Material.BLACKSTONE, new Vector(0.18, 0.07, 0.35));
            this.headGlow = makeObj(player, Material.SHROOMLIGHT, new Vector(0.2, 0.2, 0.2));
        }

        private GameObject makeObj(Player p, Material mat, Vector scale) {
            GameObject obj = new GameObject(p, GameObject.DisplayMode.BLOCK_DISPLAY,
                    p.getLocation(), scale, new Vector(0, 0, 0), new Vector());
            obj.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
            obj.setBlockMaterial(mat);
            return obj;
        }

        void updateOrbit(Location center, double radius, double angle, double form) {
            double rad = Math.toRadians(angle);
            double vertWave = Math.sin(Math.toRadians(angle * 1.5 + phaseOffset)) * 0.3 * form;
            this.location = center.clone().add(Math.cos(rad) * radius, vertWave, Math.sin(rad) * radius);
            this.spinAngle = angle;

            double yaw = Math.toRadians(angle + 90);
            double s = form;

            bodyA.setLocation(this.location);
            bodyA.setScale(new Vector(0.28 * s, 0.09 * s, 0.5 * s));
            bodyA.setRotation(new Vector(0, yaw, Math.toRadians(spinAngle * 0.3)));

            bodyB.setLocation(this.location.clone().add(0, 0.05 * s, 0));
            bodyB.setScale(new Vector(0.18 * s, 0.07 * s, 0.35 * s));
            bodyB.setRotation(new Vector(0, yaw + 0.3, 0));

            headGlow.setLocation(this.location.clone().add(
                    Math.cos(yaw) * 0.3 * s, 0, Math.sin(yaw) * 0.3 * s));
            headGlow.setScale(new Vector(0.2 * s, 0.2 * s, 0.2 * s));
        }

        void launch(Vector direction) {
            this.launched = true;
            this.startLocation = this.location.clone();
            this.velocity = direction.clone().normalize().multiply(1.0);
        }

        void updateFlight(double speed) {
            this.velocity.add(new Vector(0, -0.025, 0));
            if (this.velocity.length() > speed) this.velocity.normalize().multiply(speed);
            this.location.add(velocity);
            this.spinAngle += 18;

            double yaw = Math.atan2(-velocity.getX(), velocity.getZ());
            double pitch = Math.asin(Math.max(-1, Math.min(1, velocity.clone().normalize().getY())));

            bodyA.setLocation(this.location);
            bodyA.setRotation(new Vector(-pitch, yaw, Math.toRadians(spinAngle)));
            bodyA.setScale(new Vector(0.28, 0.09, 0.5));

            bodyB.setLocation(this.location.clone().add(0, 0.05, 0));
            bodyB.setRotation(new Vector(-pitch + 0.2, yaw, Math.toRadians(spinAngle)));
            bodyB.setScale(new Vector(0.18, 0.07, 0.35));

            headGlow.setLocation(this.location);
            headGlow.setScale(new Vector(0.2, 0.2, 0.2));

            player.getWorld().spawnParticle(Particle.FLAME, this.location, 2, 0.07, 0.07, 0.07, 0.03);
            if (Math.random() < 0.3)
                player.getWorld().spawnParticle(Particle.LAVA, this.location, 1, 0.04, 0.04, 0.04, 0);
        }

        boolean checkHit(double damage, ArcFlare ability) {
            if (launched && this.location.getBlock().isSolid()) return true;
            for (Entity e : GeneralMethods.getEntitiesAroundPoint(this.location, 0.7)) {
                if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                    DamageHandler.damageEntity((LivingEntity) e, damage, ability);
                    e.setFireTicks(80);
                    Vector kb = velocity.clone().normalize().multiply(0.7);
                    e.setVelocity(e.getVelocity().add(kb));
                    return true;
                }
            }
            return false;
        }

        boolean isExpired(double range) {
            return launched && startLocation != null && this.location.distanceSquared(startLocation) > range * range;
        }

        void spawnImpact(Player p) {
            p.getWorld().spawnParticle(Particle.EXPLOSION, this.location, 1, 0, 0, 0, 0);
            p.getWorld().spawnParticle(Particle.FLAME, this.location, 15, 0.3, 0.3, 0.3, 0.1);
            p.getWorld().playSound(this.location, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 1.3f);
        }

        void display() {
            bodyA.updateAndDisplay();
            bodyB.updateAndDisplay();
            headGlow.updateAndDisplay();
        }

        void destroy() {
            if (bodyA != null) bodyA.destroy();
            if (bodyB != null) bodyB.destroy();
            if (headGlow != null) headGlow.destroy();
        }
    }
}
