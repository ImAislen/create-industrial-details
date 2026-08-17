"""Generate the static Wooden Post model family from the proven Beam texture bindings."""

import json
from itertools import combinations
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


def compatible(first: tuple[int, int], second: tuple[int, int]) -> bool:
    return abs(first[0] - second[0]) >= 2 or abs(first[1] - second[1]) >= 2


def valid_arrangements() -> list[tuple[str, ...]]:
    arrangements = []
    position_names = tuple(POSITIONS)
    for member_count in range(1, len(position_names) + 1):
        for members in combinations(position_names, member_count):
            if all(
                compatible(POSITIONS[first], POSITIONS[second])
                for first, second in combinations(members, 2)
            ):
                arrangements.append(members)

    counts = {size: sum(len(value) == size for value in arrangements) for size in range(1, 5)}
    if counts != {1: 9, 2: 16, 3: 8, 4: 1} or len(arrangements) != 34:
        raise RuntimeError(f"Expected 34 Wooden Post arrangements, found {counts}")
    return arrangements


def arrangement_name(members: tuple[str, ...]) -> str:
    return "_".join(members)


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


def generate_material(beam_model: Path, arrangements: list[tuple[str, ...]]) -> int:
    beam_name = beam_model.stem
    registry_name = post_name(beam_name)
    textures = json.loads(beam_model.read_text(encoding="utf-8"))["textures"]

    for position in POSITIONS:
        write_json(POST_MODELS / f"{registry_name}_{position}.json", {
            "parent": f"create_industrial_details:block/wooden_post/wooden_post_{position}",
            "textures": textures,
        })

    multipart = []
    for position in POSITIONS:
        model = f"create_industrial_details:block/wooden_post/{registry_name}_{position}"
        matching_arrangements = [
            {"post_position": arrangement_name(members)}
            for members in arrangements
            if position in members
        ]
        multipart.append({
            "when": matching_arrangements[0]
            if len(matching_arrangements) == 1
            else {"OR": matching_arrangements},
            "apply": {"model": model},
        })
    write_json(ASSETS / "blockstates" / f"{registry_name}.json", {"multipart": multipart})
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


def validate_material(registry_name: str, arrangements: list[tuple[str, ...]]) -> None:
    blockstate_path = ASSETS / "blockstates" / f"{registry_name}.json"
    multipart = json.loads(blockstate_path.read_text(encoding="utf-8"))["multipart"]
    if len(multipart) != len(POSITIONS):
        raise RuntimeError(f"Expected nine multipart entries in {blockstate_path}")

    expected_names = {arrangement_name(members) for members in arrangements}
    seen_names = set()
    for part, position in zip(multipart, POSITIONS, strict=True):
        when = part["when"]
        terms = when["OR"] if "OR" in when else [when]
        actual_names = {term["post_position"] for term in terms}
        required_names = {
            arrangement_name(members)
            for members in arrangements
            if position in members
        }
        if actual_names != required_names:
            raise RuntimeError(f"Incorrect {position} arrangements in {blockstate_path}")
        seen_names.update(actual_names)

        model_path = POST_MODELS / f"{registry_name}_{position}.json"
        if not model_path.is_file():
            raise RuntimeError(f"Missing generated Wooden Post model: {model_path}")

    if seen_names != expected_names:
        raise RuntimeError(f"Not all arrangements are represented in {blockstate_path}")


def main() -> None:
    count = generate_shared_geometry()
    arrangements = valid_arrangements()
    beam_models = sorted(
        path for path in BEAM_MODELS.glob("*.json")
        if path.stem != "wooden_beam" and not path.stem.endswith("_attachment")
    )
    if len(beam_models) != 33:
        raise RuntimeError(f"Expected 33 Wooden Beam material models, found {len(beam_models)}")
    for beam_model in beam_models:
        count += generate_material(beam_model, arrangements)
    for beam_model in beam_models:
        validate_material(post_name(beam_model.stem), arrangements)
    print(
        f"Generated {count} Wooden Post JSON resources for {len(beam_models)} material variants "
        f"and {len(arrangements)} legal arrangements."
    )


if __name__ == "__main__":
    main()
