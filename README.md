# MHC Decay Patch

A Forge 1.20.1 addon for **My Hero Craft (MHC)** that fixes the lag from Shigaraki's Decay moves without
gutting the visuals. It works when installed on only the server, only the client, or both.

## What it fixes

**Cheat Area**
- The block-spreading wave no longer floods the solid ground *below* the caster (a half-sphere of stone it never
  breaks); it also caches protection checks and uses compact position sets. Same wave, same blocks broken.
- Optional per-tick budget so huge casts spread over a few extra ticks instead of freezing the server.
- Caps the extra "falling cube" debris MHC spawns past the 256 nearest crumbling blocks (nearest first).

**Decay eruption effect** (the purple ground effect under Cheat Area / Ground Hack)
- Terrain scan rewritten (same result), spread across frames/ticks with a time budget.
- Scorch cracks are built once per rescan and kept on the GPU instead of rebuilt every frame.
- Floating block debris is now lit by where it actually is (no more fullbright chunks in dark caves).

**Ground Hack**
- Strong casts spawned one flying block entity per surface block (thousands). Now full density near the caster,
  and bigger chunks on a sparser grid further out, covering the same ground. Small casts are unchanged.

**MHC bug fix**
- Every mob death added junk Twin Impact / Magnetism mark entries to every entity within 900 blocks, forever.
  This bloats saves, is re-synced every tick, and eventually kicks you with
  `Tried to read NBT tag that was too big`. Fixed, and already-bloated saves clean themselves on load.

Everything is configurable (and can be switched off) in `config/mhc_decay_patch-common.toml` and
`config/mhc_decay_patch-client.toml`.

## Building

MHC is not open source, so its jar is not included. Copy your own copies into `libs/`:

- `My Hero Craft-31.0.0.jar` → `libs/mhc-31.0.0.jar`
- `geckolib-forge-1.20.1-4.8.4.jar` → `libs/geckolib-4.8.4.jar`

Then run `./gradlew build`. The mod jar ends up in `build/libs/`.
