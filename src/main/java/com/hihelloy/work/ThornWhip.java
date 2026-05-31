package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.PlantAbility;
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

public class ThornWhip extends PlantAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double whipRange;
    private long entangleDuration;
    private long maxLifetime;

    private enum WhipState {
        LASHING,
        ENTANGLED,
        RETRACTING,
        MISSED
    }
    private WhipState state;

    private VerletHandler vh;
    private VerletRope rope;
    private Location tipLoc;
    private Vector tipVelocity;
    private LivingEntity entangledTarget;
    private long entangleStartTime;

    public ThornWhip(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Plant.ThornWhip.Cooldown", 5000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Plant.ThornWhip.Damage", 3.0);
        this.whipRange = config.getDouble("ExtraAbilities.Hihelloy.Plant.ThornWhip.Range", 14.0);
        this.entangleDuration = config.getLong("ExtraAbilities.Hihelloy.Plant.ThornWhip.EntangleDuration", 2500);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Plant.ThornWhip.MaxLifetime", 6000);

        this.state = WhipState.LASHING;
        this.tipLoc = GeneralMethods.getMainHandLocation(player).clone();
        this.tipVelocity = player.getLocation().getDirection().clone().multiply(1.9);

        this.vh = new VerletHandler(player);
        int nodes = (int) (whipRange * 4);
        Vector maxScale = new Vector(0.09, whipRange / nodes, 0.09);
        Vector minScale = new Vector(0.04, whipRange / nodes, 0.04);
        this.rope = new VerletRope(vh, player, this.tipLoc, whipRange, nodes, 1,
                Color.fromRGB(40, 130, 30), 1.0f, true, true, maxScale, minScale, Material.OAK_LOG);
        this.rope.setRopeLength(0.5, false);
        this.vh.setGravity(new Vector(0, -0.05, 0));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRASS_BREAK, 1.0f, 0.8f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        if (state == WhipState.LASHING) {
            tipVelocity.add(new Vector(0, -0.06, 0));
            if (tipVelocity.length() > 1.9) tipVelocity.normalize().multiply(1.9);
            tipLoc.add(tipVelocity);

            double len = Math.min(tipLoc.distance(player.getLocation()) + 0.5, whipRange);
            rope.setRopeLength(len, false);
            rope.moveStartPoint(GeneralMethods.getMainHandLocation(player));
            rope.moveEndPoint(tipLoc);
            vh.update();
            vh.display();

            spawnTipParticles();

            if (tipLoc.distanceSquared(player.getLocation()) > whipRange * whipRange || tipLoc.getBlock().isSolid()) {
                state = WhipState.MISSED;
                return;
            }

            for (Entity e : GeneralMethods.getEntitiesAroundPoint(tipLoc, 0.85)) {
                if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                    entangledTarget = (LivingEntity) e;
                    DamageHandler.damageEntity(entangledTarget, damage, this);
                    new MovementHandler(entangledTarget, this).stopWithDuration(entangleDuration, getElement().getColor() + "*ThornWhip*");
                    state = WhipState.ENTANGLED;
                    entangleStartTime = System.currentTimeMillis();
                    player.getWorld().playSound(tipLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.7f);
                    player.getWorld().playSound(tipLoc, Sound.BLOCK_GRASS_BREAK, 1.5f, 1.2f);
                    player.getWorld().spawnParticle(Particle.BLOCK, tipLoc, 15, 0.2, 0.2, 0.2,
                            0, Material.OAK_LEAVES.createBlockData());
                    return;
                }
            }

        } else if (state == WhipState.ENTANGLED) {
            if (entangledTarget == null || entangledTarget.isDead()) {
                state = WhipState.RETRACTING;
                return;
            }
            if (System.currentTimeMillis() > entangleStartTime + entangleDuration) {
                state = WhipState.RETRACTING;
                return;
            }

            tipLoc = entangledTarget.getLocation().add(0, 1.0, 0);
            rope.moveStartPoint(GeneralMethods.getMainHandLocation(player));
            rope.moveEndPoint(tipLoc);
            vh.update();
            vh.display();

            spawnEntangleParticles(entangledTarget.getLocation());

        } else if (state == WhipState.RETRACTING || state == WhipState.MISSED) {
            if (entangledTarget != null) {
                MovementHandler handler = MovementHandler.getFromEntityAndAbility(entangledTarget, this);
                if (handler != null) handler.reset();
                entangledTarget = null;
            }
            rope.moveStartPoint(GeneralMethods.getMainHandLocation(player));
            rope.furl(0.6, 0.4, false);
            vh.update();
            vh.display();
            if (rope.getRopeLength() <= 0.4) {
                remove();
            }
        }
    }

    private void spawnTipParticles() {
        player.getWorld().spawnParticle(Particle.BLOCK, tipLoc, 1, 0.05, 0.05, 0.05,
                0, Material.OAK_LEAVES.createBlockData());
    }

    private void spawnEntangleParticles(Location loc) {
        if (Math.random() < 0.4) {
            player.getWorld().spawnParticle(Particle.BLOCK,
                    loc.clone().add(
                            (Math.random() - 0.5) * 0.6,
                            Math.random() * 1.8,
                            (Math.random() - 0.5) * 0.6),
                    1, 0, 0, 0, 0, Material.OAK_LEAVES.createBlockData());
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
        return tipLoc;
    }

    @Override
    public String getName() {
        return "ThornWhip";
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
        return "Lash out a vine whip that snares and entangles enemies, rooting them briefly.";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Crack the vine whip at your target.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (entangledTarget != null) { MovementHandler handler = MovementHandler.getFromEntityAndAbility(entangledTarget, this); if (handler != null) handler.reset(); }
        if (rope != null) rope.destroy();
    }

    @Override
    public void load() {
        abilityListener = new ThornWhipListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Plant.ThornWhip.Cooldown", 5000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Plant.ThornWhip.Damage", 3.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Plant.ThornWhip.Range", 14.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Plant.ThornWhip.EntangleDuration", 2500L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Plant.ThornWhip.MaxLifetime", 6000L);
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