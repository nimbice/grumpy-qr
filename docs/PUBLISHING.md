# Publishing plan: GitHub and Google Play

The checklist for getting Grumpy QR from this folder to a public GitHub repo and a live Play Store listing. Policy facts were checked on **2026-10-04**. Play rules change, so re-check anything marked ⚠️ before you rely on it.

**Ready to submit?** The field-by-field Play Console values are in [PLAY_CONSOLE.md](PLAY_CONSOLE.md).

## Compliance review (2026-10-04, v1.0.2)

**Licenses**
- Everything shipped in the APK was audited: 143 runtime libraries, plus zxing-cpp's native code. All are Apache-2.0, except libzueci (BSD-3-Clause, compiled into zxing-cpp) and the Checker Framework qualifiers (MIT). JSR-305's `javax.annotation.concurrent` annotations are CC BY 2.5. All are compatible with GPL-3.0-or-later.
- The app shows the GPL notices the license requires (copyright, no warranty, where to get the license and source) under **Settings → Open-source licenses**.
- The same screen holds the full Apache-2.0, BSD-3 and MIT texts and the two NOTICE files that apply (kotlinx.coroutines, Jakarta Inject). The texts live in `app/src/main/res/raw/`.
- Store graphics use Roboto (Apache-2.0). No proprietary fonts or assets.
- "QR Code is a registered trademark of DENSO WAVE INCORPORATED" appears in the app, the store description and the README.

