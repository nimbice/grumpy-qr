"""
The Grumpy QR logo, drawn from one set of shapes: two QR finder patterns as
half-lidded eyes, with the letters "QR" as the mouth.

    pip install pillow
    python tools/store_assets.py

Writes:
    fastlane/metadata/android/en-US/images/icon.png            512 x 512   (Play "App icon")
    fastlane/metadata/android/en-US/images/featureGraphic.png  1024 x 500  (Play "Feature graphic")
    app/src/main/res/drawable/ic_launcher_foreground.xml       adaptive icon foreground
    app/src/main/res/drawable/ic_launcher_monochrome.xml       Android 13+ themed icon
    app/src/main/res/drawable/ic_tile_scan.xml                 Quick Settings tile

All coordinates are on the 108 x 108 adaptive-icon canvas. Everything stays
inside the 66-unit safe zone so no launcher mask clips it.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
IMAGES = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images"
DRAWABLE = ROOT / "app" / "src" / "main" / "res" / "drawable"

MUSTARD = (244, 196, 48)
INK = (31, 26, 16)
SUPERSAMPLE = 4

EYE = 19       # finder-pattern eye size
STROKE = 3.8   # letter stroke


def shapes():
    """The logo as simple primitives shared by the PNG and vector renderers."""
    out = []
    s = EYE / 20
    top = 28
    for ex in (54 - 3 - EYE, 54 + 3):  # finder-pattern eyes
        out.append(("ring", ex, top, ex + EYE, top + EYE, 5 * s,
                    ex + 4.5 * s, top + 4.5 * s, ex + EYE - 4.5 * s, top + EYE - 4.5 * s, 2.5 * s))
        out.append(("pupil", ex + 7 * s, top + 10 * s, ex + 13 * s, top + 13 * s, s))  # half-lidded: flat top

    # Flat, unimpressed mouth.
    mouth_top = top + EYE + 6
    out.append(("rrect", 41, mouth_top, 67, mouth_top + 4.5, 2.25))

    # "QR" underneath, centred.
    ltop, h, w, letter_w, gap = mouth_top + 4.5 + 6, 14, STROKE, 12.5, 3.5
    k = h / 17
    hw = w / 2
    x = 54 - (2 * letter_w + gap) / 2
    y0, y1 = ltop + hw, ltop + h - hw
    # Q: a rounded-square ring like the eyes, plus a tail.
    qx0, qx1 = x + hw, x + letter_w - hw
    out.append(("stroke_rrect", qx0, y0, qx1, y1, 3.5 * k, w))
    out.append(("line", qx1 - 3.5 * k, y1 - 3.5 * k, qx1 + 1.5 * k, y1 + 2 * k, w))
    # R: bowl, stem and leg.
    rx = x + letter_w + gap
    sx = rx + hw
    bowl_bottom = ltop + h * 0.56
    out.append(("stroke_rrect", sx, y0, rx + letter_w - 3 * k, bowl_bottom, 3.5 * k, w))
    out.append(("line", sx, y0, sx, y1, w))
    out.append(("line", sx + 4.5 * k, bowl_bottom, rx + letter_w - hw, y1, w))
    return out


def glyph_bounds():
    """Bounding box (left, top, right, bottom) of the whole logo."""
    xs, ys = [], []
    for shape in shapes():
        kind = shape[0]
        if kind in ("ring", "pupil", "rrect"):
            xs += [shape[1], shape[3]]
            ys += [shape[2], shape[4]]
        elif kind == "stroke_rrect":
            w = shape[6] / 2
            xs += [shape[1] - w, shape[3] + w]
            ys += [shape[2] - w, shape[4] + w]
        elif kind == "line":
            w = shape[5] / 2
            xs += [shape[1] - w, shape[3] + w, shape[1] + w, shape[3] - w]
            ys += [shape[2] - w, shape[4] + w, shape[2] + w, shape[4] - w]
    return min(xs), min(ys), max(xs), max(ys)


GLYPH_LEFT, GLYPH_TOP, GLYPH_RIGHT, GLYPH_BOTTOM = glyph_bounds()


# --- PNG rendering -----------------------------------------------------------

def draw_logo(draw: ImageDraw.ImageDraw, scale: float, dx: float, dy: float) -> None:
    def box(x0, y0, x1, y1):
        return [x0 * scale + dx, y0 * scale + dy, x1 * scale + dx, y1 * scale + dy]

    def rr(x0, y0, x1, y1, r, fill):
        draw.rounded_rectangle(box(x0, y0, x1, y1), radius=max(r, 0) * scale, fill=fill)

    def dot(x, y, radius):
        rr(x - radius, y - radius, x + radius, y + radius, radius, INK)

    for shape in shapes():
        kind = shape[0]
        if kind == "ring":
            _, ox0, oy0, ox1, oy1, orad, ix0, iy0, ix1, iy1, irad = shape
            rr(ox0, oy0, ox1, oy1, orad, INK)
            rr(ix0, iy0, ix1, iy1, irad, MUSTARD)
        elif kind == "pupil":
            _, x0, y0, x1, y1, r = shape
            rr(x0, y0, x1, y1, r, INK)
            draw.rectangle(box(x0, y0, x1, y0 + r), fill=INK)
        elif kind == "rrect":
            _, x0, y0, x1, y1, r = shape
            rr(x0, y0, x1, y1, r, INK)
        elif kind == "stroke_rrect":
            _, x0, y0, x1, y1, r, w = shape
            rr(x0 - w / 2, y0 - w / 2, x1 + w / 2, y1 + w / 2, r + w / 2, INK)
            rr(x0 + w / 2, y0 + w / 2, x1 - w / 2, y1 - w / 2, r - w / 2, MUSTARD)
        elif kind == "line":
            _, x0, y0, x1, y1, w = shape
            draw.line([(x0 * scale + dx, y0 * scale + dy), (x1 * scale + dx, y1 * scale + dy)], fill=INK, width=round(w * scale))
            dot(x0, y0, w / 2)
            dot(x1, y1, w / 2)


def render(width: int, height: int, paint) -> Image.Image:
    big = Image.new("RGB", (width * SUPERSAMPLE, height * SUPERSAMPLE), MUSTARD)
    paint(ImageDraw.Draw(big), SUPERSAMPLE)
    return big.resize((width, height), Image.LANCZOS)


def font(size: int, bold: bool) -> ImageFont.FreeTypeFont:
    """
    Roboto (Apache-2.0), the same typeface the app's UI uses. Point
    ROBOTO_TTF at Roboto-Regular.ttf (the variable font from an Android
    system image or from github.com/googlefonts/roboto-3-classic) if it isn't
    installed. Falls back to DejaVu Sans, which is also freely licensed.
    """
    import os

    candidates = [
        os.environ.get("ROBOTO_TTF"),
        "C:/Windows/Fonts/Roboto-Regular.ttf",
        "/usr/share/fonts/truetype/roboto/unhinted/RobotoTTF/Roboto-Regular.ttf",
        str(Path.home() / "Library/Fonts/Roboto-Regular.ttf"),
    ]
    for candidate in filter(None, candidates):
        if Path(candidate).exists():
            f = ImageFont.truetype(candidate, size)
            try:
                f.set_variation_by_axes([700 if bold else 400, 100, 0])  # wght, wdth, ital
            except OSError:
                pass  # a static (non-variable) Roboto
            return f
    dejavu = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    if Path(dejavu).exists():
        return ImageFont.truetype(dejavu, size)
    raise SystemExit("No freely licensed font found. Set ROBOTO_TTF to a Roboto-Regular.ttf file.")


def icon():
    # Play shows the full square (it applies its own mask), so map the
    # launcher's visible 72x72 area onto 512x512.
    def paint(d, ss):
        s = 512 * ss / 72
        draw_logo(d, s, -18 * s, -18 * s)

    return render(512, 512, paint)


def feature_graphic():
    def paint(d, ss):
        s = 5.0 * ss
        glyph_h = GLYPH_BOTTOM - GLYPH_TOP
        draw_logo(d, s, 84 * ss - GLYPH_LEFT * s, (250 - glyph_h * 5.0 / 2) * ss - GLYPH_TOP * s)
        title = font(96 * ss, bold=True)
        subtitle = font(46 * ss, bold=True)
        tagline = font(36 * ss, bold=False)
        d.text((380 * ss, 222 * ss), "Grumpy QR", font=title, fill=INK, anchor="ls")
        d.text((384 * ss, 284 * ss), "Reader", font=subtitle, fill=INK, anchor="ls")
        d.text((384 * ss, 342 * ss), "Scans the code. That\u2019s it.", font=tagline, fill=INK, anchor="ls")

    return render(1024, 500, paint)


# --- Android vector drawables -----------------------------------------------

def _n(v: float) -> str:
    return f"{v:.2f}".rstrip("0").rstrip(".")


def _rrect_path(x0, y0, x1, y1, r) -> str:
    n = _n
    return (
        f"M{n(x0 + r)},{n(y0)} H{n(x1 - r)} A{n(r)},{n(r)} 0 0 1 {n(x1)},{n(y0 + r)} "
        f"V{n(y1 - r)} A{n(r)},{n(r)} 0 0 1 {n(x1 - r)},{n(y1)} "
        f"H{n(x0 + r)} A{n(r)},{n(r)} 0 0 1 {n(x0)},{n(y1 - r)} "
        f"V{n(y0 + r)} A{n(r)},{n(r)} 0 0 1 {n(x0 + r)},{n(y0)} Z"
    )


def _paths(color: str) -> str:
    n = _n
    out = []
    for shape in shapes():
        kind = shape[0]
        if kind == "ring":
            _, ox0, oy0, ox1, oy1, orad, ix0, iy0, ix1, iy1, irad = shape
            data = _rrect_path(ox0, oy0, ox1, oy1, orad) + " " + _rrect_path(ix0, iy0, ix1, iy1, irad)
            out.append(f'<path android:fillColor="{color}" android:fillType="evenOdd" android:pathData="{data}" />')
        elif kind == "pupil":
            _, x0, y0, x1, y1, r = shape
            data = (f"M{n(x0)},{n(y0)} H{n(x1)} V{n(y1 - r)} A{n(r)},{n(r)} 0 0 1 {n(x1 - r)},{n(y1)} "
                    f"H{n(x0 + r)} A{n(r)},{n(r)} 0 0 1 {n(x0)},{n(y1 - r)} Z")
            out.append(f'<path android:fillColor="{color}" android:pathData="{data}" />')
        elif kind == "rrect":
            _, x0, y0, x1, y1, r = shape
            out.append(f'<path android:fillColor="{color}" android:pathData="{_rrect_path(x0, y0, x1, y1, r)}" />')
        elif kind == "stroke_rrect":
            _, x0, y0, x1, y1, r, w = shape
            out.append(f'<path android:strokeColor="{color}" android:strokeWidth="{n(w)}" '
                       f'android:pathData="{_rrect_path(x0, y0, x1, y1, r)}" />')
        elif kind == "line":
            _, x0, y0, x1, y1, w = shape
            out.append(f'<path android:strokeColor="{color}" android:strokeWidth="{n(w)}" '
                       f'android:strokeLineCap="round" android:pathData="M{n(x0)},{n(y0)} L{n(x1)},{n(y1)}" />')
    return "\n".join("        " + p for p in out)


def _vector(comment: str, size_dp: int, viewport: int, body: str) -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Generated by tools/store_assets.py; edit the shapes there, not here. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size_dp}dp"
    android:height="{size_dp}dp"
    android:viewportWidth="{viewport}"
    android:viewportHeight="{viewport}">
{body}
</vector>
"""


