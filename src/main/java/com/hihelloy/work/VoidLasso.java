package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ChiAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.MovementHandler;
import com.hihelloy.work.lib.verlet.VerletHandler;
import com.hihelloy.work.lib.verlet.VerletRope;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

public class VoidLasso extends ChiAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double throwSpeed;
    private double lassoRange;
    private long pinDuration;
    private long maxLifetime;

    private enum LassoState {
        THROWING,
        WRAPPING,
        PINNED,
        RETRACTING,
        MISSED
    }
    private LassoState state;

    private VerletHandler loopVh;
    private VerletRope loopRope;

    private Location loopTip;
    private Vector throwVelocity;
    private LivingEntity pinnedTarget;
    private long pinStartTime;

    private double loopAngle;
    private double loopRadius;
    private com.hihelloy.work.lib.Transition wrapTransition;

    public VoidLasso(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Chi.VoidLasso.Cooldown", 9000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Chi.VoidLasso.Damage", 1.5);
        this.throwSpeed = config.getDouble("ExtraAbilities.Hihelloy.Chi.VoidLasso.ThrowSpeed", 2.0);
        this.lassoRange = config.getDouble("ExtraAbilities.Hihelloy.Chi.VoidLasso.Range", 20.0);
        this.pinDuration = config.getLong("ExtraAbilities.Hihelloy.Chi.VoidLasso.PinDuration", 3500);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Chi.VoidLasso.MaxLifetime", 8000);

        this.state = LassoState.THROWING;
        this.loopTip = GeneralMethods.getMainHandLocation(player).clone();
        this.throwVelocity = player.getLocation().getDirection().clone().multiply(throwSpeed);
        this.loopAngle = 0;
        this.loopRadius = 0.4;

        this.loopVh = new VerletHandler(player);
        int nodes = (int) (lassoRange * 4.5);
        Vector maxScale = new Vector(0.07, lassoRange / nodes, 0.07);
        Vector minScale = new Vector(0.07, lassoRange / nodes, 0.07);
        this.loopRope = new VerletRope(this.loopVh, player, this.loopTip, lassoRange, nodes, 1,
                Color.fromRGB(220, 220, 240), 1.0f, true, true, maxScale, minScale, Material.POLISHED_DEEPSLATE);
        this.loopRope.setRopeLength(0.5, false);
        this.loopVh.setGravity(new Vector(0, -0.04, 0));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 1.6f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        loopAngle += 18;

        if (state == LassoState.THROWING) {
            throwVelocity.add(new Vector(0, -0.03, 0));
            if (throwVelocity.length() > throwSpeed) throwVelocity.normalize().multiply(throwSpeed);
            loopTip.add(throwVelocity);

            double ropeLen = Math.min(loopTip.distance(player.getLocation()) + 0.5, lassoRange);
            loopRope.setRopeLength(ropeLen, false);
            loopRope.moveStartPoint(GeneralMethods.getMainHandLocation(player));
            loopRope.moveEndPoint(loopTip);
            loopVh.update();
            loopVh.display();

            spawnLoopParticles(loopTip, loopRadius);

            if (loopTip.distanceSquared(player.getLocation()) > lassoRange * lassoRange) {
                state = LassoState.MISSED;
                return;
            }
            if (loopTip.getBlock().isSolid()) {
                state = LassoState.MISSED;
                return;
            }

            checkLassoHit();

        } else if (state == LassoState.WRAPPING) {
            if (pinnedTarget == null || pinnedTarget.isDead()) { remove(); return; }
            wrapTransition.update();
            loopRadius = 0.4 + wrapTransition.getX() * 0.4;

            loopTip = pinnedTarget.getLocation().add(0, 1.0, 0);
            loopRope.moveStartPoint(GeneralMethods.getMainHandLocation(player));
            loopRope.moveEndPoint(loopTip);
            loopVh.update();
            loopVh.display();

            spawnLoopParticles(loopTip, loopRadius);
            spawnWrapRing(loopTip);

            if (wrapTransition.getX() >= 0.99) {
                beginPin();
            }

        } else if (state == LassoState.PINNED) {
            if (pinnedTarget == null || pinnedTarget.isDead()) { remove(); return; }
            if (System.currentTimeMillis() > pinStartTime + pinDuration) {
                state = LassoState.RETRACTING;
                return;
            }

            loopTip = pinnedTarget.getLocation().add(0, 1.0, 0);
            loopRope.moveStartPoint(GeneralMethods.getMainHandLocation(player));
            loopRope.moveEndPoint(loopTip);
            loopVh.update();
            loopVh.display();

            spawnLoopParticles(loopTip, loopRadius);
            spawnWrapRing(loopTip);

            double remaining = (pinDuration - (System.currentTimeMillis() - pinStartTime)) / (double) pinDuration;
            if (remaining < 0.3 && Math.random() < 0.3) {
                player.getWorld().spawnParticle(Particle.CRIT, loopTip, 2, 0.2, 0.2, 0.2, 0.02);
            }

        } else if (state == LassoState.RETRACTING || state == LassoState.MISSED) {
            loopRope.moveStartPoint(GeneralMethods.getMainHandLocation(player));
            loopRope.furl(0.7, 0.4, false);
            loopVh.update();
            loopVh.display();
            if (loopRope.getRopeLength() <= 0.4) {
                remove();
            }
        }
    }

    private void checkLassoHit() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(loopTip, 0.85)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                pinnedTarget = (LivingEntity) e;
                state = LassoState.WRAPPING;
                DamageHandler.damageEntity(pinnedTarget, damage, this);
                wrapTransition = new com.hihelloy.work.lib.Transition(
                        new Vector(0, 0, 0),
                        new Vector(1, 0, 0),
                        new Vector(10, 0, 0),
                        new Vector(0, 0, 0));
                player.getWorld().playSound(loopTip, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.9f);
                player.getWorld().spawnParticle(Particle.CRIT, loopTip, 15, 0.2, 0.2, 0.2, 0.05);
                return;
            }
        }
    }

    private void beginPin() {
        state = LassoState.PINNED;
        pinStartTime = System.currentTimeMillis();
        new MovementHandler(pinnedTarget, this).stopWithDuration(pinDuration, getElement().getColor() + "*VoidLasso*");
        player.getWorld().playSound(loopTip, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1.0f, 0.5f);
    }

    private void spawnLoopParticles(Location center, double radius) {
        int points = 8;
        for (int i = 0; i < points; i++) {
            double a = Math.toRadians(loopAngle + (360.0 / points) * i);
            Location p = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0,
                    0, new Particle.DustOptions(Color.fromRGB(200, 200, 255), 0.5f));
        }
    }

    private void spawnWrapRing(Location center) {
        int points = 16;
        for (int i = 0; i < points; i++) {
            double a = Math.toRadians(loopAngle * 0.5 + (360.0 / points) * i);
            double r = loopRadius * 1.2;
            Location p = center.clone().add(
                    Math.cos(a) * r,
                    Math.sin(Math.toRadians(loopAngle + i * 22.5)) * 0.3,
                    Math.sin(a) * r);
            player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0,
                    0, new Particle.DustOptions(Color.fromRGB(230, 230, 255), 0.4f));
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
        return loopTip;
    }

    @Override
    public String getName() {
        return "VoidLasso";
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
        return "Hurl a chi lasso that snares a target in a glowing loop, pinning them in place.";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Throw lasso.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (loopRope != null) loopRope.destroy();
        if (pinnedTarget != null) { MovementHandler handler = MovementHandler.getFromEntityAndAbility(pinnedTarget, this); if (handler != null) handler.reset(); }
    }

    @Override
    public void load() {
        abilityListener = new VoidLassoListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.VoidLasso.Cooldown", 9000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.VoidLasso.Damage", 1.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.VoidLasso.ThrowSpeed", 2.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.VoidLasso.Range", 20.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.VoidLasso.PinDuration", 3500L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Chi.VoidLasso.MaxLifetime", 8000L);
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