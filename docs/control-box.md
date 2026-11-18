# Control Box

Registry ID: `create_industrial_details:control_box`.

The original `cable_tray/control_box_closed` and `control_box_open` models are the
canonical assets. Only their texture references were corrected to
`create_industrial_details:block/cable_tray/control_box`; geometry, UVs, display
transforms, and texture pixels remain unchanged except for separating the dial
elements into partial models as described below. The two industrial-prefixed
parent-model wrappers are removed. Inventory and dropped items use the closed model.

## Door and selection

The closed enclosure retains its cached shape. The open state retains the
cabinet and the original eight broader cached door strips and fixed handle box,
recovered from the saved implementation before the diagonal hitbox refinement.
The supplied door rotates 22.5 degrees around Y at (18.0625, 8, 12.9375), from
(17.625, 0, 3.75) to (18.125, 16, 16.75). The handle shares this pivot. Shapes
include the parts extending beyond the normal block volume and rotate with facing.

A closed enclosure click opens the door. An open box closes only when a ray
intersects the actual rotated door or handle before reaching the cabinet.
Controls, cabinet surfaces and empty space do not close it. Exact ray tracing
is separate from the conservative collision strips. A client right-click hook
also finds doors extending into neighboring cells, respects nearer obstructions,
and sends the normal Minecraft block-use packet. This bounded, loaded-cell query
runs on interaction only, not every frame or idle tick. Wrenches retain priority.

## Native Create Value Settings

With the door open, look at one of the four dials and hold right-click. Create's
native Value Settings screen opens for that dial. Drag or scroll to an integer
from 0 to 15, then release to confirm. Short clicks no longer increment values,
and Shift-click no longer decrements them.

Four `BlockEntityBehaviour` subclasses implement the installed
`ValueSettingsBehaviour` contract, with unique behaviour types and packet IDs.
Each uses a model-positioned `ValueBoxTransform` and a one-row
`ValueSettingsBoard`. Create supplies hold detection, the screen, scrolling,
release-to-confirm behavior and packet transport. They do not inherit
`ScrollValueBehaviour`, so its renderer cannot draw permanent numerical boxes.
Server-side settings reject closed-door updates and wrench
interactions. No custom GUI or block entity ticker is used.

## Dial rendering

The open model's four Dial groups, each containing five cuboids, are extracted
unchanged into `control_box_dial_red`, `_yellow`, `_green` and `_blue` partial
models. The remaining 26 elements retain their exact geometry and UVs. Group
indices are remapped, and the closed model and texture are unchanged.

`ControlBoxRenderer` uses Create's SafeBlockEntityRenderer and Catnip CachedBuffers
with Flywheel PartialModel loading, including normal resource-reload handling.
It draws only the four dials while open, with no Flywheel backend exclusion.
There is no separate visual/ticker, interpolation state or new synchronization:
each render reads the existing four packed values. The matrix and normal-matrix
transforms for all sixteen settings and four facings are precomputed.

The model pivots in pixels are (5.125, 8.375, 4.25), (7.125, 8.375, 4.25),
(9.125, 8.375, 4.25), and (11.125, 8.375, 4.25). Rotation is perpendicular
to the panel around local Z. The angle is -135 + value * 18 degrees, clockwise
when viewed from the front. The original lighter top segment supplies a visible
orientation cue without new artwork. Lighting, overlay and the original texture
UVs are preserved; no stationary dial copy remains in the open model.
Inventory and dropped items still use the unchanged closed model.

The block entity extends Create's `SmartBlockEntity` to expose these behaviours,
but the block supplies no ticker. Initialization is driven by loading. The
existing `Signals` integer remains the
sole channel storage: red bits 0-3, yellow 4-7, green 8-11, blue 12-15. Settings
persist in world NBT and the `control_box_signals` item component. Survival drops
and wrench pickup preserve settings; replacement starts with the door closed.

Passive inspection uses Create's native goggles overlay. With goggles equipped,
targeting one dial on the open panel yields one compact channel/value line.
Cabinet surfaces, closed doors and other targets produce no information. The
overlay rebuilds its information for the current target rather than retaining
the previous channel's value. Goggles never gate adjustment through Value Settings.
The small client adapter applies Create's font-aware goggles spacing; targeting,
goggles eligibility and value selection remain testable without client classes.

