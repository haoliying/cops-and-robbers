# -*- coding: utf-8 -*-
"""生成复古像素风启动图标（纯 Python 手写 PNG，不依赖 Pillow）"""
import os, zlib, struct

COP = [
    "...22...",
    "..2222..",
    "..FFFF..",
    "..FKFK..",
    ".111111.",
    "Y111111Y",
    "..1111..",
    "..2..2..",
]
PAL = {
    '1': (0x2E, 0x6B, 0xE6),
    '2': (0x16, 0x30, 0x7A),
    'F': (0xF2, 0xC4, 0x9B),
    'K': (0x0A, 0x0A, 0x12),
    'Y': (0xFF, 0xD2, 0x3F),
}
BG = (0x0E, 0x14, 0x26)
EDGE = (0x4C, 0x8C, 0xFF)


def png(path, size):
    px = size / 8.0
    rows = []
    for y in range(size):
        row = bytearray()
        gy = int(y // px)
        for x in range(size):
            gx = int(x // px)
            ch = COP[gy][gx]
            if ch == '.':
                r, g, b = BG
            else:
                r, g, b = PAL[ch]
            # 最外圈描边
            d = min(x, y, size - 1 - x, size - 1 - y)
            if d < max(1, size // 48):
                r, g, b = EDGE
            row += bytes((r, g, b, 0xFF))
        rows.append(bytes(row))
    raw = b''.join(b'\x00' + r for r in rows)

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    out += chunk(b'IDAT', zlib.compress(raw, 9))
    out += chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(out)
    print('wrote', path, size)


base = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                    'app', 'src', 'main', 'res')
for folder, size in [('mipmap-mdpi', 48), ('mipmap-hdpi', 72),
                     ('mipmap-xhdpi', 96), ('mipmap-xxhdpi', 144),
                     ('mipmap-xxxhdpi', 192)]:
    png(os.path.join(base, folder, 'ic_launcher.png'), size)
