import argparse
import itertools
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/create_industrial_details"
MATERIALS = {
    "andesite": "create:andesite_casing",
    "brass": "create:brass_casing",
    "copper": "create:copper_casing",
    "railway": "create:railway_casing",
    "industrial_iron": "create:industrial_iron_block",
    "industrial_steel": "create_industrial_details:block_of_industrial_steel",
    "plated_industrial_steel": "create_industrial_details:block_of_plated_industrial_steel",
}
DIRECTIONS = ("north", "east", "south", "west", "up", "down")
OPPOSITE = dict(zip(DIRECTIONS, ("south", "west", "north", "east", "down", "up")))
AXES = {"north": "z", "south": "z", "east": "x", "west": "x", "up": "y", "down": "y"}
VECTORS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
           "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def transform(point, direction):
    x, y, z = point
    return {"north": (x, y, z), "east": (16-z, y, x), "south": (16-x, y, 16-z),
            "west": (z, y, 16-x), "up": (x, 16-z, y), "down": (x, z, 16-y)}[direction]


def rotate_face(face, direction):
    center = transform((8, 8, 8), direction)
    normal = transform(tuple(8 + value for value in VECTORS[face]), direction)
    result = tuple(b - a for a, b in zip(center, normal))
    return next(name for name, vector in VECTORS.items() if vector == result)


def project(lo, hi, face):
    x, y, z = lo
    X, Y, Z = hi
    return {"north": (16-X, 16-Y, 16-x, 16-y), "south": (x, 16-Y, X, 16-y),
            "east": (16-Z, 16-Y, 16-z, 16-y), "west": (z, 16-Y, Z, 16-y),
            "up": (x, z, X, Z), "down": (x, 16-Z, X, 16-z)}[face]


def panels(opening):
    if not opening:
        return [((0, 0, 0), (16, 16, 0), "north", True)]
    return [
        ((0, 0, 0), (1.5, 16, 0), "north", True),
        ((14.5, 0, 0), (16, 16, 0), "north", True),
        ((1.5, 0, 0), (14.5, 1.5, 0), "north", True),
        ((1.5, 14.5, 0), (14.5, 16, 0), "north", True),
        ((1.5, 1.5, 0.75), (1.5, 14.5, 2), "east", False),
        ((14.5, 1.5, 0.75), (14.5, 14.5, 2), "west", False),
        ((1.5, 1.5, 0.75), (14.5, 1.5, 2), "up", False),
        ((1.5, 14.5, 0.75), (14.5, 14.5, 2), "down", False),
    ]


def opening_condition(direction):
    return {"OR": [{direction: "true"},
                   {"axis": AXES[direction], **{side: "false" for side in DIRECTIONS}},
                   {side: "true" if side == OPPOSITE[direction] else "false" for side in DIRECTIONS}]}


def closed_condition(direction):
    return {"AND": [{direction: "false"},
                    {"OR": [{"axis": "|".join(axis for axis in "xyz" if axis != AXES[direction])}]
                            + [{side: "true"} for side in DIRECTIONS]},
                    {"OR": [{OPPOSITE[direction]: "false"}]
                            + [{side: "true"} for side in DIRECTIONS if side != OPPOSITE[direction]]}]}


def matches(condition, state):
    if "AND" in condition:
        return all(matches(child, state) for child in condition["AND"])
    if "OR" in condition:
        return any(matches(child, state) for child in condition["OR"])
    return all(state[key] in value.split("|") for key, value in condition.items())


def validate_conditions():
    for axis in "xyz":
        for bits in itertools.product((False, True), repeat=6):
            active = {side for side, value in zip(DIRECTIONS, bits) if value}
            visible = active or {side for side in DIRECTIONS if AXES[side] == axis}
            if len(active) == 1:
                visible = active | {OPPOSITE[next(iter(active))]}
            state = {"axis": axis, **{side: str(value).lower() for side, value in zip(DIRECTIONS, bits)}}
            for side in DIRECTIONS:
                assert matches(opening_condition(side), state) == (side in visible)
                assert matches(closed_condition(side), state) == (side not in visible)


