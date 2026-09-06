# How to Release MeroType

## For Developers Only

### Step 1: Update Version
Edit `app/build.gradle.kts`:
```kotlin
versionCode = 2
versionName = "1.1.0"
```

### Step 2: Update Changelog
Edit `CHANGELOG.md`:
```markdown
## [1.1.0] - 2026-09-13

### Added
- New feature 1
- New feature 2
```

### Step 3: Commit & Tag
```bash
git add .
git commit -m "Release v1.1.0"
git tag v1.1.0
git push origin main
git push origin v1.1.0
```

### Step 4: Done!
GitHub Actions automatically:
- Builds APK
- Signs it
- Creates release
- Uploads file

**Check:** https://github.com/sanNn-8848/CustomKeyboard/releases

---

## Setup Keystore (One-time)

### Create Keystore
```bash
keytool -genkey -v -keystore keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias merotype
```

### Encode Keystore for GitHub Secrets
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore.jks")) | Set-Content keystore_base64.txt
```

### Add GitHub Secrets
Go to: https://github.com/sanNn-8848/CustomKeyboard/settings/secrets/actions

| Secret | Value |
|--------|-------|
| `SIGNING_KEY` | Content of `keystore_base64.txt` |
| `KEY_ALIAS` | `merotype` |
| `KEY_STORE_PASSWORD` | Your keystore password |
| `KEY_PASSWORD` | Your key password |

---

## Troubleshooting

**Build failed?**
- Check Java version (must be 17)
- Check Gradle version compatibility

**APK not signed?**
- Verify GitHub Secrets are correct
- Check `SIGNING_KEY` is full base64 content

**Release not created?**
- Tag must start with `v` (e.g., `v1.0.0`)
- Check Actions tab for build logs
