# Individual Control Cables

Items: Red, Yellow, Green and Blue Control Cable, beside the tray/control box in
the existing creative tab. The hidden `individual_control_cable` block has no
separate item. No recipes or Terminal Box are added.

## Straight segments

Normal right-click places one segment. Clicking an existing host with another
color adds it without changing existing routes. A matching color can add one
compatible perpendicular component. Identical orientations and occupied solid
positions are rejected. Floor/ceiling clicks follow the player's cardinal facing;
side clicks place vertical cables against that wall face. Straight-run placement remains manual.

All four supplied models remain canonical. Only their unresolved `block/cable_tray`
texture references were corrected to the existing mod texture. Their cuboids are
1.5 pixels wide, 1 pixel high, Y=0..1 and Z=0..16. Red, Yellow, Green and Blue start
at X=3.5, 6, 8.5 and 11 respectively. Cached selection shapes follow the actual
multipart X/Z/Y rotations. Cables are collision-free.

Each color keeps its signed primary route property (28,561 possible host states).
One optional secondary route and a separate endpoint identity per color live in
the same non-ticking host block entity, without new blockstate combinations. Red/Yellow properties,
item IDs, serialized color numbers and saved span format remain unchanged. Old
states lacking Green/Blue load with those colors absent; old two-token entities
preserve their Red/Yellow identities.

Normal wrench use rotates the targeted unspanned color only. Perpendicular floor
profiles that would physically intersect are rejected. Shift-wrench on an anchor
removes one attached span first, then that segment once no spans remain. Other
colors survive. Ordinary breaking drops each segment and refunds incident span
material once. Creative removal creates no refunds.

## Sagging spans and previews

Place same-colored segments, use matching external Control Box ports, or target
an exposed colored lane at a tray's run end. Hold that color's cable item and
sneak-right-click the first, then normal-right-click the second. Success costs `ceil(block distance)`
items from the held stack in Survival; Creative placement is free. Maximum block
distance is Strut's installed 30-block limit. Both anchors must be loaded, distinct,
contain the selected color (or expose its port) and permit interaction. Host-only
span restrictions remain unchanged. Port spans are rejected if their curve enters
either endpoint enclosure; the action bar explains the obstruction.

Attachments use the actual cross-section center and the longitudinal end facing
the peer. Tied axial directions choose opposing ends deterministically. Rendering,
targeting and previews share these coordinates. Rendered spans omit end caps that
would duplicate stationary anchor end faces. Each rectangular profile has a short
axial lead so its end plane meets the source model squarely. Wall-facing profile
roll is shared by rendering and ray targeting. Port attachments use the explicit
rotated black-square center and an outward lead before the normal Strut curve.
Enclosure clearance extends a box lead only when required, from 0.25 to 1 block.
Preview, rendered mesh, outlines and authoritative ray targeting share this path.
Strut sag retains separate color
lanes, capped at 9% for short spans to prevent excessive droop/backtracking.

A pending selection displays a translucent cable using its real source sprite,
UVs and profile. It follows the crosshair and snaps to matching anchors. Invalid
or duplicate targets and insufficient held material tint the ghost red. Geometry
is cached until its endpoints change; free aim is quantized to 1/32 block. Success,
explicit cancellation and switching items clear the preview. Missing/unloaded or
replaced starts cancel through the existing pending-item inventory callback.

Click the selected start again to cancel, or switch away. Invalid second targets
keep a valid start for retry and show an action-bar reason. Only the server mutates
selection, charges items and creates records; NeoForge's pre-block item phase
handles sneak clicks before ordinary block interactions. Server permission checks
remain authoritative even when a client preview appears valid.

Placement and completed spans broadcast one subtle existing metal placement
sound. Selecting an endpoint, cancelling and rejected placements play no success
sound. Existing wrench removal sounds remain intact.

## Targeting and removal

With a wrench in the main hand, point at any visible part of a sagging cable.
A thin outline identifies the nearest cable. Shift-right-click removes only that
span, preserving its anchors and all other colors, and refunds its recorded cost
in Survival. Normal interaction reach and solid-block line of sight apply.

A custom removal payload carries the stable color/endpoint/route/port key. The
server independently retraces the player's view, checks the exact nearest record,
wrench, sneak state, both loaded endpoints, build permissions and break-event
protections. Duplicate/replayed requests cannot refund a removed record twice.
Both anchors must currently be loaded for direct removal; chunks are never loaded
just to accept a request. Existing endpoint wrench removal remains available.

