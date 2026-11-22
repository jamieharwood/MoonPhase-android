"""Generates the Orbitly Mission watch face (Watch Face Format v1).

Run from the repo root:   python3 watchface/tools/generate.py

Writes:
  watchface/src/main/res/raw/watchface.xml          the watch face definition
  watchface/src/main/res/drawable-nodpi/moon_NN.png 29 moon images, one per [MOON_PHASE_POSITION]
  watchface/src/main/res/drawable-nodpi/dial.png    minute/hour tick ring
  watchface/src/main/res/drawable-nodpi/preview.png picker preview (also the app icon)

Design: black, white and greys only, thin lines, condensed upper-case type, a mission-control
style clock with a UTC readout. Five complication slots default to the Orbitly watch app's
astronomy data sources and fall back to system ones when that app isn't installed.
"""
import math
import os
from xml.sax.saxutils import escape

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "res")
RAW = os.path.join(ROOT, "raw")
DRAW = os.path.join(ROOT, "drawable-nodpi")
FONT_DIR = os.path.join(ROOT, "font")

SIZE = 450
C = SIZE / 2
SYNODIC = 29.530588

WHITE = "#FFFFFFFF"
GREY = "#FF9AA0A6"
DIM = "#FF5F6368"
FAINT = "#FF2A2D31"

WEAR_PKG = "io.github.jamieharwood.moonphase"
WEAR_CLS = "io.github.jamieharwood.moonphase.wear"

PHASE_NAMES = [  # [MOON_PHASE_TYPE] 0..7
    "NEW MOON", "WAXING CRESCENT", "FIRST QUARTER", "WAXING GIBBOUS",
    "FULL MOON", "WANING GIBBOUS", "LAST QUARTER", "WANING CRESCENT",
]
DAYS = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"]  # [DAY_OF_WEEK] 1..7
MONTHS = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]

MOON_BOX = (175, 40, 100)
UTC_H = "floor([UTC_TIMESTAMP] / 3600000) % 24"
UTC_M = "floor([UTC_TIMESTAMP] / 60000) % 60"  # x, y, size

# slotId, name, string, x, y, size, primary service, primary type, fallback system provider, fallback type
SLOTS = [
    (0, "left", "slot_left", 28, 192, 70, "IlluminationComplicationService", "RANGED_VALUE", "WATCH_BATTERY", "RANGED_VALUE"),
    (1, "right", "slot_right", 352, 192, 70, "FullMoonComplicationService", "RANGED_VALUE", "DATE", "SHORT_TEXT"),
    (2, "bottomLeft", "slot_bottom_left", 116, 338, 66, "AuroraComplicationService", "RANGED_VALUE", "STEP_COUNT", "SHORT_TEXT"),
    (3, "bottom", "slot_bottom", 192, 352, 66, "IssComplicationService", "SHORT_TEXT", "SUNRISE_SUNSET", "SHORT_TEXT"),
    (4, "bottomRight", "slot_bottom_right", 268, 338, 66, "SunComplicationService", "SHORT_TEXT", "WATCH_BATTERY", "SHORT_TEXT"),
]


# ---------------------------------------------------------------- images

def moon_image(position: int, size: int = 200) -> Image.Image:
    """Moon `position` days after new moon (northern-hemisphere view), with a faint earthshine side."""
    p = (position % 30) / SYNODIC
    s = 3  # supersample
    n = size * s
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    px = img.load()
    r = n * 0.47
    craters = [(-0.35, -0.30, 0.16), (0.30, -0.42, 0.10), (0.18, 0.05, 0.22),
               (-0.20, 0.38, 0.13), (0.42, 0.30, 0.09), (-0.48, 0.08, 0.08)]
    cos_p = math.cos(2 * math.pi * p)
    for j in range(n):
        y = (j + 0.5 - n / 2) / r
        if abs(y) > 1:
            continue
        half = math.sqrt(1 - y * y)
        for i in range(n):
            x = (i + 0.5 - n / 2) / r
            if abs(x) > half:
                continue
            lit = x > cos_p * half if p <= 0.5 else x < -cos_p * half
            crater = any((x - cx) ** 2 + (y - cy) ** 2 < cr * cr for cx, cy, cr in craters)
            if lit:
                v = 200 if crater else 236
                px[i, j] = (v, v, v - 4, 255)
            else:
                v = 30 if crater else 36
                px[i, j] = (v, v + 2, v + 6, 255)
    ring = ImageDraw.Draw(img)
    ring.ellipse([n / 2 - r, n / 2 - r, n / 2 + r, n / 2 + r], outline=(90, 94, 100, 255), width=s)
    return img.resize((size, size), Image.LANCZOS)