def generate(create_jar, minecraft_jar):
    with zipfile.ZipFile(create_jar) as create, zipfile.ZipFile(minecraft_jar) as minecraft:
        def load(reference):
            namespace, name = reference.split(":") if ":" in reference else ("minecraft", reference)
            path = f"assets/{namespace}/models/{name}.json"
            if namespace == "create_industrial_details":
                raw = json.loads((ROOT / "src/main/resources" / path).read_text())
            else:
                raw = json.loads((create if namespace == "create" else minecraft).read(path))
            inherited = load(raw["parent"]) if "parent" in raw else {}
            result = {**inherited, **raw}
            result["textures"] = {**inherited.get("textures", {}), **raw.get("textures", {})}
            return result

        blockstate = ASSETS / "blockstates/air_duct.json"
        data = json.loads(blockstate.read_text())
        data["multipart"] = [part for part in data["multipart"] if not part["apply"]["model"].startswith(
            "create_industrial_details:block/air_duct/casing/")]
        output = ASSETS / "models/block/air_duct/casing"
        output.mkdir(parents=True, exist_ok=True)
        for material, block in MATERIALS.items():
            namespace, name = block.split(":")
            path = "block/riveted_steel/" + name if namespace == "create_industrial_details" else "block/" + name
            source = load(namespace + ":" + path)
            assert len(source["elements"]) == 1
            element = source["elements"][0]
            assert element["from"] == [0, 0, 0] and element["to"] == [16, 16, 16]
            textures = {}
            for face, details in element["faces"].items():
                texture = details["texture"]
                while texture.startswith("#"):
                    texture = source["textures"][texture[1:]]
                textures[face] = texture
            textures["particle"] = textures["north"]
            for direction in DIRECTIONS:
                for opening in (False, True):
                    elements = []
                    for lower, upper, normal, cull in panels(opening):
                        points = [transform(point, direction) for point in itertools.product(*zip(lower, upper))]
                        lo = [min(point[i] for point in points) for i in range(3)]
                        hi = [max(point[i] for point in points) for i in range(3)]
                        face = rotate_face(normal, direction)
                        native = element["faces"][face]
                        assert native.get("rotation", 0) == 0
                        u, v, U, V = native.get("uv", [0, 0, 16, 16])
                        a, b, A, B = project(lo, hi, face)
                        uv = [u+(U-u)*a/16, v+(V-v)*b/16, u+(U-u)*A/16, v+(V-v)*B/16]
                        surface = {"uv": uv, "texture": "#" + face}
                        if cull:
                            surface["cullface"] = face
                        elements.append({"from": lo, "to": hi, "faces": {face: surface}})
                    suffix = "open" if opening else "closed"
                    model_name = f"{material}_{direction}_{suffix}"
                    (output / (model_name + ".json")).write_text(json.dumps(
                        {"textures": textures, "elements": elements}, indent=2) + "\n")
                    data["multipart"].append({"when": {"AND": [{"casing": material},
                        opening_condition(direction) if opening else closed_condition(direction)]},
                        "apply": {"model": "create_industrial_details:block/air_duct/casing/" + model_name}})
        blockstate.write_text(json.dumps(data, indent=2) + "\n")
        loot = ROOT / "src/main/resources/data/create_industrial_details/loot_table/blocks/air_duct.json"
        table = json.loads(loot.read_text())
        table["pools"] = [pool for pool in table["pools"] if not any(
            condition.get("condition") == "minecraft:block_state_property"
            and condition.get("block") == "create_industrial_details:air_duct"
            and "casing" in condition.get("properties", {}) for condition in pool.get("conditions", []))]
        for material, item in MATERIALS.items():
            table["pools"].append({"rolls": 1, "conditions": [
                {"condition": "minecraft:survives_explosion"},
                {"condition": "minecraft:block_state_property", "block": "create_industrial_details:air_duct",
                 "properties": {"casing": material}}], "entries": [{"type": "minecraft:item", "name": item}]})
        loot.write_text(json.dumps(table, indent=2) + "\n")


if __name__ == "__main__":
    properties = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text().splitlines() if "=" in line)
    version = properties["create_version"]
    cache = Path.home() / f".gradle/caches/modules-2/files-2.1/com.simibubi.create/create-1.21.1/{version}"
    parser = argparse.ArgumentParser()
    parser.add_argument("--create-jar", type=Path, default=next(cache.glob("*/*-slim.jar"), None))
    parser.add_argument("--minecraft-jar", type=Path, default=ROOT / ".gradle/caches/minecraft/versions/1.21.1/client.jar")
    args = parser.parse_args()
    validate_conditions()
    generate(args.create_jar, args.minecraft_jar)
    print("Generated 84 casing face models and loot; validated openings for all 192 connection/axis configurations.")
