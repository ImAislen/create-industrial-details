import itertools
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/create_industrial_details"
FAMILY = ASSETS / "textures/block/riveted_steel"
OUTPUT = FAMILY / "block_of_plated_industrial_steel"


def read_png(path):
    data = path.read_bytes()
    offset, compressed = 8, bytearray()
    while offset < len(data):
        size = struct.unpack_from(">I", data, offset)[0]
        kind, payload = data[offset + 4:offset + 8], data[offset + 8:offset + 8 + size]
        if kind == b"IHDR":
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", payload)
            assert (depth, color, compression, filtering, interlace) == (8, 6, 0, 0, 0)
        elif kind == b"IDAT":
            compressed.extend(payload)
        offset += size + 12
    raw = zlib.decompress(compressed)
    stride, rows, previous = width * 4, [], bytearray(width * 4)
    for y in range(height):
        mode = raw[y * (stride + 1)]
        row = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for x in range(stride):
            a, b, c = (row[x - 4] if x >= 4 else 0), previous[x], (previous[x - 4] if x >= 4 else 0)
            estimate = a + b - c
            distances = [abs(estimate - value) for value in (a, b, c)]
            paeth = (a, b, c)[distances.index(min(distances))]
            predictors = (0, a, b, (a + b) // 2, paeth)
            row[x] = (row[x] + predictors[mode]) & 255
        rows.append([bytes(row[x:x + 4]) for x in range(0, stride, 4)])
        previous = row
    return rows


def write_png(path, rows):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\0" + b"".join(row) for row in rows)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                    + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def texture_index(c):
    up, down, left, right, tl, tr, bl, br = c
    x, y = int(up) + 2 * int(down), int(left) + 2 * int(right)
    borders = 4 - sum(c[:4])
    if borders == 0:
        x += int(tr) + 2 * int(tl)
        y += int(bl) + 2 * int(br)
    if borders == 1:
        if not right and (tl or bl):
            y, x = 4, -1 + int(bl) + 2 * int(tl)
        if not left and (tr or br):
            y, x = 5, -1 + int(br) + 2 * int(tr)
        if not down and (tl or tr):
            y, x = 6, -1 + int(tl) + 2 * int(tr)
        if not up and (bl or br):
            y, x = 7, -1 + int(bl) + 2 * int(br)
    if borders == 2 and ((up and left and tl) or (down and left and bl)
                         or (up and right and tr) or (down and right and br)):
        x += 3
    return x + 8 * y


def contexts():
    result = {}
    for bits in itertools.product((False, True), repeat=8):
        up, down, left, right, tl, tr, bl, br = bits
        c = (up, down, left, right, tl and up and left, tr and up and right,
             bl and down and left, br and down and right)
        index = texture_index(c)
        assert index not in result or result[index] == c
        result[index] = c
    assert len(result) == 47 and texture_index((False,) * 8) == 0
    return result


def connected_tile(tile, c):
    up, down, left, right, tl, tr, bl, br = c
    output = []
    for y in range(16):
        row = []
        for x in range(16):
            sx = x + 8 if left and x < 3 else x - 8 if right and x >= 13 else x
            sy = y + 8 if up and y < 3 else y - 8 if down and y >= 13 else y
            corner = ((x < 3 and y < 3 and left and up and not tl)
                      or (x >= 13 and y < 3 and right and up and not tr)
                      or (x < 3 and y >= 13 and left and down and not bl)
                      or (x >= 13 and y >= 13 and right and down and not br))
            row.append(tile[y][x] if corner else tile[sy][sx])
        output.append(row)
    return output


def generate():
    source = read_png(FAMILY / "block_of_industrial_steel.png")
    model = json.loads((ASSETS / "models/block/riveted_steel/block_of_industrial_steel.json").read_text())
    OUTPUT.mkdir(parents=True, exist_ok=True)
    mapping = contexts()
    for face, data in model["elements"][0]["faces"].items():
        u, v, U, V = [round(value * 4) for value in data["uv"]]
        xs = list(range(min(u, U), max(u, U)))
        ys = list(range(min(v, V), max(v, V)))
        if U < u:
            xs.reverse()
        if V < v:
            ys.reverse()
        tile = [[source[y][x] for x in xs] for y in ys]
        assert len(tile) == len(tile[0]) == 16
        write_png(OUTPUT / f"{face}.png", tile)
        atlas = [[bytes(4) for _ in range(128)] for _ in range(128)]
        for index in range(64):
            connected = connected_tile(tile, mapping.get(index, (False,) * 8))
            for y, row in enumerate(connected):
                atlas[index // 8 * 16 + y][index % 8 * 16:index % 8 * 16 + 16] = row
        write_png(OUTPUT / f"{face}_connected.png", atlas)
        assert [row[:16] for row in atlas[:16]] == tile
    print("Generated six exact isolated faces and six 128x128 omnidirectional sheets (47 valid contexts each).")


if __name__ == "__main__":
    generate()
