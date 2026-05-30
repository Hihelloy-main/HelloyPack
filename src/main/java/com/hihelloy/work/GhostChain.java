package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ChiAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.MovementHandler;
import com.hihelloy.work.lib.GameObject;
import com.hihelloy.work.lib.Transition;
import com.hihelloy.work.lib.verlet.VerletHandler;
import com.hihelloy.work.lib.verlet.VerletRope;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

public class GhostChain extends ChiAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double throwRange;
    private double pullSpeed;
    private long bindDuration;
    private long maxLifetime;

    private enum ChainState {
        THROWING,
        BOUND,
        PULLING,
        MISSED
    }
    private ChainState state;

    private VerletHandler vh;
    private VerletRope rope;

    private Location hookLocation;
    private Vector throwVelocity;
    private LivingEntity boundTarget;
    private long bindStartTime;

    private GameObject hookObject;
    private Transition hookSpinTransition;
    private double hookYaw;

    public GhostChain(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Chi.GhostChain.Cooldown", 8000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Chi.GhostChain.Damage", 2.0);
        this.throwRange = config.getDouble("ExtraAbilities.Hihelloy.Chi.GhostChain.ThrowRange", 18.0);
        this.pullSpeed = config.getDouble("ExtraAbilities.Hihelloy.Chi.GhostChain.PullSpeed", 0.5);
        this.bindDuration = config.getLong("ExtraAbilities.Hihelloy.Chi.GhostChain.BindDuration", 3000);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Chi.GhostChain.MaxLifetime", 6000);

        this.state = ChainState.THROWING;
        this.hookLocation = GeneralMethods.getMainHandLocation(player).clone();
        this.hookYaw = 0;

        this.throwVelocity = player.getLocation().getDirection().clone().multiply(1.8);

        this.vh = new VerletHandler(player);
        double maxLen = throwRange;
        int nodeCount = (int) (maxLen * 4);
        Vector maxScale = new Vector(0.07, maxLen / nodeCount, 0.07);
        Vector minScale = new Vector(0.07, maxLen / nodeCount, 0.07);
        this.rope = new VerletRope(this.vh, player, this.hookLocation, maxLen, nodeCount, 1,
                Color.fromRGB(180, 180, 200), 1.0f, true, true, maxScale, minScale, Material.IRON_BLOCK);
        this.rope.setRopeLength(0.5, false);
        this.vh.setGravity(new Vector(0, -0.05, 0));

        this.hookObject = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.hookLocation, new Vector(0.2, 0.2, 0.2),
                new Vector(0, 0, 0), new Vector());
        this.hookObject.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.hookObject.setBlockMaterial(Material.IRON_BLOCK);

        this.hookSpinTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(720, 0, 0),
                new Vector(30, 0, 0),
                new Vector(0, 0, 0));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.5f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.2f, 0.9f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        hookSpinTransition.update();
        hookYaw += 15;

        if (state == ChainState.THROWING) {
            throwVelocity.add(new Vector(0, -0.06, 0));
            if (throwVelocity.length() > 1.8) throwVelocity.normalize().multiply(1.8);
            hookLocation.add(throwVelocity);

            if (hookLocation.distanceSquared(player.getLocation()) > throwRange * throwRange) {
                state = ChainState.MISSED;
            }
            if (hookLocation.getBlock().isSolid()) {
                state = ChainState.MISSED;
            }

            double ropeTarget = hookLocation.distance(player.getLocation());
            rope.setRopeLength(Math.min(ropeTarget + 0.5, throwRange), false);
            rope.moveStartPoint(GeneralMethods.getMainHandLocation(player));
            rope.moveEndPoint(hookLocation);
            vh.update();
            vh.display();

            checkHookHit();

        } else if (state == ChainState.BOUND) {
            if (boundTarget == null || boundTarget.isDead()) {
                remove();
                return;
            }
            if (System.currentTimeMillis() > bindStartTime + bindDuration) {
                state = ChainState.PULLING;
                return;
            }

            hookLocation = boundTarget.getLocation().add(0, 1.0, 0);
            rope.moveStartPoint(GeneralMethods.getMainHandLocation(player));
            rope.moveEndPoint(hookLocation);

            if (rope.getStartLocation().distanceSquared(rope.getEndLocation()) > throwRange * throwRange * 0.25) {
                rope.furl(0.3, 2.0, false);
            }

            vh.update();
            vh.display();

            if (player.getLocation().distanceSquared(boundTarget.getLocation()) < 4) {
                remove();
                return;
            }

        } else if (state == ChainState.PULLING) {
            if (boundTarget == null || boundTarget.isDead()) {
                remove();
                return;
            }

            hookLocation = boundTarget.getLocation().add(0, 1.0, 0);
            rope.moveStartPoint(GeneralMethods.getMainHandLocation(player));
            rope.moveEndPoint(hookLocation);
            rope.furl(0.4, 1.5, false);

            Vector pullDir = player.getEyeLocation().toVector()
                    .subtract(boundTarget.getLocation().toVector()).normalize().multiply(pullSpeed);
            Vector vel = boundTarget.getVelocity().add(pullDir);
            if (vel.lengthSquared() > pullSpeed * pullSpeed * 4) vel.normalize().multiply(pullSpeed * 2);
            boundTarget.setVelocity(vel);

            vh.update();
            vh.display();

            if (player.getLocation().distanceSquared(boundTarget.getLocation()) < 4) {
                DamageHandler.damageEntity(boundTarget, damage * 1.5, this);
                remove();
                return;
            }

        } else if (state == ChainState.MISSED) {
            rope.moveStartPoint(GeneralMethods.getMainHandLocation(player));
            rope.furl(0.5, 0.5, false);
            vh.update();
            vh.display();
            if (rope.getRopeLength() <= 0.5) {
                remove();
                return;
            }
        }

        this.hookObject.setLocation(hookLocation);
        this.hookObject.setRotation(new Vector(0, Math.toRadians(hookYaw), Math.toRadians(hookSpinTransition.getX())));
        this.hookObject.updateAndDisplay();
    }

    public void onLeftClick() {
        if (state == ChainState.BOUND) {
            state = ChainState.PULLING;
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.5f, 1.4f);
        }
    }

    private void checkHookHit() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(hookLocation, 0.8)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                boundTarget = (LivingEntity) e;
                state = ChainState.BOUND;
                bindStartTime = System.currentTimeMillis();
                DamageHandler.damageEntity(boundTarget, damage, this);
                new MovementHandler(boundTarget, this).stopWithDuration(bindDuration, "GhostChain");
                player.getWorld().playSound(hookLocation, Sound.ENTITY_PLAYER_ATTACK_CRIT, 2.0f, 0.7f);
                player.getWorld().playSound(hookLocation, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.5f, 0.6f);
                player.getWorld().spawnParticle(Particle.CRIT, hookLocation, 10, 0.2, 0.2, 0.2, 0.1);
                return;
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
        return hookLocation;
    }

    @Override
    public String getName() {
        return "GhostChain";
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
        return "Throw a chi-infused chain hook that binds and drags your target toward you!";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Throw chain\nLeft Click again while bound: Pull target toward you immediately.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (rope != null) rope.destroy();
        if (hookObject != null) hookObject.destroy();
        if (boundTarget != null) { MovementHandler handler = MovementHandler.getFromEntityAndAbility(boundTarget, this); if (handler != null) handler.reset(); }
    }

    @Override
    public void load() {
        abilityListener = new GhostChainListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.GhostChain.Cooldown", 8000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.GhostChain.Damage", 2.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.GhostChain.ThrowRange", 18.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.GhostChain.PullSpeed", 0.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.GhostChain.BindDuration", 3000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.GhostChain.MaxLifetime", 6000L);
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
