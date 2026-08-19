# Phoenix Domains — KubeJS integration

Domains registers a KubeJS plugin (`net.phoenixvine.domains.integration.kubejs.DomainsKubeJSPlugin`,
via `kubejs.plugins.txt`) that's only ever loaded if KubeJS itself is installed — Domains works
identically without it.

## Script type: **server only**

Everything Domains exposes is server-side. Claims, ownership, and power pools are stored per-world
on the server (`DomainManager`, keyed off `server.overworld()`), and every `DomainAPI` method takes
a `MinecraftServer`/`ServerPlayer`/`ServerLevel` — none of that exists on a client. Every binding
and class below is only usable from **`kubejs/server_scripts`**.

- Putting these calls in `client_scripts` will fail — there's no client-side equivalent of any of
  this (contrast with Solaris, which is the opposite: client-only).
- There is nothing to register in `startup_scripts` — Domains doesn't define blocks, items,
  recipes, or anything else startup scripts are for.

## Bindings (global names available in scripts)

| Global name | Class | Purpose |
|---|---|---|
| `DomainAPI` | `net.phoenixvine.domains.api.DomainAPI` | Everything below — feature gates, claim queries/actions, power grants |
| `ClaimFlag` | `net.phoenixvine.domains.data.ClaimFlag` | Enum of per-claim environmental toggles (see below) |
| `DomainFeatureState` | `net.phoenixvine.domains.api.DomainFeatureState` | Enum: `DISABLED`, `VISIBLE`, `ENABLED` |

Also allowed (for type/return-value access, not bound as a top-level global):
`net.phoenixvine.domains.data.ChunkKey`, `Claim`, `ClaimPower`,
`net.phoenixvine.domains.ownership.ClaimPermissions`, `DomainOwnership`.

## Feature gating

Same layered model as Solaris's `SolarisAPI` — gate, then tier requirement, checked in that order
by `DomainAPI.isFeatureEnabled(id, dimension)`:

```js
// Server-driven gate
DomainAPI.setFeatureEnabled('claiming', false)   // disable claiming everywhere

// Per-dimension tier requirement
DomainAPI.setTier(dimension, 3)
DomainAPI.requireTier('chunkloading', dimension, 3)   // chunkloading needs tier 3+ in this dimension

// Explicit per-dimension state
DomainAPI.setFeatureState('claim_flags', dimension, DomainFeatureState.DISABLED)
```

Built-in feature ids (constants on `DomainAPI`, e.g. `DomainAPI.FEATURE_CLAIMING`):

| Feature id | Gates | Notes |
|---|---|---|
| `claiming` | `DomainAPI.claim(player, key)` | Returns `"feature_disabled"` if blocked |
| `chunkloading` | `DomainAPI.setChunkloaded(player, key, true)` | Only gates turning it **on** — turning it off always works, so a disabled feature can't trap an owner with a claim they can't stop force-loading |
| `claim_flags` | `DomainAPI.setFlag(player, key, flag, value)` | Returns `"feature_disabled"` if blocked |

`DomainAPI.unclaim(player, key)` is **not** gated by `claiming` — disabling the ability to make new
claims shouldn't also trap players in claims they can't remove.

You can register your own feature id and gate it the same way — an unknown id just logs a
debug-level note (`PhoenixDomains.LOGGER.debug`), it never blocks anything.

## Claim queries

```js
DomainAPI.getOwner(server, key)                        // Optional<UUID>
DomainAPI.isClaimed(server, key)                        // boolean
DomainAPI.canInteract(player, blockPos)                 // true if allowed to build/break here (also true if unclaimed)
DomainAPI.getAvailableClaimBlocks(server, token)
DomainAPI.getAvailableChunkloadBlocks(server, token)
```

`token` throughout is an "owner token" — a guild's UUID if the player is in a Phoenix Guild,
otherwise their own player UUID (`DomainOwnership.tokenFor(playerUuid)`).

## Claim actions

All return a result key string: `"ok"` on success, or an error key for the caller to translate/display
(`"already_claimed"`, `"too_far"`, `"too_many_claims"`, `"no_power"`, `"not_claimed"`,
`"no_permission"`, `"no_change"`, `"too_many_forceloaded"`, `"no_chunkload_power"`,
`"dimension_not_loaded"`, `"feature_disabled"`):

```js
DomainAPI.claim(player, key)
DomainAPI.unclaim(player, key)
DomainAPI.setChunkloaded(player, key, true)
DomainAPI.setFlag(player, key, ClaimFlag.PVP, false)
```

`ChunkKey` is a record: `new ChunkKey(dimension, chunkX, chunkZ)`, or build one from a block
position with `ChunkKey.of(level, blockX, blockZ)`.

## Admin / external grants

Bypass distance/power/permission checks entirely — for reward systems, quest completions, etc.
Each is a no-op if the server isn't running or arguments are null:

```js
DomainAPI.grantClaimPower(token, 500)
DomainAPI.grantChunkloadPower(token, 100)
DomainAPI.adminSetClaim(server, key, ownerUuid)       // force-claim, unclaiming any existing owner first
DomainAPI.adminRemoveClaim(server, key)               // force-unclaim regardless of owner/permission
DomainAPI.adminSetChunkloaded(server, key, true)      // force chunkload state, bypassing power/permission
```

## `ClaimFlag` values

Environmental/relationship toggles an owner can set per claim (rank-gated build/interact access is
**not** a flag — that's resolved from guild rank via `ClaimPermissions`):

`MOB_GRIEFING`, `EXPLOSIONS`, `FIRE_SPREAD`, `FLUID_FLOW`, `HOSTILE_SPAWNING`, `PASSIVE_SPAWNING`,
`PVP`, `ALLY_BUILD`, `ALLY_INTERACT`, `ALLY_CONTAINERS` (see `ClaimFlag.java` for each one's
mod-wide default).
