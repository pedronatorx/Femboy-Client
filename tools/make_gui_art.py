"""Generates the small GUI textures of Femboy Client (all original, drawn from math here; no external assets).

Usage: python3 tools/make_gui_art.py src/client/resources/assets/femboyclient/textures/gui

  round.png   64x64   white disc with an anti-aliased edge; its quarters are the corners of rounded rectangles
  shadow.png  64x64   soft blurred rounded square for drop shadows (9-slice: 24 px corners)
  glow.png    128x128 radial falloff for the soft color glows behind the menu
  ramp.png    64x4    left-to-right alpha ramp for horizontal gradients
  logo.png    128x128 the Femboy Client badge: purple-to-magenta rounded square, white heart and sparkle

Every texture gets a .png.mcmeta with blur on (smooth when scaled) and clamp on (no bleeding at the edges).
"""
import math, os, struct, sys, zlib

def write_png(path, w, h, px):
    raw = b''.join(b'\x00' + bytes(px[y*w*4:(y+1)*w*4]) for y in range(h))
    chunk = lambda t, d: struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t+d) & 0xffffffff)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
    with open(path + '.mcmeta', 'w') as f:
        f.write('{\n\t"texture": {\n\t\t"blur": true,\n\t\t"clamp": true\n\t}\n}\n')
    print(f"{os.path.basename(path)}: {w}x{h}")

def coverage(test, x, y, ss=8):
    return sum(test(x + (sx + .5)/ss, y + (sy + .5)/ss) for sy in range(ss) for sx in range(ss)) / (ss*ss)

def round_png(path):
    n = 64; px = bytearray(n*n*4)
    for y in range(n):
        for x in range(n):
            a = coverage(lambda u, v: (u-32)**2 + (v-32)**2 <= 32**2, x, y)
            px[(y*n+x)*4:(y*n+x)*4+4] = bytes([255, 255, 255, round(a*255)])
    write_png(path, n, n, px)

def shadow_png(path):
    # Distance to a rounded square, pushed through a smooth falloff: a cheap stand-in for a gaussian blur.
    n = 64; px = bytearray(n*n*4); inset, radius, blur = 20, 6, 18
    for y in range(n):
        for x in range(n):
            qx = max(abs(x + .5 - 32) - (32 - inset - radius), 0); qy = max(abs(y + .5 - 32) - (32 - inset - radius), 0)
            d = math.hypot(qx, qy) - radius
            a = 1.0 if d <= 0 else max(0.0, 1 - d / blur) ** 2.2
            px[(y*n+x)*4:(y*n+x)*4+4] = bytes([0, 0, 0, round(a*255)])
    write_png(path, n, n, px)

def glow_png(path):
    n = 128; px = bytearray(n*n*4)
    for y in range(n):
        for x in range(n):
            d = math.hypot(x + .5 - 64, y + .5 - 64) / 64
            a = max(0.0, 1 - d) ** 2.4
            px[(y*n+x)*4:(y*n+x)*4+4] = bytes([255, 255, 255, round(a*255)])
    write_png(path, n, n, px)

def ramp_png(path):
    w, h = 64, 4; px = bytearray(w*h*4)
    for y in range(h):
        for x in range(w):
            px[(y*w+x)*4:(y*w+x)*4+4] = bytes([255, 255, 255, round((x + .5)/w*255)])
    write_png(path, w, h, px)

def logo_png(path):
    n = 128; px = bytearray(n*n*4)
    purple, magenta = (139, 92, 246), (224, 112, 232)
    def badge(u, v):
        r = 28; qx = max(abs(u - 64) - (60 - r), 0); qy = max(abs(v - 64) - (60 - r), 0)
        return math.hypot(qx, qy) <= r
    def heart(u, v):
        x, y = (u - 60) / 27, (v - 70) / 27   # unit heart, a little left and low
        return (x*x + y*y - 1)**3 - x*x * (-y)**3 <= 0
    def sparkle(u, v):
        cx, cy, s = 98, 30, 11
        x, y = abs(u - cx) / s, abs(v - cy) / s
        return x**0.55 + y**0.55 <= 1
    for y in range(n):
        for x in range(n):
            a = coverage(badge, x, y, 4)
            if a <= 0:
                continue
            t = min(1, max(0, (x + y) / (2*n)))
            col = [round(purple[i] + (magenta[i] - purple[i]) * t) for i in range(3)]
            # Soft highlight at the top edge.
            hl = max(0.0, 1 - y / 46) * 0.18
            col = [round(c + (255 - c) * hl) for c in col]
            w = max(coverage(heart, x, y, 4), coverage(sparkle, x, y, 4))
            col = [round(c + (255 - c) * w) for c in col]
            px[(y*n+x)*4:(y*n+x)*4+4] = bytes(col + [round(a*255)])
    write_png(path, n, n, px)

if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    round_png(os.path.join(out, 'round.png'))
    shadow_png(os.path.join(out, 'shadow.png'))
    glow_png(os.path.join(out, 'glow.png'))
    ramp_png(os.path.join(out, 'ramp.png'))
    logo_png(os.path.join(out, 'logo.png'))
