package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.hihelloy.work.lib.GameObject;
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

    private enum DashState { AIRBORNE, SPIKING, LANDING }
    private DashState state;

    private boolean spikeUsed;
    private long airborneStart;
    private long launchTime;

    private List<SpikeSegment> spikeSegments = new ArrayList<>();
    private Location spikeTop;
    private Vector spikeDir;
    private double spikeProgress;
    private double spikeTotalLen;
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
        this.launchStrength = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneDash.LaunchStrength", 1.4);
        this.spikeDamage = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneDash.SpikeDamage", 6.0);
        this.spikeRange = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneDash.SpikeRange", 12.0);
        this.landingShockwaveRadius = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneDash.LandingRadius", 3.0);
        this.landingShockwaveDamage = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneDash.LandingDamage", 2.5);
        this.airWindow = config.getLong("ExtraAbilities.Hihelloy.Earth.StoneDash.AirWindow", 2000);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Earth.StoneDash.MaxLifetime", 6000);

        this.state = DashState.AIRBORNE;
        this.spikeUsed = false;
        this.airborneStart = System.currentTimeMillis();
        this.launchTime = System.currentTimeMillis();
        this.shockwaveProgress = 0;

        Vector dir = player.getLocation().getDirection().clone();
        dir.setY(Math.max(dir.getY(), 0.3));
        dir.normalize().multiply(launchStrength);
        if (dir.getY() < 0.55) dir.setY(0.55);
        player.setVelocity(dir);

        Location base = player.getLocation().clone();
        player.getWorld().playSound(base, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.8f);
        player.getWorld().playSound(base, Sound.BLOCK_STONE_BREAK, 1.5f, 0.7f);
        player.getWorld().spawnParticle(Particle.BLOCK, base, 18, 0.3, 0.1, 0.3, 0,
                Material.STONE_BRICKS.createBlockData());
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) { remove(); return; }

        if (state == DashState.AIRBORNE) {
            long airElapsed = System.currentTimeMillis() - airborneStart;
            double windowFraction = 1.0 - (airElapsed / (double) airWindow);

            boolean pastGracePeriod = airElapsed > 400;
            if (pastGracePeriod && player.isOnGround()) {
                beginLanding(player.getLocation());
                return;
            }
            if (airElapsed > airWindow) {
                beginLanding(player.getLocation());
                return;
            }

            spawnAirborneIndicator(windowFraction);

        } else if (state == DashState.SPIKING) {
            spikeProgress += 0.9;

            int visibleSegments = (int) Math.min(
                    (spikeProgress / spikeTotalLen) * spikeSegments.size() + 1,
                    spikeSegments.size());
            for (int i = 0; i < visibleSegments; i++) {
                spikeSegments.get(i).display();
            }

            Location frontLoc = spikeTop.clone().add(
                    spikeDir.clone().multiply(Math.min(spikeProgress, spikeTotalLen)));
            player.getWorld().spawnParticle(Particle.BLOCK, frontLoc, 3, 0.15, 0.1, 0.15, 0,
                    Material.STONE_BRICKS.createBlockData());

            checkSpikeHit(frontLoc);

            if (spikeProgress >= spikeTotalLen) {
                beginLanding(frontLoc);
            }

        } else if (state == DashState.LANDING) {
            shockwaveProgress += 0.3;
            spawnShockwaveRing(landingPoint, shockwaveProgress);
            checkShockwaveHits(landingPoint, shockwaveProgress);
            if (shockwaveProgress >= landingShockwaveRadius) {
                remove();
            }
        }
    }

    public void onLeftClick() {
        if (state != DashState.AIRBORNE || spikeUsed) return;
        long elapsed = System.currentTimeMillis() - airborneStart;
        if (elapsed > airWindow) return;

        spikeUsed = true;
        state = DashState.SPIKING;
        spikeProgress = 0;
        spikeDir = new Vector(0, -1, 0);
        spikeTop = player.getLocation().clone().add(0, 1.5, 0);

        Location search = spikeTop.clone();
        Location ground = null;
        for (int i = 0; i < (int) spikeRange; i++) {
            search.subtract(0, 1, 0);
            if (search.getBlock().isSolid()) {
                ground = search.clone().add(0, 1, 0);
                break;
            }
        }
        Location spikeBottom = ground != null ? ground : spikeTop.clone().subtract(0, spikeRange, 0);
        spikeTotalLen = spikeTop.distance(spikeBottom);

        int segCount = Math.max(3, (int) (spikeTotalLen * 2.0));
        for (int i = 0; i < segCount; i++) {
            double t = (double) i / segCount;
            Location segLoc = spikeTop.clone().add(spikeDir.clone().multiply(t * spikeTotalLen));
            double width = 0.45 - t * 0.28;
            spikeSegments.add(new SpikeSegment(player, segLoc, width, i == 0));
        }

        player.getWorld().playSound(spikeTop, Sound.ENTITY_IRON_GOLEM_ATTACK, 2.0f, 0.6f);
        player.getWorld().playSound(spikeTop, Sound.BLOCK_STONE_BREAK, 1.5f, 0.5f);
    }

    private void checkSpikeHit(Location frontLoc) {
        if (frontLoc.getBlock().isSolid()) {
            beginLanding(frontLoc);
            return;
        }
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(frontLoc, 1.1)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())
                    && !spikeHit.contains(e)) {
                spikeHit.add(e);
                DamageHandler.damageEntity((LivingEntity) e, spikeDamage, this);
                Vector kb = new Vector(0, -0.4, 0);
                e.setVelocity(e.getVelocity().add(kb));
                player.getWorld().spawnParticle(Particle.BLOCK, frontLoc, 20, 0.2, 0.2, 0.2, 0,
                        Material.STONE_BRICKS.createBlockData());
                player.getWorld().playSound(frontLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 2.0f, 0.7f);
                beginLanding(frontLoc);
                return;
            }
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
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center, radius + 0.5)) {
            if (!(e instanceof LivingEntity) || e.getUniqueId().equals(player.getUniqueId())) continue;
            if (shockwaveHit.contains(e)) continue;
            if (Math.abs(e.getLocation().distance(center) - radius) < 0.8) {
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
        if (Math.random() > 0.35) return;
        Location below = player.getLocation().clone();
        for (int i = 0; i < 10; i++) {
            below.subtract(0, 1, 0);
            if (below.getBlock().isSolid()) { below.add(0, 1, 0); break; }
        }
        double r = 0.5 + (1.0 - windowFraction) * 0.5;
        double a = Math.random() * Math.PI * 2;
        player.getWorld().spawnParticle(Particle.BLOCK,
                below.clone().add(Math.cos(a) * r, 0.05, Math.sin(a) * r),
                1, 0, 0, 0, 0, Material.STONE.createBlockData());
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
        return "Launch yourself into the air. Left-click while airborne to spike straight down — massive damage on impact. Landing always creates a shockwave.";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Launch upward-forward.\nLeft Click again (airborne): Drive spike downward.\nLanding always creates a ground shockwave.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        for (SpikeSegment seg : spikeSegments) seg.destroy();
    }

    @Override
    public void load() {
        abilityListener = new StoneDashListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.Cooldown", 5000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.LaunchStrength", 1.4);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.SpikeDamage", 6.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.SpikeRange", 12.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.LandingRadius", 3.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.LandingDamage", 2.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.AirWindow", 2000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneDash.MaxLifetime", 6000L);
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
        private GameObject obj;

        SpikeSegment(Player player, Location loc, double width, boolean isHead) {
            this.player = player;
            this.obj = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    loc, new Vector(width, 0.55, width), new Vector(0, 0, 0), new Vector());
            this.obj.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
            this.obj.setBlockMaterial(isHead ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
        }

        void display() { if (obj != null) obj.updateAndDisplay(); }
        void destroy() { if (obj != null) obj.destroy(); }
    }
}
