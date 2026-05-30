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

public class StoneDash extends EarthAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double launchStrength;
    private double spikeDamage;
    private double spikeRange;
    private double landingShockwaveRadius;
    private double landingShockwaveDamage;
    private long airWindow;
    private long maxLifetime;

    private enum DashState {
        LAUNCHING,
        AIRBORNE,
        SPIKING,
        LANDING,
        DONE
    }
    private DashState state;

    private GameObject launchBlock;
    private Transition launchTransition;
    private long launchStartTime;
    private static final long LAUNCH_DURATION = 300;

    private boolean spikeUsed;
    private long airborneStart;

    private List<SpikeSegment> spikeSegments = new ArrayList<>();
    private Location spikeTop;
    private Location spikeBottom;
    private Vector spikeDir;
    private double spikeProgress;
    private Set<Entity> spikeHit = new HashSet<>();

    private double shockwaveProgress;
    private Location landingPoint;
    private Set<Entity> shockwaveHit = new HashSet<>();

    public StoneDash(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Earth.StoneDash.Cooldown", 5000);
        this.launchStrength = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneDash.LaunchStrength", 1.5);
        this.spikeDamage = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneDash.SpikeDamage", 6.0);
        this.spikeRange = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneDash.SpikeRange", 12.0);
        this.landingShockwaveRadius = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneDash.LandingRadius", 3.0);
        this.landingShockwaveDamage = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneDash.LandingDamage", 2.5);
        this.airWindow = config.getLong("ExtraAbilities.Hihelloy.Earth.StoneDash.AirWindow", 1800);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Earth.StoneDash.MaxLifetime", 5000);

        this.state = DashState.LAUNCHING;
        this.spikeUsed = false;
        this.launchStartTime = System.currentTimeMillis();
        this.shockwaveProgress = 0;

        Vector dir = player.getLocation().getDirection().clone();
        dir.setY(Math.max(dir.getY(), 0.35));
        dir.normalize().multiply(launchStrength);
        dir.setY(Math.max(dir.getY(), 0.6));
        player.setVelocity(dir);
        player.setFallDistance(-999f);

        Location base = player.getLocation().clone();

        this.launchBlock = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                base, new Vector(0.65, 0.25, 0.65),
                new Vector(0, 0, 0), new Vector());
        this.launchBlock.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.launchBlock.setBlockMaterial(Material.STONE_BRICKS);

        this.launchTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(LAUNCH_DURATION / 50.0, 0, 0),
                new Vector(0, 0, 0));

        player.getWorld().playSound(base, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.8f);
        player.getWorld().playSound(base, Sound.BLOCK_STONE_BREAK, 1.5f, 0.7f);
        player.getWorld().spawnParticle(Particle.BLOCK, base, 18, 0.3, 0.1, 0.3, 0,
                Material.STONE_BRICKS.createBlockData());
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        if (state == DashState.LAUNCHING) {
            launchTransition.update();
            double p = launchTransition.getX();

            Location base = player.getLocation().clone();
            launchBlock.setLocation(base.clone().subtract(0, 0.2, 0));
            double s = 0.65 + p * 0.15;
            launchBlock.setScale(new Vector(s, 0.25 * (1.0 - p), s));
            launchBlock.updateAndDisplay();

            if (p >= 0.99) {
                launchBlock.destroy();
                launchBlock = null;
                state = DashState.AIRBORNE;
                airborneStart = System.currentTimeMillis();
            }

        } else if (state == DashState.AIRBORNE) {
            player.setFallDistance(-999f);

            long airElapsed = System.currentTimeMillis() - airborneStart;
            double windowFraction = 1.0 - (airElapsed / (double) airWindow);

            if (airElapsed > airWindow || player.isOnGround()) {
                beginLanding(player.getLocation());
                return;
            }

            spawnAirborneIndicator(windowFraction);

        } else if (state == DashState.SPIKING) {
            spikeProgress += 1.0;
            double totalLen = spikeTop.distance(spikeBottom);

            for (int i = spikeSegments.size() - 1; i >= 0; i--) {
                SpikeSegment seg = spikeSegments.get(i);
                double segDist = (double) i / spikeSegments.size() * totalLen;
                if (segDist <= spikeProgress) {
                    seg.update(spikeProgress, totalLen);
                    seg.display();
                }
            }

            Location frontLoc = spikeTop.clone().add(spikeDir.clone().multiply(Math.min(spikeProgress, totalLen)));
            player.getWorld().spawnParticle(Particle.BLOCK, frontLoc, 4, 0.15, 0.15, 0.15, 0,
                    Material.STONE_BRICKS.createBlockData());

            checkSpikeHit(frontLoc);

            if (spikeProgress >= totalLen) {
                beginLanding(frontLoc);
            }

        } else if (state == DashState.LANDING) {
            shockwaveProgress += 0.35;
            spawnShockwaveRing(landingPoint, shockwaveProgress);
            checkShockwaveHits(landingPoint, shockwaveProgress);
            if (shockwaveProgress >= landingShockwaveRadius) {
                remove();
            }
        }
    }

    public void onLeftClick() {
        if (state != DashState.AIRBORNE || spikeUsed) return;

        spikeUsed = true;
        state = DashState.SPIKING;
        spikeProgress = 0;

        spikeTop = player.getLocation().clone().add(0, 1.5, 0);
        spikeDir = new Vector(0, -1, 0);

        Location groundSearch = spikeTop.clone();
        Location foundGround = null;
        for (int i = 0; i < (int) spikeRange; i++) {
            groundSearch.subtract(0, 1, 0);
            if (groundSearch.getBlock().isSolid()) {
                foundGround = groundSearch.clone().add(0, 1, 0);
                break;
            }
        }
        spikeBottom = foundGround != null ? foundGround : spikeTop.clone().subtract(0, spikeRange, 0);

        double totalLen = spikeTop.distance(spikeBottom);
        int segCount = Math.max(3, (int) (totalLen * 1.8));
        for (int i = 0; i < segCount; i++) {
            double t = (double) i / segCount;
            Location segLoc = spikeTop.clone().add(spikeDir.clone().multiply(t * totalLen));
            double width = 0.45 - t * 0.3;
            spikeSegments.add(new SpikeSegment(player, segLoc, width, i, segCount));
        }

        player.getWorld().playSound(spikeTop, Sound.ENTITY_IRON_GOLEM_ATTACK, 2.0f, 0.6f);
        player.getWorld().playSound(spikeTop, Sound.BLOCK_STONE_BREAK, 1.5f, 0.5f);
    }

    private void checkSpikeHit(Location frontLoc) {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(frontLoc, 1.0)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())
                    && !spikeHit.contains(e)) {
                spikeHit.add(e);
                DamageHandler.damageEntity((LivingEntity) e, spikeDamage, this);
                Vector kb = new Vector(0, -0.5, 0)
                        .add(player.getLocation().getDirection().clone().setY(0).normalize().multiply(0.3));
                e.setVelocity(e.getVelocity().add(kb));
                player.getWorld().spawnParticle(Particle.BLOCK, frontLoc, 20, 0.2, 0.2, 0.2, 0,
                        Material.STONE_BRICKS.createBlockData());
                player.getWorld().playSound(frontLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 2.0f, 0.7f);
            }
        }

        if (frontLoc.getBlock().isSolid()) {
            beginLanding(frontLoc);
        }
    }

    private void beginLanding(Location loc) {
        state = DashState.LANDING;
        landingPoint = loc.clone();
        shockwaveProgress = 0;
        shockwaveHit.clear();

        for (SpikeSegment seg : spikeSegments) seg.destroy();
        spikeSegments.clear();

        player.getWorld().playSound(landingPoint, Sound.ENTITY_IRON_GOLEM_HURT, 1.5f, 0.8f);
        player.getWorld().spawnParticle(Particle.BLOCK, landingPoint, 25, 0.4, 0.1, 0.4, 0,
                Material.STONE_BRICKS.createBlockData());
    }

    private void checkShockwaveHits(Location center, double radius) {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center, radius + 0.6)) {
            if (!(e instanceof LivingEntity) || e.getUniqueId().equals(player.getUniqueId())) continue;
            if (shockwaveHit.contains(e)) continue;
            double dist = e.getLocation().distance(center);
            if (Math.abs(dist - radius) < 0.9) {
                shockwaveHit.add(e);
                DamageHandler.damageEntity((LivingEntity) e, landingShockwaveDamage, this);
                Vector kb = e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.7);
                kb.setY(0.3);
                e.setVelocity(e.getVelocity().add(kb));
            }
        }
    }

    private void spawnShockwaveRing(Location center, double radius) {
        int points = Math.max(10, (int) (radius * 12));
        for (int i = 0; i < points; i++) {
            double a = (2 * Math.PI / points) * i;
            Location p = center.clone().add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius);
            player.getWorld().spawnParticle(Particle.BLOCK, p, 1, 0, 0, 0, 0,
                    Material.STONE.createBlockData());
        }
    }

    private void spawnAirborneIndicator(double windowFraction) {
        if (Math.random() > 0.4) return;
        Location below = player.getLocation().clone();
        for (int i = 0; i < 8; i++) {
            below.subtract(0, 1, 0);
            if (below.getBlock().isSolid()) {
                below.add(0, 1, 0);
                break;
            }
        }
        double r = 0.6 + (1.0 - windowFraction) * 0.4;
        double a = Math.random() * Math.PI * 2;
        Location p = below.clone().add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
        player.getWorld().spawnParticle(Particle.BLOCK, p, 1, 0, 0, 0, 0,
                Material.STONE.createBlockData());
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
        return "StoneDash";
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
        return "Launch yourself diagonally in your look direction on a stone pillar. " +
                "Left-click while airborne to spike downward — if it hits an enemy below, deal massive impact damage. " +
                "Landing always creates a small shockwave.";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Activate — launches you diagonally forward/upward." +
                "\nLeft Click again (airborne, within time window): Drive a stone spike straight down." +
                "\nMissing the spike window or landing naturally still triggers a landing shockwave.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (launchBlock != null) launchBlock.destroy();
        for (SpikeSegment seg : spikeSegments) seg.destroy();
    }

    @Override
    public void load() {
        abilityListener = new StoneDashListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.Cooldown", 5000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.LaunchStrength", 1.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.SpikeDamage", 6.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.SpikeRange", 12.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.LandingRadius", 3.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.LandingDamage", 2.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.AirWindow", 1800L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.MaxLifetime", 5000L);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }

    private static class SpikeSegment {
        private final Player player;
        private final int index;
        private final int total;
        private GameObject obj;
        private Location location;

        SpikeSegment(Player player, Location loc, double width, int index, int total) {
            this.player = player;
            this.index = index;
            this.total = total;
            this.location = loc.clone();

            this.obj = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    loc, new Vector(width, 0.5, width),
                    new Vector(0, 0, 0), new Vector());
            this.obj.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
            this.obj.setBlockMaterial(index == 0 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
        }

        void update(double spikeProgress, double totalLen) {
            double t = (double) index / total;
            double distFromTop = t * totalLen;
            double vis = Math.min(1.0, (spikeProgress - distFromTop) * 1.5);
            double width = (0.45 - t * 0.3) * vis;
            this.obj.setScale(new Vector(width, 0.5, width));
            this.obj.updateAndDisplay();
        }

        void display() {
            this.obj.updateAndDisplay();
        }

        void destroy() {
            if (obj != null) obj.destroy();
        }
    }
}