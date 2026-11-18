# Cable Tray

The existing `cable_tray` block and item use the supplied replacement model and
texture directly. Only the model's two texture references required correction
to `create_industrial_details:block/cable_tray/cable_tray`. Geometry, UVs, texture
pixels, inventory transforms, registration, loot and creative placement are unchanged.

The model runs along local Z with cables exposed toward local UP. Existing
`axis=x` and `axis=z` states retain their original model rotations and saved-world
meaning. The same `axis` property now also accepts Y. `facing` selects the visible
cable face for vertical trays and the viewing heading for floor trays. Floor
placement stores the player's horizontal heading, keeping canonical red-to-blue
order across their view; horizontal wrench turns rotate this heading as well.
Legacy axis-only states load with north facing: Z retains Y=0, and X uses the
legacy Y=90 fallback when facing is not on its run axis. No channel IDs are remapped.

Twelve state variants share one model, with four floor viewing headings and four
vertical appearances. Vertical variants use X=270 and Y=180/270/0/90 for north/east/south/west.
These rotations keep the exposed cables facing forward while matching the Control
Box's red/yellow/green/blue order, without mirrored assets or texture changes.
Collision and selection use six cached unions of the model's eleven cuboids.
There is no tray block entity, dynamic rendering or block ticker. The server-side
four-channel network is described in [cable-networks.md](cable-networks.md).

Normal floor/ceiling placement retains player-aligned horizontal runs. Wall
placement creates a vertical tray with cables facing away from the wall. Extending
a vertical tray from its top or bottom preserves its visible cable face.

Directly above or below a Control Box, its front facing takes priority. Placing
the box afterward and wrenching the box also align those two adjacent trays through
normal shape updates. Side-adjacent and more distant trays are untouched. Removing
support or the box never breaks a tray or resets its orientation. If two differently
facing boxes sandwich one tray, the lower box takes priority.

Top/bottom wrench clicks turn horizontal runs or rotate the cable face of a
vertical tray. Applicable side clicks tip between horizontal and vertical runs;
Control Box attachments remain aligned through Create's normal post-wrench updates.
Shift-wrench pickup continues returning one tray through the existing loot table.

Holding a tray and targeting another tray invokes Catnip/Create's native placement
helper and preview. It searches only along the run axis through matching trays,
stops at occupied/unsupported/unloaded destinations, and inherits axis/facing.
Sneaking bypasses assistance. Control Box alignment wins at attachment positions.
Permission, world-boundary and collision checks precede native placement and item
consumption. No idle polling or additional rendering system is used.

GameTests cover resource rotations against the local cable-facing normal, shapes
against all model elements, normal placement, vertical extension, all eight box
attachment combinations, box placement/rotation/removal, legacy state loading,
serialization, structure transforms and wrench pickup. Actual client rendering,
texture appearance and placement ergonomics still require in-game verification.
