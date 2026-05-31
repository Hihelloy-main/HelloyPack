HelloyPack
by Hihelloy

A ProjectKorra ability addon pack containing 36 custom abilities spanning every bending element and subelement, built with block display entity visuals, Verlet-physics ropes, and multi-stage ability mechanics.


REQUIREMENTS

Minecraft / Paper: 1.20.6 or higher
ProjectKorra: 1.12.0
Java: 17 or higher

Note: The pack uses the Display Entity API and the 1.20.6 Particle API. It will not run correctly on Paper versions below 1.20.6.


INSTALLATION

1. Build with mvn package. The shaded jar will appear as HelloyPack.jar in the target/ folder.
2. Drop HelloyPack.jar into your server's plugins/ folder alongside ProjectKorra.jar.
3. Restart the server. Abilities register automatically. No plugin.yml is needed since this is an addon pack, not a standalone plugin.
4. Assign abilities to players with /b b <AbilityName> as usual.


WATER ABILITIES -- SOURCE REQUIREMENT

The following water abilities require either a water source block within 4 blocks or a water bottle in the player's inventory to activate:

CrystalNeedles
SeismicWhip
TidalPull
TorrentStep


ABILITIES


FIRE

EmberStaff
Conjure a staff of living fire. Sweep it to blast nearby foes or charge and hurl a magma fireball.
Left Click: Flame sweep hitting all enemies within sweep radius.
Hold Sneak: Charge.
Release Sneak (full charge): Launch spinning magma fireball.
Release Sneak (partial): Short sweep.

AshVeil
Release a spreading veil of hot ash that blinds and poisons anyone who enters.
Left Click: Exhale ash veil at your feet.

ArcFlare
Conjure three spinning fire arcs that orbit you. Each left click launches one as a fast fire streak.
Left Click: Activate -- three arcs orbit you.
Left Click again (up to 3 times): Launch each arc as a projectile.

PhoenixCoil
Summon a coil of living fire that orbits you and grows more powerful over time. Reaches Stage 2 after 5 seconds and Stage 3 after 10. Each stage changes the left-click attack.
Left Click (Stage 1): Fire whip, lash the coil at a target.
Left Click (Stage 2): Seeking bolt that homes toward the nearest enemy.
Left Click (Stage 3): Spiraling column drill, a rotating fire cylinder.
Sneak (any stage): Detonate the coil outward as a burst. Radius and damage scale with stage.


WATER

SeismicWhip
Crack an ice whip that shatters on impact, slowing and damaging targets. Requires water source or water bottle.
Left Click: Crack the whip toward your crosshair.

CrystalNeedles
Crystallize water into a fan of ice needles that impale and slow targets. Requires water source or water bottle.
Left Click: Fire crystal needles in a spread.

TidalPull
Shoot a water tendril that latches onto a target and violently slams them into the ground. Requires water source or water bottle.
Left Click: Launch water tendril.

TorrentStep (PvP)
Fire a fast water bullet then dash through its impact point, leaving a freezing wake that slows enemies who walk through it. Requires water source or water bottle.
Left Click: Fire bullet. You automatically dash to its impact point.
Sneak while bullet is flying: Recall the bullet early and dash to its current position instead.
The icy wake lingers along the dash path for 2.5 seconds.


AIR

OrbitalStrike
Charge rotating air blades around yourself then release them as projectiles in a forward fan.
Hold Sneak: Charge air blades.
Release Sneak: Launch all blades in a spread.

Thunderclap
Wind up a mighty clap that releases a concussive air cone, launching and nauseating everything in its path.
Hold Sneak: Wind up. Must hold for full charge.
Release Sneak: Clap, releasing a cone of force forward.

CycloneStep
Spin a shell of air rings around yourself that repels enemies while active, then release it as a concussive burst.
Hold Sneak: Maintain the cyclone shell.
Left Click while active: Release burst wave outward.
Release Sneak: Cancel.


EARTH

Chakram
Hurl a spinning stone disc that boomerangs back to you.
Left Click: Throw Chakram. Returns after reaching max range or hitting terrain.

