# MineniteClient

One modded client, any server.

NeoForge assumes the client and the server load the same mods. That assumption
breaks in two ordinary situations: a network whose servers run different mod
sets, and a vanilla server you want to join without swapping your install.

This is a **client-side** mod. Servers are unaffected, and a server running it
gains nothing — everything here is about what the client is willing to accept.

## What it fixes

Three separate failures, each at a different stage of connecting:

### 1. Refused before connecting

> You are trying to connect to a server that is not running NeoForge, but you
> have mods that require it. A connection could not be established.

Any client mod declaring a network channel it needs on both sides fails
negotiation against a server that lacks it. The check is overridden, so the
connection proceeds.

### 2. Connected, then dropped during registry sync

```
Missing element ResourceKey[minecraft:trim_material / biomesoplenty:glowworm_silk]
  at Item$Properties.lambda$delayedHolderComponent$0
  at RegistryDataCollector.collectGameRegistries
```

Items carry references into datapack registries — armor trim materials, jukebox
songs — resolved against what the server sent. A server without the mod never
sends them, so the lookup throws and the connection dies at
`finish_configuration`, before the world appears.

The failing component initializer is skipped rather than the whole sync. The item
survives without that component, which is correct: the mod providing the data is
not loaded on that server either.

### 3. Playing, then kicked on a creative item

```
Internal Exception: io.netty.handler.codec.DecoderException:
Failed to decode packet 'serverbound/minecraft:set_creative_mode_slot'
```

Items cross the wire as numeric registry ids. A server without the mod has no
matching id, cannot read the packet, and drops the connection — for taking a
block out of your own creative menu. The packet is no longer sent, so nothing
appears in the slot and you stay connected.

Scoped to non-vanilla items on a non-NeoForge connection; on a NeoForge server
registries are synced and modded items send normally.

## What it does not do

- **It does not add missing content.** A mod the server lacks is still absent
  there. Its blocks do not exist, its recipes do not run, its machines are not
  there to interact with.
- **It does not fix server-side mod mismatch.** Servers still need whichever
  mods their own content depends on.
- **It gives up a useful warning.** The negotiation check exists to turn "this
  server lacks a mod you need" into one clear message. Without it, a mod that
  loses its server side fails when it *sends* rather than when you connect, so a
  dead feature looks like a bug rather than an unmet requirement. Each occurrence
  is logged at INFO with the channel names.

Nothing here risks world or inventory data. The content it steps around is absent
from that server regardless.

## Building

```
./gradlew jar
```

Output in `build/libs/`. Drop it in your client's `mods/` folder alongside
NeoForge 26.2.

## Why a mod and not a NeoForge fork

The change is three mixins. A fork would hold the same logic while requiring a
rebase on every NeoForge release and a custom loader install for every player.
Mixins can target NeoForge's own classes — one of these does — so a fork buys no
capability that matters here.

## Related

Built alongside [CardForge](https://github.com/icedmoca/cardforge), which
implements Velocity modern forwarding for NeoForge servers. `docs/PROXY.md`
there covers the server side of running a network like this.