The spatial index stores conservative bounds by 16-block section. Queries touch
only sections crossed by the short interaction ray, with a bounded 256-entry LRU
for detailed curve geometry. It never scans every world span per mouse movement.
Client index ownership follows normal endpoint packets and chunk unloading.

## Automatic bends

A bounded 26-neighbor check identifies matching-color perpendicular end profiles
within 0.1 block. It never connects nearby cable midsections or unrelated colors.
The missing outside corner is computed from the endpoint envelope after subtracting
both original straight profiles. Only the remaining small cuboids are rendered,
using the original cable profile and texture, cached with the host render geometry.
Neighbor changes invalidate the cache without ticking or extra blockstates.
Signed floor and wall variants rotate around the block center. Legacy X/Y/Z
values retain their original transforms and serialized ordinal values.

## Storage, Strut and signals

Dimension SavedData `cid_individual_cable_spans` stores each color-keyed span once
and indexes both endpoints. Existing endpoint packets synchronize visible records;
unloaded peers never restore stale duplicated copies. Save/load validation checks
loaded blockstates rather than prematurely rejecting still-loading entities.
Typed endpoints distinguish a host route from a Control Box port (face and saved
facing, with color on the span). Optional port fields extend the record while
absent fields decode existing host-only records unchanged. Port packet records
and host packet records feed the same client span index and renderer cache.
Rotating/removing a box safely removes its incident port spans; Survival refunds
each record once and Creative removal produces no refund. Source dial changes
update electrical values without regenerating span geometry.

Strut Your Stuff 1.2.1 supplies the native sampling, mesh and outline utilities.
Its complete baker is private and its sampler is client-only, so the server uses
matching parabolic sampling solely for ray validation. JVM tests compare these
points against the installed Strut sampler. There is no rope physics or ticking.

`IndividualCableTopology.links(level, position, color)` exposes loaded straight,
span and physically valid bend links. The existing `CableNetworkManager` now uses
these links together with matching tray lane contacts and typed port sources.
Each color carries only its channel, without attenuation; multiple sources use
their maximum. No graph traverses a Control Box from one port to another.
Electrical topology is transient and cached, with bounded event-driven rebuilds
on actual changes. There is no ticker or separate individual network manager.
A future Terminal/Output Box can observe a channel through this shared API and
convert it to vanilla redstone; that block is not implemented here. See
[cable-networks.md](cable-networks.md) for reads, observations and diagnostics.

## Verification

`CableEndpoint` also identifies tray lanes by saved tray axis, facing and exposed
run end. Only matching colored end faces can be selected; tray side walls,
occupied run ends and unloaded neighboring cells are not anchors. Segment, box
and tray endpoints work in either placement order. The same maximum distance,
item cost, permission checks, cancellation and direct removal rules apply.

Connection-specific straight geometry uses the supplied `cable_*_elevated` models
(Y=1.5..2.5 instead of Y=0..1). `CableConnectionGeometry` selects a variant per
color and signed route only when that model's endpoint aligns with a directly
connected tray lane or box port. Ordinary runs remain regular. This local rule
has no neighbor feedback or saved height flags, so removing a destination restores
the regular model and cannot leave a self-sustaining elevated run.

Regular/elevated neighbors use a compact rectangular step at their shared end.
Existing component volumes are subtracted, and source-face UV cropping preserves
texel density. Perpendicular corner infill reuses the established corner math
with the selected model bounds. There are no curved straight-cable ramps or
procedurally raised copies of the regular model. The existing cached baked-model
wrapper selects the actual asset; secondary components retain the existing host
renderer. Selection, Pick Block and sagging endpoints follow the same variant.

Optional `FromOffset`/`ToOffset` fields persist any adjusted host attachment on a
span. They default to zero in old records and refresh only on physical endpoint
changes. Stable span keys still use color, positions, routes and explicit port
identities, so geometry changes cannot bypass duplicate detection or change
removal identity. Preview, real mesh and curve targeting consume these same
resolved endpoints, including when the peer is unloaded.

Tray-owned spans use chunk-scoped visual snapshots on changes and chunk watch.
They feed the existing client spatial index; no tray block entity or electrical
topology packet is introduced. A bounded, frustum-culled rendering pass reuses
the same mesh baker and caches stationary geometry, with event-driven cleanup.
Tray rotation, removal or closing an exposed run end removes only dependent spans
and refunds their material once under the existing rules.

