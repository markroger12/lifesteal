#!/usr/bin/env python3
"""
Regenerates the original LifeCore resource pack in ../resourcepack:
16x16 pixel-art textures, item models, CustomModelData overrides (<= 1.21.3)
and item model definitions (1.21.4+). Only the Python standard library is used.

Usage: python3 tools/generate_resourcepack.py
"""
import json
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'resourcepack')

def png(path, pixels):
    h = len(pixels); w = len(pixels[0])
    raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *p) for p in row) for row in pixels)
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'wb').write(data)

def hexc(s, a=255):
    s = s.lstrip('#'); return (int(s[0:2],16), int(s[2:4],16), int(s[4:6],16), a)

T = (0,0,0,0)

def from_art(art, palette):
    return [[palette[c] if c != '.' else T for c in row] for row in art]

HEART = [
    "................",
    "................",
    "...oooo..oooo...",
    "..oHHmmooommmo..",
    ".oHWHmmmmmmmmdo.",
    ".oHHmmmmmmmmmdo.",
    ".ommmmmmmmmmmdo.",
    ".ommmmmmmmmmddo.",
    "..ommmmmmmmmdo..",
    "...ommmmmmmdo...",
    "....ommmmmdo....",
    ".....ommmdo.....",
    "......omdo......",
    ".......oo.......",
    "................",
    "................",
]

def heart(outline, hi, white, mid, dark):
    return from_art(HEART, {'o': hexc(outline), 'H': hexc(hi), 'W': hexc(white), 'm': hexc(mid), 'd': hexc(dark)})

tex = ROOT + '/assets/lifecore/textures/item/'
png(tex + 'small_heart.png', heart('#4a0508', '#ff8a8a', '#ffffff', '#e0202a', '#9e0f16'))
large = heart('#3a0010', '#ff9eb5', '#ffffff', '#c8103c', '#7a0624')
for (x, y) in [(1, 2), (14, 3), (14, 12)]:
    large[y][x] = hexc('#ffb3c6')
png(tex + 'large_heart.png', large)
# legendary: gold outline, magenta/orange core + sparkles
leg = heart('#ffd700', '#ffe08a', '#ffffff', '#ff4500', '#c71585')
for (x, y) in [(1, 1), (14, 2), (13, 13), (2, 12)]:
    leg[y][x] = hexc('#fff6b0')
png(tex + 'legendary_heart.png', leg)

SCROLL = [
    "................",
    "..bbbbbbbbbbb...",
    ".bPPPPPPPPPPPb..",
    ".bbbbbbbbbbbbb..",
    "..pPPPPPPPPPp...",
    "..pPllllllPPp...",
    "..pPPPPPPPPPp...",
    "..pPlllllPPPp...",
    "..pPPPPPPPPPp...",
    "..pPllllPPSSp...",
    "..pPPPPPPSRRS...",
    "..pPPPPPPSRRS...",
    ".bbbbbbbbbSSbb..",
    ".bPPPPPPPPPPPb..",
    "..bbbbbbbbbbb...",
    "................",
]
def scroll(paper, shade, line, seal, sealdark):
    return from_art(SCROLL, {'b': hexc(shade), 'P': hexc(paper), 'p': hexc(shade), 'l': hexc(line), 'S': hexc(sealdark), 'R': hexc(seal)})
png(tex + 'sacrificial_scroll.png', scroll('#f2e3c0', '#9c7a4a', '#b89a6a', '#d1001f', '#6b0010'))
png(tex + 'blood_pact.png', scroll('#d9b8a0', '#5a1a14', '#8a3a2a', '#ff1a1a', '#4b0000'))
png(tex + 'soul_contract.png', scroll('#e6d5f2', '#4a2a6a', '#8a6ab0', '#f806cc', '#2e0249'))

NOTE = [
    "................",
    "..ppppppppppp...",
    "..pWWWWWWWWWp...",
    "..pWllllllWWp...",
    "..pWWWWWWWWWp...",
    "..pWlllllWWWp...",
    "..pWWWWWWWWWp...",
    "..pWWrr.rrWWp...",
    "..pWrRRrRRrWp...",
    "..pWrRRRRRrWp...",
    "..pWWrRRRrWWp...",
    "..pWWWrRrWWWp...",
    "..pWWWWrWWWWp...",
    "..pWWWWWWWWWp...",
    "..ppppppppppp...",
    "................",
]
note = from_art(NOTE, {'p': hexc('#8a8a7a'), 'W': hexc('#fbf8ee'), 'l': hexc('#c9c3b0'), 'r': hexc('#7a0a0a'), 'R': hexc('#ff4d4d')})
note[7][7] = hexc('#fbf8ee')
png(tex + 'heart_note.png', note)

