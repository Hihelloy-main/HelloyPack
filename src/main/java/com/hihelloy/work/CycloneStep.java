package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.AirAbility;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CycloneStep extends AirAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double contactDamage;
    private double burstDamage;
    private double burstRadius;
    private double spinRadius;
    private long maxDuration;
    private long maxLifetime;

    private enum CycloneState { SPINNING, BURSTING }
    private CycloneState state;

    private List<CycloneRing> rings = new ArrayList<>();
    private double masterAngle;
    private double verticalOffset;
    private Transition formTransition;
    private long spinStart;

    private double burstProgress;
    private double burstMaxRadius;
    private Set<Entity> burstHit = new HashSet<>();
    private Set<Entity> contactHit = new HashSet<>();
    private long lastContactClear;

    public CycloneStep(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Air.CycloneStep.Cooldown", 9000);
        this.contactDamage = config.getDouble("ExtraAbilities.Hihelloy.Air.CycloneStep.ContactDamage", 1.5);
        this.burstDamage = config.getDouble("ExtraAbilities.Hihelloy.Air.CycloneStep.BurstDamage", 4.0);
        this.burstRadius = config.getDouble("ExtraAbilities.Hihelloy.Air.CycloneStep.BurstRadius", 5.0);
        this.spinRadius = config.getDouble("ExtraAbilities.Hihelloy.Air.CycloneStep.SpinRadius", 1.2);
        this.maxDuration = config.getLong("ExtraAbilities.Hihelloy.Air.CycloneStep.MaxDuration", 4000);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Air.CycloneStep.MaxLifetime", 8000);

        this.state = CycloneState.SPINNING;
        this.masterAngle = 0;
        this.verticalOffset = 0;
        this.burstProgress = 0;
        this.burstMaxRadius = burstRadius;
        this.spinStart = System.currentTimeMillis();
        this.lastContactClear = System.currentTimeMillis();

        this.formTransition = new Transition(
                new Vector(0, 0, 0), new Vector(1, 0, 0),
                new Vector(12, 0, 0), new Vector(0, 0, 0));

        Material ringMat = Material.WHITE_STAINED_GLASS;

        for (int i = 0; i < 3; i++) {
            double phaseOffset = (360.0 / 3.0) * i;
            double heightOff = (i - 1) * 0.7;
            rings.add(new CycloneRing(player, phaseOffset, heightOff, ringMat));
        }

        playAirbendingSound(player.getLocation());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.8f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) { remove(); return; }

        masterAngle += 16;
        verticalOffset = Math.sin(Math.toRadians(masterAngle * 0.5)) * 0.15;
        formTransition.update();
        double form = formTransition.getX();

        Location center = player.getLocation().add(0, 1.0, 0);

        if (state == CycloneState.SPINNING) {
            if (System.currentTimeMillis() - spinStart > maxDuration && player.isSneaking()) {
                triggerBurst(center);
                return;
            }
            if (!player.isSneaking()) {
                remove();
                return;
            }

            for (CycloneRing ring : rings) {
                ring.updateSpin(center, spinRadius * form, masterAngle, verticalOffset);
                ring.display();
            }

            spawnSpinParticles(center, form);
            checkContactHits(center);

        } else if (state == CycloneState.BURSTING) {
            burstProgress += 0.5;
            spawnBurstRing(center, burstProgress);
            checkBurstHits(center, burstProgress);

            for (CycloneRing ring : rings) {
                ring.scaleDown(burstProgress / burstMaxRadius);
                ring.display();
            }

            if (burstProgress >= burstMaxRadius) {
                remove();
            }
        }
    }

    public void onSneak() {
        if (state == CycloneState.SPINNING) {
            triggerBurst(player.getLocation().add(0, 1, 0));
        }
    }

    private void triggerBurst(Location center) {
        state = CycloneState.BURSTING;
        burstProgress = 0;
        burstHit.clear();
        playAirbendingSound(center);
        player.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 1.5f);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 5, 0.3, 0.3, 0.3, 0);
    }

    private void checkContactHits(Location center) {
        long now = System.currentTimeMillis();
        if (now - lastContactClear > 600) { contactHit.clear(); lastContactClear = now; }

        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center, spinRadius + 0.6)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())
                    && !contactHit.contains(e)) {
                contactHit.add(e);
                DamageHandler.damageEntity((LivingEntity) e, contactDamage, this);
                Vector away = e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.9);
                away.setY(0.3);
                e.setVelocity(e.getVelocity().add(away));
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, e.getLocation().add(0, 1, 0), 2, 0.1, 0.1, 0.1, 0);
            }
        }
    }

    private void checkBurstHits(Location center, double radius) {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center, radius + 0.6)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())
                    && !burstHit.contains(e)) {
                if (Math.abs(e.getLocation().distance(center) - radius) < 1.2) {
                    burstHit.add(e);
                    DamageHandler.damageEntity((LivingEntity) e, burstDamage, this);
                    ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0));
                    Vector kb = e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.3);
                    kb.setY(0.5);
                    e.setVelocity(e.getVelocity().add(kb));
                }
            }
        }
    }

    private void spawnSpinParticles(Location center, double form) {
        if (Math.random() > 0.5) return;
        double a = Math.toRadians(masterAngle + Math.random() * 180);
        double r = spinRadius * form * (0.7 + Math.random() * 0.4);
        double h = (Math.random() - 0.5) * 2.0;
        Location p = center.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
        player.getWorld().spawnParticle(Particle.CLOUD, p, 1, 0.05, 0.05, 0.05, 0.02);
        if (Math.random() < 0.15)
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0, 0, 0, 0);
    }

    private void spawnBurstRing(Location center, double radius) {
        int points = Math.max(16, (int) (radius * 10));
        for (int i = 0; i < points; i++) {
            double a = (2 * Math.PI / points) * i;
            double h = Math.sin(a * 3) * 0.4;
            Location p = center.clone().add(Math.cos(a) * radius, h, Math.sin(a) * radius);
            player.getWorld().spawnParticle(Particle.CLOUD, p, 1, 0.05, 0.05, 0.05, 0.03);
            if (Math.random() < 0.2)
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0, 0, 0, 0);
        }
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
        return "CycloneStep";
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
        return "Spin a shell of air rings around yourself that repels enemies while active, then release it as a concussive burst.";
    }
    @Override
    public String getInstructions() {
        return "\nHold Sneak: Maintain the cyclone shell — it repels nearby enemies.\nRelease Sneak: Cancel.\nLeft Click while active: Release burst wave outward.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        for (CycloneRing ring : rings) ring.destroy();
    }

    @Override
    public void load() {
        abilityListener = new CycloneStepListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.CycloneStep.Cooldown", 9000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.CycloneStep.ContactDamage", 1.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.CycloneStep.BurstDamage", 4.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.CycloneStep.BurstRadius", 5.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.CycloneStep.SpinRadius", 1.2);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.CycloneStep.MaxDuration", 4000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Air.CycloneStep.MaxLifetime", 8000L);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }

    private static class CycloneRing {
        private final Player player;
        private final double phaseOffset;
        private final double heightOffset;
        private final Material material;
        private GameObject segA, segB, segC, segD;
        private double currentScale = 1.0;

        CycloneRing(Player player, double phaseOffset, double heightOffset, Material mat) {
            this.player = player;
            this.phaseOffset = phaseOffset;
            this.heightOffset = heightOffset;
            this.material = mat;

            segA = makeSegment(player, mat);
            segB = makeSegment(player, mat);
            segC = makeSegment(player, mat);
            segD = makeSegment(player, mat);
        }

        private GameObject makeSegment(Player p, Material mat) {
            GameObject obj = new GameObject(p, GameObject.DisplayMode.BLOCK_DISPLAY,
                    p.getLocation(), new Vector(0.55, 0.06, 0.12), new Vector(0, 0, 0), new Vector());
            obj.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
            obj.setBlockMaterial(mat);
            return obj;
        }

        void updateSpin(Location center, double radius, double masterAngle, double verticalBob) {
            Location ringCenter = center.clone().add(0, heightOffset + verticalBob, 0);
            double baseAngle = Math.toRadians(masterAngle + phaseOffset);

            for (int i = 0; i < 4; i++) {
                double segAngle = baseAngle + Math.PI * 0.5 * i;
                double x = Math.cos(segAngle) * radius;
                double z = Math.sin(segAngle) * radius;
                Location sLoc = ringCenter.clone().add(x, 0, z);
                double yaw = segAngle + Math.PI * 0.5;
                double s = 0.55 * currentScale;

                GameObject seg = i == 0 ? segA : i == 1 ? segB : i == 2 ? segC : segD;
                seg.setLocation(sLoc);
                seg.setScale(new Vector(s, 0.06, 0.12 * currentScale));
                seg.setRotation(new Vector(0, yaw, 0));
            }
        }

        void scaleDown(double progress) {
            currentScale = Math.max(0, 1.0 - progress);
        }

        void display() {
            segA.updateAndDisplay();
            segB.updateAndDisplay();
            segC.updateAndDisplay();
            segD.updateAndDisplay();
        }

        void destroy() {
            if (segA != null) segA.destroy();
            if (segB != null) segB.destroy();
            if (segC != null) segC.destroy();
            if (segD != null) segD.destroy();
        }
    }
}
