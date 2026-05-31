package com.hihelloy.work;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PhoenixCoil extends FireAbility implements AddonAbility {

    public Listener abilityListener;

    private long cooldown;
    private double whipDamage;
    private double boltDamage;
    private double columnDamage;
    private double burstBaseDamage;
    private double burstBaseRadius;
    private long stage2Time;
    private long stage3Time;
    private long maxLifetime;

    private enum CoilStage { STAGE1, STAGE2, STAGE3 }
    private CoilStage stage;

    private enum AttackState { ORBITING, WHIPPING, SEEKING, COLUMN_DRILL, BURSTING }
    private AttackState attackState;

    private List<CoilArc> arcs = new ArrayList<>();
    private GameObject coreOrb;
    private double masterAngle;

    private Transition formTransition;

    private Location whipTipLoc;
    private Vector whipVelocity;
    private Location whipStart;
    private boolean whipHit;

    private Location boltLoc;
    private Vector boltVelocity;
    private Location boltStart;
    private LivingEntity seekTarget;

    private List<DrillSegment> drillSegments = new ArrayList<>();
    private double drillProgress;
    private double drillLength;
    private Vector drillDir;
    private Location drillStart;
    private Set<Entity> drillHit = new HashSet<>();

    private double burstRadius;
    private double burstMaxRadius;
    private Set<Entity> burstHit = new HashSet<>();

    private long attackStartTime;
    private static final long ATTACK_DURATION = 1000;

    public PhoenixCoil(Player player) {
        super(player);
        if (!bPlayer.canBend(this)) return;
        setFields();
        start();
    }

    private void setFields() {
        FileConfiguration config = ConfigManager.getConfig();
        this.cooldown = config.getLong("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.Cooldown", 18000);
        this.whipDamage = config.getDouble("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.WhipDamage", 4.0);
        this.boltDamage = config.getDouble("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.BoltDamage", 5.0);
        this.columnDamage = config.getDouble("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.ColumnDamage", 3.5);
        this.burstBaseDamage = config.getDouble("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.BurstBaseDamage", 5.0);
        this.burstBaseRadius = config.getDouble("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.BurstBaseRadius", 4.0);
        this.stage2Time = config.getLong("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.Stage2Time", 5000);
        this.stage3Time = config.getLong("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.Stage3Time", 10000);
        this.maxLifetime = config.getLong("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.MaxLifetime", 20000);

        this.stage = CoilStage.STAGE1;
        this.attackState = AttackState.ORBITING;
        this.masterAngle = 0;

        this.formTransition = new Transition(
                new Vector(0, 0, 0),
                new Vector(1, 0, 0),
                new Vector(15, 0, 0),
                new Vector(0, 0, 0));

        Location center = player.getLocation().add(0, 1, 0);
        arcs.add(new CoilArc(player, center, 0, Material.MAGMA_BLOCK, Material.SHROOMLIGHT));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1.5f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.4f);
    }

    @Override
    public void progress() {
        if (System.currentTimeMillis() > getStartTime() + maxLifetime) {
            remove();
            return;
        }

        long alive = System.currentTimeMillis() - getStartTime();
        checkStageProgression(alive);

        masterAngle += getOrbitSpeed();

        Location center = player.getLocation().add(0, 1.1, 0);

        if (attackState == AttackState.ORBITING) {
            updateOrbiting(center);

        } else if (attackState == AttackState.WHIPPING) {
            updateWhip(center);

        } else if (attackState == AttackState.SEEKING) {
            updateSeekingBolt(center);

        } else if (attackState == AttackState.COLUMN_DRILL) {
            updateColumnDrill(center);

        } else if (attackState == AttackState.BURSTING) {
            updateBurst(center);
        }
    }

    private void checkStageProgression(long alive) {
        if (stage == CoilStage.STAGE1 && alive >= stage2Time) {
            stage = CoilStage.STAGE2;
            arcs.add(new CoilArc(player, player.getLocation().add(0, 1, 0), 180, Material.BLACKSTONE, Material.GLOWSTONE));
            this.coreOrb = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    player.getLocation().add(0, 1, 0), new Vector(0.2, 0.2, 0.2),
                    new Vector(0, 0, 0), new Vector());
            this.coreOrb.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
            this.coreOrb.setBlockMaterial(Material.SHROOMLIGHT);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
            player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.1);
        }
        if (stage == CoilStage.STAGE2 && alive >= stage3Time) {
            stage = CoilStage.STAGE3;
            arcs.add(new CoilArc(player, player.getLocation().add(0, 1, 0), 90, Material.BASALT, Material.MAGMA_BLOCK));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 2.0f, 0.3f);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.8f);
            for (int i = 0; i < 3; i++) {
                player.getWorld().spawnParticle(Particle.EXPLOSION,
                        player.getLocation().add((Math.random() - 0.5) * 2, 1 + Math.random(), (Math.random() - 0.5) * 2),
                        1, 0, 0, 0, 0);
            }
            player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 40, 0.6, 0.6, 0.6, 0.15);
        }
    }

    private void updateOrbiting(Location center) {
        formTransition.update();
        double form = formTransition.getX();
        double orbitR = 1.2 + form * 0.3;
        double bobAmp = 0.4 * form;

        for (int i = 0; i < arcs.size(); i++) {
            double phaseOffset = (360.0 / arcs.size()) * i;
            arcs.get(i).updateOrbit(center, orbitR, masterAngle + phaseOffset, bobAmp, alive());
        }

        if (coreOrb != null) {
            coreOrb.setLocation(center);
            coreOrb.setRotation(new Vector(Math.toRadians(masterAngle), Math.toRadians(masterAngle * 0.7), 0));
            double pulse = 0.18 + Math.sin(alive() * 0.006) * 0.06;
            coreOrb.setScale(new Vector(pulse, pulse, pulse));
            coreOrb.updateAndDisplay();
        }

        spawnOrbitParticles(center);
    }

    private void updateWhip(Location center) {
        if (!whipHit) {
            whipVelocity.add(new Vector(0, -0.04, 0));
            whipTipLoc.add(whipVelocity);
            spawnWhipTrail(whipTipLoc);

            if (whipTipLoc.distanceSquared(whipStart) > 20 * 20 || whipTipLoc.getBlock().isSolid()) {
                endAttack();
                return;
            }

            for (Entity e : GeneralMethods.getEntitiesAroundPoint(whipTipLoc, 0.9)) {
                if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                    DamageHandler.damageEntity((LivingEntity) e, whipDamage, this);
                    e.setFireTicks(80);
                    Vector kb = whipVelocity.clone().normalize().multiply(0.8);
                    e.setVelocity(e.getVelocity().add(kb));
                    spawnWhipImpact(whipTipLoc);
                    whipHit = true;
                    break;
                }
            }
        } else if (System.currentTimeMillis() > attackStartTime + 300) {
            endAttack();
        }

        updateOrbiting(center);
    }

    private void updateSeekingBolt(Location center) {
        if (seekTarget == null || seekTarget.isDead() || seekTarget.getWorld() != player.getWorld()) {
            seekTarget = findNearestEnemy(boltLoc, 20);
        }

        if (seekTarget != null) {
            Vector toTarget = seekTarget.getLocation().add(0, 1, 0).toVector().subtract(boltLoc.toVector());
            double dist = toTarget.length();
            if (dist > 0.1) {
                toTarget.normalize().multiply(0.08);
                boltVelocity.add(toTarget);
            }
        }

        double speed = 1.3;
        if (boltVelocity.length() > speed) boltVelocity.normalize().multiply(speed);
        boltLoc.add(boltVelocity);

        spawnBoltTrail(boltLoc);

        if (boltLoc.distanceSquared(boltStart) > 22 * 22 || boltLoc.getBlock().isSolid()) {
            spawnBoltImpact(boltLoc);
            endAttack();
            return;
        }

        for (Entity e : GeneralMethods.getEntitiesAroundPoint(boltLoc, 0.8)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                DamageHandler.damageEntity((LivingEntity) e, boltDamage, this);
                e.setFireTicks(100);
                spawnBoltImpact(boltLoc);
                endAttack();
                return;
            }
        }

        updateOrbiting(center);
    }

    private void updateColumnDrill(Location center) {
        drillProgress += 0.6;

        if (drillProgress > drillLength + 4) {
            endAttack();
            return;
        }

        int rings = (int) Math.min(drillProgress / 0.6, drillLength / 0.6);
        for (int r = 0; r < rings; r++) {
            double ringDist = r * 0.6;
            if (ringDist > drillProgress) break;
            Location ringCenter = drillStart.clone().add(drillDir.clone().multiply(ringDist));

            int points = 8;
            double ringRadius = 0.6 + Math.sin(Math.toRadians(masterAngle + ringDist * 40)) * 0.2;
            Vector perp1 = drillDir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
            if (perp1.lengthSquared() < 0.001) perp1 = new Vector(1, 0, 0);
            Vector perp2 = drillDir.clone().crossProduct(perp1).normalize();

            for (int i = 0; i < points; i++) {
                double a = Math.toRadians((360.0 / points) * i + masterAngle * 3 + ringDist * 60);
                Location p = ringCenter.clone().add(
                        perp1.clone().multiply(Math.cos(a) * ringRadius)
                                .add(perp2.clone().multiply(Math.sin(a) * ringRadius)));
                if (Math.random() < 0.5)
                    player.getWorld().spawnParticle(Particle.FLAME, p, 1, 0.03, 0.03, 0.03, 0.02);
                if (Math.random() < 0.15)
                    player.getWorld().spawnParticle(Particle.LAVA, p, 1, 0.05, 0.05, 0.05, 0);
            }

            if (r == rings - 1) {
                for (Entity e : GeneralMethods.getEntitiesAroundPoint(ringCenter, 1.0)) {
                    if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())
                            && !drillHit.contains(e)) {
                        drillHit.add(e);
                        DamageHandler.damageEntity((LivingEntity) e, columnDamage, this);
                        e.setFireTicks(60);
                        Vector kb = drillDir.clone().multiply(0.5);
                        kb.setY(kb.getY() + 0.3);
                        e.setVelocity(e.getVelocity().add(kb));
                    }
                }
            }
        }

        updateOrbiting(center);
    }

    private void updateBurst(Location center) {
        burstRadius += 0.55;
        spawnBurstRing(center, burstRadius);
        checkBurstHits(center, burstRadius);

        for (CoilArc arc : arcs) {
            arc.scaleDown(burstRadius / burstMaxRadius);
        }
        if (coreOrb != null) {
            coreOrb.setLocation(center);
            double s = Math.max(0, 0.18 * (1.0 - burstRadius / burstMaxRadius));
            coreOrb.setScale(new Vector(s, s, s));
            coreOrb.updateAndDisplay();
        }

        if (burstRadius >= burstMaxRadius) {
            remove();
        }
    }

    public void onLeftClick() {
        if (attackState != AttackState.ORBITING) return;

        if (stage == CoilStage.STAGE1) {
            beginWhip();
        } else if (stage == CoilStage.STAGE2) {
            beginSeekingBolt();
        } else {
            beginColumnDrill();
        }
    }

    public void onSneak() {
        if (attackState == AttackState.ORBITING) {
            beginBurst();
        }
    }

    private void beginWhip() {
        attackState = AttackState.WHIPPING;
        attackStartTime = System.currentTimeMillis();
        whipHit = false;
        whipTipLoc = GeneralMethods.getMainHandLocation(player).clone();
        whipStart = whipTipLoc.clone();
        whipVelocity = player.getLocation().getDirection().clone().multiply(1.6);
        whipVelocity.setY(whipVelocity.getY() + 0.2);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.7f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.2f, 1.6f);
    }

    private void beginSeekingBolt() {
        attackState = AttackState.SEEKING;
        attackStartTime = System.currentTimeMillis();
        boltLoc = GeneralMethods.getMainHandLocation(player).clone();
        boltStart = boltLoc.clone();
        boltVelocity = player.getLocation().getDirection().clone().multiply(1.3);
        seekTarget = findNearestEnemy(boltLoc, 20);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.0f);
    }

    private void beginColumnDrill() {
        attackState = AttackState.COLUMN_DRILL;
        attackStartTime = System.currentTimeMillis();
        drillDir = player.getLocation().getDirection().clone().normalize();
        drillStart = player.getEyeLocation().clone();
        drillProgress = 0;
        drillLength = 14.0;
        drillHit.clear();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
    }

    private void beginBurst() {
        attackState = AttackState.BURSTING;
        attackStartTime = System.currentTimeMillis();
        burstRadius = 0;
        long alive = System.currentTimeMillis() - getStartTime();
        double stageBonus = stage == CoilStage.STAGE3 ? 1.4 : stage == CoilStage.STAGE2 ? 1.2 : 1.0;
        burstMaxRadius = burstBaseRadius * stageBonus;
        burstHit.clear();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 2.0f, 0.4f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 2, 0.2, 0.2, 0.2, 0);
    }

    private void endAttack() {
        attackState = AttackState.ORBITING;
        drillHit.clear();
    }

    private double getOrbitSpeed() {
        return switch (stage) {
            case STAGE1 -> 6.0;
            case STAGE2 -> 9.0;
            case STAGE3 -> 13.0;
        };
    }

    private long alive() {
        return System.currentTimeMillis() - getStartTime();
    }

    private LivingEntity findNearestEnemy(Location from, double range) {
        LivingEntity nearest = null;
        double nearestSq = range * range;
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(from, range)) {
            if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
                double dSq = e.getLocation().distanceSquared(from);
                if (dSq < nearestSq) {
                    nearestSq = dSq;
                    nearest = (LivingEntity) e;
                }
            }
        }
        return nearest;
    }

    private void spawnOrbitParticles(Location center) {
        for (CoilArc arc : arcs) {
            Location tip = arc.getTipLocation();
            if (tip == null) continue;
            if (Math.random() < 0.7)
                player.getWorld().spawnParticle(Particle.FLAME, tip, 1, 0.08, 0.08, 0.08, 0.03);
            if (Math.random() < 0.2)
                player.getWorld().spawnParticle(Particle.LAVA, tip, 1, 0.05, 0.05, 0.05, 0);
        }
        if (stage == CoilStage.STAGE3 && Math.random() < 0.15) {
            player.getWorld().spawnParticle(Particle.FLAME, center, 2, 0.3, 0.3, 0.3, 0.08);
        }
    }

    private void spawnWhipTrail(Location loc) {
        player.getWorld().spawnParticle(Particle.FLAME, loc, 3, 0.1, 0.1, 0.1, 0.05);
        player.getWorld().spawnParticle(Particle.LAVA, loc, 1, 0.05, 0.05, 0.05, 0);
    }

    private void spawnWhipImpact(Location loc) {
        player.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
        player.getWorld().spawnParticle(Particle.FLAME, loc, 20, 0.3, 0.3, 0.3, 0.1);
        player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.8f);
    }

    private void spawnBoltTrail(Location loc) {
        player.getWorld().spawnParticle(Particle.FLAME, loc, 4, 0.08, 0.08, 0.08, 0.04);
        if (Math.random() < 0.3)
            player.getWorld().spawnParticle(Particle.LAVA, loc, 1, 0.04, 0.04, 0.04, 0);
    }

    private void spawnBoltImpact(Location loc) {
        player.getWorld().spawnParticle(Particle.EXPLOSION, loc, 2, 0.2, 0.2, 0.2, 0);
        player.getWorld().spawnParticle(Particle.FLAME, loc, 25, 0.4, 0.4, 0.4, 0.12);
        player.getWorld().playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.2f);
    }

    private void spawnBurstRing(Location center, double radius) {
        int points = Math.max(20, (int) (radius * 10));
        for (int i = 0; i < points; i++) {
            double a = (2 * Math.PI / points) * i;
            double h = Math.sin(a * 4 + masterAngle * 0.05) * 0.5;
            Location p = center.clone().add(Math.cos(a) * radius, h, Math.sin(a) * radius);
            player.getWorld().spawnParticle(Particle.FLAME, p, 1, 0.05, 0.05, 0.05, 0.04);
            if (Math.random() < 0.2)
                player.getWorld().spawnParticle(Particle.LAVA, p, 1, 0.05, 0.05, 0.05, 0);
        }
        if (Math.random() < 0.3) {
            player.getWorld().playSound(center, Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.2f + (float) Math.random() * 0.5f);
        }
    }

    private void checkBurstHits(Location center, double radius) {
        for (Entity e : GeneralMethods.getEntitiesAroundPoint(center, radius + 0.8)) {
            if (!(e instanceof LivingEntity) || e.getUniqueId().equals(player.getUniqueId())) continue;
            if (burstHit.contains(e)) continue;
            double dist = e.getLocation().distance(center);
            if (Math.abs(dist - radius) < 1.5) {
                burstHit.add(e);
                double stageBonus = stage == CoilStage.STAGE3 ? 1.4 : stage == CoilStage.STAGE2 ? 1.2 : 1.0;
                DamageHandler.damageEntity((LivingEntity) e, burstBaseDamage * stageBonus, this);
                e.setFireTicks(100 + (stage == CoilStage.STAGE3 ? 60 : 0));
                Vector kb = e.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.4);
                kb.setY(0.6);
                e.setVelocity(e.getVelocity().add(kb));
                player.getWorld().spawnParticle(Particle.FLAME,
                        e.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.08);
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
        return "PhoenixCoil";
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
        return "Summon a coil of living fire that orbits you and grows more powerful over time. " +
                "Stage 1: fire whip. Stage 2: seeking bolt. Stage 3: spiraling column drill.";
    }

    @Override
    public String getInstructions() {
        return "\nLeft Click: Attack (changes with each stage)\nSneak: Detonate the coil outward as a burst — bigger and stronger the longer it's alive.";
    }

    @Override
    public void remove() {
        if (bPlayer == null) return;
        bPlayer.addCooldown(this, cooldown);
        super.remove();
        for (CoilArc arc : arcs) arc.destroy();
        if (coreOrb != null) coreOrb.destroy();
    }

    @Override
    public void load() {
        abilityListener = new PhoenixCoilListener();
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(abilityListener, ProjectKorra.plugin);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.Cooldown", 18000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.WhipDamage", 4.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.BoltDamage", 5.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.ColumnDamage", 3.5);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.BurstBaseDamage", 5.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.BurstBaseRadius", 4.0);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.Stage2Time", 5000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.Stage3Time", 10000L);
        ConfigManager.getConfig().addDefault("ExtraAbilities.Hihelloy.Fire.PhoenixCoil.MaxLifetime", 20000L);
        ConfigManager.defaultConfig.save();
        ProjectKorra.log.info("Succesfully enabled " + getName() + " by " + getAuthor());
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(abilityListener);
        remove();
        ProjectKorra.log.info("Successfully disabled " + getName() + " by " + getAuthor());
    }

    private static class CoilArc {
        private final Player player;
        private final double phaseOffset;

        private GameObject head;
        private GameObject body1;
        private GameObject body2;
        private GameObject tail;

        private Location tipLocation;
        private double currentScale = 1.0;

        CoilArc(Player player, Location center, double phaseOffset, Material bodyMat, Material headMat) {
            this.player = player;
            this.phaseOffset = phaseOffset;
            this.tipLocation = center.clone();

            this.head = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    center, new Vector(0.22, 0.22, 0.22),
                    new Vector(0, 0, 0), new Vector());
            this.head.setBlockRotationMode(GameObject.BlockRotationMode.CENTER);
            this.head.setBlockMaterial(headMat);

            this.body1 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    center, new Vector(0.16, 0.16, 0.45),
                    new Vector(0, 0, 0), new Vector());
            this.body1.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
            this.body1.setBlockMaterial(bodyMat);

            this.body2 = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    center, new Vector(0.12, 0.12, 0.35),
                    new Vector(0, 0, 0), new Vector());
            this.body2.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
            this.body2.setBlockMaterial(bodyMat);

            this.tail = new GameObject(player, GameObject.DisplayMode.BLOCK_DISPLAY,
                    center, new Vector(0.08, 0.08, 0.22),
                    new Vector(0, 0, 0), new Vector());
            this.tail.setBlockRotationMode(GameObject.BlockRotationMode.EDGE);
            this.tail.setBlockMaterial(bodyMat);
        }

        void updateOrbit(Location center, double orbitR, double angle, double bobAmp, long alive) {
            double rad = Math.toRadians(angle + phaseOffset);
            double verticalWave = Math.sin(Math.toRadians(angle * 2 + phaseOffset)) * bobAmp;
            double spiralLift = Math.sin(alive * 0.003 + Math.toRadians(phaseOffset)) * 0.25;

            tipLocation = center.clone().add(
                    Math.cos(rad) * orbitR,
                    verticalWave + spiralLift,
                    Math.sin(rad) * orbitR);

            Location mid1 = center.clone().add(
                    Math.cos(rad - 0.4) * orbitR * 0.85,
                    verticalWave * 0.6 + spiralLift,
                    Math.sin(rad - 0.4) * orbitR * 0.85);
            Location mid2 = center.clone().add(
                    Math.cos(rad - 0.8) * orbitR * 0.65,
                    verticalWave * 0.3 + spiralLift,
                    Math.sin(rad - 0.8) * orbitR * 0.65);
            Location tailLoc = center.clone().add(
                    Math.cos(rad - 1.3) * orbitR * 0.4,
                    spiralLift,
                    Math.sin(rad - 1.3) * orbitR * 0.4);

            Vector dirToCenter = center.toVector().subtract(tipLocation.toVector()).normalize();

            head.setLocation(tipLocation);
            head.setRotation(new Vector(0, Math.atan2(-dirToCenter.getX(), dirToCenter.getZ()), 0));
            double s = 0.22 * currentScale;
            head.setScale(new Vector(s, s, s));
            head.updateAndDisplay();

            placeSegment(body1, mid1, tipLocation, 0.16 * currentScale, 0.16 * currentScale, 0.45 * currentScale);
            placeSegment(body2, mid2, mid1, 0.12 * currentScale, 0.12 * currentScale, 0.35 * currentScale);
            placeSegment(tail, tailLoc, mid2, 0.08 * currentScale, 0.08 * currentScale, 0.22 * currentScale);
        }

        private void placeSegment(GameObject obj, Location from, Location to, double sx, double sy, double sz) {
            Vector dir = to.toVector().subtract(from.toVector());
            double length = dir.length();
            if (length < 0.001) return;
            dir.normalize();
            double yaw = Math.atan2(-dir.getX(), dir.getZ());
            double pitch = Math.asin(Math.max(-1, Math.min(1, dir.getY())));
            Location midLoc = from.clone().add(to.toVector().subtract(from.toVector()).multiply(0.5));
            obj.setLocation(midLoc);
            obj.setRotation(new Vector(-pitch, yaw, 0));
            obj.setScale(new Vector(sx, sy, sz));
            obj.updateAndDisplay();
        }

        void scaleDown(double progress) {
            currentScale = Math.max(0, 1.0 - progress);
        }

        Location getTipLocation() {
            return tipLocation;
        }

        void destroy() {
            if (head != null) head.destroy();
            if (body1 != null) body1.destroy();
            if (body2 != null) body2.destroy();
            if (tail != null) tail.destroy();
        }
    }

    private static class DrillSegment {
        Location location;
        double angle;
        DrillSegment(Location location, double angle) {
            this.location = location;
            this.angle = angle;
        }
    }
}
