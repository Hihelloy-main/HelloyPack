package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.SandAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
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

import java.util.ArrayList;
import java.util.List;

public class SandSerpent extends SandAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double launchSpeed;
    private double throwRange;
    private double pullSpeed;
    private long maxLifetime;

    private enum SerpentState { COILED, LAUNCHED, LATCHED, PULLING, RETRACTING }
    private SerpentState state;

    private VerletHandler vh;
    private VerletRope tether;

    private List<GameObject> bodySegments = new ArrayList<>();
    private GameObject headBlock;
    private GameObject jawBlock;

    private Location serpentLoc;
    private Vector serpentVelocity;
    private Location serpentStart;
    private double serpentYaw;
    private double serpentPitch;
    private double serpentSpin;

    private LivingEntity latchedTarget;
    private long latchTime;
    private Transition wrapTransition;

    private static final int SEGMENT_COUNT = 5;

    public SandSerpent(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Sand.SandSerpent.Cooldown", 7000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Sand.SandSerpent.Damage", 4.5);
        this.launchSpeed = config.getDouble("ExtraAbilities.Hihelloy.Sand.SandSerpent.LaunchSpeed", 1.5);
        this.throwRange = config.getDouble("ExtraAbilities.Hihelloy.Sand.SandSerpent.ThrowRange", 18.0);
        this.pullSpeed = config.getDouble("ExtraAbilities.Hihelloy.Sand.SandSerpent.PullSpeed", 0.6);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Sand.SandSerpent.MaxLifetime", 7000);

        this.state = SerpentState.COILED;
        this.serpentYaw = -player.getLocation().getYaw();
        this.serpentPitch = 0;
        this.serpentSpin = 0;

        Location hand = GeneralMethods.getMainHandLocation(player);

        this.vh = new VerletHandler(player);
        int nodes = 16;
        double tetherLen = 2.5;
        Vector maxScale = new Vector(0.08, tetherLen / nodes, 0.08);
        Vector minScale = new Vector(0.04, tetherLen / nodes, 0.04);
        this.tether = new VerletRope(vh, player, hand, tetherLen + 8, nodes, 1,
                Color.fromRGB(210, 185, 120), 1.0f, true, true, maxScale, minScale, Material.SANDSTONE);
        this.tether.setRopeLength(tetherLen, false);
        this.vh.setGravity(new Vector(0, -0.04, 0));

        this.serpentLoc = hand.clone();

        buildSerpent(hand);

        player.getWorld().playSound(hand, Sound.BLOCK_SAND_BREAK, 1.2f, 0.7f);
        player.getWorld().playSound(hand, Sound.BLOCK_SAND_STEP, 1.0f, 1.4f);
    }

    private void buildSerpent(Location loc) {
        Material[] mats = {
                Material.CHISELED_SANDSTONE,
                Material.SANDSTONE,
                Material.SMOOTH_SANDSTONE,
                Material.SANDSTONE,
                Material.SANDSTONE
        };
        double[] widths  = { 0.28, 0.36, 0.42, 0.38, 0.30 };
        double[] heights = { 0.28, 0.30, 0.32, 0.28, 0.22 };

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            GameObject seg = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    loc, new Vector(widths[i], heights[i], widths[i]),
                    new Vector(0, 0, 0), new Vector());
            seg.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
            seg.setBlockMaterial(mats[i]);
            bodySegments.add(seg);
        }

        this.headBlock = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                loc, new Vector(0.35, 0.22, 0.45),
                new Vector(0, 0, 0), new Vector());
        this.headBlock.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
        this.headBlock.setBlockMaterial(Material.CHISELED_SANDSTONE);

        this.jawBlock = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                loc, new Vector(0.28, 0.12, 0.36),
                new Vector(0, 0, 0), new Vector());
        this.jawBlock.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
        this.jawBlock.setBlockMaterial(Material.CUT_SANDSTONE);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) { remove(); return; }

        serpentSpin += 6;
        Location hand = GeneralMethods.getMainHandLocation(player);

        if (state == SerpentState.COILED) {
            serpentYaw = -player.getLocation().getYaw();
            serpentPitch = player.getLocation().getPitch() * 0.4f;
            serpentLoc = hand.clone();

            tether.moveStartPoint(hand);
            tether.moveEndPoint(serpentLoc);
            vh.update();
            vh.display();

            displaySerpent(serpentLoc, Math.toRadians(serpentYaw), Math.toRadians(serpentPitch), 1.0);
            spawnCoiledParticles(hand);

        } else if (state == SerpentState.LAUNCHED) {
            serpentVelocity.add(new Vector(0, -0.035, 0));
            if (serpentVelocity.length() > launchSpeed) serpentVelocity.normalize().multiply(launchSpeed);
            serpentLoc.add(serpentVelocity);

            double yaw = Math.atan2(-serpentVelocity.getX(), serpentVelocity.getZ());
            double pitch = -Math.asin(Math.max(-1, Math.min(1, serpentVelocity.clone().normalize().getY())));
            serpentYaw = Math.toDegrees(yaw);
            serpentPitch = Math.toDegrees(pitch);

            double ropeLen = Math.min(serpentLoc.distance(hand) + 0.5, throwRange);
            tether.setRopeLength(ropeLen, false);
            tether.moveStartPoint(hand);
            tether.moveEndPoint(serpentLoc);
            vh.update();
            vh.display();

            displaySerpent(serpentLoc, yaw, pitch, 1.0);
            spawnLaunchParticles();

            if (serpentLoc.distanceSquared(serpentStart) > throwRange * throwRange
                    || serpentLoc.getBlock().isSolid()) {
                beginRetract();
                return;
            }

            checkLatch();

        } else if (state == SerpentState.LATCHED) {
            if (latchedTarget == null || latchedTarget.isDead()) { remove(); return; }

            wrapTransition.update();
            serpentLoc = latchedTarget.getLocation().add(0, 1.0, 0);

            tether.moveStartPoint(hand);
            tether.moveEndPoint(serpentLoc);
            tether.furl(0.4, 1.8, false);
            vh.update();
            vh.display();

            double scale = 1.0 + wrapTransition.getX() * 0.15;
            displaySerpent(serpentLoc, Math.toRadians(serpentSpin * 0.3), 0, scale);
            spawnLatchParticles(serpentLoc);

            if (wrapTransition.getX() >= 0.99) {
                state = SerpentState.PULLING;
                player.getWorld().playSound(serpentLoc, Sound.BLOCK_SAND_BREAK, 2.0f, 0.5f);
            }

        } else if (state == SerpentState.PULLING) {
            if (latchedTarget == null || latchedTarget.isDead()) { remove(); return; }

            serpentLoc = latchedTarget.getLocation().add(0, 1.0, 0);

            tether.moveStartPoint(hand);
            tether.moveEndPoint(serpentLoc);
            tether.furl(0.5, 1.0, false);
            vh.update();
            vh.display();

            displaySerpent(serpentLoc, Math.toRadians(serpentSpin * 0.4), 0, 1.0);

            Vector toPlayer = player.getEyeLocation().toVector()
                    .subtract(latchedTarget.getLocation().toVector());
            double dist = toPlayer.length();

            if (dist < 2.0) {
                DamageHandler.damageEntity(latchedTarget, damage, this);
                spawnShatterEffect(serpentLoc);
                remove();
                return;
            }

            latchedTarget.setVelocity(toPlayer.normalize().multiply(pullSpeed));

            spawnPullParticles(serpentLoc);

        } else if (state == SerpentState.RETRACTING) {
            tether.moveStartPoint(hand);
            tether.furl(0.5, 0.5, false);
            vh.update();
            vh.display();

            double shrink = Math.max(0, tether.getRopeLength() / throwRange);
            displaySerpent(serpentLoc, Math.toRadians(serpentYaw), Math.toRadians(serpentPitch), shrink);

            if (tether.getRopeLength() <= 0.5) { remove(); return; }
        }
    }

    public void onLeftClick() {
        if (state == SerpentState.COILED) {
            state = SerpentState.LAUNCHED;
            serpentStart = serpentLoc.clone();
            serpentVelocity = player.getLocation().getDirection().clone().multiply(launchSpeed);
            tether.setRopeLength(1.0, false);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SAND_BREAK, 1.5f, 1.4f);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.2f, 0.8f);
        }
    }

    public void onSneak() {
        if (state == SerpentState.LATCHED || state == SerpentState.LAUNCHED) {
            beginRetract();
        }
    }

    private void checkLatch() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(serpentLoc, 0.85)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                latchedTarget = (LivingEntity) e;
                state = SerpentState.LATCHED;
                latchTime = System.currentTimeMillis();
                wrapTransition = new Transition(
                        new Vector(0, 0, 0), new Vector(1, 0, 0),
                        new Vector(12, 0, 0), new Vector(0, 0, 0));
                player.getWorld().playSound(serpentLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.8f, 0.7f);
                player.getWorld().spawnParticle(Particle.BLOCK, serpentLoc,
                        12, 0.2, 0.2, 0.2, 0, Material.SANDSTONE.createBlockData());
                return;
            }
        }
    }

    private void beginRetract() {
        state = SerpentState.RETRACTING;
        if (latchedTarget != null) {
            latchedTarget = null;
        }
    }

    private void displaySerpent(Location center, double yaw, double pitch, double scale) {
        double[] segOffsets = { 0.32, 0.18, 0.0, -0.18, -0.36 };
        Vector forward = new Vector(-Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));

        for (int i = 0; i < SEGMENT_COUNT && i < bodySegments.size(); i++) {
            Location segLoc = center.clone().subtract(forward.clone().multiply(segOffsets[i]));
            double[] widths  = { 0.28, 0.36, 0.42, 0.38, 0.30 };
            double[] heights = { 0.28, 0.30, 0.32, 0.28, 0.22 };
            double w = widths[i] * scale;
            double h = heights[i] * scale;
            bodySegments.get(i).setLocation(segLoc);
            bodySegments.get(i).setScale(new Vector(w, h, w));
            bodySegments.get(i).setRotation(new Vector(pitch, yaw + Math.toRadians(serpentSpin * 0.2 * (i % 2 == 0 ? 1 : -1)), 0));
            bodySegments.get(i).updateAndDisplay();
        }

        Location headLoc = center.clone().add(forward.clone().multiply(0.38 * scale));
        headBlock.setLocation(headLoc);
        headBlock.setScale(new Vector(0.35 * scale, 0.22 * scale, 0.45 * scale));
        headBlock.setRotation(new Vector(pitch, yaw, 0));
        headBlock.updateAndDisplay();

        Location jawLoc = center.clone().add(forward.clone().multiply(0.42 * scale))
                .subtract(0, 0.08 * scale, 0);
        jawBlock.setLocation(jawLoc);
        jawBlock.setScale(new Vector(0.28 * scale, 0.12 * scale, 0.36 * scale));
        jawBlock.setRotation(new Vector(pitch + Math.toRadians(15 + Math.sin(Math.toRadians(serpentSpin * 2)) * 8), yaw, 0));
        jawBlock.updateAndDisplay();
    }

    private void spawnCoiledParticles(Location loc) {
        if (Math.random() < 0.25)
            player.getWorld().spawnParticle(Particle.BLOCK, loc, 1, 0.12, 0.08, 0.12, 0,
                    Material.SAND.createBlockData());
    }

    private void spawnLaunchParticles() {
        player.getWorld().spawnParticle(Particle.BLOCK, serpentLoc, 2, 0.1, 0.08, 0.1, 0,
                Material.SANDSTONE.createBlockData());
        if (Math.random() < 0.3)
            player.getWorld().spawnParticle(Particle.DUST, serpentLoc, 1, 0.08, 0.05, 0.08,
                    0, new Particle.DustOptions(Color.fromRGB(210, 185, 120), 0.7f));
    }

    private void spawnLatchParticles(Location loc) {
        if (Math.random() < 0.5)
            player.getWorld().spawnParticle(Particle.BLOCK, loc, 2, 0.2, 0.2, 0.2, 0,
                    Material.SAND.createBlockData());
    }

    private void spawnPullParticles(Location loc) {
        if (Math.random() < 0.3)
            player.getWorld().spawnParticle(Particle.BLOCK, loc, 1, 0.15, 0.1, 0.15, 0,
                    Material.SANDSTONE.createBlockData());
    }

    private void spawnShatterEffect(Location loc) {
        player.getWorld().spawnParticle(Particle.BLOCK, loc, 25, 0.4, 0.3, 0.4, 0,
                Material.SANDSTONE.createBlockData());
        player.getWorld().spawnParticle(Particle.BLOCK, loc, 12, 0.3, 0.2, 0.3, 0,
                Material.SAND.createBlockData());
        player.getWorld().playSound(loc, Sound.BLOCK_STONE_BREAK, 1.5f, 0.8f);
        player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.9f);
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
        return serpentLoc;
    }

    @Override
    public String getName() {
        return "SandSerpent";
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
        return "Summon a segmented sand serpent tethered to your hand by a Verlet rope. " +
                "Launch it at an enemy — it wraps around them and drags them to you before shattering.";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Activate — serpent coils at your hand.\n" +
                "Left Click again: Launch serpent at your target.\n" +
                "Sneak while launched or latching: Recall early.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (tether != null) tether.destroy();
        for (GameObject seg : bodySegments) seg.destroy();
        if (headBlock != null) headBlock.destroy();
        if (jawBlock != null) jawBlock.destroy();
    }

    @Override
    public void load() {
        abilityListener = new SandSerpentListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandSerpent.Cooldown", 7000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandSerpent.Damage", 4.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandSerpent.LaunchSpeed", 1.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandSerpent.ThrowRange", 18.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandSerpent.PullSpeed", 0.6);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Sand.SandSerpent.MaxLifetime", 7000L);
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