IronMantle
Rip stone plates from the earth and orbit them around yourself as a spinning shield.
Sneak: Activate or deactivate the orbiting stone mantle.

StoneGauntlet
Encase your fist in a stone gauntlet. Punch to deal massive knockback and trigger a ground shockwave.
Left Click: Equip gauntlet.
Left Click (equipped): Punch, causing knockback and a shockwave on hit.

GroundPike
Erupt a towering stone spike beneath your target, launching them into the air.
Left Click: Strike the ground at your target location.

StoneDash (PvP)
Launch yourself diagonally forward. Left-click while airborne to drive a spike straight down. Deals massive damage if it lands on an enemy below. Landing always creates a small shockwave.
Left Click: Launch diagonally in your look direction.
Left Click (airborne, within time window): Drive stone spike downward.


LIGHTNING (Fire subelement)

LightningCoil
Charge a coil of lightning around your hand then fire a bolt that chains between nearby enemies.
Hold Sneak: Charge.
Release Sneak: Fire bolt. Chains up to 3 times.


COMBUSTION (Fire subelement)

CombustionRound
Charge a volatile combustion orb then launch it. Detonates on impact with a powerful blast.
Hold Sneak: Charge orb.
Release Sneak: Fire. Hits walls or entities to explode.


ICE (Water subelement)

FrostShards
Summon orbiting ice shards that build up around you then fling them all in a wide spread.
Left Click: Activate. Shards build up automatically.
Left Click (fully armed): Launch all shards.

FrostCage
Raise a cage of towering ice spires around a target location, trapping and slowly freezing anyone caught inside.
Left Click: Raise ice cage at your target location.


BLOOD (Water subelement)

BloodSnare
Seize the blood of a targeted entity, rooting them in place and draining their health over time.
Hold Sneak while aiming at a target: Root and drain health.
Release Sneak: Release.


HEALING (Water subelement)

MendingStream
Channel a glowing stream of healing water toward an ally, restoring their health over time.
Hold Sneak while aiming at an ally: Channel healing stream.
Release Sneak: Stop.


PLANT (Water subelement)

ThornWhip
Lash out a vine whip that snares and entangles enemies, rooting them briefly.
Left Click: Crack the vine whip at your target.


LAVA (Earth subelement)

LavaDisk
Hurl a spinning disk of molten rock that burns through anything in its path.
Left Click: Launch lava disk.

MagmaFist
Erupt a massive magma fist from the ground beneath your target, launching them skyward.
Left Click: Strike the ground at target location with a rising lava fist.


METAL (Earth subelement)

MetalShot
Fire a dense metal bolt that pierces through multiple enemies before stopping.
Hold Sneak: Charge.
Release Sneak: Fire. Pierces up to 2 targets.


SAND (Earth subelement)

SandVeil
Raise a swirling veil of sand around yourself that blinds and abrades all who enter.
Left Click: Raise the sand veil around your position.


FLIGHT (Air subelement)

GaleRing
Generate a powerful air ring beneath you. Hold sneak to fly forward and repel nearby enemies.
Hold Sneak: Activate ring and fly in your look direction.
Release Sneak: Deactivate.


SPIRITUAL (Air subelement)

SpiritPulse
Release an expanding sphere of spirit energy that passes through walls and disorients all it touches.
Left Click: Emit spirit pulse from your position.


CHI

GhostChain
Throw a chi-infused chain hook that binds and drags your target toward you.
Left Click: Throw chain.
Left Click (while bound): Pull target immediately.

VoidLasso
Hurl a chi lasso that snares a target in a glowing loop, pinning them in place.
Left Click: Throw lasso.


AVATAR

AvatarBurst
Channel all four elements into a triple-wave radial burst. Each wave deals damage and applies a different elemental effect.
Hold Sneak: Channel for 3 seconds.
Release Sneak (early): Cancel.
Full charge: Releases three expanding elemental waves.

VoidPortal
Manifest a disk of end portal energy with three modes: throw it as a returning boomerang, hold it as a contact shield, or stand on it to fly.
Left Click: Throw disk. Boomerang -- left click again to recall early.
Sneak: Hold disk as shield in front of you.
Double-Sneak: Stand on disk and fly in look direction.


