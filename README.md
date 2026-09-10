# MeroType — Roman Nepali Keyboard

An intelligent, fully offline Roman Nepali keyboard for Android. Type in Roman
Nepali and get smart word predictions that learn from your own typing.

## Features

- **Gboard-style layout** — QWERTY letters, `?123` symbols, symmetric home row, arrow enter, haptic feedback
- **Two-tier smart suggestions**
  - Tier 1: dictionary prefix matching, Levenshtein fuzzy matching, bigram context, and learned words
  - Tier 2: trained character trigram model (beam search) that fills in missing letters
- **Learns as you type** — new words you type are remembered and reused
- **100% offline & private** — everything runs on-device, no permissions, no internet
- **Settings screen** — one-tap keyboard enable/check

## Install

### From GitHub Releases (recommended)
1. Download the latest `MeroType.apk` from
   https://github.com/sanNn-8848/CustomKeyboard/releases
2. Allow "Install unknown apps" for your browser/file manager
3. Open the APK and install
4. Enable the keyboard: Settings → Languages & input → Virtual keyboards → MeroType Keyboard

### Build from source
```bash
./gradlew assembleDebug
```
```bash
./gradlew installDebug
```

## Release workflow

Pushing a tag `v1.1.x` triggers GitHub Actions (`.github/workflows/build-release.yml`),
which signs the release APK with the `merotype-release` keystore (credentials stored
as repository secrets), renames it `MeroType.apk`, and publishes it as a GitHub Release.

```bash
git tag v1.1.2
git push origin main
git push origin v1.1.2
```

## Project layout

```
app/src/main/java/com/romannepali/keyboard/
├── RomanNepaliIME.kt        # Input method service
├── GboardKeyboardView.kt    # Custom QWERTY/symbol keyboard view
├── SuggestionBar.kt         # Suggestion strip + settings gear
└── suggestion/              # Trie, n-gram model, char model, suggestion engine
app/src/main/assets/char_model.json   # Trained trigram model (shipped)
tools/...                    # Python training scripts (data/ is git-ignored)
```

## Model training (optional)

The shipped `char_model.json` was generated from `data/roman_nepali_words.txt`:

```bash
python build_word_model.py   # parquet corpus → word-frequency list
python build_char_model.py   # word-frequency list → char trigram JSON
```

`data/` (large corpus files) is git-ignored; only the small trained model is committed.

## License

MIT — see `LICENSE`.