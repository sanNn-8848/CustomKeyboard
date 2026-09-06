# Roman Nepali Keyboard

A free, open-source Android keyboard app for typing in Roman Nepali with smart suggestions, autocorrect, and GBoard-like features.

## Features

- **QWERTY Layout** - Standard keyboard with Roman Nepali mapping
- **Word Suggestions** - Smart predictions based on what you type
- **Autocorrect** - Automatically fixes typos
- **Next-word Prediction** - Suggests the next word based on context
- **Emoji Support** - Full emoji keyboard with search
- **Themes** - Light and dark mode support
- **Swipe Typing** - Type by sliding your finger
- **One-Hand Mode** - Compact layout for single-hand use
- **Clipboard Manager** - Copy/paste history
- **100% Private** - No internet, no tracking, no data collection

## Installation

### Option 1: Download APK (Recommended)

1. Go to [Releases](../../releases)
2. Download the latest APK file
3. Open the APK file on your Android device
4. Enable "Install from Unknown Sources" if prompted
5. Follow the installation instructions

### Option 2: Build from Source

1. Clone this repository
2. Open in Android Studio
3. Build and run on your device

## Setup

1. Open the Roman Nepali app
2. Tap "Enable Keyboard"
3. Go to Settings > Enable "Roman Nepali"
4. Go back to the app and tap "Select Input Method"
5. Choose "Roman Nepali"

### Switch Keyboards

- **Long-press space bar** - Shows keyboard picker (Android built-in)
- **Tap ?123** - Switch to numbers/symbols
- **Long-press ?123** - Switch to text editing mode

## How It Works

The keyboard uses an **N-gram + Trie** model for suggestions:

1. **Trie** - Fast prefix matching for autocomplete
2. **N-gram** - Word pair frequency for next-word prediction
3. **User Learning** - Learns your frequently used words

No internet connection required. All processing happens on your device.

## Privacy

- **Zero internet access** - No data ever leaves your device
- **No tracking** - No analytics, no usage data collection
- **No ads** - Completely free, no monetization
- **Open source** - Full transparency, inspect the code yourself

## Permissions

The keyboard requires:

- `BIND_INPUT_METHOD` - Required for keyboard service
- No other permissions needed

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by GBoard and SwiftKey
- Built with love for the Nepali community
