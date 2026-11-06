#!/usr/bin/env python3
"""Render the editable SVG using system librsvg/Cairo (no app dependency).

Run: python3 scripts/render-branding.py
Debian/Ubuntu renderer packages: librsvg2-2 libcairo2
"""
import ctypes as c
import ctypes.util
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets/branding"
MASTER = (ASSETS / "applock.svg").read_text()


class Rectangle(c.Structure):
    _fields_ = [(name, c.c_double) for name in ("x", "y", "width", "height")]


def bind(library, name, result, arguments):
    fn = getattr(library, name)
    fn.restype, fn.argtypes = result, arguments
    return fn


rsvg = c.CDLL(ctypes.util.find_library("rsvg-2"))
cairo = c.CDLL(ctypes.util.find_library("cairo"))
gobject = c.CDLL(ctypes.util.find_library("gobject-2.0"))
load = bind(rsvg, "rsvg_handle_new_from_data", c.c_void_p,
            [c.c_char_p, c.c_size_t, c.c_void_p])
render = bind(rsvg, "rsvg_handle_render_document", c.c_int,
              [c.c_void_p, c.c_void_p, c.POINTER(Rectangle), c.c_void_p])
surface_new = bind(cairo, "cairo_image_surface_create", c.c_void_p,
                   [c.c_int, c.c_int, c.c_int])
context_new = bind(cairo, "cairo_create", c.c_void_p, [c.c_void_p])
write_png = bind(cairo, "cairo_surface_write_to_png", c.c_int,
                 [c.c_void_p, c.c_char_p])
context_free = bind(cairo, "cairo_destroy", None, [c.c_void_p])
surface_free = bind(cairo, "cairo_surface_destroy", None, [c.c_void_p])
unref = bind(gobject, "g_object_unref", None, [c.c_void_p])


def export(name, size, *, color="#6750A4", background=None, crop=False):
    svg = MASTER.replace("#6750A4", color)
    if crop:
        svg = svg.replace('viewBox="0 0 1024 1024"', 'viewBox="208 208 608 608"')
    if background:
        svg = svg.replace('<path id="shield"',
                          f'<rect width="1024" height="1024" fill="{background}"/>'
                          '\n  <path id="shield"')
    # Keep each derived vector editable alongside the single master geometry.
    (ASSETS / f"{name}.svg").write_text(svg)
    data = svg.encode()
    handle = load(data, len(data), None)
    if not handle:
        raise RuntimeError(f"Invalid SVG: {name}")
    surface = surface_new(0, size, size)  # CAIRO_FORMAT_ARGB32, initially transparent
    context = context_new(surface)
    try:
        if not render(handle, context, c.byref(Rectangle(0, 0, size, size)), None):
            raise RuntimeError(f"Cannot render {name}")
        if write_png(surface, str(ASSETS / f"{name}.png").encode()):
            raise RuntimeError(f"Cannot write {name}")
    finally:
        context_free(context)
        surface_free(surface)
        unref(handle)
    print(f"{name}.png: {size} × {size}")


export("icon", 1024, background="#EADDFF")
export("adaptive-foreground", 1024)
export("adaptive-monochrome", 1024, color="#FFFFFF")
export("splash", 288, crop=True)
export("splash-dark", 288, crop=True, color="#D0BCFF")
