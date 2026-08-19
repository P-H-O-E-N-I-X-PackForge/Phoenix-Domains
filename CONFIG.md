# Configuration

Phoenix Domains registers its config as a Forge **SERVER**-type config
(`ModConfig.Type.SERVER`, see `config/DomainsConfig.java`), which means it is
**per-world / per-server**, not global.

- **Singleplayer world**: `saves/<world name>/serverconfig/phoenix_domains-server.toml`
- **Dedicated server**: `<server root>/world/serverconfig/phoenix_domains-server.toml`
  (or whichever folder your `level-name` points at)

The file is generated the first time the world/server is loaded with this mod
installed. Editing it while the world is running requires a relog/restart to
take effect (Forge does not hot-reload `SERVER` configs).

## Global overrides (for modpack developers)

The per-world file above isn't distributed with a modpack — it doesn't exist
until a world/server first loads it, and every world gets its own independent
copy. If you're a pack developer and want to force certain values everywhere
the pack is deployed, without depending on what a given world's own
`serverconfig` file happens to contain, ship an override file in the pack's
own **global** `config/` folder instead:

- **Path**: `config/phoenix_domains-server-overrides.toml`
  (same base name as the per-world file, `phoenix_domains-server.toml`, with
  `-overrides` inserted — so you can copy keys straight out of a generated
  per-world file into this one)

**The file is auto-generated the first time it's missing.** If
`config/phoenix_domains-server-overrides.toml` doesn't exist when the mod
loads, it's created on the spot with every overridable key present, in the
same nested-table layout shown below, each set to its current shipped
default — a complete, browsable file rather than an empty stub, with a
header comment explaining what it is.

A value in this file only becomes an **active override** once you edit it
away from its shipped default. Every other key — anything still equal to
its current default, which includes every key the first time the file is
generated — is treated as "not set" and simply falls through to normal
per-world behavior (the value in that world's own `serverconfig` file, or
the built-in default if that world hasn't set it). This also means the file
stays forward-compatible across mod updates: if this mod's own default for
some key changes later, an entry you never touched (still equal to the
*old* default) is quietly ignored rather than permanently freezing that
field at a stale value — only genuinely-edited entries count as real
overrides.

Active overrides are re-applied every time any world/server loads or
reloads `phoenix_domains-server.toml`, so they take effect consistently
across every world the pack is used in — including worlds created before
the override file was added.

Overrides only ever affect the in-memory config for the running
session/world; they are never written back into that world's own
`serverconfig/phoenix_domains-server.toml`, so removing an override (either
deleting the whole file, or just editing a key back to its default) simply
reverts that world to its own stored value (or the default) again.

If a key in the overrides file doesn't match any real config path (e.g. a
typo), it's logged as a warning and otherwise ignored — it won't crash world
load. If the file can't be auto-generated (e.g. a permissions issue), that's
also just a logged warning — it won't crash mod or world load.

Example — the generated file starts with every key present at its default
(elided below for brevity); a pack dev who wants a hard chunk cap and a
slower claim-power accrual rate pack-wide edits just those two lines, and
leaves every other key as-is (still at default, so still inert):

```toml
# phoenix_domains-server-overrides.toml
#
# Every value below is currently set to its shipped DEFAULT. ...
# Editing a value here and saving is what makes it an ACTIVE override: ...

[claim_power]
    base = 500
    perHour = 1.0          # <- edited away from the default (4.0): now an active override
    maxAccrual = 500.0

[chunkload_power]
    base = 100
    perHour = 0.5
    maxAccrual = 50.0

[restrictions]
    maxClaimDistanceChunks = 32
    maxClaimedChunksPerOwner = 200  # <- edited away from the default (0): now an active override
    maxForceloadedChunksPerOwner = 0
```
