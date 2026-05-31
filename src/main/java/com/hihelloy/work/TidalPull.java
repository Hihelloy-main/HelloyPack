package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.hihelloy.work.lib.verlet.VerletHandler;
import com.hihelloy.work.lib.verlet.VerletRope;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class TidalPull extends WaterAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double slamDamage;
    private double tendrilRange;
    private double tendrilSpeed;
    private long slamDuration;
    private long maxLifetime;

    private enum TidalState {
        SHOOTING,
        LATCHED,
        SLAMMING,
        RETRACTING,
        MISSED
    }
    private TidalState state;

    private VerletHandler vh;
    private VerletRope rope;

    private Location tipLoc;
    private Vector tipVelocity;
    private LivingEntity target;
    private long latchStartTime;
    private long slamStartTime;

    public TidalPull(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        if (!hasWaterSource()) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Water.TidalPull.Cooldown", 7000);
        this.slamDamage = config.getDouble("ExtraAbilities.Hihelloy.Water.TidalPull.SlamDamage", 5.0);
        this.tendrilRange = config.getDouble("ExtraAbilities.Hihelloy.Water.TidalPull.Range", 16.0);
        this.tendrilSpeed = config.getDouble("ExtraAbilities.Hihelloy.Water.TidalPull.Speed", 1.6);
        this.slamDuration = config.getLong("ExtraAbilities.Hihelloy.Water.TidalPull.SlamDuration", 1200);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Water.TidalPull.MaxLifetime", 7000);

        this.state = TidalState.SHOOTING;
        this.tipLoc = player.getEyeLocation().clone();
        this.tipVelocity = player.getLocation().getDirection().clone().multiply(tendrilSpeed);

        this.vh = new VerletHandler(player);
        int nodeCount = (int) (tendrilRange * 5);
        Vector maxScale = new Vector(0.1, tendrilRange / nodeCount, 0.1);
        Vector minScale = new Vector(0.05, tendrilRange / nodeCount, 0.05);

        this.rope = new VerletRope(this.vh, player, this.tipLoc, tendrilRange, nodeCount, 1,
                Color.fromRGB(0, 120, 200), 1.2f, true, true, maxScale, minScale, Material.BLUE_ICE);
        this.rope.setRopeLength(0.5, false);
        this.vh.setGravity(new Vector(0, -0.03, 0));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.8f, 1.5f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        if (state == TidalState.SHOOTING) {
            tipVelocity.add(new Vector(0, -0.04, 0));
            if (tipVelocity.length() > tendrilSpeed) tipVelocity.normalize().multiply(tendrilSpeed);
            tipLoc.add(tipVelocity);

            double ropeLen = Math.min(tipLoc.distance(player.getEyeLocation()) + 0.5, tendrilRange);
            rope.setRopeLength(ropeLen, false);
            rope.moveStartPoint(player.getEyeLocation());
            rope.moveEndPoint(tipLoc);
            vh.update();
            vh.display();

            spawnTipParticles();

            if (tipLoc.distanceSquared(player.getLocation()) > tendrilRange * tendrilRange) {
                state = TidalState.MISSED;
                return;
            }
            if (tipLoc.getBlock().isSolid()) {
                state = TidalState.MISSED;
                return;
            }

            checkLatch();

        } else if (state == TidalState.LATCHED) {
            if (target == null || target.isDead()) { remove(); return; }
            tipLoc = target.getLocation().add(0, 1.0, 0);
            rope.moveStartPoint(player.getEyeLocation());
            rope.moveEndPoint(tipLoc);
            vh.update();
            vh.display();

            if (System.currentTimeMillis() > latchStartTime + 300) {
                beginSlam();
            }

        } else if (state == TidalState.SLAMMING) {
            if (target == null || target.isDead()) { remove(); return; }
            double elapsed = System.currentTimeMillis() - slamStartTime;
            double progress = Math.min(elapsed / (double) slamDuration, 1.0);

            double arcHeight = Math.sin(progress * Math.PI) * 6.0;
            Vector toPlayer = player.getLocation().toVector().subtract(target.getLocation().toVector());
            double flatDist = Math.sqrt(toPlayer.getX() * toPlayer.getX() + toPlayer.getZ() * toPlayer.getZ());
            Vector slamTarget = player.getLocation().toVector().subtract(
                    toPlayer.normalize().multiply(1.5));

            Location newLoc = target.getLocation().toVector()
                    .add(toPlayer.clone().normalize().multiply(flatDist * progress * (1.0 / (double) slamDuration * 50)))
                    .add(new Vector(0, arcHeight * (1 - progress), 0))
                    .toLocation(player.getWorld());

            Vector slamVelocity = new Vector(
                    (slamTarget.getX() - target.getLocation().getX()) * 0.15,
                    arcHeight > 0.5 ? 0.3 : -0.8,
                    (slamTarget.getZ() - target.getLocation().getZ()) * 0.15);

            target.setVelocity(slamVelocity);

            tipLoc = target.getLocation().add(0, 1, 0);
            rope.moveStartPoint(player.getEyeLocation());
            rope.moveEndPoint(tipLoc);
            rope.furl(0.3, 1.0, false);
            vh.update();
            vh.display();

            player.getWorld().spawnParticle(Particle.SPLASH, tipLoc, 5, 0.2, 0.2, 0.2, 0.05);

            if (progress >= 1.0) {
                DamageHandler.damageEntity(target, slamDamage, this);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                spawnSlamImpact(target.getLocation());
                state = TidalState.RETRACTING;
            }

        } else if (state == TidalState.RETRACTING || state == TidalState.MISSED) {
            rope.moveStartPoint(player.getEyeLocation());
            rope.furl(0.6, 0.4, false);
            vh.update();
            vh.display();
            if (rope.getRopeLength() <= 0.4) {
                remove();
            }
        }
    }

    private void checkLatch() {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(tipLoc, 0.9)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                target = (LivingEntity) e;
                state = TidalState.LATCHED;
                latchStartTime = System.currentTimeMillis();
                player.getWorld().playSound(tipLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.6f);
                player.getWorld().spawnParticle(Particle.SPLASH, tipLoc, 20, 0.3, 0.3, 0.3, 0.1);
                return;
            }
        }
    }

    private void beginSlam() {
        state = TidalState.SLAMMING;
        slamStartTime = System.currentTimeMillis();
        player.getWorld().playSound(tipLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 1.0f);
    }

    private void spawnTipParticles() {
        player.getWorld().spawnParticle(Particle.SPLASH, tipLoc, 3, 0.05, 0.05, 0.05, 0.03);
        player.getWorld().spawnParticle(Particle.DUST, tipLoc, 1, 0, 0, 0,
                0, new Particle.DustOptions(Color.fromRGB(0, 160, 230), 0.8f));
    }

    private void spawnSlamImpact(Location loc) {
        player.getWorld().spawnParticle(Particle.SPLASH, loc, 40, 0.5, 0.2, 0.5, 0.15);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 20, 0.4, 0.1, 0.4, 0.05);
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 2.0f, 0.7f);
        player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.8f);
    }


    private boolean hasWaterSource() {
        org.bukkit.block.Block src = getWaterSourceBlock(player, 4, true);
        if (src != null) return true;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() == Material.POTION) {
                org.bukkit.inventory.meta.PotionMeta meta =
                        (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
                if (meta != null && meta.getBasePotionType() == org.bukkit.potion.PotionType.WATER)
                    return true;
            }
        }
        return false;
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
        return "TidalPull";
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
        return "Shoot a water tendril that latches onto a target and violently slams them into the ground.";
    }
    @Override
    public String getInstructions() {
        return "\nLeft Click: Launch water tendril.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        if (rope != null) rope.destroy();
    }

    @Override
    public void load() {
        abilityListener = new TidalPullListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TidalPull.Cooldown", 7000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TidalPull.SlamDamage", 5.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TidalPull.Range", 16.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TidalPull.Speed", 1.6);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TidalPull.SlamDuration", 1200L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Water.TidalPull.MaxLifetime", 7000L);
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
