package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.LavaAbility;
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
import java.util.List;
import java.util.Set;

public class MagmaFist extends LavaAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double targetRange;
    private long strikeDelay;
    private double fistHeight;
    private double launchStrength;
    private long maxLifetime;

    private enum FistState { TELEGRAPHING, RISING, HELD, RETRACTING }
    private FistState state;

    private Location strikeCenter;
    private long telegraphStart;
    private long riseStart;
    private static final long RISE_DURATION = 450;
    private static final long HOLD_DURATION = 600;

    private List<GameObject> knuckles = new ArrayList<>();
    private List<GameObject> palmSlabs = new ArrayList<>();
    private List<GameObject> fingers = new ArrayList<>();
    private GameObject wrist;

    private double riseProgress;
    private Transition riseTransition;
    private Transition retractTransition;
    private Set<Entity> fistHit = new HashSet<>();
    private boolean launched;

    public MagmaFist(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Lava.MagmaFist.Cooldown", 9000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Lava.MagmaFist.Damage", 7.0);
        this.targetRange = config.getDouble("ExtraAbilities.Hihelloy.Lava.MagmaFist.TargetRange", 12.0);
        this.strikeDelay = config.getLong("ExtraAbilities.Hihelloy.Lava.MagmaFist.StrikeDelay", 600);
        this.fistHeight = config.getDouble("ExtraAbilities.Hihelloy.Lava.MagmaFist.FistHeight", 3.5);
        this.launchStrength = config.getDouble("ExtraAbilities.Hihelloy.Lava.MagmaFist.LaunchStrength", 1.2);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Lava.MagmaFist.MaxLifetime", 6000);

        this.state = FistState.TELEGRAPHING;
        this.riseProgress = 0;
        this.launched = false;
        this.telegraphStart = System.currentTimeMillis();

        strikeCenter = GeneralMethods.getTargetedLocation(player, (int) targetRange);
        for (int i = 0; i < 5; i++) {
            if (!strikeCenter.getBlock().isSolid()) strikeCenter.subtract(0, 1, 0);
            else { strikeCenter.add(0, 1, 0); break; }
        }

        buildFist();

        player.getWorld().playSound(strikeCenter, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.4f);
        player.getWorld().playSound(strikeCenter, Sound.BLOCK_LAVA_AMBIENT, 1.2f, 0.6f);
    }

    private void buildFist() {
        Location base = strikeCenter.clone().subtract(0, fistHeight, 0);
        Material lava = Material.MAGMA_BLOCK;
        Material dark = Material.BLACKSTONE;
        Material crack = Material.CRACKED_STONE_BRICKS;

        this.wrist = makeObj(base, new Vector(0.7, fistHeight * 0.5, 0.7), lava);

        for (int i = 0; i < 4; i++) {
            double angle = (90.0 * i);
            double ox = Math.cos(Math.toRadians(angle)) * 0.25;
            double oz = Math.sin(Math.toRadians(angle)) * 0.25;
            Location palmLoc = base.clone().add(ox, fistHeight * 0.5, oz);
            palmSlabs.add(makeObj(palmLoc, new Vector(0.32, 0.32, 0.32), i % 2 == 0 ? lava : dark));
        }

        for (int i = 0; i < 4; i++) {
            double angle = 45 + (90.0 * i);
            double ox = Math.cos(Math.toRadians(angle)) * 0.32;
            double oz = Math.sin(Math.toRadians(angle)) * 0.32;
            Location kLoc = base.clone().add(ox, fistHeight * 0.55, oz);
            knuckles.add(makeObj(kLoc, new Vector(0.22, 0.28, 0.22), crack));
        }

        for (int i = 0; i < 4; i++) {
            double angle = 45 + (90.0 * i);
            double ox = Math.cos(Math.toRadians(angle)) * 0.35;
            double oz = Math.sin(Math.toRadians(angle)) * 0.35;
            Location fLoc = base.clone().add(ox, fistHeight * 0.65, oz);
            fingers.add(makeObj(fLoc, new Vector(0.15, 0.38, 0.15), dark));
        }
    }

    private GameObject makeObj(Location loc, Vector scale, Material mat) {
        GameObject obj = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                loc, scale, new Vector(0, 0, 0), new Vector());
        obj.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        obj.setBlockMaterial(mat);
        return obj;
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) { remove(); return; }

        if (state == FistState.TELEGRAPHING) {
            spawnTelegraphIndicator();
            if (System.currentTimeMillis() - telegraphStart >= strikeDelay) {
                state = FistState.RISING;
                riseStart = System.currentTimeMillis();
                riseTransition = new Transition(
                        new Vector(0, 0, 0), new Vector(fistHeight, 0, 0),
                        new Vector(RISE_DURATION / 50.0, 0, 0), new Vector(0, 0, 0));
                player.getWorld().playSound(strikeCenter, Sound.ENTITY_IRON_GOLEM_ATTACK, 2.0f, 0.5f);
                player.getWorld().spawnParticle(Particle.LAVA, strikeCenter, 20, 0.4, 0.1, 0.4, 0);
            }

        } else if (state == FistState.RISING) {
            riseTransition.update();
            double rise = riseTransition.getX();
            setFistHeight(rise);
            displayFist();
            checkFistHit(rise);

            if (rise >= fistHeight * 0.99) {
                state = FistState.HELD;
                riseStart = System.currentTimeMillis();
            }

        } else if (state == FistState.HELD) {
            setFistHeight(fistHeight);
            displayFist();
            if (System.currentTimeMillis() - riseStart >= HOLD_DURATION) {
                state = FistState.RETRACTING;
                retractTransition = new Transition(
                        new Vector(fistHeight, 0, 0), new Vector(0, 0, 0),
                        new Vector(25, 0, 0), new Vector(0, 0, 0));
                player.getWorld().playSound(strikeCenter, Sound.BLOCK_STONE_BREAK, 2.0f, 1.2f);
            }

        } else if (state == FistState.RETRACTING) {
            retractTransition.update();
            double rise = retractTransition.getX();
            setFistHeight(rise);
            displayFist();
            if (rise <= 0.01) remove();
        }
    }

    private void setFistHeight(double rise) {
        Location base = strikeCenter.clone().subtract(0, fistHeight - rise, 0);

        if (wrist != null) {
            wrist.setLocation(base.clone().subtract(0, rise * 0.3, 0));
            wrist.updateAndDisplay();
        }

        for (int i = 0; i < palmSlabs.size(); i++) {
            double angle = 90.0 * i;
            double ox = Math.cos(Math.toRadians(angle)) * 0.25;
            double oz = Math.sin(Math.toRadians(angle)) * 0.25;
            palmSlabs.get(i).setLocation(base.clone().add(ox, 0, oz));
        }
        for (int i = 0; i < knuckles.size(); i++) {
            double angle = 45 + (90.0 * i);
            double ox = Math.cos(Math.toRadians(angle)) * 0.32;
            double oz = Math.sin(Math.toRadians(angle)) * 0.32;
            knuckles.get(i).setLocation(base.clone().add(ox, rise * 0.08, oz));
        }
        for (int i = 0; i < fingers.size(); i++) {
            double angle = 45 + (90.0 * i);
            double ox = Math.cos(Math.toRadians(angle)) * 0.35;
            double oz = Math.sin(Math.toRadians(angle)) * 0.35;
            fingers.get(i).setLocation(base.clone().add(ox, rise * 0.18, oz));
        }
    }

    private void displayFist() {
        for (GameObject obj : palmSlabs) obj.updateAndDisplay();
        for (GameObject obj : knuckles) obj.updateAndDisplay();
        for (GameObject obj : fingers) obj.updateAndDisplay();
    }

    private void checkFistHit(double rise) {
        Location fistTop = strikeCenter.clone().add(0, rise * 0.2, 0);
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(fistTop, 1.1)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())
                    && !fistHit.contains(e)) {
                fistHit.add(e);
                DamageHandler.damageEntity((LivingEntity) e, damage, this);
                e.setFireTicks(80);
                Vector launch = new Vector(
                        (Math.random() - 0.5) * launchStrength,
                        launchStrength,
                        (Math.random() - 0.5) * launchStrength);
                e.setVelocity(e.getVelocity().add(launch));
                player.getWorld().spawnParticle(Particle.LAVA, fistTop, 12, 0.2, 0.2, 0.2, 0);
                player.getWorld().playSound(fistTop, Sound.ENTITY_PLAYER_ATTACK_CRIT, 2.0f, 0.7f);
            }
        }
    }

    private void spawnTelegraphIndicator() {
        double progress = (System.currentTimeMillis() - telegraphStart) / (double) strikeDelay;
        int points = 14;
        for (int i = 0; i < points; i++) {
            double a = (2 * Math.PI / points) * i;
            double r = 0.9 * (1.0 - progress * 0.4);
            Location p = strikeCenter.clone().add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
            player.getWorld().spawnParticle(Particle.LAVA, p, 1, 0.03, 0.03, 0.03, 0);
        }
        player.getWorld().spawnParticle(Particle.FLAME, strikeCenter.clone().add(0, 0.1, 0),
                2, 0.3, 0.05, 0.3, 0.02);
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
        return strikeCenter;
    }
    @Override
    public String getName() {
        return "MagmaFist";
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
        return "Erupt a massive magma fist from the ground beneath your target, launching them skyward.";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Strike the ground at target location with a rising lava fist.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (wrist != null) wrist.destroy();
        for (GameObject obj : palmSlabs) obj.destroy();
        for (GameObject obj : knuckles) obj.destroy();
        for (GameObject obj : fingers) obj.destroy();
    }

    @Override
    public void load() {
        abilityListener = new MagmaFistListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.MagmaFist.Cooldown", 9000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.MagmaFist.Damage", 7.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.MagmaFist.TargetRange", 12.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.MagmaFist.StrikeDelay", 600L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.MagmaFist.FistHeight", 3.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.MagmaFist.LaunchStrength", 1.2);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Lava.MagmaFist.MaxLifetime", 6000L);
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
