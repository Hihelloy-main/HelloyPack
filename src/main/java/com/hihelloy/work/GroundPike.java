package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
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

public class GroundPike extends EarthAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double pikeHeight;
    private double targetRange;
    private long strikeDelay;
    private long maxLifetime;

    private enum PikeState {
        TARGETING,
        RISING,
        HELD,
        RETRACTING
    }
    private PikeState state;

    private Location strikeLocation;
    private Location targetLocation;

    private List<PikeSegment> segments = new ArrayList<>();
    private int segmentCount;
    private double risenHeight;

    private Transition riseTransition;
    private Transition retractTransition;
    private long riseStartTime;
    private long heldStartTime;
    private static final long HELD_DURATION = 1500;

    private Set<Entity> pikeHit = new HashSet<>();
    private boolean didLaunch = false;

    public GroundPike(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Earth.GroundPike.Cooldown", 8000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Earth.GroundPike.Damage", 5.5);
        this.pikeHeight = config.getDouble("ExtraAbilities.Hihelloy.Earth.GroundPike.PikeHeight", 5.0);
        this.targetRange = config.getDouble("ExtraAbilities.Hihelloy.Earth.GroundPike.TargetRange", 14.0);
        this.strikeDelay = config.getLong("ExtraAbilities.Hihelloy.Earth.GroundPike.StrikeDelay", 400);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Earth.GroundPike.MaxLifetime", 8000);

        this.segmentCount = (int) Math.ceil(pikeHeight / 0.6);
        this.risenHeight = 0;
        this.state = PikeState.TARGETING;

        targetLocation = GeneralMethods.getTargetedLocation(player, (int) targetRange);
        strikeLocation = targetLocation.clone();
        for (int i = 0; i < 20; i++) {
            if (!strikeLocation.getBlock().isSolid()) {
                strikeLocation.subtract(0, 1, 0);
            } else {
                strikeLocation.add(0, 1, 0);
                break;
            }
        }

        Location buildBase = strikeLocation.clone().subtract(0, segmentCount * 0.6, 0);
        Material[] mats = {Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.COBBLESTONE, Material.MOSSY_STONE_BRICKS};
        for (int i = 0; i < segmentCount; i++) {
            double progress = (double) i / segmentCount;
            double width = 0.45 * (1.0 - progress * 0.6);
            double segHeight = 0.6;
            Material mat = mats[i % mats.length];
            Location segLoc = buildBase.clone().add(0, i * segHeight, 0);
            segments.add(new PikeSegment(player, segLoc, width, segHeight, i, mat));
        }

        spawnTargetIndicator(strikeLocation);
        player.getWorld().playSound(strikeLocation, Sound.BLOCK_STONE_BREAK, 1.0f, 0.5f);

        riseStartTime = System.currentTimeMillis() + strikeDelay;
        riseTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(pikeHeight, 0, 0),
                new Vector(pikeHeight / 0.6 * 1.2, 0, 0),
                new Vector(strikeDelay / 50.0, 0, 0));
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        if (state == PikeState.TARGETING) {
            if (System.currentTimeMillis() >= riseStartTime) {
                state = PikeState.RISING;
                player.getWorld().playSound(strikeLocation, Sound.ENTITY_IRON_GOLEM_ATTACK, 2.0f, 0.5f);
                player.getWorld().playSound(strikeLocation, Sound.BLOCK_STONE_BREAK, 2.0f, 0.7f);
                player.getWorld().spawnParticle(Particle.BLOCK, strikeLocation, 30, 0.4, 0.1, 0.4, 0, Material.STONE.createBlockData());
            }
        } else if (state == PikeState.RISING) {
            riseTransition.update();
            risenHeight = riseTransition.getX();

            for (int i = 0; i < segments.size(); i++) {
                segments.get(i).updatePosition(strikeLocation, risenHeight, i, segmentCount);
                segments.get(i).display();
            }

            if (!didLaunch) {
                checkPikeHit();
            }

            if (risenHeight >= pikeHeight * 0.99) {
                state = PikeState.HELD;
                heldStartTime = System.currentTimeMillis();
            }
        } else if (state == PikeState.HELD) {
            for (int i = 0; i < segments.size(); i++) {
                segments.get(i).updatePosition(strikeLocation, pikeHeight, i, segmentCount);
                segments.get(i).display();
            }

            if (System.currentTimeMillis() > heldStartTime + HELD_DURATION) {
                state = PikeState.RETRACTING;
                retractTransition = new Transition(
                        new Vector(pikeHeight, 0, 0),
                        new Vector(0, 0, 0),
                        new Vector(pikeHeight / 0.6 * 0.8, 0, 0),
                        new Vector(0, 0, 0));
                player.getWorld().playSound(strikeLocation, Sound.BLOCK_STONE_BREAK, 1.5f, 1.2f);
            }
        } else if (state == PikeState.RETRACTING) {
            retractTransition.update();
            risenHeight = retractTransition.getX();

            for (int i = 0; i < segments.size(); i++) {
                segments.get(i).updatePosition(strikeLocation, risenHeight, i, segmentCount);
                segments.get(i).display();
            }

            if (risenHeight <= 0.01) {
                remove();
            }
        }
    }

    private void checkPikeHit() {
        double topY = strikeLocation.getY() + risenHeight;
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(
                strikeLocation.clone().add(0, risenHeight * 0.5, 0), 1.2)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                if (!pikeHit.contains(e) && e.getLocation().getY() < topY + 1.0) {
                    pikeHit.add(e);
                    didLaunch = true;
                    DamageHandler.damageEntity((LivingEntity) e, damage, this);
                    Vector launch = new Vector(0, 1.4, 0);
                    e.setVelocity(e.getVelocity().add(launch));
                    player.getWorld().spawnParticle(Particle.BLOCK,
                            e.getLocation(), 15, 0.3, 0.2, 0.3, 0, Material.STONE_BRICKS.createBlockData());
                    player.getWorld().playSound(e.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.8f);
                }
            }
        }
    }

    private void spawnTargetIndicator(Location loc) {
        int points = 12;
        for (int i = 0; i < points; i++) {
            double a = (2 * Math.PI / points) * i;
            double r = 0.8;
            Location p = loc.clone().add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
            player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0,
                    0, new Particle.DustOptions(Color.fromRGB(255, 120, 40), 1.0f));
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
        return strikeLocation;
    }
    @Override
    public String getName() {
        return "GroundPike";
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
        return "Erupt a towering stone spike beneath your target, launching them into the air!";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Strike the ground at your target location with a rising stone pike.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        for (PikeSegment seg : segments) seg.destroy();
    }

    @Override
    public void load() {
        abilityListener = new GroundPikeListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.GroundPike.Cooldown", 8000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.GroundPike.Damage", 5.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.GroundPike.PikeHeight", 5.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.GroundPike.TargetRange", 14.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.GroundPike.StrikeDelay", 400L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.GroundPike.MaxLifetime", 8000L);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }

    private static class PikeSegment {
        private final Player player;
        private final int index;
        private GameObject segObj;

        PikeSegment(Player player, Location loc, double width, double height, int index, Material mat) {
            this.player = player;
            this.index = index;
            this.segObj = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    loc, new Vector(width, height, width),
                    new Vector(0, 0, 0), new Vector());
            this.segObj.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
            this.segObj.setBlockMaterial(mat);
        }

        void updatePosition(Location base, double totalHeight, int idx, int total) {
            double segHeight = 0.6;
            double progress = (double) idx / total;
            double width = 0.45 * (1.0 - progress * 0.6);
            double yOffset = idx * segHeight - (total * segHeight - totalHeight);
            Location newLoc = base.clone().add(0, yOffset, 0);
            segObj.setLocation(newLoc);
            segObj.setScale(new Vector(width, segHeight, width));
        }

        void display() {
            segObj.updateAndDisplay();
        }

        void destroy() {
            if (segObj != null) segObj.destroy();
        }
    }
}
