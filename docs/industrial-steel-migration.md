# Industrial Steel naming migration

The audit targets Minecraft 1.21.1, NeoForge 21.1.238, Create 6.0.10-280,
and Flywheel 1.0.6. The namespace remains `create_industrial_details`.

## Registry mapping

All identifiers below use the mod namespace. New identifiers are canonical;
previous identifiers resolve through NeoForge registry aliases.

| Previous identifier | Canonical identifier | Registries |
| --- | --- | --- |
| `riveted_steel_panel` | `industrial_steel_panel` | Block, item |
| `riveted_steel_grate` | `industrial_steel_grate` | Block, item |
| `riveted_steel_hatch` | `industrial_steel_hatch` | Block, item |
| `riveted_steel_beam` | `industrial_steel_beam` | Block, item |
| `riveted_steel_caged_lamp` | `industrial_steel_caged_lamp` | Block, item, block entity type, menu type |
| `riveted_steel_panel_shaft_penetration` | `industrial_steel_panel_shaft_penetration` | Block only; hidden Create bracket state |
| `riveted_steel_caged_lamp_frequencies` | `industrial_steel_caged_lamp_frequencies` | Data component type |

There are no registry-name collisions. `block_of_industrial_steel` and
`block_of_plated_industrial_steel` remain separate, unchanged registrations.
The generic Steel Wall Bracket family does not use Riveted Steel identifiers and
retains its names and behavior. Service Trench registry IDs remain unchanged;
their family package and asset directory move.

## Java and assets

`RivetedSteel*` classes and symbols become `IndustrialSteel*`. The
`content.block.rivetedsteel` and `content.item.rivetedsteel` packages become
`content.block.industrialsteel` and `content.item.industrialsteel`, retaining
their subpackages. Caged lamp classes join
`content.block.industrialsteel.lighting.cagedlamp`. Client classes retain their
existing client packages.

Affected model and texture paths move from `riveted_steel` to `industrial_steel`;
family-specific filenames use `industrial_steel_*`. Model elements, UVs,
transforms, culling, render types, and texture bytes are preserved.

The established full blocks retain their exact model, texture, and CT atlas
identifiers under `block/riveted_steel/block_of_industrial_steel` and
`block/riveted_steel/block_of_plated_industrial_steel`. These protected resource
paths are compatibility exceptions, not legacy material registrations. Their
model references, plated CT behavior, air duct material references, and asset
generators retain these paths to preserve the requested resource identifiers.

Descriptions of physical rivets are unchanged. Generated build, Gradle, IDE,
and run outputs, including saved worlds and logs, are not edited.

The sixteen existing lamp deploying recipe IDs retain their legacy paths so
saved recipe references are not renamed. Only their existing item/block
references are updated; recipe behavior and ingredients remain unchanged.

## Compatibility and verification

Aliases use `DeferredRegister.addAlias()` before registry registration events.
No duplicate legacy blocks or items are registered. Blockstate property names,
lamp NBT field names, frequency component payloads, and Create bracket
serialization remain unchanged. New serialization uses canonical IDs.

Regression tests exercise legacy registry lookups, blockstates, item stacks,
frequency components, lamp block entities, and panel bracket data. Existing
server GameTests and copycat geometry checks remain applicable. Resource checks
compare renamed models and texture bytes against a pre-refactor snapshot and
resolve all model and texture references.

Full existing-world upgrades and client visual checks still require manual
verification. Aliases do not rewrite arbitrary external text files or migrate
third-party resource packs targeting renamed legacy model/texture paths.

Verified with `gradlew build runGameTestServer`: all 29 required tests pass.
The four migration tests cover all states of the six renamed blocks, five item
IDs, the lamp's component and block entity data, its menu alias, and Create's
stored panel bracket. The resource audit resolves 753 mod models and 122
blockstates without missing model or texture references. All 31 PNG files are
byte-identical to the pre-refactor snapshot. The two established full blocks,
their CT resources and air duct assets, and the user's existing changes in
`gradle.properties` remain unchanged.

## Pre-existing data issues outside this rename

The existing `recipe/industrial_steel_block.json` still references the absent
`create_industrial_details:industrial_steel_block` item. The recipe error occurs
before this refactor too; it is not repaired here because that would change
unrelated recipe work.

The panel, grate, hatch, and beam had no block loot tables in the original
snapshot. No new drop behavior is introduced; the caged lamp's existing loot
table is renamed and its references are updated.