def dial_image() -> Image.Image:
    s = 3
    n = SIZE * s
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = n / 2
    for k in range(60):
        a = math.radians(k * 6)
        major = k % 5 == 0
        r_out = (SIZE / 2 - 6) * s
        r_in = r_out - (14 if major else 6) * s
        col = (255, 255, 255, 255) if major else (95, 99, 104, 255)
        w = (3 if major else 2) * s // 2
        d.line([(c + r_in * math.sin(a), c - r_in * math.cos(a)),
                (c + r_out * math.sin(a), c - r_out * math.cos(a))], fill=col, width=w)
    return img.resize((SIZE, SIZE), Image.LANCZOS)


def preview_image() -> Image.Image:
    """Approximation of the face at 22:10, used in the watch face picker."""
    img = Image.new("RGB", (SIZE, SIZE), (0, 0, 0))
    img.paste(dial_image(), (0, 0), dial_image())
    d = ImageDraw.Draw(img)
    semi = lambda sz: ImageFont.truetype(os.path.join(FONT_DIR, "barlow_semibold.ttf"), sz)
    med = lambda sz: ImageFont.truetype(os.path.join(FONT_DIR, "barlow_medium.ttf"), sz)
    # seconds arc (at :40)
    d.arc([7, 7, SIZE - 7, SIZE - 7], -90, -90 + 240, fill=(255, 255, 255), width=2)
    mx, my, ms = MOON_BOX
    moon = moon_image(11, ms)
    img.paste(moon, (mx, my), moon)

    def centred(text, y, font, fill, cx=C):
        w = d.textlength(text, font=font)
        d.text((cx - w / 2, y), text, font=font, fill=fill)

    centred("WAXING GIBBOUS", 146, med(19), (154, 160, 166))
    centred("22:10", 158, semi(112), (255, 255, 255))
    centred("SAT 21 NOV", 288, med(22), (255, 255, 255))
    centred("UTC 22:10", 313, med(17), (154, 160, 166))
    demo = {0: ("91%", "LIT", 0.91), 1: ("3D", "FULL", 0.9), 2: ("2.7", "KP", 0.3),
            3: ("418", "ISS KM", None), 4: ("07:38", "SUNRISE", None)}
    for sid, _, _, x, y, sz, *_ in SLOTS:
        text, title, frac = demo[sid]
        box = [x + 2, y + 2, x + sz - 2, y + sz - 2]
        d.ellipse(box, outline=(42, 45, 49), width=2)
        if frac is not None:
            d.arc(box, -90, -90 + 360 * frac, fill=(255, 255, 255), width=2)
        centred(text, y + sz * 0.22, semi(int(sz * 0.32)), (255, 255, 255), x + sz / 2)
        centred(title, y + sz * 0.60, med(int(sz * 0.16)), (154, 160, 166), x + sz / 2)
    return img


# ---------------------------------------------------------------- XML helpers

def font(size, color=WHITE, family="barlow_medium", spacing=None, inner=""):
    ls = f' letterSpacing="{spacing}"' if spacing is not None else ""
    return f'<Font family="{family}" size="{size}" color="{color}"{ls}>{inner}</Font>'


def template(fmt, *exprs):
    params = "".join(f'<Parameter expression="{escape(e)}"/>' for e in exprs)
    return f"<Template>{escape(fmt)}{params}</Template>"


def part_text(name, x, y, w, h, font_xml, align="CENTER"):
    return (f'<PartText name="{name}" x="{x}" y="{y}" width="{w}" height="{h}">'
            f'<Text align="{align}" ellipsis="TRUE">{font_xml}</Text></PartText>')


def condition(name_prefix, expr_for, cases, default):
    """cases: list of (value, element_xml). Picks the element whose expression matches."""
    exprs = "".join(f'<Expression name="{name_prefix}{v}"><![CDATA[{expr_for(v)}]]></Expression>' for v, _ in cases)
    compares = "".join(f'<Compare expression="{name_prefix}{v}">{el}</Compare>' for v, el in cases)
    return f"<Condition><Expressions>{exprs}</Expressions>{compares}<Default>{default}</Default></Condition>"