def write_android_icons():
    group = "    <group>\n{}\n    </group>"
    DRAWABLE.joinpath("ic_launcher_foreground.xml").write_text(
        _vector("Launcher icon: grumpy finder-pattern eyes with \"QR\" for a mouth.", 108, 108,
                group.format(_paths("@color/brand_ink"))), encoding="utf-8", newline="\n")
    DRAWABLE.joinpath("ic_launcher_monochrome.xml").write_text(
        _vector("Themed (Android 13+) icon. The system recolours it.", 108, 108,
                group.format(_paths("#FFFFFFFF"))), encoding="utf-8", newline="\n")
    # Quick Settings tile: the logo scaled to fill a 24dp glyph.
    w, h = GLYPH_RIGHT - GLYPH_LEFT, GLYPH_BOTTOM - GLYPH_TOP
    scale = 23 / max(w, h)
    tx = (24 - w * scale) / 2 - GLYPH_LEFT * scale
    ty = (24 - h * scale) / 2 - GLYPH_TOP * scale
    tile_group = (f'    <group android:scaleX="{_n(scale)}" android:scaleY="{_n(scale)}" '
                  f'android:translateX="{_n(tx)}" android:translateY="{_n(ty)}">\n{_paths("#FFFFFFFF")}\n    </group>')
    DRAWABLE.joinpath("ic_tile_scan.xml").write_text(
        _vector("Quick Settings tile glyph.", 24, 24, tile_group), encoding="utf-8", newline="\n")


if __name__ == "__main__":
    IMAGES.mkdir(parents=True, exist_ok=True)
    icon().save(IMAGES / "icon.png")
    feature_graphic().save(IMAGES / "featureGraphic.png")
    write_android_icons()
    print("Wrote store images and Android icon drawables.")
