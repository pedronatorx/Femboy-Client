"""Generates the Femboy Client GUI icon atlas (white glyphs, tinted at draw time).

Usage: python3 tools/make_icons.py src/client/resources/assets/femboyclient/textures/gui/icons.png
Each icon is a 16x16 cell; the order below must match Icon.java.
"""
import math, struct, sys, zlib

CELL, SS = 16, 8          # cell size, supersampling factor
ICONS = []                # (name, draw function)

def icon(name):
    def wrap(fn):
        ICONS.append((name, fn)); return fn
    return wrap

# --- shape primitives in cell coordinates (0..16), return coverage test functions ---------------
def circle(cx, cy, r):           return lambda x, y: (x-cx)**2 + (y-cy)**2 <= r*r
def ring(cx, cy, r, w):          return lambda x, y: (r-w/2)**2 <= (x-cx)**2 + (y-cy)**2 <= (r+w/2)**2
def rect(x0, y0, x1, y1):        return lambda x, y: x0 <= x <= x1 and y0 <= y <= y1
def rrect(x0, y0, x1, y1, r):
    def f(x, y):
        if not (x0 <= x <= x1 and y0 <= y <= y1): return False
        cx = min(max(x, x0+r), x1-r); cy = min(max(y, y0+r), y1-r)
        return (x-cx)**2 + (y-cy)**2 <= r*r
    return f
def line(x0, y0, x1, y1, w):
    def f(x, y):
        dx, dy = x1-x0, y1-y0; l2 = dx*dx+dy*dy
        t = max(0, min(1, ((x-x0)*dx+(y-y0)*dy)/l2)) if l2 else 0
        px, py = x0+t*dx, y0+t*dy
        return (x-px)**2 + (y-py)**2 <= (w/2)**2
    return f
def poly(pts):
    def f(x, y):
        inside = False; n = len(pts)
        for i in range(n):
            (ax, ay), (bx, by) = pts[i], pts[(i+1) % n]
            if (ay > y) != (by > y) and x < (bx-ax)*(y-ay)/(by-ay)+ax: inside = not inside
        return inside
    return f
def outline(fill, inner):        return lambda x, y: fill(x, y) and not inner(x, y)
def union(*fs):                  return lambda x, y: any(f(x, y) for f in fs)
def minus(a, b):                 return lambda x, y: a(x, y) and not b(x, y)
def arc(cx, cy, r, w, a0, a1):
    def f(x, y):
        if not ring(cx, cy, r, w)(x, y): return False
        a = math.degrees(math.atan2(y-cy, x-cx)) % 360
        return (a0 <= a <= a1) if a0 <= a1 else (a >= a0 or a <= a1)
    return f
W = 1.6  # stroke width used by all outline icons

@icon("dashboard")
def _(): return union(rrect(2,2,7,7,1.2), rrect(9,2,14,7,1.2), rrect(2,9,7,14,1.2), rrect(9,9,14,14,1.2))
@icon("visual")
def _():
    eye = lambda x, y: ((x-8)/6.5)**2 + ((y-8)/4.2)**2 <= 1
    inner = lambda x, y: ((x-8)/(6.5-W))**2 + ((y-8)/(4.2-W))**2 <= 1
    return union(outline(eye, inner), circle(8, 8, 2.1))
@icon("analysis")  # amethyst crystal
def _():
    return union(outline(poly([(8,1.5),(13.5,6),(8,14.8),(2.5,6)]), poly([(8,3.7),(11.3,6.5),(8,12.1),(4.7,6.5)])),
                 line(3.2,6,12.8,6,1.3))
@icon("scenarios")  # flask
def _():
    body = poly([(6,2),(10,2),(10,6.5),(14,13.5),(13,14.5),(3,14.5),(2,13.5),(6,6.5)])
    inner = poly([(7.4,3.4),(8.6,3.4),(8.6,7),(12.2,13.2),(3.8,13.2),(7.4,7)])
    return union(outline(body, inner), poly([(4.6,11),(11.4,11),(12.2,13.2),(3.8,13.2)]), rect(5,1.5,11,2.8))
@icon("results")  # bar chart
def _(): return union(rrect(2,9,5,14,0.8), rrect(6.5,5,9.5,14,0.8), rrect(11,2,14,14,0.8))
@icon("performance")  # pulse line
def _(): return union(line(1.5,9,4.5,9,W), line(4.5,9,6.5,4,W), line(6.5,4,9.5,13,W), line(9.5,13,11.5,8,W), line(11.5,8,14.5,8,W))
@icon("settings")  # gear
def _():
    teeth = [rect(7,0.8,9,15.2), rect(0.8,7,15.2,9)]
    rot = lambda f, a: (lambda x, y: f(8+(x-8)*math.cos(a)-(y-8)*math.sin(a), 8+(x-8)*math.sin(a)+(y-8)*math.cos(a)))
    gear = union(circle(8,8,5.2), *teeth, *[rot(t, math.pi/4) for t in teeth])
    return minus(gear, circle(8,8,2.2))