def ambient_alpha(value):
    return f'<Variant mode="AMBIENT" target="alpha" value="{value}"/>'


# ---------------------------------------------------------------- watch face

def moon_group():
    x, y, s = MOON_BOX
    def img(pos):
        return (f'<PartImage name="moon{pos}" x="{x}" y="{y}" width="{s}" height="{s}">'
                f'<Image resource="moon_{pos:02d}"/></PartImage>')
    cond = condition("moon", lambda v: f"[MOON_PHASE_POSITION] < {v + 1}",
                     [(v, img(v)) for v in range(29)], img(0))
    names = condition("phase", lambda v: f"[MOON_PHASE_TYPE] == {v}",
                      [(v, part_text(f"phase{v}", 75, 146, 300, 24, font(19, GREY, spacing=0.12, inner=n)))
                       for v, n in enumerate(PHASE_NAMES)],
                      part_text("phaseDefault", 75, 146, 300, 24, font(19, GREY, spacing=0.12, inner="MOON")))
    return (f'<Group name="moon" x="0" y="0" width="{SIZE}" height="{SIZE}">'
            f'{ambient_alpha(150)}{cond}{names}</Group>')


def clock_group():
    time = ('<DigitalClock x="85" y="160" width="280" height="122">'
            '<TimeText x="0" y="0" width="280" height="122" format="hh:mm" hourFormat="SYNC_TO_DEVICE" align="CENTER">'
            f'<Font family="barlow_semibold" size="112" color="{WHITE}" letterSpacing="0.02"/>'
            '</TimeText></DigitalClock>')
    weekday = condition("dow", lambda v: f"[DAY_OF_WEEK] == {v}",
                        [(i + 1, part_text(f"dow{i}", 95, 288, 127, 28, font(22, WHITE, spacing=0.1, inner=d), "END"))
                         for i, d in enumerate(DAYS)],
                        part_text("dowDefault", 95, 288, 127, 28, font(22, WHITE, spacing=0.1, inner=""), "END"))
    month = condition("mon", lambda v: f"[MONTH] == {v}",
                      [(i + 1, part_text(f"mon{i}", 228, 288, 124, 28,
                                         font(22, WHITE, spacing=0.1, inner=template(f"%s {m}", "[DAY]")), "START"))
                       for i, m in enumerate(MONTHS)],
                      part_text("monDefault", 228, 288, 124, 28, font(22, WHITE, spacing=0.1, inner=template("%s", "[DAY]")), "START"))
    utc_label = part_text("utcLabel", 135, 312, 87, 22, font(17, GREY, spacing=0.12, inner="UTC"), "END")
    utc = part_text("utc", 228, 312, 90, 22,
                    font(17, GREY, spacing=0.12, inner=template("%02d:%02d", UTC_H, UTC_M)), "START")
    date_group = (f'<Group name="date" x="0" y="0" width="{SIZE}" height="{SIZE}">'
                  f'{ambient_alpha(170)}{weekday}{month}{utc_label}{utc}</Group>')
    return time + date_group


def seconds_and_dial():
    dial = (f'<PartImage name="dial" x="0" y="0" width="{SIZE}" height="{SIZE}">'
            f'{ambient_alpha(90)}<Image resource="dial"/></PartImage>')
    arc = (f'<PartDraw name="seconds" x="0" y="0" width="{SIZE}" height="{SIZE}">'
           f'{ambient_alpha(0)}'
           f'<Arc centerX="{C}" centerY="{C}" width="{SIZE - 14}" height="{SIZE - 14}" startAngle="0" endAngle="0" direction="CLOCKWISE">'
           f'<Transform target="endAngle" value="[SECOND] * 6"/>'
           f'<Stroke color="{WHITE}" thickness="2" cap="BUTT"/></Arc></PartDraw>')
    return dial + arc


