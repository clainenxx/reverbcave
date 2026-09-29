# Dynamic Surrounding

> **Status:** ✅ Berhasil di-build dan berjalan di Minecraft 1.21.5 (Fabric).
> Jar hasil build: `build/libs/dynamicsurrounding-1.0.0.jar`

Fabric mod for Minecraft **1.21.5** that adds a cave-like reverb to
in-game sounds whenever the player is underground / enclosed in a cave.

## How it works

1. `DynamicSurroundingClient` runs every client tick and checks whether the
   player currently has no sky light access (i.e. is underground or under a
   solid ceiling). It waits a short debounce period (10 ticks) before
   flipping state, so reverb doesn't flicker on/off right at cave entrances.
2. `ReverbManager` lazily creates a single OpenAL **EFX** auxiliary effect
   slot with a reverb effect tuned for a stone-cave feel (long decay,
   moderate diffusion). This uses LWJGL's `EXTEfx` bindings, which ship with
   Minecraft already, so no extra native libraries are needed — as long as
   the player's sound driver/OS actually exposes the `ALC_EXT_EFX` OpenAL
   extension (most do; a few Linux/OpenAL-soft setups may need
   `ALSOFT_DRIVERS`/config tweaks, and it silently no-ops if unsupported).
3. `ChannelMixin` hooks `com.mojang.blaze3d.audio.Channel#play()` and
   `#close()` (the class that owns each raw OpenAL source Minecraft plays
   sounds through) to attach/detach the reverb aux-send on every sound
   source while the player is in a cave, and detach everything the moment
   they leave.

This affects **every** sound source Minecraft plays (mobs, blocks,
footsteps, ambience, etc.) while underground — not just a specific
category — for a genuinely "surrounding" reverb effect.

## Project layout

```
dynamicsurrounding/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradle/wrapper/gradle-wrapper.properties
└── src/main/
    ├── java/com/example/dynamicsurrounding/
    │   ├── DynamicSurroundingClient.java   (cave detection + tick loop)
    │   ├── ReverbManager.java              (OpenAL EFX reverb slot)
    │   └── mixin/ChannelMixin.java         (hooks into MC's sound sources)
    └── resources/
        ├── fabric.mod.json
        └── dynamicsurrounding.mixins.json
```

## Building

Requires **JDK 21** and internet access for Gradle to pull dependencies.

```bash
# generate the wrapper jar (only needed once, if gradle isn't installed globally)
gradle wrapper --gradle-version 8.10

# build the mod
./gradlew build
```

The compiled mod jar will be at `build/libs/dynamicsurrounding-1.0.0.jar`.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.5.
2. Download/build [Fabric API](https://modrinth.com/mod/fabric-api) for
   1.21.5 and put it in your `mods` folder — this mod depends on it.
3. Drop `dynamicsurrounding-1.0.0.jar` into your `mods` folder.
4. Launch the game and walk into a cave — ambient sound should start
   sounding noticeably "wetter"/echoey.

## Tuning the reverb

Reverb parameters live in `ReverbManager.ensureEfx()` — e.g. increase
`AL_REVERB_DECAY_TIME` for a bigger/longer echo, or lower
`AL_REVERB_GAIN`/`AL_REVERB_LATE_REVERB_GAIN` if it feels too strong.

## Notes / limitations

- Cave detection is a heuristic (no sky light + below sea level, or a solid
  ceiling within 8 blocks). It's deliberately simple; feel free to refine it
  (e.g. factor in biome, or require enclosure on multiple sides) in
  `DynamicSurroundingClient#isPlayerInCave`.
- This was authored against the Yarn mappings for 1.21.5. If a future
  mappings/game update renames `com.mojang.blaze3d.audio.Channel` or its
  `pointer`/`play`/`close` members, update `ChannelMixin` accordingly.


## Credits
Made by **Geord**. GitHub: https://github.com/clainenxx
Licensed under MIT (see `LICENSE`).
