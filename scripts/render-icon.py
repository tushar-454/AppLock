"""Rasterize the AppLock geometric vector mark. Requires Pillow; no source images."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / 'assets/images'
scale = 2

def mark(background=True, monochrome=False):
    image = Image.new('RGBA', (1024 * scale, 1024 * scale), '#F3EDF7' if background else (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    color = '#FFFFFF' if monochrome else '#6750A4'
    def box(values): return tuple(int(v * scale) for v in values)
    # A rounded shackle, solid body, and punched-out keyhole.
    draw.rounded_rectangle(box((331, 173, 693, 586)), radius=181 * scale, fill=color)
    draw.rounded_rectangle(box((397, 239, 627, 586)), radius=115 * scale, fill='#F3EDF7' if background else (0, 0, 0, 0))
    draw.rounded_rectangle(box((292, 442, 732, 792)), radius=72 * scale, fill=color)
    cutout = '#F3EDF7' if background else (0, 0, 0, 0)
    draw.ellipse(box((474, 554, 550, 630)), fill=cutout)
    draw.rounded_rectangle(box((496, 592, 528, 693)), radius=16 * scale, fill=cutout)
    return image.resize((1024, 1024), Image.Resampling.LANCZOS)

mark().save(root / 'applock.png')
mark(False).save(root / 'applock-foreground.png')
mark(False, True).save(root / 'applock-monochrome.png')