BEACON = [
    "................",
    ".......gg.......",
    "......gLLg......",
    ".....gLCCLg.....",
    "......gCCg......",
    ".......gg.......",
    "...oooooooooo...",
    "...oGGGGGGGGo...",
    "...oGCCCCCCGo...",
    "...oGCLLLLCGo...",
    "...oGCLWWLCGo...",
    "...oGCLLLLCGo...",
    "...oGCCCCCCGo...",
    "...oGGGGGGGGo...",
    "...oooooooooo...",
    "................",
]
def beacon(glass, core, light, beam, obsidian):
    return from_art(BEACON, {'g': hexc(beam, 180), 'L': hexc(light), 'C': hexc(core), 'W': hexc('#ffffff'),
                             'o': hexc(obsidian), 'G': hexc(glass), 'd': hexc(obsidian)})
png(tex + 'beacon_basic.png', beacon('#bfe9ff', '#00c6ff', '#9ae6ff', '#0072ff', '#1b1b2f'))
png(tex + 'beacon_advanced.png', beacon('#fff1c2', '#f7971e', '#ffe08a', '#ffd200', '#2a1a05'))
png(tex + 'beacon_ultimate.png', beacon('#f3d6ff', '#da22ff', '#e3a6ff', '#9733ee', '#1a0524'))

# pack icon: 64x64 scaled legendary heart on a dark background
icon = [[hexc('#1a0b0d') for _ in range(64)] for _ in range(64)]
base = heart('#2a0306', '#ff8a8a', '#ffffff', '#e0202a', '#9e0f16')
for y in range(16):
    for x in range(16):
        c = base[y][x]
        if c[3] == 0:
            continue
        for dy in range(4):
            for dx in range(4):
                icon[y * 4 + dy][x * 4 + dx] = c
png(ROOT + '/pack.png', icon)

# ---------------------------------------------------------------- models
items = {
    'small_heart': ('red_dye', 710001),
    'large_heart': ('red_dye', 710002),
    'legendary_heart': ('nether_star', 710003),
    'heart_note': ('paper', 710010),
    'sacrificial_scroll': ('paper', 710020),
    'blood_pact': ('paper', 710021),
    'soul_contract': ('paper', 710022),
    'beacon_basic': ('beacon', 710030),
    'beacon_advanced': ('beacon', 710031),
    'beacon_ultimate': ('beacon', 710032),
}

def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')

for name in items:
    write_json(f'{ROOT}/assets/lifecore/models/item/{name}.json',
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'lifecore:item/{name}'}})
    # 1.21.4+ item model definition, usable through item-model: "lifecore:<name>"
    write_json(f'{ROOT}/assets/lifecore/items/{name}.json',
               {'model': {'type': 'minecraft:model', 'model': f'lifecore:item/{name}'}})

by_base = {}
for name, (base, cmd) in items.items():
    by_base.setdefault(base, []).append((cmd, name))

vanilla_parent = {
    'red_dye': ('minecraft:item/generated', {'layer0': 'minecraft:item/red_dye'}),
    'nether_star': ('minecraft:item/generated', {'layer0': 'minecraft:item/nether_star'}),
    'paper': ('minecraft:item/generated', {'layer0': 'minecraft:item/paper'}),
    'beacon': ('minecraft:block/beacon', None),
}

for base, entries in by_base.items():
    entries.sort()
    parent, textures = vanilla_parent[base]
    legacy = {'parent': parent}
    if textures:
        legacy['textures'] = textures
    legacy['overrides'] = [{'predicate': {'custom_model_data': cmd}, 'model': f'lifecore:item/{name}'} for cmd, name in entries]
    # <= 1.21.3: CustomModelData overrides on the vanilla item model
    write_json(f'{ROOT}/assets/minecraft/models/item/{base}.json', legacy)
    # >= 1.21.4: item model definition with range dispatch on custom model data
    vanilla_model = 'minecraft:block/beacon' if base == 'beacon' else f'minecraft:item/{base}'
    fallback = {'type': 'minecraft:model', 'model': vanilla_model}
    dispatch_entries = []
    for cmd, name in entries:
        dispatch_entries.append({'threshold': cmd, 'model': {'type': 'minecraft:model', 'model': f'lifecore:item/{name}'}})
        dispatch_entries.append({'threshold': cmd + 1, 'model': fallback})
    # collapse duplicate thresholds (consecutive ids)
    seen = {}
    for e in dispatch_entries:
        if e['threshold'] in seen and e['model'] == fallback:
            continue
        seen[e['threshold']] = e
    write_json(f'{ROOT}/assets/minecraft/items/{base}.json', {
        'model': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:custom_model_data', 'index': 0,
                  'fallback': fallback, 'entries': sorted(seen.values(), key=lambda e: e['threshold'])}})

write_json(ROOT + '/pack.mcmeta', {
    'pack': {
        'pack_format': 34,
        'supported_formats': [34, 99],
        'description': '§cLifeCore §7- hearts, scrolls & revive beacons'
    }
})
print('generated')