## Comparator output

Vanilla comparators read the maximum of the four locally configured channel
values, regardless of facing or door state. No ordinary redstone power is emitted.
Analog neighbor notifications occur when that maximum changes, when restored
settings change it, on loading, and on removal/replacement. Changes below the
maximum do not issue analog notifications. Cable-port notifications skip vanilla
comparators so the back port cannot cause redundant comparator recalculation.
There is no ticker, polling or dependence on the cable network's aggregated values.

## Three-port source boundary

Top, bottom and back expose the same configured outputs. Back is opposite
`FACING`; front and both sides expose no output. `canConnectCable(Direction)`
identifies these three interfaces. `getSignal(ControlChannel, Direction)` reads
only the configured value, never input from other ports. Values are not relayed
between ports, and no network traversal through the box exists.

Changes notify the server-side cable network manager directly, independently of
comparator notifications. It caches source attachments and aggregates each
channel's maximum without relaying received values through the box. Loading,
rotation and removal update the affected topology. No neighbor notification
storm, client topology synchronization or continuously ticking simulation is used.
See [cable-networks.md](cable-networks.md) for physical connection rules and the
future output-device API.

## Twelve individual-cable ports

`ControlBoxPorts` defines four color-specific external regions on each existing
top, bottom and back face. The black squares are part of the supplied Main Body
texture, rather than separate model elements. Their centers are resolved through
that element's original face UVs. No artwork, model element or texture is changed.
Canonical SOUTH-facing model coordinates, in pixels, are:

| Face | X (Red / Yellow / Green / Blue) | Y | Z |
| --- | --- | --- | --- |
| Top | 4.25 / 6.75 / 9.25 / 11.75 | 16 | 2 |
| Bottom | 4.25 / 6.75 / 9.25 / 11.75 | 0 | 2 |
| Back | 4.25 / 6.75 / 9.25 / 11.75 | 2 | 0 |

The existing model rotation maps these positions into all four facings without
exchanging colors. Front and sides have no individual ports. Holding a matching
cable and normally clicking a port places an aligned adjacent straight segment,
with no host inside the box. The supplied elevated model is used where its
endpoint aligns with the port; no curved elevation ramp is generated. External port interactions work with the door closed
and take priority over opening it; wrench priority remains intact.

Shift-click a port with its matching cable item to start a sagging span, then
normally click a matching segment or another box port. Either endpoint type can
be selected first. Pending selection stores the exact face/facing identity and
the box instance token, so removal/replacement cannot silently reuse a stale
selection. Typed spans and their preview/render paths use the same physical
coordinates and an outward lead. Short leads remain aligned with the cable profile;
box leads extend only as necessary (up to one block) to clear the enclosure before
the existing sagging curve. The existing span distance, cost, permission
and refund rules apply. A span that would enter either enclosure is rejected.

All twelve ports read the existing four packed settings. They are source leaves
in the unified channel graphs: higher incoming signals never alter stored dials,
the comparator, or another otherwise disconnected port's network. Port span
packets reuse this block entity, with no new entity, ticker or renderer framework.

## Compatibility and verification

Native DeferredRegister aliases map `industrial_control_box` to `control_box` in
block, item and block entity registries. A data-component alias maps
`industrial_control_box_signals` to `control_box_signals`. Old saved blockstates,
block entity NBT and configured inventory items load into the canonical types.
There are no duplicate legacy blocks, items or Java implementations.

Server GameTests exercise canonical and legacy data, all facing directions,
the restored cached shapes, exact door rays, cabinet/control separation, independent
native Value Settings packet routing, bounds and persistence, three-port
exposure, targeted goggles eligibility, maximum-only comparator notifications,
placed-comparator lifecycle updates, and configured drops and wrench pickup. Client interaction, hover
legibility at different GUI scales, release/scroll behavior, out-of-block door
targeting and multiplayer timing still require in-game validation. No client
interaction or visual tests have been claimed as completed.
