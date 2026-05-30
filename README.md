# HelloyPack

A ProjectKorra ability addon pack by **Hihelloy** — 27 custom abilities spanning every bending element and subelement, built with block display entity visuals, Verlet-physics ropes, and multi-stage ability mechanics.

---

## Requirements

| Requirement | Version |
|---|---|
| Minecraft / Paper | **1.20.6 or higher** |
| ProjectKorra | **1.12.0** |
| Java | **17 or higher** |

> **Note:** The pack uses the Display Entity API (`BlockDisplay`, `TextDisplay`) and the 1.20.6 Particle API. It will not compile or run correctly on Paper versions below 1.20.6.

---

## Installation

1. Build with `mvn package` — the shaded jar will appear as `HelloyPack.jar` in `target/`.
2. Drop `HelloyPack.jar` into your server's `plugins/ProjectKorra/abilities` folder alongside other addon abilities.
3. Restart the server. Abilities register automatically — no `plugin.yml` needed (this is an addon pack, not a plugin).
4. Assign abilities to players with `/b b <AbilityName>` as usual.

---

## Abilities

### Fire

#### EmberStaff
Conjure a staff of living fire — sweep it to blast nearby foes, or charge and hurl a magma fireball.

| Input | Effect |
|---|---|
| Left Click | Flame sweep (hits all enemies within sweep radius) |
| Hold Sneak | Charge |
| Release Sneak (full charge) | Launch spinning magma fireball |
| Release Sneak (partial) | Short sweep |

---

#### AshVeil
Release a spreading veil of hot ash that blinds and poisons anyone who enters.

| Input | Effect |
|---|---|
| Left Click | Exhale ash veil at your feet |

---

#### PhoenixCoil
Summon a coil of living fire that orbits you and grows more powerful over time. Reaches Stage 2 after 5 seconds, Stage 3 after 10. Each stage changes the left-click attack.

| Input | Effect |
|---|---|
| Left Click (Stage 1) | Fire whip — lash the coil at a target |
| Left Click (Stage 2) | Seeking bolt — homes toward nearest enemy |
| Left Click (Stage 3) | Spiraling column drill — rotating fire cylinder |
| Sneak (any stage) | Detonate — radial burst, radius/damage scales with stage |

---

### Water

#### SeismicWhip
Crack an ice whip that shatters on impact, slowing and damaging targets.

| Input | Effect |
|---|---|
| Left Click | Crack the whip toward your crosshair |

---

#### CrystalNeedles
Crystallize water into a fan of ice needles that impale and slow targets.

| Input | Effect |
|---|---|
| Left Click | Fire crystal needles in a spread |

---

#### TidalPull
Shoot a water tendril that latches onto a target and violently slams them into the ground.

| Input | Effect |
|---|---|
| Left Click | Launch water tendril |

---

#### TorrentStep *(PvP)*
Fire a fast water bullet then dash through its impact point, leaving a freezing wake that slows enemies who walk through it. Low cooldown, high mobility — aim the bullet to choose where you end up.

| Input | Effect |
|---|---|
| Left Click | Fire bullet — dash to impact point automatically |
| Sneak (while bullet flying) | Recall bullet early and dash to its current position |

The icy wake lingers along the dash path for 2.5 seconds and slows enemies who cross it.

---

### Air

#### OrbitalStrike
Charge rotating air blades around yourself, then release them as projectiles in a forward fan.

| Input | Effect |
|---|---|
| Hold Sneak | Charge air blades |
| Release Sneak | Launch all blades in a spread |

---

#### Thunderclap
Wind up a mighty clap that releases a concussive air cone, launching and nauseating everything in its path.

| Input | Effect |
|---|---|
| Hold Sneak | Wind up (must hold full charge) |
| Release Sneak | Clap — cone of force forward |

---

### Earth

#### Chakram
Hurl a spinning stone disc that boomerangs back to you.

| Input | Effect |
|---|---|
| Left Click | Throw Chakram — returns after max range or hitting terrain |

---

#### IronMantle
Rip stone plates from the earth and orbit them around yourself as a spinning shield.

| Input | Effect |
|---|---|
| Sneak | Activate / deactivate orbiting stone mantle |

---

#### StoneGauntlet
Encase your fist in a stone gauntlet. Punch to deal massive knockback and trigger a ground shockwave.

| Input | Effect |
|---|---|
| Left Click | Equip gauntlet |
| Left Click (equipped) | Punch — knockback + shockwave on hit |

---

#### GroundPike
Erupt a towering stone spike beneath your target, launching them into the air.

| Input | Effect |
|---|---|
| Left Click | Strike the ground at your target location |

---

#### StoneDash *(PvP)*
Launch yourself diagonally forward on a stone pillar. Left-click while airborne to drive a spike straight down — massive damage if it lands on an enemy below. Landing always creates a small shockwave.

| Input | Effect |
|---|---|
| Left Click | Launch diagonally in look direction |
| Left Click (airborne, within window) | Drive stone spike downward |

---

### Lightning (Fire subelement)

#### LightningCoil
Charge a coil of lightning around your hand then fire a bolt that chains between nearby enemies.

| Input | Effect |
|---|---|
| Hold Sneak | Charge |
| Release Sneak | Fire bolt — chains up to 3 times |

