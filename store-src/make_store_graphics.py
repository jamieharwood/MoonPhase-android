"""Renders the Play Store icon (512x512) and feature graphic (1024x500).

Run from the repo root:  python3 store-src/make_store_graphics.py
The crescent is the same shape as the app's launcher icon (two circles from the original favicon).
"""
import math
import random
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = "fastlane/metadata/android/en-GB/images"
MOON = (244, 227, 166)
TOP, BOTTOM = (11, 31, 63), (3, 10, 26)
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def circle_centre(p1, p2, r, large, sweep):
    """Centre of an SVG arc from p1 to p2 with radius r (y down)."""
    (x1, y1), (x2, y2) = p1, p2
    mx, my = (x1 + x2) / 2, (y1 + y2) / 2
    dx, dy = x2 - x1, y2 - y1
    d = math.hypot(dx, dy)
    h = math.sqrt(max(r * r - (d / 2) ** 2, 0))
    ux, uy = -dy / d, dx / d
    sign = 1 if large != sweep else -1
    return mx + sign * h * ux, my + sign * h * uy


P1, P2 = (31.59, 10.06), (54.77, 43.38)
OUTER = (*circle_centre(P1, P2, 23, 1, 0), 23)
INNER = (*circle_centre(P1, P2, 20.3, 0, 1), 20.3)


def crescent(height):
    """RGBA crescent image, cropped to its bounds, `height` px tall."""
    s = 40  # supersample
    m = Image.new("L", (64 * s, 64 * s), 0)
    d = ImageDraw.Draw(m)
    for (cx, cy, r), v in ((OUTER, 255), (INNER, 0)):
        d.ellipse([(cx - r) * s, (cy - r) * s, (cx + r) * s, (cy + r) * s], fill=v)
    m = m.crop(m.getbbox())
    w = round(m.width * height / m.height)
    m = m.resize((w, height), Image.LANCZOS)
    img = Image.new("RGBA", m.size, MOON + (0,))
    img.putalpha(m)
    return img


def sky(w, h):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))
    return img


def paste_centred(base, im, cx, cy):
    base.paste(im, (round(cx - im.width / 2), round(cy - im.height / 2)), im)


def icon():
    img = sky(512, 512)
    paste_centred(img, crescent(300), 256, 256)
    img.save(f"{OUT}/icon.png")


def feature():
    w, h = 1024, 500
    img = sky(w, h)
    rnd = random.Random(7)
    d = ImageDraw.Draw(img)
    for _ in range(70):
        x, y, r = rnd.randrange(w), rnd.randrange(h), rnd.choice([1, 1, 1.5, 2])
        a = rnd.randrange(90, 220)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(a, a, min(255, a + 20)))
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([60, 60, 440, 440], fill=MOON + (60,))
    img.paste(glow.filter(ImageFilter.GaussianBlur(60)), (0, 0), glow.filter(ImageFilter.GaussianBlur(60)))
    paste_centred(img, crescent(320), 250, 250)
    d = ImageDraw.Draw(img)
    d.text((480, 160), "Orbitly", font=ImageFont.truetype(BOLD, 76), fill=(255, 255, 255))
    sub = ImageFont.truetype(REG, 30)
    d.text((484, 270), "Moon, Sun, aurora and ISS", font=sub, fill=(201, 211, 230))
    d.text((484, 312), "on your phone and watch", font=sub, fill=(201, 211, 230))
    img.save(f"{OUT}/featureGraphic.png")


if __name__ == "__main__":
    icon()
    feature()
