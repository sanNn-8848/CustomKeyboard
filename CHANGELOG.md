# MeroType Changelog

## [1.1.2] - 2026-09-10

### Changed
- Gboard-style keyboard layout: QWERTY letters first, `?123` symbols on second page
- Symmetric home row (equal spacer on both sides of A–L)
- Enter icon is now a right arrow (→)
- Bottom row `?123 , space . →` clears system navigation/gesture bar in all orientations
- Key styling per design: `#2C2C2C` letters / `#3C3C3C` actions, 8dp rounded corners, `#121212` background
- Haptic feedback on every key press
- Suggestion row: settings gear added, compact 48dp bar
- Commas (` , `) now end the current word correctly

## [1.1.1] - 2026-09-10

### Changed
- Fixed fullscreen keyboard bug after rotation (removed duplicate inset handling)
- App branded as **MeroType** (launcher label + keyboard name)
- New keyboard-glyph launcher icon

## [1.1.0] - 2026-09-09

### Added
- Two-tier smart suggestions (prefix + fuzzy + bigram + learned words, then trigram char-model fill)
- Shipped trained model `assets/char_model.json`
- GitHub Actions release signing + auto-release of `MeroType.apk` on tag push
- Release keystore (`merotype-release`) + signing secrets

## [1.0.1] - 2026-09-06

### Added
- Initial working Android keyboard (Roman Nepali)
- Suggestion bar, symbols page, basic Roman Nepali word predictions