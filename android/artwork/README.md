# مُسجِّل bitmap icon

The supplied image is used for the launcher, recent-apps icon, and app header.

- `musajjil-icon-source.jpeg`: descriptively named copy of the supplied JPEG.
- `musajjil-icon-cutout.png`: built-in image editor's RGBA output.
- `musajjil-icon.png`: cropped, centered 1024 × 1024 transparent master.
- `previous-ledger-icon.xml`: previous vector, retained as design history.
- Android launcher bitmaps are in `app/src/main/res/mipmap-*`, at 48, 72, 96,
  144 and 192 pixels. The generated alpha is preserved during resizing.

Original input: `/home/work/Downloads/Gemini_Generated_Image_u5o6jju5o6jju5o6.jpeg`.
The original download is preserved. Bitmap inspection confirmed RGBA with both
fully transparent and opaque pixels. No APK was built or installed for this update.

Mode: built-in image generation/edit tool, transparent background enabled.
Prompt:

> Use case: background-extraction. Edit target: the supplied JPEG. Asset type:
> Android launcher icon bitmap for مُسجِّل. Remove the surrounding light gray
> canvas and the exterior cast shadow only, cut precisely around the outer
> rounded-square blue/teal plaque, and tightly crop to a square canvas with a
> small transparent margin. Preserve the ENTIRE existing icon artwork inside
> the plaque unchanged: its rounded bevel, navy-to-teal gradient, subtle line
> network, white calendar and three people, green checkmark, and lower-right
> gear. Do not redesign, redraw, simplify, add objects, add text, or recolor the
> icon. The blue rounded-square plaque remains opaque as part of the icon.
> Everything outside its outer silhouette must be truly transparent alpha,
> with clean antialiased edges and no gray fringe, matte, shadow, white
> background, or checkerboard drawn into the image. Return one high-quality
> square RGBA PNG, centered and suitable for small launcher sizes.

Final Android resource preparation only crops the transparent margins, centers
the bitmap, and downsizes it for display; it does not reconstruct the artwork.
