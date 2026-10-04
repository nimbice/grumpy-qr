"""
Renders the Google Play / F-Droid store graphics from the same geometry as
the launcher icon (app/src/main/res/drawable/ic_launcher_foreground.xml).

    pip install pillow
    python tools/store_assets.py

Writes into fastlane/metadata/android/en-US/images/:
    icon.png            512 x 512   (Play "App icon")
    featureGraphic.png  1024 x 500  (Play "Feature graphic")
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

MUSTARD = (244, 196, 48)
INK = (31, 26, 16)
SUPERSAMPLE = 4
OUT = Path(__file__).resolve().parent.parent / "fastlane" / "metadata" / "android" / "en-US" / "images"


def draw_face(draw: ImageDraw.ImageDraw, scale: float, dx: float, dy: float) -> None:
    """Draws the face using the 108x108 adaptive-icon coordinates."""

    def rr(x0, y0, x1, y1, r, fill):
        draw.rounded_rectangle(
            [x0 * scale + dx, y0 * scale + dy, x1 * scale + dx, y1 * scale + dy],
            radius=r * scale,
            fill=fill,
        )

    def rect(x0, y0, x1, y1, fill):
        draw.rectangle([x0 * scale + dx, y0 * scale + dy, x1 * scale + dx, y1 * scale + dy], fill=fill)

    for ex in (31, 57):  # two finder-pattern eyes
        rr(ex, 31, ex + 20, 51, 5, INK)
        rr(ex + 4.5, 35.5, ex + 15.5, 46.5, 2.5, MUSTARD)
        # half-lidded pupil: flat top, rounded bottom
        rr(ex + 7, 41, ex + 13, 44, 1, INK)
        rect(ex + 7, 41, ex + 13, 42, INK)
    rr(40, 64, 68, 69, 2.5, INK)  # flat mouth


def render(width: int, height: int, paint) -> Image.Image:
    big = Image.new("RGB", (width * SUPERSAMPLE, height * SUPERSAMPLE), MUSTARD)
    paint(ImageDraw.Draw(big), SUPERSAMPLE)
    return big.resize((width, height), Image.LANCZOS)


def font(names, size):
    for name in names:
        for folder in (Path("C:/Windows/Fonts"), Path("/usr/share/fonts/truetype/dejavu"), Path("/Library/Fonts")):
            path = folder / name
            if path.exists():
                return ImageFont.truetype(str(path), size)
    return ImageFont.load_default(size)


def icon():
    # Play shows the full square (it applies its own mask), so map the
    # launcher's visible 72x72 area onto 512x512.
    def paint(d, ss):
        s = 512 * ss / 72
        draw_face(d, s, -18 * s, -18 * s)

    return render(512, 512, paint)


def feature_graphic():
    def paint(d, ss):
        s = 5.2 * ss
        glyph_left, glyph_top = 84 * ss, (250 - 19 * 5.2) * ss  # glyph is 46 x 38 units
        draw_face(d, s, glyph_left - 31 * s, glyph_top - 31 * s)
        title = font(["segoeuib.ttf", "DejaVuSans-Bold.ttf", "Arial Bold.ttf"], 96 * ss)
        subtitle = font(["segoeuib.ttf", "DejaVuSans-Bold.ttf", "Arial Bold.ttf"], 46 * ss)
        tagline = font(["segoeui.ttf", "DejaVuSans.ttf", "Arial.ttf"], 36 * ss)
        d.text((380 * ss, 222 * ss), "Grumpy QR", font=title, fill=INK, anchor="ls")
        d.text((384 * ss, 284 * ss), "Reader", font=subtitle, fill=INK, anchor="ls")
        d.text((384 * ss, 342 * ss), "Scans the code. That\u2019s it.", font=tagline, fill=INK, anchor="ls")

    return render(1024, 500, paint)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    icon().save(OUT / "icon.png")
    feature_graphic().save(OUT / "featureGraphic.png")
    print(f"Wrote {OUT / 'icon.png'} and {OUT / 'featureGraphic.png'}")
