# Industrial cable networks

`CableConnections` owns the physical rules. A tray exposes exactly its two run
ends. Horizontal trays join only matching axes; vertical trays also need the
same cable-facing direction. Parallel side-by-side trays, perpendicular runs and
opposite-facing vertical trays do not connect. All channels retain their names;
there is no rotation-dependent channel swapping.

A Control Box is a source endpoint, never a traversable network node. Top/bottom tray
attachments require matching vertical cable-facing orientation. The rear port
accepts a horizontal tray running along the box's facing axis. Front and sides
are invalid. Every valid port contributes the same locally configured values,
but its connected network is independent of the other ports. Each face also has
four explicit individual-cable ports; see [control-box.md](control-box.md).

Individual segments use `IndividualCableTopology`'s existing same-color straight,
corner and local perpendicular connections. `CablePhysicalConnections` joins an
individual segment to a tray only at the matching color lane and run end. A cached
supplied elevated cable model aligns with the tray's 1.5-pixel recess. Regular
neighbors meet it through a compact rectangular step. Model selection changes
neither channel identity nor electrical routing. An adjacent
but misaligned segment has no electrical connection. Cable models and textures
are unchanged.

Each tray participates in four independent channel graphs. An individual segment
or sagging span participates only in its own color's graph. A Red link between
two trays therefore merges only Red; the other channels remain separate. Valid
same-color components sharing a host are connected. Control Box ports contribute
their configured channel values as source leaves, never as conductive edges
between other ports. This applies to tray, individual and port-to-port spans.
Sagging spans can also directly terminate at exposed tray lane ends. These join
only their own channel to the tray's graph; all other propagation rules are unchanged.

## Cached, event-driven state

`CableNetworks` owns one `CableNetworkManager` per server level. Networks map
tray/individual positions and span records to shared channel component state and
cache their source endpoints. It extends the existing manager; no parallel
individual-cable propagation manager or client topology copy exists.
Each channel's signal is the maximum of those sources, without attenuation.
Dial confirmations and restored settings refresh only attached components' cached
source values. They do not rediscover tray paths, issue neighbor storms, or alter
the Control Box comparator's local-only calculation.

Tray/individual placement, removal and orientation, span changes, and box changes invalidate
only affected components. A bounded discovery job rebuilds components and handles
splits/merges. Work is limited to 2,048 scan/discovery steps per processing pass;
pending jobs continue at server-tick end. Stable networks do no propagation or
scanning on ticks. Incomplete/invalidated components return disconnected/zero
rather than exposing partially aggregated or stale signals. Very large edits can
therefore take multiple passes before output devices receive a completed result.

Discovery uses only `getChunkNow`, never chunk-loading lookups. Load events queue
palette-filtered section scans, deferred until chunks are available as full chunks.
Only sections containing trays or individual hosts need cell scans. Span nodes
are indexed by both endpoint chunks; an unavailable endpoint breaks conduction.
Chunk unloads remove cached local
members, invalidate affected components, rebuild surviving fragments, and withdraw
unavailable sources. Reloads rediscover saved tray states and box values. Network
identities and topology are transient server data, not new block/entity NBT.
Level unload and server stop release the manager map.

## Future output-device API

On the server thread, obtain `CableNetworks.get(serverLevel)`. At a device's valid
adjacent tray position:

```java
var state = manager.read(cablePos, IndividualCableColor.RED);
int strength = state.signal(selectedChannel);
```

`NetworkState` is an immutable cached snapshot containing a transient network ID
and its channel strength in the existing packed format. `connected()` is false
while absent, unloaded or rebuilding. A device may compare channel IDs to detect
a changed connection. `read(span)` resolves a completed span's graph. The retained
`read(position)` API combines all local channel snapshots for existing callers;
its composite ID must not be used to infer whole-tray electrical connectivity.

For event-driven output, retain the `AutoCloseable` observation:

```java
var subscription = manager.observe(cablePos, IndividualCableColor.RED,
    state -> updateOutput(state.signal(ControlChannel.RED)));
```

The listener receives the current state and then changes in aggregate signal or
network identity. Close the subscription when the device disconnects/unloads or
is removed. Callbacks read only completed snapshots and may schedule ordinary
redstone output changes; they should not recursively poll or mutate large builds.
The original four-channel observation overload is retained. No output/terminal
block, redstone emitter or recipe is registered.

## Verification limits

In development runs, operators (permission level 2) can aim at a tray, individual
component or visible sagging span and run `/cid cable_signals`. Tray results show
all four channel graphs; individual/span results show the targeted color. Output
includes cached graph ID, strength, source availability, tray membership and
Control Box attachment. These are connected-graph flags, not a claim that the
target touches each device directly. Absent/rebuilding state reports unavailable.
Repeat the command after
edits to inspect the new values. The command is intentionally not registered in
production builds. No goggles, block entities, packet polling or client topology
copies are needed for this diagnostic.

This is the permitted development-command fallback: Create's goggles information
overlay requires a block entity, while trays have none and their network state is
server-only. A tray goggles overlay would require a custom client layer plus a
request/subscription packet path. That infrastructure is deferred; Control Box
goggles behaviour is unchanged. `CableNetworkInspection` formats actual cached
manager snapshots, including results from multiple sources. Diagnostic flags are
cached during discovery/source refresh rather than traversing paths for display.

GameTests cover all color-order rotations, straight-path compatibility, source
aggregation, multiple independent ports, source changes/removal, splits/merges,
observations, bounded-job interruption, unloaded reads and lifecycle-event handling.
Unified tests additionally exercise twelve ports in all facings, native cable
item interactions, typed spans, per-color tray bridges, both propagation directions,
local corners and loops, source isolation, removal, chunk lifecycle, legacy span
decoding and signal changes that do not invalidate render geometry.
Chunk lifecycle tests dispatch real NeoForge load/unload events against server
chunks; they do not replace manual disk save/reload and real chunk eviction tests.
Actual client appearance, multiplayer dial updates and large-build profiling
remain in-game verification tasks. Existing comparator, wrench, persistence,
Value Settings and dial-pose regression tests remain applicable.
