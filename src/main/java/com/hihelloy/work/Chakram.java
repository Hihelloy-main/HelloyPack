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

public class Chakram extends EarthAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double range;
    private double returnSpeed;
    private double launchSpeed;
    private long maxLifetime;

    private enum ChakramState {
        IDLE,
        LAUNCHING,
        RETURNING
    }
    private ChakramState state;

    private Location chakramLocation;
    private Vector velocity;
    private double yaw;
    private double roll;
    private double spinSpeed;
    private long launchTime;

    private Set<Entity> hitEntities = new HashSet<>();

    private GameObject discObject;
    private GameObject rimObject1;
    private GameObject rimObject2;

    private Transition launchTransition;
    private Transition returnTransition;

    public Chakram(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Earth.Chakram.Cooldown", 7000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Earth.Chakram.Damage", 4.0);
        this.range = config.getDouble("ExtraAbilities.Hihelloy.Earth.Chakram.Range", 25.0);
        this.returnSpeed = config.getDouble("ExtraAbilities.Hihelloy.Earth.Chakram.ReturnSpeed", 1.2);
        this.launchSpeed = config.getDouble("ExtraAbilities.Hihelloy.Earth.Chakram.LaunchSpeed", 1.6);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Earth.Chakram.MaxLifetime", 8000);

        this.chakramLocation = GeneralMethods.getMainHandLocation(player).clone();
        this.yaw = -player.getLocation().getYaw();
        this.roll = 0;
        this.spinSpeed = 18;
        this.state = ChakramState.IDLE;

        Vector rot = new Vector(0, Math.toRadians(yaw), 0);
        Vector trans = new Vector();
        Vector scale = new Vector(1, 1, 1);

        this.discObject = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                chakramLocation, new Vector(0.5, 0.05, 0.5), rot, trans);
        this.discObject.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.discObject.setBlockMaterial(Material.POLISHED_ANDESITE);

        this.rimObject1 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.discObject, new Vector(0.6, 0.1, 0.6),
                new Vector(0, Math.toRadians(45), 0), new Vector());
        this.rimObject1.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.rimObject1.setBlockMaterial(Material.CHISELED_STONE_BRICKS);

        this.rimObject2 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                this.discObject, new Vector(0.7, 0.08, 0.7),
                new Vector(0, Math.toRadians(22.5), 0), new Vector());
        this.rimObject2.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.rimObject2.setBlockMaterial(Material.STONE_BRICKS);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        if (state == ChakramState.IDLE) {
            this.chakramLocation = GeneralMethods.getMainHandLocation(player);
            this.yaw += spinSpeed;
        } else if (state == ChakramState.LAUNCHING) {
            if (this.chakramLocation.distanceSquared(player.getLocation()) > range * range) {
                beginReturn();
            }

            velocity.add(new Vector(0, -0.04, 0));
            if (velocity.lengthSquared() > launchSpeed * launchSpeed)
                velocity.normalize().multiply(launchSpeed);
            this.chakramLocation.add(velocity);
            this.yaw += 25;

            if (chakramLocation.getBlock().isSolid()) {
                beginReturn();
            }

            checkHits();
        } else if (state == ChakramState.RETURNING) {
            Vector toPlayer = player.getEyeLocation().toVector().subtract(chakramLocation.toVector());
            double dist = toPlayer.length();
            if (dist < 1.2) {
                remove();
                return;
            }
            toPlayer.normalize().multiply(returnSpeed);
            velocity = velocity.add(toPlayer).multiply(0.7);
            if (velocity.lengthSquared() > returnSpeed * returnSpeed * 4)
                velocity.normalize().multiply(returnSpeed * 2);
            this.chakramLocation.add(velocity);
            this.yaw += 30;
            checkHits();
        }

        updateDisplay();
    }

    public void onLeftClick() {
        if (state == ChakramState.IDLE) {
            state = ChakramState.LAUNCHING;
            launchTime = System.currentTimeMillis();
            velocity = player.getLocation().getDirection().clone().multiply(launchSpeed);
            velocity.setY(velocity.getY() + 0.15);
            player.getWorld().playSound(chakramLocation, Sound.BLOCK_STONE_BREAK, 1.5f, 1.8f);
            player.getWorld().playSound(chakramLocation, Sound.ENTITY_ARROW_SHOOT, 1.2f, 0.7f);
        }
    }

    private void beginReturn() {
        state = ChakramState.RETURNING;
        hitEntities.clear();
        player.getWorld().playSound(chakramLocation, Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 0.8f);
    }

    private void checkHits() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(chakramLocation, 0.9)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                if (!hitEntities.contains(e)) {
                    hitEntities.add(e);
                    DamageHandler.damageEntity((LivingEntity) e, damage, this);
                    Vector knockback = chakramLocation.toVector().subtract(e.getLocation().toVector()).normalize().multiply(-0.6);
                    e.setVelocity(e.getVelocity().add(knockback));
                    player.getWorld().spawnParticle(Particle.BLOCK, chakramLocation, 12, 0.15, 0.15, 0.15, 0, Material.STONE.createBlockData());
                    player.getWorld().playSound(chakramLocation, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 1.2f);
                }
            }
        }
    }

    private void updateDisplay() {
        Vector rotVec = new Vector(Math.toRadians(this.roll), Math.toRadians(this.yaw), 0);
        this.discObject.setLocation(this.chakramLocation);
        this.discObject.setRotation(rotVec);
        this.discObject.updateAndDisplay();
        this.rimObject1.setRotation(new Vector(0, Math.toRadians(yaw + 45), 0));
        this.rimObject1.updateAndDisplay();
        this.rimObject2.setRotation(new Vector(0, Math.toRadians(yaw + 22.5), 0));
        this.rimObject2.updateAndDisplay();
    }

    @Override
    public boolean isSneakAbility() { return false; }
    @Override
    public boolean isHarmlessAbility() { return false; }
    @Override
    public long getCooldown() { return this.cooldown; }
    @Override
    public Location getLocation() { return this.chakramLocation; }
    @Override
    public String getName() { return "Chakram"; }
    @Override
    public String getAuthor() { return "Hihelloy"; }
    @Override
    public String getDescription() { return "Hurl a spinning stone disc that boomerangs back to you!"; }
    @Override
    public String getInstructions() { return "\nLeft Click: Throw Chakram\nChakram returns to you after reaching max range or hitting terrain."; }
    @Override
    public String getVersion() { return "1.0"; }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (discObject != null) discObject.destroy();
        if (rimObject1 != null) rimObject1.destroy();
        if (rimObject2 != null) rimObject2.destroy();
    }

    @Override
    public void load() {
        abilityListener = new ChakramListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.Chakram.Cooldown", 7000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.Chakram.Damage", 4.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.Chakram.Range", 25.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.Chakram.ReturnSpeed", 1.2);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.Chakram.LaunchSpeed", 1.6);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Earth.Chakram.MaxLifetime", 8000L);
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
