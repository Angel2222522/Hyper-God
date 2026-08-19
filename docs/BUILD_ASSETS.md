# Pinned build assets

The application has no runtime download path. Three large assets are fetched only at build time to keep the source repository lean while preserving reproducibility.

`preparePinnedAssets` downloads from immutable Git commits and refuses the build unless size and SHA-256 match:

- Tesseract `ell.traineddata` and `eng.traineddata` from `tesseract-ocr/tessdata_fast` commit `a8ba5063ab8013372a20e300da0c97ee46b92b07`.
- Noto Sans variable font from `google/fonts` commit `e1118da94a8cb00cf6d06cdac9ef13eb1e5c6ab7`.

After build, both OCR languages and the font are embedded in the APK. The app itself has no `INTERNET` permission and works offline.