GameTests exercise native item/block dispatch, four-color co-occupancy, independent
removal, costs, sounds, span records, attachment ends, nearest-curve targeting,
server removal validation, replay rejection, obstruction, cache ownership, bends,
legacy data and pending-state cleanup. Existing Cable Tray, Control Box, comparator
and Wooden Beam regressions also run.

JVM checks bake all four actual JSON models, compare all three rotated bounds to
selection geometry, verify profile width/UVs/end caps, and compare native Strut
sampling with server targeting. These do not render a game frame.

Manual client checks remain necessary for ghost opacity and placement feedback,
actual mouse targeting/outline clarity, seams and bends, placement sound balance,
multiplayer timing, real chunk eviction/disk reload, and large-build performance.

Pick Block uses the existing component-aware NeoForge clone hook for straight
cables. Direct span picking uses the same bounded curve trace and vanilla Creative
or Survival inventory selection behavior. No hidden host item is returned.

## Perpendicular components and corrected corners

A color may contain one horizontal and one vertical component, with full signed
cardinal orientation retained. Only physically meeting end profiles are accepted;
parallel duplicates, two floor runs, two wall runs and arbitrary crossings are
rejected. All four colors may independently have these pairs in the same host.
The extra route is a four-byte `SecondaryRoutes` array and has per-component
identities in `SecondaryTokens`. Missing fields in old saves mean no secondary
components. The existing primary blockstate, tokens and span NBT format remain.

Normal placement adds the requested perpendicular component for one item and one
placement sound. Selection, Pick Block, wrench rotation and removal identify the
actual physical route. Wrench removal preserves the previous rule: attached spans
on that targeted route are removed first; sibling components and their spans are
untouched. Removing a primary promotes its surviving secondary without changing
its endpoint identity. Normal breaking accounts for every physical piece.

Span selection stores the targeted route alongside the existing pending position
and token. Source and destination routes already present in saved span records are
now checked against both host components. Distinct component pairs can have their
own same-color spans; exact duplicates remain rejected. Removal request keys and
ray tie-breaking include these routes. No electrical propagation was introduced.

Extra component geometry is cached from the original cardinal model quads, with
the intersection occupied by the primary subtracted to avoid duplicate surfaces.
Within-host L profiles already meet; no additional connector is inserted there.
Neighboring outside corners use the intersection of each straight profile's axial
extension, minus the original pieces. This fills only the missing outside corner,
not the center-inflated region that previously protruded on the inner side.

Connector and cropped-component faces keep affine UV coordinates from the actual
rotated source quads, proportional to world dimensions. Full-length textures are
no longer squeezed onto tiny boxes. Small extensions wrap within the same source
face strip, preserving color and texel density. No supplied model or texture was
changed. Blockstate permutations and block entity ticker counts remain unchanged.

The four component GameTests cover native additions in every cardinal direction,
all colors, persistence and synchronization, Pick Block, individual refunds,
component-bound spans, promotion, outside-corner bounds and rejected combinations.
JVM baking checks compare UV gradients per physical distance on all source faces.
Client seams, texture phase across turns and interactive multiplayer timing still
need manual in-game verification.

## Placement fallback

A placement-only resolver preserves valid defaults and duplicate rejection. When
the requested orientation fails existing geometry checks, it evaluates supported
candidates with the same horizontal/vertical role, scores actual same-color end
connections in the host and its 26 immediate neighbors, then breaks ties by
quarter-turn distance and stable route order. No existing component is changed.
Wall candidates retain the clicked mounting plane. Four appended wall variants
use the canonical model with X=90-degree rotation (plus cardinal Y rotations),
so the fallback can retain the same color lanes on the opposite wall. Existing
route ordinals, names and default orientations remain unchanged. The added wall
states reuse existing assets and cached geometry; no models or textures are copied.

The complete-route GameTest places the ground runs on both sides, the upward
component, the top run and the opposite downward component through real item
interaction dispatch. It verifies all colors and cardinal directions, both top
corners, unchanged primary components, same-wall surface contact, saved secondary
routes and exactly five items consumed per color. JVM model tests compare the
actual X=90 baked bounds to the shared selection/attachment geometry and check UV
density. When a normal orientation is already valid it still wins, as requested;
the resolver does not reorient valid manual placements merely to seek neighbors.
Actual client mouse interaction and visual seams still need in-game verification.
