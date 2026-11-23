"""Renders the Play Store icon (512x512) and feature graphic (1024x500) for the watch face app.

Run from the repo root:  python3 store-src/make_watchface_graphics.py
Both are built from watchface/store/screenshot-active.png and the face's own Barlow font.
"""
from PIL import Image, ImageDraw, ImageFont

STORE = "watchface/store"
FONT = "watchface/src/main/res/font/barlow_semibold.ttf"
FONT_MED = "watchface/src/main/res/font/barlow_medium.ttf"
INK, GREY, MOON = (236, 236, 236), (150, 150, 150), (244, 227, 166)


def round_face(size):
    """The active screenshot cropped to a circle, `size` px across."""
    s = 4
    face = Image.open(f"{STORE}/screenshot-active.png").convert("RGB").resize((size * s, size * s), Image.LANCZOS)
    mask = Image.new("L", face.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, face.width - 1, face.height - 1], fill=255)
    out = Image.new("RGBA", face.size, (0, 0, 0, 0))
    out.paste(face, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def ring(draw, box, width, colour):
    draw.ellipse(box, outline=colour, width=width)


def icon():
    # Play masks the icon itself, so fill the whole square.
    img = Image.new("RGBA", (512, 512), (0, 0, 0, 255))
    face = round_face(470)
    img.paste(face, (21, 21), face)
    ring(ImageDraw.Draw(img), [19, 19, 492, 492], 3, (70, 70, 70))
    img.convert("RGB").save(f"{STORE}/icon.png")


def feature():
    w, h = 1024, 500
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    # A faint orbit line behind the face, like the dial's arc.
    ring(d, [560, -40, 1140, 540], 2, (40, 40, 40))
    face = round_face(420)
    img.paste(face, (w - 420 - 70, 40), face)
    ring(d, [w - 420 - 72, 38, w - 70 + 2, 462], 2, (70, 70, 70))

    d.text((70, 150), "ORBITLY", font=ImageFont.truetype(FONT_MED, 34), fill=MOON)
    d.text((66, 186), "Mission", font=ImageFont.truetype(FONT, 104), fill=INK)
    d.text((70, 318), "A Moon phase watch face", font=ImageFont.truetype(FONT_MED, 34), fill=GREY)
    d.text((70, 360), "for Wear OS", font=ImageFont.truetype(FONT_MED, 34), fill=GREY)
    img.convert("RGB").save(f"{STORE}/featureGraphic.png")


if __name__ == "__main__":
    icon()
    feature()