def complication_slot(slot):
    sid, name, label, x, y, s, primary, ptype, sysdef, systype = slot
    c = s / 2
    ring_d = s - 4
    ring = (f'<PartDraw name="ring{sid}" x="0" y="0" width="{s}" height="{s}">'
            f'<Arc centerX="{c}" centerY="{c}" width="{ring_d}" height="{ring_d}" startAngle="0" endAngle="360" direction="CLOCKWISE">'
            f'<Stroke color="{FAINT}" thickness="2" cap="BUTT"/></Arc></PartDraw>')
    frac = ("clamp(([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / "
            "([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]), 0, 1)")
    progress = (f'<PartDraw name="progress{sid}" x="0" y="0" width="{s}" height="{s}">'
                f'{ambient_alpha(120)}'
                f'<Arc centerX="{c}" centerY="{c}" width="{ring_d}" height="{ring_d}" startAngle="0" endAngle="0" direction="CLOCKWISE">'
                f'<Transform target="endAngle" value="{escape(frac)} * 360"/>'
                f'<Stroke color="{WHITE}" thickness="2" cap="BUTT"/></Arc></PartDraw>')

    def texts(sid_suffix):
        value = part_text(f"value{sid}{sid_suffix}", 4, round(s * 0.18), s - 8, round(s * 0.40),
                          font(round(s * 0.32), WHITE, "barlow_semibold",
                               inner=template("%s", "[COMPLICATION.TEXT]")))
        title = part_text(f"title{sid}{sid_suffix}", 6, round(s * 0.58), s - 12, round(s * 0.22),
                          font(round(s * 0.16), GREY, spacing=0.08,
                               inner=template("%s", "[COMPLICATION.TITLE]")))
        return value + title

    short = f'<Complication type="SHORT_TEXT">{ring}{texts("s")}</Complication>'
    ranged = f'<Complication type="RANGED_VALUE">{ring}{progress}{texts("r")}</Complication>'
    return (f'<ComplicationSlot slotId="{sid}" name="{name}" displayName="@string/{label}" '
            f'supportedTypes="SHORT_TEXT RANGED_VALUE" x="{x}" y="{y}" width="{s}" height="{s}" isCustomizable="TRUE">'
            f'<BoundingOval x="0" y="0" width="{s}" height="{s}"/>'
            f'<DefaultProviderPolicy defaultSystemProvider="{sysdef}" defaultSystemProviderType="{systype}" '
            f'primaryProvider="{WEAR_PKG}/{WEAR_CLS}.{primary}" primaryProviderType="{ptype}"/>'
            f'{short}{ranged}</ComplicationSlot>')


def watchface_xml():
    body = (seconds_and_dial() + moon_group() + clock_group()
            + "".join(complication_slot(s) for s in SLOTS))
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- Generated by watchface/tools/generate.py: edit that script, not this file. -->\n'
           f'<WatchFace width="{SIZE}" height="{SIZE}">'
           '<Metadata key="CLOCK_TYPE" value="DIGITAL"/>'
           '<Metadata key="PREVIEW_TIME" value="22:10:40"/>'
           f'<Scene backgroundColor="#FF000000">{body}</Scene></WatchFace>\n')
    return pretty(xml)


def pretty(xml: str) -> str:
    """Line breaks only between elements, so text inside <Font>/<Template> keeps no stray whitespace."""
    import re
    import xml.dom.minidom as md
    md.parseString(xml.encode())  # fail fast on malformed XML
    tags = "Scene|Group|PartText|PartImage|PartDraw|Condition|Expressions|Expression|Compare|Default|" \
           "DigitalClock|ComplicationSlot|Complication|DefaultProviderPolicy|BoundingOval|Metadata|Variant"
    xml = re.sub(rf"><((?:{tags})\b)", r">\n<\1", xml)  # before opening tags
    # before closing tags of containers (never inside <Expression>, whose text is the expression)
    return re.sub(rf"><(/(?!Expression>)(?:{tags})\b)", r">\n<\1", xml)


def main():
    os.makedirs(RAW, exist_ok=True)
    os.makedirs(DRAW, exist_ok=True)
    for pos in range(29):
        moon_image(pos).save(os.path.join(DRAW, f"moon_{pos:02d}.png"), optimize=True)
    dial_image().save(os.path.join(DRAW, "dial.png"), optimize=True)
    preview_image().save(os.path.join(DRAW, "preview.png"), optimize=True)
    with open(os.path.join(RAW, "watchface.xml"), "w") as f:
        f.write(watchface_xml())
    print("generated watchface.xml, 29 moons, dial, preview")


if __name__ == "__main__":
    main()