**Play policy**
- Title, short description and icon contain no promotional or price words. The full description makes only factual claims, with no ALL-CAPS headings and no claims about specific competitors.
- Screenshots use only neutral example data (example.com, a documentation IP range) and no third-party brands.
- The privacy policy names the developer and app and gives a contact. It's linked from inside the app.
- The app targets API 36, meets the 16 KB page-size rule, uses only the CAMERA permission (with a fallback when it's refused), uses the photo picker for images, has no ads or analytics, and collects no data.

---

## 0. Decide these first (they're permanent)

- [ ] **App ID: `io.github.nimbice.grumpyqr`.** Once the first build is uploaded to Play, this can never change. If you rename the app, change `applicationId` in [`app/build.gradle.kts`](../app/build.gradle.kts) *before* the first upload. The Kotlin `namespace` can stay as it is.
- [ ] **Name: "Grumpy QR Reader"** ("Grumpy QR" on the home screen). Do a quick trademark search ([USPTO](https://tmsearch.uspto.gov/), [EUIPO](https://euipo.europa.eu/eSearch/)) for software or app uses. A web search on 2026-10-04 found no QR app with this name.
- [x] **Signing key.** Created on 2026-10-04 at `%USERPROFILE%\.keys\grumpy-qr-release.jks` (alias `grumpyqr`). Its password is in the git-ignored `keystore.properties`. Certificate SHA-256: `20:01:7B:DF:BC:94:2A:4D:61:95:76:41:61:BF:C5:7B:74:45:E1:7B:8C:98:16:90:CD:A9:23:CF:59:B3:69:66`.
  - [ ] **Back up both files to two places** (for example your password manager plus a USB stick). Losing the key means you can never ship another update to people who installed from GitHub.

### Signing: one key you control, used everywhere (recommended)

Use one key for GitHub releases *and* register it as the Play app signing key. Then:
- people can move between the GitHub APK and the Play version without uninstalling;
- you can publish the certificate fingerprint so anyone can verify a download;
- the package and key are registered with Google, which covers [developer verification](#5-android-developer-verification-for-github-apks) for GitHub APKs too.

```bash
# keytool ships with the JDK. Use a long random password and keep it in your password manager.
keytool -genkeypair -v -keystore grumpy-qr-release.jks -alias grumpyqr -keyalg RSA -keysize 4096 -validity 10000

# The SHA-256 fingerprint to publish in the README:
keytool -list -v -keystore grumpy-qr-release.jks -alias grumpyqr
```

Back up `grumpy-qr-release.jks` and its password in **two** places, for example your password manager plus an offline USB stick. Never commit it; `.gitignore` already blocks `*.jks`.

For local signed builds, create `keystore.properties` in the project root (also git-ignored). The format is in the README.

---

## 1. GitHub

- [x] Public repo created at https://github.com/nimbice/grumpy-qr, with topics, private vulnerability reporting and Dependabot alerts turned on.
- [ ] Upload `fastlane/metadata/android/en-US/images/featureGraphic.png` as the social preview (**Settings → General**; GitHub has no API for it).

### Making a release (by hand)

There's no CI, so releases are built and checked on your PC. Set up `keystore.properties` first (format in the README).

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease bundleRelease
bash tools/check-permissions.sh app/build/outputs/apk/release/app-release.apk   # must say "OK"
cp app/build/outputs/apk/release/app-release.apk grumpy-qr.apk
sha256sum grumpy-qr.apk > SHA256SUMS.txt
gh release create v1.0.0 grumpy-qr.apk SHA256SUMS.txt --title "Grumpy QR v1.0.0" --notes-file fastlane/metadata/android/en-US/changelogs/1.txt
```

Always name the asset **`grumpy-qr.apk`**. The README's download button points at `releases/latest/download/grumpy-qr.apk`, which always serves the newest release's file with that name. Don't mark releases as pre-release, because "latest" skips them.

The Play bundle is `app/build/outputs/bundle/release/app-release.aab`.

### Turning CI on later (optional)

Ready-made workflows are parked in [`.github/disabled-workflows/`](../.github/disabled-workflows/). `ci.yml` lints, tests and runs the permission check on every push; `release.yml` builds and publishes a signed release when you push a `v*` tag. To enable them:
1. Give the GitHub CLI permission to push workflow files: `gh auth refresh -h github.com -s workflow`
2. Move both files into `.github/workflows/`, then commit and push.
3. For `release.yml`, add the signing secrets:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("grumpy-qr-release.jks")) | gh secret set GRUMPY_KEYSTORE_BASE64
   gh secret set GRUMPY_KEYSTORE_PASSWORD
   gh secret set GRUMPY_KEY_ALIAS --body grumpyqr
   gh secret set GRUMPY_KEY_PASSWORD
   ```

---

## 2. Test on real phones first

**Already done on an Android 16 emulator (2026-10-04), with the R8-optimized release build.** These passed:
- The camera permission is asked once. "Don't allow" leads to the "Camera's off" screen with no re-prompt, and "Turn on camera" asks again.
- Live camera scanning works, and a dismissed code doesn't pop back up.
- Photo picker (inverted QR), file picker (PDF with the code on page 2), and the share sheet: a link with warnings, a lookalike address, Wi-Fi, vCard, EAN-13, a 2FA code, two codes in one picture, and a transparent PNG.
- Wi-Fi "Save this network?" dialog, dark mode, history (the 2FA code is left out; delete + undo works), Quick Settings tile, launcher shortcuts, and the ZXing scan intent.

An emulator can't tell you about real camera focus, real-world lighting or older Android versions, so before inviting testers check these on real phones. Aim for at least one older phone (Android 8 to 10), one current phone, and ideally a Samsung or Xiaomi.

- [ ] **Real codes:** a restaurant menu QR, a Wi-Fi sticker, a product barcode, a tiny QR on a receipt, a QR on a screen, and a crumpled or glossy one in bad light.
- [ ] **Close focus:** hold the phone 5 to 10 cm from a small code. Some newer phones can't focus that close; pinch to zoom should help.
- [ ] **Older Android (8 to 10):** the Photos button falls back to the document picker; Wi-Fi copies the password and opens Wi-Fi settings.
- [ ] **Real share sheets:** share straight from the screenshot preview, and a PDF ticket from Gmail.
- [ ] Flashlight, rotation, largest font size, a TalkBack pass.
- [ ] History export to CSV; turning history off.
- [ ] **Airplane mode:** everything still works (it has to).
- [ ] Optionally retake the store screenshots on a real phone. The current set in `fastlane/.../images/phoneScreenshots/` comes from the emulator.

Install a build with `adb install app-release.apk` (from a GitHub release) or from Android Studio.

---

## 3. Google Play

### 3.1 Developer account
- Sign up at https://play.google.com/console ($25 one-time). A **personal** account is fine for a solo hobby project.
- Identity verification takes a few days. Your developer name and contact email are public, so consider a dedicated email address for the app.
- ⚠️ The developer name must not include words like "free" or "no ads" either.

### 3.2 Create the app
- App name: **Grumpy QR Reader** (28 of 30 characters). Default language: English (US). App, not game. **Free.** An app marked free can never become paid, which suits this project.

### 3.3 App content (Policy → App content)

| Section | Answer |
|---|---|
| Privacy policy | `https://github.com/nimbice/grumpy-qr/blob/main/PRIVACY.md` |
| Ads | **No, my app does not contain ads** |
| App access | All functionality is available without special access |
| Content rating | Fill in the IARC questionnaire as a utility app; "no" to everything gives Everyone / PEGI 3 |
| Target audience | **13 and over** (13–15, 16–17, 18+). Including under-13 groups opts into the Families program's extra requirements and review; the app would comply, but it adds overhead for no benefit. |
| Data safety | **"Does your app collect or share any of the required user data types?" → No.** Processing that happens only on the device (camera frames, picked images, local history) doesn't count as collection, and things the user chooses to share through the share sheet don't count as sharing. |
| Financial features | My app doesn't provide any financial features (it only *displays* payment codes) |
| Health, news, government | No |

No special permission declarations are needed: camera has no declaration form, and the photo picker means no photo/video permission declaration.

### 3.4 Store listing
Everything is in [`fastlane/metadata/android/en-US/`](../fastlane/metadata/android/en-US/):
- `title.txt`, `short_description.txt` (79 of 80 characters), `full_description.txt`
- `images/icon.png` (512×512) and `images/featureGraphic.png` (1024×500), regenerated with `python tools/store_assets.py`
- Phone screenshots: six are already in `images/phoneScreenshots/` (1080×2400, from the emulator). F-Droid and IzzyOnDroid pick them up from there too.
- Category: **Tools**. Contact email: required. Website: the GitHub repo.

⚠️ Metadata rules: no "free", "no ads", "#1", "best" or similar in the **title, icon, developer name or short description**. Factual statements in the full description ("there are no ads") are fine. The current text follows these rules.

### 3.5 App signing and the first upload
- In **Test and release → App integrity → Play App Signing**, choose to use **your own key** and follow the PEPK export steps for `grumpy-qr-release.jks`.
- Upload `app/build/outputs/bundle/release/app-release.aab` (from `./gradlew bundleRelease` with `keystore.properties` set up).

### 3.6 The 12-testers-for-14-days rule ⚠️
Personal developer accounts created after November 13, 2023 must run a **closed test with at least 12 testers who stay opted in for 14 consecutive days** before applying for production access. Production review then usually takes up to 7 days.
- Create a closed testing track and add testers by email or with a Google Group. Friends and family with Android phones work well.
- Testers must opt in through the link and keep the app installed. If someone drops out, the count can fall below 12 and the clock may not count those days.
- Use the two weeks well: collect feedback on scan failures and odd phones.
- Then apply for production in the Dashboard. You'll answer a short questionnaire about your testing.

### 3.7 Production
- Roll out in stages (for example 20%, then 100%) and watch **Android vitals** for crashes. Play's own crash reporting needs no code in the app.

---

## 4. F-Droid and IzzyOnDroid (recommended)

This audience is exactly who Grumpy QR is for.
- **IzzyOnDroid:** pulls signed APKs straight from GitHub releases, so it's usually quick. Open a request on their [repo](https://gitlab.com/IzzyOnDroid/repo). The fastlane metadata is already in place.
- **F-Droid:** builds from source. Open a Request for Packaging or a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata).
  - zxing-cpp comes as a prebuilt native AAR from Maven Central. F-Droid generally prefers building it from source; Binary Eye's [build recipe](https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/de.markusfisch.android.binaryeye.yml) shows how it's done.
  - Making builds reproducible lets F-Droid ship *your* signature, so users can switch sources freely.

---

## 5. Android developer verification (for GitHub APKs)

Status as of October 2026 ⚠️:
- Since **September 30, 2026**, Google checks developer verification for apps installed on certified devices in **Brazil, Indonesia, Singapore and Thailand**, through participating app stores.
- **Direct sideloads, like an APK from GitHub, aren't enforced yet.** The global rollout is planned for "2027 and beyond".
- Apps created in Play Console are registered automatically. Signing GitHub APKs with the same key you register on Play (section 0) means the GitHub APK is covered too.
- Docs: https://developer.android.com/developer-verification

---

## 6. After launch

- **Every release:** bump `versionCode` and `versionName` in `app/build.gradle.kts`, add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, follow "Making a release" above, then upload the `.aab` to Play.
- **Every year before August 31:** raise `targetSdk` to whatever Play requires. API 36 satisfies the current rule, which took effect August 31, 2026. Test on the newest Android first.
- **When Dependabot opens an update PR:** check it out, run the release commands above (tests, lint, permission check), and only merge if they all pass.
