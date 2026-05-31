package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.AvatarAbility;
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

public class VoidPortal extends AvatarAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double throwDamage;
    private double throwRange;
    private double throwSpeed;
    private double shieldContactDamage;
    private double flySpeed;
    private long maxLifetime;

    private enum PortalMode { HOLDING, THROWN, RETURNING, SHIELD, FLYING }
    private PortalMode mode;

    private GameObject diskOuter;
    private GameObject diskInner;
    private GameObject diskCore;
    private GameObject rimA;
    private GameObject rimB;

    private double spinAngle;
    private double tiltAngle;
    private double breathe;

    private Location diskLoc;
    private Location throwStart;
    private Vector throwVelocity;
    private boolean pastPeak;
    private Set<Entity> throwHit = new HashSet<>();

    private Set<Entity> shieldHit = new HashSet<>();
    private long lastShieldHitClear;

    private long flyStart;
    private static final long FLY_MAX = 5000;

    private Transition formTransition;

    public VoidPortal(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Avatar.VoidPortal.Cooldown", 12000);
        this.throwDamage = config.getDouble("ExtraAbilities.Hihelloy.Avatar.VoidPortal.ThrowDamage", 6.0);
        this.throwRange = config.getDouble("ExtraAbilities.Hihelloy.Avatar.VoidPortal.ThrowRange", 24.0);
        this.throwSpeed = config.getDouble("ExtraAbilities.Hihelloy.Avatar.VoidPortal.ThrowSpeed", 1.5);
        this.shieldContactDamage = config.getDouble("ExtraAbilities.Hihelloy.Avatar.VoidPortal.ShieldDamage", 3.0);
        this.flySpeed = config.getDouble("ExtraAbilities.Hihelloy.Avatar.VoidPortal.FlySpeed", 0.55);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Avatar.VoidPortal.MaxLifetime", 25000);

        this.mode = PortalMode.HOLDING;
        this.spinAngle = 0;
        this.tiltAngle = 0;
        this.breathe = 0;
        this.lastShieldHitClear = System.currentTimeMillis();

        this.formTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(12, 0, 0),
                new Vector(0, 0, 0));

        Location hand = GeneralMethods.getMainHandLocation(player);

        this.diskOuter = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                hand, new Vector(0.9, 0.07, 0.9), new Vector(0, 0, 0), new Vector());
        this.diskOuter.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.diskOuter.setBlockMaterial(Material.END_PORTAL);

        this.diskInner = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                hand, new Vector(0.6, 0.09, 0.6), new Vector(0, 0, 0), new Vector());
        this.diskInner.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.diskInner.setBlockMaterial(Material.END_PORTAL);

        this.diskCore = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                hand, new Vector(0.25, 0.12, 0.25), new Vector(0, 0, 0), new Vector());
        this.diskCore.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.diskCore.setBlockMaterial(Material.CRYING_OBSIDIAN);

        this.rimA = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                hand, new Vector(1.0, 0.05, 0.12), new Vector(0, 0, 0), new Vector());
        this.rimA.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.rimA.setBlockMaterial(Material.OBSIDIAN);

        this.rimB = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                hand, new Vector(0.12, 0.05, 1.0), new Vector(0, 0, 0), new Vector());
        this.rimB.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.rimB.setBlockMaterial(Material.OBSIDIAN);

        player.getWorld().playSound(hand, Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.5f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) { remove(); return; }

        spinAngle += getModeSpinSpeed();
        breathe += 3.5;

        formTransition.update();
        double form = formTransition.getX();

        if (mode == PortalMode.HOLDING) {
            Location hand = GeneralMethods.getMainHandLocation(player);
            this.diskLoc = hand;
            double playerYaw = Math.toRadians(-player.getLocation().getYaw());
            double playerPitch = Math.toRadians(player.getLocation().getPitch() * 0.5);
            updateDisk(hand, form, playerYaw, playerPitch);
            spawnHoldParticles(hand);

        } else if (mode == PortalMode.THROWN || mode == PortalMode.RETURNING) {
            if (mode == PortalMode.THROWN) {
                throwVelocity.add(new Vector(0, -0.018, 0));
                diskLoc.add(throwVelocity);

                if (diskLoc.distanceSquared(throwStart) > throwRange * throwRange) {
                    beginReturn();
                }
                if (diskLoc.getBlock().isSolid()) {
                    beginReturn();
                }
                checkThrowHit();
            } else {
                Vector toPlayer = player.getEyeLocation().toVector().subtract(diskLoc.toVector());
                double dist = toPlayer.length();
                if (dist < 1.5) {
                    throwHit.clear();
                    mode = PortalMode.HOLDING;
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.4f, 1.8f);
                    return;
                }
                Vector ret = toPlayer.normalize().multiply(Math.min(throwSpeed * 1.4, dist));
                diskLoc.add(ret);
                checkThrowHit();
            }

            updateDisk(diskLoc, 1.0, Math.toRadians(spinAngle), Math.toRadians(tiltAngle));
            spawnThrowParticles(diskLoc);

        } else if (mode == PortalMode.SHIELD) {
            Location hand = GeneralMethods.getMainHandLocation(player);
            this.diskLoc = hand;
            double yaw = Math.toRadians(-player.getLocation().getYaw());
            double pitch = Math.toRadians(-90);
            updateDisk(hand, 1.0, yaw, pitch);
            checkShieldHits(hand);
            spawnShieldParticles(hand);

            if (!player.isSneaking()) {
                mode = PortalMode.HOLDING;
            }

        } else if (mode == PortalMode.FLYING) {
            long flyElapsed = System.currentTimeMillis() - flyStart;
            if (flyElapsed > FLY_MAX || !player.isSneaking()) {
                player.setAllowFlight(false);
                player.setFlying(false);
                mode = PortalMode.HOLDING;
                return;
            }

            player.setAllowFlight(true);
            player.setFlying(true);

            Vector dir = player.getLocation().getDirection().clone();
            dir.setY(Math.max(dir.getY(), -0.3));
            dir.normalize().multiply(flySpeed);
            player.setVelocity(dir);

            Location below = player.getLocation().subtract(0, 1.0, 0);
            this.diskLoc = below;
            double yaw = Math.toRadians(-player.getLocation().getYaw());
            updateDisk(below, 1.0, yaw, 0);
            spawnFlyParticles(below);
        }
    }

    public boolean isShielding() {
        return mode == PortalMode.SHIELD;
    }

    public void onLeftClick() {
        if (mode == PortalMode.HOLDING) {
            beginThrow();
        } else if (mode == PortalMode.THROWN) {
            beginReturn();
        }
    }

    public void onSneak() {
        if (mode == PortalMode.HOLDING) {
            mode = PortalMode.SHIELD;
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 1.2f);
        }
    }

    public void onDoubleSneak() {
        if (mode == PortalMode.HOLDING || mode == PortalMode.SHIELD) {
            mode = PortalMode.FLYING;
            flyStart = System.currentTimeMillis();
            player.setAllowFlight(true);
            player.setFlying(true);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.3f);
        }
    }

    private void beginThrow() {
        mode = PortalMode.THROWN;
        diskLoc = GeneralMethods.getMainHandLocation(player).clone();
        throwStart = diskLoc.clone();
        throwVelocity = player.getLocation().getDirection().clone().normalize().multiply(throwSpeed);
        pastPeak = false;
        throwHit.clear();
        tiltAngle = 45;
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.6f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.8f);
    }

    private void beginReturn() {
        mode = PortalMode.RETURNING;
        player.getWorld().playSound(diskLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
    }

    private void checkThrowHit() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(diskLoc, 0.95)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())
                    && !throwHit.contains(e)) {
                throwHit.add(e);
                DamageHandler.damageEntity((LivingEntity) e, throwDamage, this);
                Vector kb = throwVelocity.clone().normalize().multiply(1.0);
                kb.setY(0.4);
                e.setVelocity(e.getVelocity().add(kb));
                spawnHitEffect(diskLoc);
                if (mode == PortalMode.THROWN) beginReturn();
                return;
            }
        }
    }

    private void checkShieldHits(Location center) {
        long now = System.currentTimeMillis();
        if (now - lastShieldHitClear > 700) { shieldHit.clear(); lastShieldHitClear = now; }

        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center, 1.1)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())
                    && !shieldHit.contains(e)) {
                shieldHit.add(e);
                DamageHandler.damageEntity((LivingEntity) e, shieldContactDamage, this);
                Vector kb = e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.8);
                e.setVelocity(e.getVelocity().add(kb));
                spawnHitEffect(center);
            }
        }
    }

    private void updateDisk(Location loc, double form, double yaw, double pitch) {
        double s = form;
        double outerS = 0.9 * s;
        double innerS = 0.6 * s;
        double coreS = 0.25 * s;
        double bob = Math.sin(Math.toRadians(breathe)) * 0.03 * form;

        Vector rot = new Vector(pitch, yaw, Math.toRadians(spinAngle));

        diskOuter.setLocation(loc.clone().add(0, bob, 0));
        diskOuter.setScale(new Vector(outerS, 0.07, outerS));
        diskOuter.setRotation(rot);
        diskOuter.updateAndDisplay();

        diskInner.setLocation(loc.clone().add(0, bob, 0));
        diskInner.setScale(new Vector(innerS, 0.09, innerS));
        diskInner.setRotation(new Vector(pitch, yaw + Math.toRadians(spinAngle * 0.6), 0));
        diskInner.updateAndDisplay();

        diskCore.setLocation(loc.clone().add(0, bob, 0));
        diskCore.setScale(new Vector(coreS, 0.12, coreS));
        diskCore.setRotation(new Vector(Math.toRadians(spinAngle * 0.3), yaw, 0));
        diskCore.updateAndDisplay();

        rimA.setLocation(loc.clone().add(0, bob, 0));
        rimA.setScale(new Vector(1.0 * s, 0.05, 0.12 * s));
        rimA.setRotation(new Vector(pitch, yaw + Math.toRadians(spinAngle * 0.5), 0));
        rimA.updateAndDisplay();

        rimB.setLocation(loc.clone().add(0, bob, 0));
        rimB.setScale(new Vector(0.12 * s, 0.05, 1.0 * s));
        rimB.setRotation(new Vector(pitch, yaw + Math.toRadians(spinAngle * 0.5), 0));
        rimB.updateAndDisplay();
    }

    private double getModeSpinSpeed() {
        return switch (mode) {
            case THROWN, RETURNING -> 22;
            case SHIELD -> 14;
            case FLYING -> 10;
            default -> 6;
        };
    }

    private void spawnHoldParticles(Location loc) {
        if (Math.random() < 0.3)
            player.getWorld().spawnParticle(Particle.PORTAL, loc, 1, 0.3, 0.1, 0.3, 0.05);
        if (Math.random() < 0.1)
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 1, 0.2, 0.05, 0.2, 0.02);
    }

    private void spawnThrowParticles(Location loc) {
        player.getWorld().spawnParticle(Particle.PORTAL, loc, 3, 0.15, 0.15, 0.15, 0.08);
        if (Math.random() < 0.3)
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 1, 0.1, 0.1, 0.1, 0.03);
    }

    private void spawnShieldParticles(Location loc) {
        if (Math.random() < 0.5)
            player.getWorld().spawnParticle(Particle.PORTAL, loc, 2, 0.5, 0.1, 0.5, 0.04);
        if (Math.random() < 0.15)
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 1, 0.4, 0.05, 0.4, 0.02);
    }

    private void spawnFlyParticles(Location loc) {
        player.getWorld().spawnParticle(Particle.PORTAL, loc, 4, 0.4, 0.05, 0.4, 0.06);
        if (Math.random() < 0.2)
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 1, 0.3, 0.03, 0.3, 0.03);
    }

    private void spawnHitEffect(Location loc) {
        player.getWorld().spawnParticle(Particle.PORTAL, loc, 20, 0.3, 0.3, 0.3, 0.15);
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 8, 0.2, 0.2, 0.2, 0.08);
        player.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_HURT, 1.5f, 0.9f);
        player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
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
        return diskLoc != null ? diskLoc : player.getLocation();
    }
    @Override
    public String getName() {
        return "VoidPortal";
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
        return "Manifest a disk of end portal energy with three modes: throw it as a returning boomerang, hold it as a contact shield, or stand on it to fly.";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Throw disk (boomerang — left click again to recall early).\nSneak: Hold disk as shield in front of you.\nDouble-Sneak: Stand on disk and fly in look direction.\nRequires Avatar bending.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (diskOuter != null) diskOuter.destroy();
        if (diskInner != null) diskInner.destroy();
        if (diskCore != null) diskCore.destroy();
        if (rimA != null) rimA.destroy();
        if (rimB != null) rimB.destroy();
        if (player.isOnline() && player.getGameMode() != GameMode.CREATIVE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    @Override
    public void load() {
        abilityListener = new VoidPortalListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.VoidPortal.Cooldown", 12000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.VoidPortal.ThrowDamage", 6.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.VoidPortal.ThrowRange", 24.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.VoidPortal.ThrowSpeed", 1.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.VoidPortal.ShieldDamage", 3.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.VoidPortal.FlySpeed", 0.55);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.VoidPortal.MaxLifetime", 25000L);
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