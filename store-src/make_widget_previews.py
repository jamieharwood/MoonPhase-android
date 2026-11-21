"""Renders the widget-picker previews (approximations of the Glance widgets).

Run from the repo root:  python3 store-src/make_widget_previews.py
"""
import math
from PIL import Image, ImageDraw, ImageFont

OUT = "app/src/main/res/drawable-nodpi"
NIGHT = (11, 21, 48)
LIT, CRATER, SHADOW = (237, 230, 204), (196, 191, 177), (8, 13, 26)
CRATERS = [(-0.35, -0.30, 0.16), (0.30, -0.42, 0.10), (0.18, 0.05, 0.22),
           (-0.20, 0.38, 0.13), (0.48, 0.30, 0.09), (-0.55, 0.05, 0.08)]
F = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def moon(size, phase=0.39):
    s = 3
    n = size * s
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = img.load()
    r = n * 0.46
    k = math.cos(2 * math.pi * phase)
    for j in range(n):
        y = (j + 0.5 - n / 2) / r
        if abs(y) > 1:
            continue
        h = math.sqrt(1 - y * y)
        for i in range(n):
            x = (i + 0.5 - n / 2) / r
            if abs(x) > h:
                continue
            lit = x > k * h if phase <= 0.5 else x < -k * h
            cr = any((x - a) ** 2 + (y - b) ** 2 < c * c for a, b, c in CRATERS)
            col = (CRATER if cr else LIT) if lit else (36, 40, 52)
            px[i, j] = col + (255,)
    return img.resize((size, size), Image.LANCZOS)


def frame(w, h):
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle([0, 0, w - 1, h - 1], radius=48, fill=NIGHT + (255,))
    return img


def centred(d, text, y, font, fill, w):
    d.text(((w - d.textlength(text, font=font)) / 2, y), text, font=font, fill=fill)


def moon_widget():
    w = h = 330
    img = frame(w, h)
    m = moon(200)
    img.paste(m, ((w - 200) // 2, 28), m)
    d = ImageDraw.Draw(img)
    centred(d, "Waxing Gibbous", 238, ImageFont.truetype(FB, 32), (255, 255, 255), w)
    centred(d, "93% · full in 3d", 280, ImageFont.truetype(F, 26), (244, 227, 166), w)
    img.save(f"{OUT}/widget_preview_moon.png", optimize=True)


def sky_widget():
    w, h = 750, 330
    img = frame(w, h)
    m = moon(280)
    img.paste(m, (25, 25), m)
    d = ImageDraw.Draw(img)
    x = 335
    d.text((x, 40), "Waxing Gibbous", font=ImageFont.truetype(FB, 44), fill=(255, 255, 255))
    d.text((x, 98), "93% lit · 12 days old", font=ImageFont.truetype(F, 30), fill=(244, 227, 166))
    small = ImageFont.truetype(F, 30)
    for i, t in enumerate(["Full moon in 3 days", "Sun 07:38 – 16:02", "Aurora Kp 0.7 · Quiet"]):
        d.text((x, 160 + i * 46), t, font=small, fill=(184, 194, 217))
    img.save(f"{OUT}/widget_preview_sky.png", optimize=True)


if __name__ == "__main__":
    moon_widget()
    sky_widget()
