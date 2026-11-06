# AppLock branding

Original geometric shield with a lock formed from negative space. All shapes are editable paths, with no fonts, gradients or external artwork.

| Asset | Purpose |
| --- | --- |
| `applock.svg` | Editable master geometry, 1024 × 1024, transparent |
| `icon.png` | Opaque 1024 × 1024 launcher source, lavender background |
| `adaptive-foreground.png` | Transparent 1024 × 1024 adaptive foreground |
| `adaptive-monochrome.png` | White alpha-mask layer, 1024 × 1024, transparent lock cutout |
| `splash.png` | Transparent 288 × 288 light splash logo |
| `splash-dark.png` | Transparent 288 × 288 dark splash logo |

Each PNG has a derived SVG beside it. Edit `applock.svg`, then regenerate with `python3 scripts/render-branding.py`. The renderer uses system librsvg and Cairo (`librsvg2-2` and `libcairo2` on Debian/Ubuntu), without adding a mobile dependency.

The adaptive layers share the same geometry and alignment. All visible pixels fit inside Android's central 66 dp safe circle in the 108 dp canvas: measured radius 285.56 px, below the 312.89 px limit. Transparent padding must remain intact; do not crop adaptive assets. The launcher owns the final icon mask.

Colors: primary `#6750A4`, launcher container `#EADDFF`, light splash `#FFFBFE`, dark splash `#141218`, dark logo `#D0BCFF`. Splash assets crop the master canvas to 608 × 608 vector units before rendering, so `imageWidth: 88` produces an approximately 82 dp high symbol. The official Expo splash plugin centers it in Android's native splash canvas.

References: [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), [Expo SDK 58 splash plugin](https://docs.expo.dev/versions/v58.0.0/sdk/splash-screen/).
