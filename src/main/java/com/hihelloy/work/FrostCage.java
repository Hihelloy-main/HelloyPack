package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.IceAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.hihelloy.work.lib.GameObject;
import com.hihelloy.work.lib.Transition;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FrostCage extends IceAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double tickDamage;
    private double cageRadius;
    private double spireHeight;
    private int spireCount;
    private long duration;
    private long maxLifetime;

    private enum CageState { RISING, ACTIVE, SHATTERING }
    private CageState state;

    private Location cageCenter;
    private List<SpireColumn> spires = new ArrayList<>();
    private double riseProgress;
    private Transition riseTransition;
    private Transition shatterTransition;
    private long activeStart;
    private long lastDamageTick;
    private Set<Entity> trapped = new HashSet<>();

    public FrostCage(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Ice.FrostCage.Cooldown", 11000);
        this.tickDamage = config.getDouble("ExtraAbilities.Hihelloy.Ice.FrostCage.TickDamage", 1.0);
        this.cageRadius = config.getDouble("ExtraAbilities.Hihelloy.Ice.FrostCage.CageRadius", 2.5);
        this.spireHeight = config.getDouble("ExtraAbilities.Hihelloy.Ice.FrostCage.SpireHeight", 4.0);
        this.spireCount = config.getInt("ExtraAbilities.Hihelloy.Ice.FrostCage.SpireCount", 8);
        this.duration = config.getLong("ExtraAbilities.Hihelloy.Ice.FrostCage.Duration", 4000);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Ice.FrostCage.MaxLifetime", 8000);

        this.state = CageState.RISING;
        this.riseProgress = 0;
        this.lastDamageTick = System.currentTimeMillis();

        cageCenter = GeneralMethods.getTargetedLocation(player, 14);
        for (int i = 0; i < 5; i++) {
            if (!cageCenter.getBlock().isSolid()) cageCenter.subtract(0, 1, 0);
            else { cageCenter.add(0, 1, 0); break; }
        }

        for (int i = 0; i < spireCount; i++) {
            double angle = (360.0 / spireCount) * i;
            double ox = Math.cos(Math.toRadians(angle)) * cageRadius;
            double oz = Math.sin(Math.toRadians(angle)) * cageRadius;
            Location spireBase = cageCenter.clone().add(ox, 0, oz);
            spires.add(new SpireColumn(player, spireBase, spireHeight, i % 2 == 0));
        }

        this.riseTransition = new Transition(
                new Vector(0, 0, 0), new Vector(spireHeight, 0, 0),
                new Vector(spireHeight / 0.15, 0, 0), new Vector(0, 0, 0));

        player.getWorld().playSound(cageCenter, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.8f, 1.4f);
        player.getWorld().playSound(cageCenter, Sound.BLOCK_GLASS_BREAK, 1.5f, 0.6f);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, cageCenter, 20, cageRadius * 0.5, 0.2, cageRadius * 0.5, 0.05);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) { remove(); return; }

        if (state == CageState.RISING) {
            riseTransition.update();
            double rise = riseTransition.getX();

            for (SpireColumn spire : spires) {
                spire.setVisible(rise);
                spire.display();
            }

            spawnRiseParticles(rise);

            if (rise >= spireHeight * 0.99) {
                state = CageState.ACTIVE;
                activeStart = System.currentTimeMillis();
                player.getWorld().playSound(cageCenter, Sound.BLOCK_GLASS_BREAK, 2.0f, 0.5f);
                playFreezeEffect();
            }

        } else if (state == CageState.ACTIVE) {
            for (SpireColumn spire : spires) spire.display();

            spawnActiveParticles();
            applyTrapEffects();

            if (System.currentTimeMillis() - activeStart >= duration) {
                beginShatter();
            }

        } else if (state == CageState.SHATTERING) {
            shatterTransition.update();
            double scale = 1.0 - shatterTransition.getX();

            for (SpireColumn spire : spires) {
                spire.setScale(scale);
                spire.display();
            }

            spawnShatterParticles(shatterTransition.getX());

            if (shatterTransition.getX() >= 0.99) {
                remove();
            }
        }
    }

    private void beginShatter() {
        state = CageState.SHATTERING;
        shatterTransition = new Transition(
                new Vector(0, 0, 0), new Vector(1, 0, 0),
                new Vector(20, 0, 0), new Vector(0, 0, 0));
        player.getWorld().playSound(cageCenter, Sound.BLOCK_GLASS_BREAK, 2.0f, 1.4f);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, cageCenter.clone().add(0, spireHeight * 0.5, 0),
                30, cageRadius * 0.5, spireHeight * 0.3, cageRadius * 0.5, 0.1);
    }

    private void applyTrapEffects() {
        long now = System.currentTimeMillis();
        if (now - lastDamageTick < 1000) return;
        lastDamageTick = now;

        trapped.clear();
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(cageCenter.clone().add(0, spireHeight * 0.5, 0), cageRadius)) {
            if (!(e instanceof LivingEntity)) continue;
            if (e.getUniqueId().equals(player.getUniqueId())) continue;
            double dx = e.getLocation().getX() - cageCenter.getX();
            double dz = e.getLocation().getZ() - cageCenter.getZ();
            if (Math.sqrt(dx * dx + dz * dz) <= cageRadius) {
                trapped.add(e);
                DamageHandler.damageEntity((LivingEntity) e, tickDamage, this);
                ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 2, true, false));
                player.getWorld().playSound(e.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 1.5f);
            }
        }
    }

    private void playFreezeEffect() {
        for (int i = 0; i < 3; i++) {
            player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    cageCenter.clone().add(0, spireHeight * 0.5, 0), 10, cageRadius * 0.4, spireHeight * 0.2, cageRadius * 0.4, 0.04);
        }
    }

    private void spawnRiseParticles(double rise) {
        if (Math.random() > 0.5) return;
        int i = (int) (Math.random() * spires.size());
        Location baseLoc = spires.get(i).getBase();
        player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                baseLoc.clone().add(0, rise * Math.random(), 0), 1, 0.08, 0.05, 0.08, 0.02);
    }

    private void spawnActiveParticles() {
        if (Math.random() > 0.4) return;
        double a = Math.random() * Math.PI * 2;
        double r = cageRadius * (0.8 + Math.random() * 0.2);
        double h = Math.random() * spireHeight;
        Location p = cageCenter.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, p, 1, 0.05, 0.05, 0.05, 0.01);
    }

    private void spawnShatterParticles(double progress) {
        if (Math.random() > progress * 0.8) return;
        double a = Math.random() * Math.PI * 2;
        double r = cageRadius;
        double h = Math.random() * spireHeight;
        Location p = cageCenter.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, p, 2, 0.15, 0.15, 0.15, 0.08);
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
        return cageCenter;
    }
    @Override
    public String getName() {
        return "FrostCage";
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
        return "Raise a cage of towering ice spires around a target location, trapping and slowly freezing anyone caught inside.";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Raise ice cage at your target location.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        for (SpireColumn spire : spires) spire.destroy();
    }

    @Override
    public void load() {
        abilityListener = new FrostCageListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostCage.Cooldown", 11000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostCage.TickDamage", 1.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostCage.CageRadius", 2.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostCage.SpireHeight", 4.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostCage.SpireCount", 8);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostCage.Duration", 4000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Ice.FrostCage.MaxLifetime", 8000L);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }

    private static class SpireColumn {
        private final Player player;
        private final Location base;
        private final double maxHeight;
        private final boolean isSharp;
        private GameObject shaft;
        private GameObject tip;
        private double currentVisibleHeight = 0;
        private double globalScale = 1.0;

        SpireColumn(Player player, Location base, double maxHeight, boolean isSharp) {
            this.player = player;
            this.base = base.clone();
            this.maxHeight = maxHeight;
            this.isSharp = isSharp;

            double width = 0.35;
            this.shaft = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    base, new Vector(width, maxHeight, width), new Vector(0, 0, 0), new Vector());
            this.shaft.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
            this.shaft.setBlockMaterial(Material.PACKED_ICE);

            this.tip = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    base, new Vector(width * 0.6, maxHeight * 0.25, width * 0.6), new Vector(0, 0, 0), new Vector());
            this.tip.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
            this.tip.setBlockMaterial(isSharp ? Material.BLUE_ICE : Material.ICE);
        }

        void setVisible(double rise) {
            this.currentVisibleHeight = rise;
        }

        void setScale(double scale) {
            this.globalScale = scale;
        }

        void display() {
            double h = Math.min(currentVisibleHeight, maxHeight) * globalScale;
            if (h < 0.05) return;
            double w = 0.35 * globalScale;

            Location shaftLoc = base.clone().add(0, -(maxHeight - h) * 0.5, 0);
            shaft.setLocation(shaftLoc);
            shaft.setScale(new Vector(w, h, w));
            shaft.updateAndDisplay();

            Location tipLoc = base.clone().add(0, h * 0.5, 0);
            tip.setLocation(tipLoc);
            tip.setScale(new Vector(w * 0.6, h * 0.2, w * 0.6));
            tip.updateAndDisplay();
        }

        Location getBase() { return base; }

        void destroy() {
            if (shaft != null) shaft.destroy();
            if (tip != null) tip.destroy();
        }
    }
}
