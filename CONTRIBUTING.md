# Contributing to Roman Nepali Keyboard

Thank you for your interest in contributing! Here's how you can help.

## How to Contribute

### 1. Report Bugs
- Open an issue on GitHub
- Describe the bug clearly
- Include steps to reproduce
- Mention your device and Android version

### 2. Suggest Features
- Open an issue with the "feature request" label
- Describe the feature you want
- Explain why it would be useful

### 3. Add Words to Dictionary
- Fork the repository
- Edit `dictionary/nepali_words.txt`
- Add new words (one per line)
- Submit a pull request

### 4. Improve Code
- Fork the repository
- Create a feature branch
- Make your changes
- Test on a real device
- Submit a pull request

## Development Setup

### Prerequisites
- Android Studio (latest version)
- Android SDK 24+
- Kotlin knowledge

### Setup
1. Fork the repository
2. Clone your fork
3. Open in Android Studio
4. Sync Gradle
5. Run on device

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable names
- Add comments for complex logic
- Keep functions short

## Pull Request Guidelines

- Describe what you changed
- Reference any related issues
- Test on a real device
- Keep PRs focused on one change

## Adding New Words

Edit `dictionary/nepali_words.txt`:
```
word1
word2
word3
```

Then update `dictionary/word_freq.txt`:
```
word1 1000
word2 900
word3 800
```

Higher frequency = more likely to appear in suggestions.

## Questions?

Open an issue on GitHub if you have questions.
