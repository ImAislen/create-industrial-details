"""Generate the static Wooden Post model family from the proven Beam texture bindings."""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "create_industrial_details"
DATA = RESOURCES / "data" / "create_industrial_details"
BEAM_MODELS = ASSETS / "models" / "block" / "wooden_beam"
POST_MODELS = ASSETS / "models" / "block" / "wooden_post"

POSITIONS = {
    "north_west": (-1, -1),
    "north": (0, -1),
    "north_east": (1, -1),
    "west": (-1, 0),
    "center": (0, 0),
    "east": (1, 0),
    "south_west": (-1, 1),
    "south": (0, 1),
    "south_east": (1, 1),
}

DISPLAY = {
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4] * 3},
    "firstperson_lefthand": {"rotation": [0, -135, 0], "scale": [0.4] * 3},
    "ground": {"translation": [0, 3, 0], "scale": [0.25] * 3},
    "gui": {"rotation": [30, -135, 0], "scale": [0.625] * 3},
}


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def post_name(beam_name: str) -> str:
    if beam_name.endswith("_wooden_beam"):
        return beam_name.removesuffix("_wooden_beam") + "_wooden_post"
    return beam_name.removesuffix("_plank_beam") + "_plank_post"


def generate_shared_geometry() -> int:
    write_json(POST_MODELS / "wooden_post.json", {
        "parent": "minecraft:block/block",
        "display": DISPLAY,
    })
    count = 1
    for name, (x_index, z_index) in POSITIONS.items():
        center_x = 8 + x_index * 4
        center_z = 8 + z_index * 4
        write_json(POST_MODELS / f"wooden_post_{name}.json", {
            "parent": "create_industrial_details:block/wooden_post/wooden_post",
            "elements": [{
                "from": [center_x - 4, 0, center_z - 4],
                "to": [center_x + 4, 16, center_z + 4],
                "faces": {
                    "north": {"uv": [0, 0, 16, 16], "texture": "#side"},
                    "east": {"uv": [0, 0, 16, 16], "texture": "#side"},
                    "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
                    "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
                    "up": {"uv": [0, 0, 16, 16], "texture": "#end"},
                    "down": {"uv": [0, 0, 16, 16], "texture": "#end"},
                },
            }],
        })
        count += 1
    return count


def generate_material(beam_model: Path) -> int:
    beam_name = beam_model.stem
    registry_name = post_name(beam_name)
    textures = json.loads(beam_model.read_text(encoding="utf-8"))["textures"]

    for position in POSITIONS:
        write_json(POST_MODELS / f"{registry_name}_{position}.json", {
            "parent": f"create_industrial_details:block/wooden_post/wooden_post_{position}",
            "textures": textures,
        })

    variants = {}
    for position in POSITIONS:
        model = f"create_industrial_details:block/wooden_post/{registry_name}_{position}"
        variants[f"post_position={position},waterlogged=false"] = {"model": model}
        variants[f"post_position={position},waterlogged=true"] = {"model": model}
    write_json(ASSETS / "blockstates" / f"{registry_name}.json", {"variants": variants})
    write_json(ASSETS / "models" / "item" / f"{registry_name}.json", {
        "parent": f"create_industrial_details:block/wooden_post/{registry_name}_center",
    })
    write_json(DATA / "loot_table" / "blocks" / f"{registry_name}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": f"create_industrial_details:{registry_name}"}],
        }],
        "random_sequence": f"create_industrial_details:blocks/{registry_name}",
    })
    return len(POSITIONS) + 3


def main() -> None:
    count = generate_shared_geometry()
    beam_models = sorted(
        path for path in BEAM_MODELS.glob("*.json")
        if path.stem != "wooden_beam" and not path.stem.endswith("_attachment")
    )
    if len(beam_models) != 33:
        raise RuntimeError(f"Expected 33 Wooden Beam material models, found {len(beam_models)}")
    for beam_model in beam_models:
        count += generate_material(beam_model)
    print(f"Generated {count} Wooden Post JSON resources for {len(beam_models)} material variants.")


if __name__ == "__main__":
    main()
