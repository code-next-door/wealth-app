# Releasing

Releases are built and signed by GitHub Actions (`.github/workflows/release.yml`)
when a version tag is pushed. The APK and its SHA-256 checksum are attached to a
GitHub Release, ready to share.

## One-time setup: the signing key

Android only installs an update if it's signed with the same key as the installed
app. **Keep the key and its password safe** (e.g. in a password manager): if the key
is lost, friends have to uninstall the app, losing their data, to install a new one.

1. Create the key (you'll be asked for a password; `keytool` comes with the JDK):

   ```bash
   keytool -genkeypair -v -keystore ~/wealth-release.jks -alias wealth -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Wealth"
   ```

2. Give it to GitHub as repository secrets (`gh` asks for the password when it's not piped in):

   ```bash
   base64 -i ~/wealth-release.jks | gh secret set WEALTH_KEYSTORE_BASE64
   gh secret set WEALTH_KEYSTORE_PASSWORD
   gh secret set WEALTH_KEY_ALIAS --body wealth
   ```

3. Back up `~/wealth-release.jks` and the password somewhere safe. Never commit it
   (`.gitignore` blocks `*.jks`).

## Each release

1. Check the shrunk (release-like) build on a device or emulator:

   ```bash
   ./gradlew :releasetest:connectedMinifiedAndroidTest
   ```

2. Tag and push; the version comes from the tag (`v1.2.3` → version 1.2.3, code 10203):

   ```bash
   git tag v0.1.0 && git push origin v0.1.0
   ```

3. The release appears under GitHub → Releases a few minutes later. Friends install
   `wealth-<version>.apk` (Android asks them to allow installs from their browser or
   file manager).