CONFIGURATION

All values live under ExtraAbilities.Hihelloy in ProjectKorra's config.yml. Defaults are written automatically on first load. All Cooldown, Duration, and MaxLifetime values are in milliseconds. BlindDuration, PoisonDuration, NauseaDuration, SlowDuration, and FireTicks are in ticks (20 ticks = 1 second). MaxLifetime is a hard failsafe that removes the ability after the given time regardless of state.

ExtraAbilities:
  Hihelloy:
    Fire:
      ArcFlare:
        Cooldown: 7000
        Damage: 3.5
        FlareSpeed: 1.6
        FlareRange: 22.0
        MaxArcs: 3
        MaxLifetime: 10000
      AshVeil:
        Cooldown: 12000
        TickDamage: 0.8
        MaxRadius: 6.0
        ExpandSpeed: 0.12
        Duration: 8000
        MaxLifetime: 14000
        BlindDuration: 40
        PoisonDuration: 60
      EmberStaff:
        Cooldown: 8000
        SweepDamage: 2.5
        FireballDamage: 5.0
        FireballSpeed: 1.4
        SweepRadius: 3.0
        ChargeTime: 1500
        MaxLifetime: 15000
      PhoenixCoil:
        Cooldown: 18000
        WhipDamage: 4.0
        BoltDamage: 5.0
        ColumnDamage: 3.5
        BurstBaseDamage: 5.0
        BurstBaseRadius: 4.0
        Stage2Time: 5000
        Stage3Time: 10000
        MaxLifetime: 20000
    Water:
      CrystalNeedles:
        Cooldown: 5000
        Damage: 2.0
        NeedleCount: 7
        SpreadAngle: 20.0
        NeedleSpeed: 1.7
        NeedleRange: 22.0
        MaxLifetime: 5000
        SlowDuration: 40
      SeismicWhip:
        Cooldown: 6000
        Damage: 3.5
        Range: 12.0
        MaxLifetime: 6000
        SlowLevel: 1
      TidalPull:
        Cooldown: 7000
        SlamDamage: 5.0
        Range: 16.0
        Speed: 1.6
        SlamDuration: 1200
        MaxLifetime: 7000
      TorrentStep:
        Cooldown: 4500
        BulletDamage: 3.0
        BulletSpeed: 1.8
        BulletRange: 20.0
        WakeRadius: 1.2
        WakeSlow: 1
        WakeSlowDuration: 1000
        MaxLifetime: 6000
    Air:
      CycloneStep:
        Cooldown: 9000
        ContactDamage: 1.5
        BurstDamage: 4.0
        BurstRadius: 5.0
        SpinRadius: 1.2
        MaxDuration: 4000
        MaxLifetime: 8000
      OrbitalStrike:
        Cooldown: 9000
        Damage: 3.0
        ProjectileSpeed: 1.5
        ChargeRadius: 1.5
        MaxBlades: 5
        ChargeTime: 2500
        MaxLifetime: 12000
      Thunderclap:
        Cooldown: 10000
        Damage: 4.0
        ConeLength: 8.0
        ConeAngle: 35.0
        ChargeTime: 800
        MaxLifetime: 5000
        NauseaDuration: 80
    Earth:
      Chakram:
        Cooldown: 7000
        Damage: 4.0
        Range: 25.0
        ReturnSpeed: 1.2
        LaunchSpeed: 1.6
        MaxLifetime: 8000
      GroundPike:
        Cooldown: 8000
        Damage: 5.5
        PikeHeight: 5.0
        TargetRange: 14.0
        StrikeDelay: 400
        MaxLifetime: 8000
      IronMantle:
        Cooldown: 10000
        ContactDamage: 2.5
        OrbitRadius: 1.2
        Duration: 8000
        PlateCount: 4
        RotationSpeed: 6.0
        VerticalBob: 0.3
      StoneDash:
        Cooldown: 5000
        LaunchStrength: 1.4
        SpikeDamage: 6.0
        SpikeRange: 12.0
        LandingRadius: 3.0
        LandingDamage: 2.5
        AirWindow: 2000
        MaxLifetime: 6000
      StoneGauntlet:
        Cooldown: 9000
        PunchDamage: 6.0
        ShockwaveDamage: 3.0
        ShockwaveRadius: 4.5
        WearDuration: 12000
        MaxLifetime: 20000
    Lightning:
      LightningCoil:
        Cooldown: 7000
        Damage: 4.0
        ChainRange: 6.0
        ChainJumps: 3
        BoltSpeed: 2.2
        ChargeTime: 1200
        MaxLifetime: 8000
    Combustion:
      CombustionRound:
        Cooldown: 9000
        Damage: 7.0
        BlastRadius: 4.0
        ProjectileSpeed: 1.1
        ChargeTime: 1500
        MaxLifetime: 10000
    Ice:
      FrostCage:
        Cooldown: 11000
        TickDamage: 1.0
        CageRadius: 2.5
        SpireHeight: 4.0
        SpireCount: 8
        Duration: 4000
        MaxLifetime: 8000
      FrostShards:
        Cooldown: 6000
        Damage: 2.5
        ShardRange: 20.0
        OrbitRadius: 0.9
        MaxShards: 6
        BuildInterval: 400
        MaxLifetime: 12000
    Blood:
      BloodSnare:
        Cooldown: 10000
        DamagePerTick: 2.0
        TickInterval: 800
        MaxDuration: 5000
        SelectRange: 15.0
        MaxLifetime: 10000
    Healing:
      MendingStream:
        Cooldown: 8000
        HealPerTick: 1.0
        HealTickInterval: 600
        SelectRange: 12.0
        MaxDuration: 6000
        MaxLifetime: 10000
    Plant:
      ThornWhip:
        Cooldown: 5000
        Damage: 3.0
        Range: 14.0
        EntangleDuration: 2500
        MaxLifetime: 6000
    Lava:
      LavaDisk:
        Cooldown: 6000
        Damage: 4.5
        Range: 22.0
        Speed: 1.3
        FireTicks: 100
        MaxLifetime: 6000
      MagmaFist:
        Cooldown: 9000
        Damage: 7.0
        TargetRange: 12.0
        StrikeDelay: 600
        FistHeight: 3.5
        LaunchStrength: 1.2
        MaxLifetime: 6000
    Metal:
      MetalShot:
        Cooldown: 4000
        Damage: 5.0
        PierceCount: 2
        Speed: 2.5
        Range: 28.0
        ChargeTime: 600
        MaxLifetime: 6000
    Sand:
      SandVeil:
        Cooldown: 9000
        Radius: 4.0
        Duration: 5000
        BlindDuration: 30
        AbrasionDamage: 0.5
        MaxLifetime: 12000
    Flight:
      GaleRing:
        Cooldown: 8000
        ThrustStrength: 0.6
        RepelRadius: 4.0
        RepelStrength: 0.9
        MaxDuration: 4000
        MaxLifetime: 8000
    Spiritual:
      SpiritPulse:
        Cooldown: 7000
        Damage: 2.0
        MaxRadius: 10.0
        ExpandSpeed: 0.5
        MaxLifetime: 5000
        DisorientDuration: 60
    Chi:
      GhostChain:
        Cooldown: 8000
        Damage: 2.0
        ThrowRange: 18.0
        PullSpeed: 0.55
        BindDuration: 3000
        MaxLifetime: 6000
      VoidLasso:
        Cooldown: 9000
        Damage: 1.5
        ThrowSpeed: 2.0
        Range: 20.0
        PinDuration: 3500
        MaxLifetime: 8000
    Avatar:
      AvatarBurst:
        Cooldown: 20000
        Damage: 6.0
        BurstRadius: 10.0
        ChargeTime: 3000
        MaxLifetime: 15000
      VoidPortal:
        Cooldown: 12000
        ThrowDamage: 6.0
        ThrowRange: 24.0
        ThrowSpeed: 1.5
        ShieldDamage: 3.0
        FlySpeed: 0.55
        MaxLifetime: 25000
