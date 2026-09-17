# MeroType Changelog

## [1.1.14] - 2026-09-16

### Fixed
- Tapping/committing a suggestion word no longer adds it to the clipboard history —
  the clipboard only stores words you actually copied (from this app or the system)
- Drop zones now span the full width as two cells (⭐ FAVORITE left / 🗑 DELETE right)
  sitting directly above the suggestion bar, so releasing anywhere in a half triggers
  that action — you no longer have to aim at tiny target circles
- The zone strip pops in clearly while long-pressing a suggestion and fades out on drop
- Suggestions are now real words only: removed fabricated dictionary entries
  (e.g. "chhaau", "mujhi", "digra", "bimala", "krya") and disabled char-model
  word-fabrication fills, so the suggestion bar no longer invents nonsense words

### Changed
- Let the view have more of the screen: key rows are shorter (50dp → 42dp letter rows),
  so the keyboard takes up less vertical space

## [1.1.13] - 2026-09-16

### Added
- Clipboard pane: each copied item now has a ✕ delete button to remove it from history
- The word chip that you drag now pops out and follows your finger exactly (free 1:1 drag)

### Fixed
- "Suggestion Edit Mode" strip was popping in as a permanent black row above the
  keyboard with a large dark void — it is now fully in-app: the two drop zones
  (⭐ FAVORITE left / 🗑 DELETE right) appear directly above the suggestion bar only
  while you are long-pressing a suggestion, and disappear as soon as you drop the
  word or let go
- The overlay can no longer get stuck open over the whole screen (even when not
  long-pressing): it force-hides after any dropped gesture, on keyboard switch,
  and on every new input session
- No full-screen dark overlay anymore — the zones are small, exposed cells over the
  suggestion strip, and only the keys dim slightly while dragging
- Older 1×1 wrapper issue with the suggestion overlay that stretched the IME window

### Changed
- Dropping on FAVORITE only saves the word (no automatic text insertion) and gives
  it a persistent upgrade in predictions
- Your FAVORITE words are remembered permanently and surface higher in suggestions
- Faster feel: pressing FAVORITE/DELETE performs the action instantly (no waiting for
  the burst animation), and fly-back / pop animations are ~40% shorter so typing
  resumes immediately

## [1.1.12] - 2026-09-15

### Fixed (re-release of Suggestion Edit Mode)
- Keyboard no longer floats at the top with a huge empty void below — the keyboard
  stack is pinned to the bottom of the screen like a normal keyboard
- Dragging a suggestion now tracks your finger 1:1 across the entire keyboard area:
  the suggestion scroller can no longer hijack (and cancel) the drag gesture
- Ghost chip sits exactly under your finger (what you see is where it drops) and no
  longer gets tugged around by a magnetic pull
- Drop zones are larger (~48dp) and easier to hit, so both REMOVE and FAVORITE
  are reachable from anywhere on the keyboard

## [1.1.11] - 2026-09-15

### Added
- Futuristic "Suggestion Edit Mode": long-press a suggestion chip and it is grabbed by your finger
  - Two invisible magnetic drop zones appear (REMOVE / FAVORITE) while the keyboard dims
  - The dragged chip follows your finger with a soft magnetic pull toward the nearest zone
  - Zone halos, colors and icons react continuously to distance (red glow = remove, gold glow = favorite)
  - Proportional haptics: drag start, entering magnetism, entering the drop zone, and confirm
  - Drop zones expand ~20% as you near them; releasing inside performs the action
  - Releasing anywhere else flies the chip smoothly back to its original place
- Favorites: dropping a suggestion on FAVORITE marks it, giving it a persistent boost in suggestions
- Replaced the old static trash delete overlay with the drag-to-REMOVE interaction

## [1.1.10] - 2026-09-15

### Fixed
- Clipboard pane now picks up text copied or cut in other apps (links, etc.)
  - Captures whenever the keyboard opens, when the Clipboard tab is opened, and live on new copies
  - Works when MeroType is set as the device's default keyboard (an Android 10+ platform rule); silently no-ops otherwise
  - Never reads the clipboard while a password field is focused
- Settings clipboard section explains the default-keyboard requirement

## [1.1.9] - 2026-09-14

### Added
- Magnetic Feature Carousel: a slim swipeable strip above the suggestions with spring-snap physics, momentum fling, haptic ticks and center magnification
- Three one-tap features: Suggestions (default), Clipboard, and Emoji
- Clipboard pane shows your most recent saved clips; tap to paste directly
- Emoji pane has quick-access emoji right on the keyboard; tap to insert
- Committed words are automatically added to your clipboard history

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