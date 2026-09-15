# MeroType Changelog

## [1.1.8] - 2026-09-14

### Fixed
- Backspace after a committed word no longer pollutes predictions: re-typing the same word shows the same suggestions again
- Suggestions no longer filled with meaningless generated words — character-model fills are capped when real matches already exist (e.g. typing "lage" no longer offers "lageet"/"longe")

### Added
- Suggestion management (suppression model): long-press a suggestion → tap the remove trash to hide it from future suggestions without losing your typing history
- Removing a suggestion is now reversible — the undo arrow restores it instantly
- Suppressed words are listed in Settings and can be restored or re-hidden individually

## [1.1.7] - 2026-09-14

### Changed
- Slimmer keyboard: letter keys ~48dp visible, number keys ~44dp (Gboard proportions), 2dp gaps
- Number row no longer stretches the keyboard taller than needed
- Landscape now scales rows down so the keyboard stays balanced and under ~55% of screen height

## [1.1.6] - 2026-09-14

### Fixed
- Uppercase, backspace, and enter icons now sit perfectly centered inside their keys (icons render as centered ImageViews instead of button compound drawables)

### Added
- Long-press any suggestion to reveal a remove overlay with a dim glowing LED indicator; tap the trash to delete that suggestion from your dictionary

## [1.1.5] - 2026-09-14

### Changed
- Keys restyled: letter keys 10dp rounded rectangle, function keys pill/capsule with warm coral tint
- Uppercase icon redesigned as a stacking chevron (filled + outline layer)
- Enter icon redesigned as a bold wrapped return path
- Icon centering fix for shift/enter/backspace
- Suggestion bar is now horizontally scrollable (up to 7 suggestions, smooth swipe)
- Enter key performs smart action (Done/Go/Send/Search in single-line fields, newline only in multi-line)

### Added
- Optional number row (off by default, toggled in Settings)

## [1.1.4] - 2026-09-14

### Added
- **FrostGlass** visual identity: 16dp rounded glass keys, coral accent, mountain silhouette + static glow, dark/light themes
- Layered suggestion ranking with confidence scores (dictionary, n-gram, typo, learned, personal, char-model, split/merge)
- Context-aware predictions (previous words) + sentence-start capitalisation + double-space → `. `
- 11 unit tests including a prediction latency benchmark (p50 ≈ 0.05 ms)

### Fixed
- Typo corrections no longer flood common prefixes with 1-2 edit "noise" words
- Fill suggestions no longer dilute confidence scores

## [1.1.3] - 2026-09-13

### Added
- Grammar-aware split/merge suggestions (e.g. "meroke" → "mero ke")
- Clipboard manager + settings UI
- Undo everywhere (keyboard + suggestion bar)
- Redesigned settings header

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