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

import java.util.HashSet;
import java.util.Set;

public class StoneGauntlet extends EarthAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double punchDamage;
    private double shockwaveDamage;
    private double shockwaveRadius;
    private long maxLifetime;
    private long wearDuration;

    private enum GauntletState {
        FORMING,
        WORN,
        PUNCHING,
        SHOCKWAVE
    }
    private GauntletState state;

    private GameObject knuckle1;
    private GameObject knuckle2;
    private GameObject knuckle3;
    private GameObject knuckle4;
    private GameObject palm;
    private GameObject backplate;
    private GameObject sideGuard;

    private Transition formTransition;
    private Transition punchTransition;
    private Transition retractTransition;

    private double gauntletYaw;
    private double gauntletPitch;
    private double gauntletRoll;

    private long wearStartTime;
    private Set<Entity> punchHit = new HashSet<>();
    private double shockwaveProgress;
    private Location punchImpactLoc;

    public StoneGauntlet(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.Cooldown", 9000);
        this.punchDamage = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.PunchDamage", 6.0);
        this.shockwaveDamage = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.ShockwaveDamage", 3.0);
        this.shockwaveRadius = config.getDouble("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.ShockwaveRadius", 4.5);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.MaxLifetime", 20000);
        this.wearDuration = config.getLong("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.WearDuration", 12000);

        this.state = GauntletState.FORMING;
        this.gauntletYaw = -player.getLocation().getYaw();
        this.gauntletPitch = 0;
        this.gauntletRoll = 0;

        Location handLoc = GeneralMethods.getMainHandLocation(player);

        this.palm = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                handLoc, new Vector(0.55, 0.55, 0.35),
                new Vector(0, Math.toRadians(gauntletYaw), 0), new Vector());
        this.palm.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.palm.setBlockMaterial(Material.STONE_BRICKS);

        this.knuckle1 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.palm, new Vector(0.18, 0.18, 0.25),
                new Vector(0, 0, 0), new Vector(-0.18, 0.18, 0.25));
        this.knuckle1.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.knuckle1.setBlockMaterial(Material.CHISELED_STONE_BRICKS);

        this.knuckle2 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.palm, new Vector(0.18, 0.18, 0.25),
                new Vector(0, 0, 0), new Vector(-0.06, 0.2, 0.25));
        this.knuckle2.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.knuckle2.setBlockMaterial(Material.CHISELED_STONE_BRICKS);

        this.knuckle3 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.palm, new Vector(0.18, 0.18, 0.25),
                new Vector(0, 0, 0), new Vector(0.06, 0.2, 0.25));
        this.knuckle3.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.knuckle3.setBlockMaterial(Material.CHISELED_STONE_BRICKS);

        this.knuckle4 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.palm, new Vector(0.18, 0.18, 0.25),
                new Vector(0, 0, 0), new Vector(0.18, 0.18, 0.25));
        this.knuckle4.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.knuckle4.setBlockMaterial(Material.CHISELED_STONE_BRICKS);

        this.backplate = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.palm, new Vector(0.6, 0.6, 0.12),
                new Vector(0, 0, 0), new Vector(0, 0, -0.2));
        this.backplate.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.backplate.setBlockMaterial(Material.DEEPSLATE_BRICKS);

        this.sideGuard = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.palm, new Vector(0.12, 0.65, 0.45),
                new Vector(0, 0, 0), new Vector(-0.35, 0, 0));
        this.sideGuard.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.sideGuard.setBlockMaterial(Material.POLISHED_DEEPSLATE);

        this.formTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(15, 0, 0),
                new Vector(0, 0, 0));

        player.getWorld().playSound(handLoc, Sound.BLOCK_STONE_BREAK, 1.5f, 0.6f);
        player.getWorld().playSound(handLoc, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.2f, 0.8f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        gauntletYaw = -player.getLocation().getYaw();
        gauntletPitch = player.getLocation().getPitch();

        Location handLoc = GeneralMethods.getMainHandLocation(player);

        if (state == GauntletState.FORMING) {
            formTransition.update();
            double s = formTransition.getX();
            palm.setLocation(handLoc);
            palm.setRotation(new Vector(Math.toRadians(gauntletPitch * s), Math.toRadians(gauntletYaw), Math.toRadians(gauntletRoll)));
            palm.updateAndDisplay();
            knuckle1.updateAndDisplay();
            knuckle2.updateAndDisplay();
            knuckle3.updateAndDisplay();
            knuckle4.updateAndDisplay();
            backplate.updateAndDisplay();
            sideGuard.updateAndDisplay();

            if (formTransition.getX() >= 0.99) {
                state = GauntletState.WORN;
                wearStartTime = System.currentTimeMillis();
            }
        } else if (state == GauntletState.WORN) {
            if (System.currentTimeMillis() > wearStartTime + wearDuration) {
                remove();
                return;
            }
            palm.setLocation(handLoc);
            palm.setRotation(new Vector(Math.toRadians(gauntletPitch), Math.toRadians(gauntletYaw), Math.toRadians(gauntletRoll)));
            palm.updateAndDisplay();
            knuckle1.updateAndDisplay();
            knuckle2.updateAndDisplay();
            knuckle3.updateAndDisplay();
            knuckle4.updateAndDisplay();
            backplate.updateAndDisplay();
            sideGuard.updateAndDisplay();

            if (Math.random() < 0.05) {
                player.getWorld().spawnParticle(Particle.BLOCK, handLoc, 2, 0.1, 0.1, 0.1, 0, Material.STONE.createBlockData());
            }
        } else if (state == GauntletState.PUNCHING) {
            punchTransition.update();
            double extend = punchTransition.getX();

            Vector dir = player.getLocation().getDirection();
            Location extendedLoc = handLoc.clone().add(dir.clone().multiply(extend * 1.2));

            palm.setLocation(extendedLoc);
            palm.setRotation(new Vector(Math.toRadians(gauntletPitch), Math.toRadians(gauntletYaw), Math.toRadians(gauntletRoll)));
            palm.updateAndDisplay();
            knuckle1.updateAndDisplay();
            knuckle2.updateAndDisplay();
            knuckle3.updateAndDisplay();
            knuckle4.updateAndDisplay();
            backplate.updateAndDisplay();
            sideGuard.updateAndDisplay();

            checkPunchHit(extendedLoc);

            if (punchTransition.getX() >= 0.99) {
                if (punchImpactLoc != null) {
                    beginShockwave(punchImpactLoc);
                } else {
                    state = GauntletState.WORN;
                    punchHit.clear();
                }
            }
        } else if (state == GauntletState.SHOCKWAVE) {
            shockwaveProgress += 0.12;
            spawnShockwaveRing(punchImpactLoc, shockwaveProgress);
            checkShockwaveHits(punchImpactLoc, shockwaveProgress);

            palm.setLocation(punchImpactLoc.clone().add(
                    player.getLocation().getDirection().clone().multiply(-0.3)));
            palm.updateAndDisplay();
            knuckle1.updateAndDisplay();
            knuckle2.updateAndDisplay();
            knuckle3.updateAndDisplay();
            knuckle4.updateAndDisplay();
            backplate.updateAndDisplay();
            sideGuard.updateAndDisplay();

            if (shockwaveProgress >= shockwaveRadius) {
                state = GauntletState.WORN;
                punchHit.clear();
            }
        }
    }

    public void onLeftClick() {
        if (state == GauntletState.WORN) {
            state = GauntletState.PUNCHING;
            punchImpactLoc = null;
            punchHit.clear();
            punchTransition = new Transition(
                    new Vector(0, 0, 0),
                    new Vector(1, 0, 0),
                    new Vector(8, 0, 0),
                    new Vector(0, 0, 0));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.8f);
        }
    }

    private void checkPunchHit(Location extendedLoc) {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(extendedLoc, 0.9)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                if (!punchHit.contains(e)) {
                    punchHit.add(e);
                    DamageHandler.damageEntity((LivingEntity) e, punchDamage, this);
                    Vector kb = player.getLocation().getDirection().clone().multiply(1.8);
                    kb.setY(0.4);
                    e.setVelocity(e.getVelocity().add(kb));
                    punchImpactLoc = e.getLocation().clone();
                    player.getWorld().spawnParticle(Particle.BLOCK, extendedLoc, 20, 0.2, 0.2, 0.2, 0, Material.STONE_BRICKS.createBlockData());
                    player.getWorld().playSound(extendedLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 2.0f, 0.7f);
                }
            }
        }
        if (extendedLoc.getBlock().isSolid() && punchImpactLoc == null) {
            punchImpactLoc = extendedLoc.clone();
            player.getWorld().spawnParticle(Particle.BLOCK, extendedLoc, 25, 0.2, 0.2, 0.2, 0, Material.STONE_BRICKS.createBlockData());
            player.getWorld().playSound(extendedLoc, Sound.BLOCK_STONE_BREAK, 2.0f, 0.6f);
        }
    }

    private void beginShockwave(Location center) {
        state = GauntletState.SHOCKWAVE;
        shockwaveProgress = 0;
        player.getWorld().playSound(center, Sound.ENTITY_IRON_GOLEM_HURT, 1.5f, 0.6f);
    }

    private void spawnShockwaveRing(Location center, double radius) {
        int points = (int) (radius * 10);
        for (int i = 0; i < points; i++) {
            double a = (2 * Math.PI / points) * i;
            Location p = center.clone().add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius);
            player.getWorld().spawnParticle(Particle.BLOCK, p, 1, 0, 0, 0, 0, Material.STONE.createBlockData());
            if (Math.random() < 0.15) {
                player.getWorld().spawnParticle(Particle.BLOCK, p, 2, 0.1, 0.3, 0.1, 0, Material.COBBLESTONE.createBlockData());
            }
        }
    }

    private void checkShockwaveHits(Location center, double radius) {
        if (Math.abs(shockwaveProgress - radius) > 0.25) return;
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center, radius + 0.5)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                if (!punchHit.contains(e)) {
                    double dist = e.getLocation().distance(center);
                    if (Math.abs(dist - radius) < 0.8) {
                        punchHit.add(e);
                        DamageHandler.damageEntity((LivingEntity) e, shockwaveDamage, this);
                        Vector kb = e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.7);
                        kb.setY(0.3);
                        e.setVelocity(e.getVelocity().add(kb));
                    }
                }
            }
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
        return player.getLocation();
    }
    @Override
    public String getName() {
        return "StoneGauntlet";
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
        return "Encase your fist in a stone gauntlet. Punch to deal massive knockback and trigger a ground shockwave.";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Equip gauntlet\nLeft Click again: Punch\nPunch a target or wall to emit a shockwave.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (palm != null) palm.destroy();
        if (knuckle1 != null) knuckle1.destroy();
        if (knuckle2 != null) knuckle2.destroy();
        if (knuckle3 != null) knuckle3.destroy();
        if (knuckle4 != null) knuckle4.destroy();
        if (backplate != null) backplate.destroy();
        if (sideGuard != null) sideGuard.destroy();
    }

    @Override
    public void load() {
        abilityListener = new StoneGauntletListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.Cooldown", 9000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.PunchDamage", 6.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.ShockwaveDamage", 3.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.ShockwaveRadius", 4.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.MaxLifetime", 20000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.StoneGauntlet.WearDuration", 12000L);
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
