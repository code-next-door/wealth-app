# Security for a local-only app with private data

## Principles
- **Local only** where possible: no server, no accounts, no analytics. Every network call is listed in the
  README with exactly what it sends (e.g. currency codes and dates).
- No logging of amounts, names, file contents or other personal data. Not even in debug.

## Encrypted database
- SQLCipher with a random 32-byte key, wrapped by an Android Keystore AES-GCM key. Never log the key.
- The key-wrapping code never creates a new key while a database file exists (see data-safety.md); missing or
  unreadable key → recovery screen, files set aside, never deleted.
- `android:allowBackup="false"`: the app's own encrypted backup (AES-256-GCM, PBKDF2 with a high iteration
  count) replaces Android auto-backup. Backup files go wherever the user picks via the system file picker.

## App lock
- PIN stored as a slow hash (PBKDF2) **peppered with a Keystore HMAC key**, so a copied settings file can't be
  brute-forced off the device. Keep a scheme number so old hashes verify and get upgraded on the next unlock.
- Checking a PIN never creates keys; "key gone" isn't a wrong attempt.
- Delay and lockout after wrong attempts; optional biometric (`BiometricPrompt` needs a `FragmentActivity`).
- The lock is a UI gate over the app: content hidden **and removed from accessibility** while locked.
- Brief exits (system file picker) shouldn't re-lock: allow them explicitly before launching.

## Screens and recents
- Prefer `setRecentsScreenshotEnabled(false)` (Android 13+) to blank the app switcher. `FLAG_SECURE` also
  blocks the user's own screenshots, which people want for sharing a chart.
- A "hide figures" eye for using the app in public (display only, remembered).

## Network and updates
- Only the `INTERNET` permission needed; no third-party SDKs that phone home.
- In-app updates from GitHub Releases: download over HTTPS, **verify the SHA-256** against the published
  checksum before handing the APK to the installer, share it through a `FileProvider` path limited to that
  folder, and let Android's installer confirm (it only accepts the same signing key).
- Never check for updates from debug/test builds (no version → no check).

## Repo hygiene
- Never commit keystores, `local.properties`, secrets, build output, or real user data.
- Signing key only in CI secrets; publish the certificate fingerprint so users can verify.
- Keep personal or AI-assistant notes out of the repo (`.gitignore`); if one was pushed, history still has it
  (rewrite with `git filter-repo` while the repo is young).

## Dependencies
- Prefer platform APIs over libraries (native PDF text extraction, a tiny .xlsx reader on `java.util.zip`):
  less code to trust, smaller APK.
- Keep libraries on the latest stable in regular maintenance passes; turn on GitHub's Dependabot **alerts**
  (security only) so known vulnerabilities surface between passes.
- Pin transitive versions the tests rely on (see dev-environment.md).
