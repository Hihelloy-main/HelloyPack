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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class AvatarBurst extends AvatarAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double damage;
    private double burstRadius;
    private long chargeTime;
    private long maxLifetime;

    private enum BurstState {
        CHARGING,
        WAVE1,
        WAVE2,
        WAVE3,
        DONE
    }
    private BurstState state;

    private GameObject coreOrb;
    private GameObject ring1;
    private GameObject ring2;
    private GameObject ring3;

    private Transition chargeTransition;
    private long chargeStartTime;
    private double orbSpin;
    private double ring1Angle;
    private double ring2Angle;
    private double ring3Angle;

    private double waveRadius;
    private int wavePhase;
    private Set<Entity> waveHit = new HashSet<>();

    public AvatarBurst(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Avatar.AvatarBurst.Cooldown", 20000);
        this.damage = config.getDouble("ExtraAbilities.Hihelloy.Avatar.AvatarBurst.Damage", 6.0);
        this.burstRadius = config.getDouble("ExtraAbilities.Hihelloy.Avatar.AvatarBurst.BurstRadius", 10.0);
        this.chargeTime = config.getLong("ExtraAbilities.Hihelloy.Avatar.AvatarBurst.ChargeTime", 3000);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Avatar.AvatarBurst.MaxLifetime", 15000);

        this.state = BurstState.CHARGING;
        this.orbSpin = 0;
        this.ring1Angle = 0;
        this.ring2Angle = 0;
        this.ring3Angle = 0;
        this.waveRadius = 0;
        this.wavePhase = 0;
        this.chargeStartTime = System.currentTimeMillis();

        Location loc = player.getLocation().add(0, 1.2, 0);

        this.coreOrb = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                loc, new Vector(0.3, 0.3, 0.3),
                new Vector(0, 0, 0), new Vector());
        this.coreOrb.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.coreOrb.setBlockMaterial(Material.GLOWSTONE);

        this.ring1 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                loc, new Vector(0.8, 0.06, 0.8),
                new Vector(0, 0, 0), new Vector());
        this.ring1.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.ring1.setBlockMaterial(Material.BLUE_ICE);

        this.ring2 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                loc, new Vector(0.65, 0.06, 0.65),
                new Vector(Math.toRadians(60), 0, 0), new Vector());
        this.ring2.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.ring2.setBlockMaterial(Material.MAGMA_BLOCK);

        this.ring3 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                loc, new Vector(0.5, 0.06, 0.5),
                new Vector(0, 0, Math.toRadians(60)), new Vector());
        this.ring3.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
        this.ring3.setBlockMaterial(Material.MOSS_BLOCK);

        this.chargeTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(chargeTime / 50.0, 0, 0),
                new Vector(0, 0, 0));

        player.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.5f);
        player.getWorld().playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 0.7f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        orbSpin += 12;
        ring1Angle += 8;
        ring2Angle -= 11;
        ring3Angle += 6;

        Location center = player.getLocation().add(0, 1.2, 0);

        if (state == BurstState.CHARGING) {
            chargeTransition.update();
            double charge = chargeTransition.getX();
            double scale = 0.3 + charge * 0.4;

            coreOrb.setLocation(center);
            coreOrb.setScale(new Vector(scale, scale, scale));
            coreOrb.setRotation(new Vector(Math.toRadians(orbSpin), Math.toRadians(orbSpin * 0.7), 0));
            coreOrb.updateAndDisplay();

            ring1.setLocation(center);
            ring1.setRotation(new Vector(0, Math.toRadians(ring1Angle), 0));
            ring1.setScale(new Vector(0.5 + charge * 0.5, 0.06, 0.5 + charge * 0.5));
            ring1.updateAndDisplay();

            ring2.setLocation(center);
            ring2.setRotation(new Vector(Math.toRadians(60), Math.toRadians(ring2Angle), 0));
            ring2.setScale(new Vector(0.4 + charge * 0.4, 0.06, 0.4 + charge * 0.4));
            ring2.updateAndDisplay();

            ring3.setLocation(center);
            ring3.setRotation(new Vector(0, Math.toRadians(ring3Angle), Math.toRadians(60)));
            ring3.setScale(new Vector(0.3 + charge * 0.35, 0.06, 0.3 + charge * 0.35));
            ring3.updateAndDisplay();

            spawnChargeParticles(center, charge);

            if (!player.isSneaking()) {
                remove();
                return;
            }

            if (System.currentTimeMillis() >= chargeStartTime + chargeTime) {
                triggerBurst(center);
            }

        } else if (state == BurstState.WAVE1 || state == BurstState.WAVE2 || state == BurstState.WAVE3) {
            waveRadius += 0.6;
            spawnWaveParticles(center, waveRadius, wavePhase);
            checkWaveHits(center, waveRadius);

            if (waveRadius >= burstRadius) {
                waveRadius = 0;
                waveHit.clear();
                wavePhase++;
                if (wavePhase >= 3) {
                    destroyDisplays();
                    remove();
                    return;
                }
                state = wavePhase == 1 ? BurstState.WAVE2 : BurstState.WAVE3;
                player.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.0f + wavePhase * 0.2f);
            }

            if (state != BurstState.DONE) {
                coreOrb.setLocation(center);
                coreOrb.setRotation(new Vector(Math.toRadians(orbSpin), Math.toRadians(orbSpin * 0.7), 0));
                coreOrb.updateAndDisplay();
            }
        }
    }

    private void triggerBurst(Location center) {
        state = BurstState.WAVE1;
        waveRadius = 0;
        wavePhase = 0;
        if (ring1 != null) { ring1.destroy(); ring1 = null; }
        if (ring2 != null) { ring2.destroy(); ring2 = null; }
        if (ring3 != null) { ring3.destroy(); ring3 = null; }
        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        player.getWorld().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 2.0f, 0.8f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, center, 3, 0.3, 0.3, 0.3, 0);
    }

    private void checkWaveHits(Location center, double radius) {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center, radius + 0.8)) {
            if (!(e instanceof LivingEntity)) continue;
            if (e.getUniqueId().equals(player.getUniqueId())) continue;
            if (waveHit.contains(e)) continue;
            double dist = e.getLocation().distance(center);
            if (Math.abs(dist - radius) < 1.5) {
                waveHit.add(e);
                LivingEntity le = (LivingEntity) e;
                double waveDamage = damage * (1.0 - wavePhase * 0.2);
                DamageHandler.damageEntity(le, waveDamage, this);
                Vector kb = e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.2 - wavePhase * 0.2);
                kb.setY(0.5);
                e.setVelocity(e.getVelocity().add(kb));
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, wavePhase));
                if (wavePhase >= 1) e.setFireTicks(60);
            }
        }
    }

    private void spawnWaveParticles(Location center, double radius, int phase) {
        Material[] waveMats = {Material.BLUE_ICE, Material.MAGMA_BLOCK, Material.MOSS_BLOCK};
        Color[] colors = {Color.fromRGB(100, 180, 255), Color.fromRGB(255, 120, 40), Color.fromRGB(60, 180, 60)};
        int points = Math.max(20, (int) (radius * 8));
        for (int i = 0; i < points; i++) {
            double a = (2 * Math.PI / points) * i;
            double h = Math.sin(a * 3 + Math.toRadians(ring1Angle)) * 0.4;
            Location p = center.clone().add(Math.cos(a) * radius, h, Math.sin(a) * radius);
            player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0,
                    0, new Particle.DustOptions(colors[phase], 1.2f));
            if (Math.random() < 0.15)
                player.getWorld().spawnParticle(Particle.CRIT, p, 3, 0.1, 0.1, 0.1, 0);
        }
    }

    private void spawnChargeParticles(Location center, double charge) {
        int count = (int) (charge * 6);
        for (int i = 0; i < count; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = 0.2 + Math.random() * charge * 1.5;
            double h = (Math.random() - 0.5) * 1.5;
            Location p = center.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
            player.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0,
                    0, new Particle.DustOptions(Color.fromRGB(
                            (int)(Math.random() * 100 + 155),
                            (int)(Math.random() * 100 + 155),
                            255), 0.8f));
        }
        if (Math.random() < charge * 0.2)
            player.getWorld().playSound(center, Sound.BLOCK_BEACON_AMBIENT, 0.4f, 1.5f);
    }

    private void destroyDisplays() {
        if (coreOrb != null) { coreOrb.destroy(); coreOrb = null; }
        if (ring1 != null) { ring1.destroy(); ring1 = null; }
        if (ring2 != null) { ring2.destroy(); ring2 = null; }
        if (ring3 != null) { ring3.destroy(); ring3 = null; }
    }

    @Override
    public boolean isSneakAbility() {
        return true;
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
        return "AvatarBurst";
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
        return "Channel all four elements into a triple-wave radial burst — each wave deals damage and applies a different elemental effect.";
    }

    @Override
    public String getInstructions() {
        return "\nHold Sneak: Channel for 3 seconds.\nRelease Sneak early: Cancel.\nFull charge: Releases three expanding elemental waves.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        destroyDisplays();
    }

    @Override
    public void load() {
        abilityListener = new AvatarBurstListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.AvatarBurst.Cooldown", 20000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.AvatarBurst.Damage", 6.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.AvatarBurst.BurstRadius", 10.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.AvatarBurst.ChargeTime", 3000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Avatar.AvatarBurst.MaxLifetime", 15000L);
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