@icon("hud")  # monitor with text lines
def _(): return union(outline(rrect(1.5,2.5,14.5,12,1.5), rrect(3.1,4.1,12.9,10.4,0.5)), line(4.5,6,9,6,1.2), line(4.5,8.4,11,8.4,1.2), rect(5,13,11,14.3))
@icon("utility")  # camera
def _(): return union(minus(rrect(1.5,4.5,14.5,13.5,1.5), circle(8,9,3.3)), circle(8,9,1.7), rrect(5,2.5,9.5,4.6,0.6))
@icon("search")
def _(): return union(ring(6.8,6.8,4.4,W), line(10,10,14.2,14.2,2.0))
@icon("reset")  # circular arrow
def _(): return union(arc(8,8,5.2,W,40,330), poly([(13.6,1.6),(14.2,7.2),(9.0,5.4)]))
@icon("key")  # keyboard
def _():
    keys = [rect(3.2+x*2.6, 6, 4.6+x*2.6, 7.4) for x in range(4)] + [rect(4.6+x*2.6, 8.6, 6.0+x*2.6, 10) for x in range(3)]
    return union(outline(rrect(1,3.5,15,12.5,1.5), rrect(2.6,5.1,13.4,10.9,0.5)), *keys, rect(5.5,11,10.5,11.2))
@icon("check")
def _(): return union(line(3,8.5,6.5,12,2.0), line(6.5,12,13,4.5,2.0))
@icon("warning")
def _(): return union(minus(poly([(8,1.5),(15,14),(1,14)]), poly([(8,4.6),(12.4,12.6),(3.6,12.6)])), rect(7.3,6.5,8.7,10), rect(7.3,11,8.7,12.2))
@icon("chevron_down")
def _(): return union(line(4,6.5,8,10.5,1.8), line(8,10.5,12,6.5,1.8))
@icon("close")
def _(): return union(line(4,4,12,12,1.8), line(12,4,4,12,1.8))
@icon("plus")
def _(): return union(line(8,3,8,13,1.8), line(3,8,13,8,1.8))
@icon("trash")
def _(): return union(outline(poly([(3.5,4.5),(12.5,4.5),(11.5,14.5),(4.5,14.5)]), poly([(5,6),(11,6),(10.2,13),(5.8,13)])), rect(2,3,14,4.4), rect(6,1.5,10,3), rect(7.3,7,8.7,12))
@icon("info")
def _(): return union(ring(8,8,6.2,W), rect(7.3,7,8.7,11.8), circle(8,4.9,1.0))
@icon("lock")
def _(): return union(rrect(3,7,13,14.5,1.2), arc(8,7,3.4,W,180,360), line(4.6,7,4.6,5.5,W), line(11.4,7,11.4,5.5,W))
@icon("star")
def _():
    pts = []
    for i in range(10):
        r = 6.8 if i % 2 == 0 else 2.9
        a = math.radians(-90 + i*36); pts.append((8 + r*math.cos(a), 8.4 + r*math.sin(a)))
    return poly(pts)
@icon("folder")
def _(): return union(outline(rrect(1.5,4,14.5,13.5,1.2), rrect(3.1,6.6,12.9,11.9,0.4)), rrect(1.5,2.5,7,5,1))
@icon("play")
def _(): return poly([(4.5,2.5),(13,8),(4.5,13.5)])
@icon("stop")
def _(): return rrect(3.5,3.5,12.5,12.5,1.2)

def render(path):
    cols = 8; rows = math.ceil(len(ICONS)/cols)
    w, h = cols*CELL, rows*CELL
    px = bytearray(w*h*4)
    for idx, (name, make) in enumerate(ICONS):
        f = make(); ox, oy = (idx % cols)*CELL, (idx//cols)*CELL
        for y in range(CELL):
            for x in range(CELL):
                cov = sum(f(x+(sx+.5)/SS, y+(sy+.5)/SS) for sy in range(SS) for sx in range(SS)) / (SS*SS)
                i = ((oy+y)*w + ox+x)*4
                px[i:i+4] = bytes([255, 255, 255, round(cov*255)])
    raw = b''.join(b'\x00' + bytes(px[y*w*4:(y+1)*w*4]) for y in range(h))
    chunk = lambda t, d: struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t+d) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                           + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
    print(f"{len(ICONS)} icons -> {path} ({w}x{h})")
    print(",".join(n for n, _ in ICONS))

if __name__ == '__main__':
    render(sys.argv[1])