---

### Combustion (Fire subelement)

#### CombustionRound
Charge a volatile combustion orb then launch it — detonates on impact with a powerful blast.

| Input | Effect |
|---|---|
| Hold Sneak | Charge orb |
| Release Sneak | Fire — hits walls or entities to explode |

---

### Ice (Water subelement)

#### FrostShards
Summon orbiting ice shards that build up around you, then fling them all in a wide spread.

| Input | Effect |
|---|---|
| Left Click | Activate — shards build up automatically |
| Left Click (fully armed) | Launch all shards |

---

### Blood (Water subelement)

#### BloodSnare
Seize the blood of a targeted entity, rooting them in place and draining their health over time.

| Input | Effect |
|---|---|
| Hold Sneak | Grab aimed target — roots and drains health |
| Release Sneak | Release |

---

### Healing (Water subelement)

#### MendingStream
Channel a glowing stream of healing water toward an ally, restoring their health over time.

| Input | Effect |
|---|---|
| Hold Sneak | Channel healing stream toward aimed ally |
| Release Sneak | Stop |

---

### Plant (Water subelement)

#### ThornWhip
Lash out a vine whip that snares and entangles enemies, rooting them briefly.

| Input | Effect |
|---|---|
| Left Click | Crack the vine whip at your target |

---

### Lava (Earth subelement)

#### LavaDisk
Hurl a spinning disk of molten rock that burns through anything in its path.

| Input | Effect |
|---|---|
| Left Click | Launch lava disk |

---

### Metal (Earth subelement)

#### MetalShot
Fire a dense metal bolt that pierces through multiple enemies before stopping.

| Input | Effect |
|---|---|
| Hold Sneak | Charge |
| Release Sneak | Fire — pierces up to 2 targets |

---

### Sand (Earth subelement)

#### SandVeil
Raise a swirling veil of sand around yourself that blinds and abrades all who enter.

| Input | Effect |
|---|---|
| Left Click | Raise the sand veil around your position |

---

### Flight (Air subelement)

#### GaleRing
Generate a powerful air ring beneath you — hold sneak to fly forward and repel nearby enemies.

| Input | Effect |
|---|---|
| Hold Sneak | Activate ring and fly in look direction |
| Release Sneak | Deactivate |

---

### Spiritual (Air subelement)

#### SpiritPulse
Release an expanding sphere of spirit energy that passes through walls and disorients all it touches.

| Input | Effect |
|---|---|
| Left Click | Emit spirit pulse from your position |

---

### Chi

#### GhostChain
Throw a chi-infused chain hook that binds and drags your target toward you.

| Input | Effect |
|---|---|
| Left Click | Throw chain |
| Left Click (while bound) | Pull target immediately |

---

#### VoidLasso
Hurl a chi lasso that snares a target in a glowing loop, pinning them in place.

| Input | Effect |
|---|---|
| Left Click | Throw lasso |

---

### Avatar

#### AvatarBurst
Channel all four elements into a triple-wave radial burst — each wave deals damage and applies a different elemental effect.

| Input | Effect |
|---|---|
| Hold Sneak | Channel for 3 seconds |
| Release Sneak (early) | Cancel |
| Full charge | Three expanding elemental waves |

---

## Configuration

All values live under `ExtraAbilities.Hihelloy` in ProjectKorra's `config.yml`. Defaults are written on first load.

```yaml
ExtraAbilities:
  Hihelloy:
    Fire:
      AshVeil:
        Cooldown: 12000
        TickDamage: 0.8
        MaxRadius: 6.0
        ExpandSpeed: 0.12
        Duration: 8000
        MaxLifetime: 14000
        BlindDuration: 40       # ticks
        PoisonDuration: 60      # ticks
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
        SlowDuration: 40        # ticks
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
        BulletSpeed: 2.0
        BulletRange: 20.0
        DashSpeed: 0.7
        WakeRadius: 1.2
        WakeSlow: 1
        WakeSlowDuration: 1000
        MaxLifetime: 6000
    Air:
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
        NauseaDuration: 80      # ticks
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
        LaunchStrength: 1.5
        SpikeDamage: 6.0
        SpikeRange: 12.0
        LandingRadius: 3.0
        LandingDamage: 2.5
        AirWindow: 1800
        MaxLifetime: 5000
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
        DamagePerTick: 0.75
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
        FireTicks: 100          # ticks
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
        BlindDuration: 30       # ticks
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
        DisorientDuration: 60   # ticks
    Chi:
      GhostChain:
        Cooldown: 8000
        Damage: 2.0
        ThrowRange: 18.0
        PullSpeed: 0.5
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
```

---

## Notes

- All **Cooldown**, **Duration**, and **MaxLifetime** values are in **milliseconds**.
- **BlindDuration**, **PoisonDuration**, **NauseaDuration**, **SlowDuration**, **FireTicks** are in **ticks** (20 ticks = 1 second).
- `MaxLifetime` is a hard failsafe — the ability removes itself after this duration regardless of state.
- Movement-stopping effects (BloodSnare, VoidLasso, ThornWhip, GhostChain) use ProjectKorra's `MovementHandler` and clean up correctly if the ability ends early.
