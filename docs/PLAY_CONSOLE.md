# Google Play Console: copy-paste kit

Every value to enter in Play Console, in the order Console asks for it. The rules behind these answers are in [PUBLISHING.md](PUBLISHING.md).

## 0. Only you can do these

- [ ] Create a developer account at https://play.google.com/console ($25 one-time). Choose **Personal**, then complete identity verification.
- [ ] **Developer name** (public). Using `nimbice` matches GitHub and the privacy policy. If you pick something else, update the developer name in [PRIVACY.md](../PRIVACY.md).
- [ ] **Contact email** (public, required). Consider a dedicated address.

## 1. Create app

| Field | Value |
|---|---|
| App name | `Grumpy QR Reader` |
| Default language | English (United States), en-US |
| App or game | App |
| Free or paid | Free |
| Declarations | Tick both (Developer Program Policies, US export laws) |

## 2. Policy → App content

| Section | Answer |
|---|---|
| Privacy policy | `https://github.com/nimbice/grumpy-qr/blob/main/PRIVACY.md` |
| App access | All functionality is available without any access restrictions |
| Ads | No, my app does not contain ads |
| Content rating | Category: **All other app types**. Answer **No** to every question (no violence, sexual content, profanity, drugs, gambling, user interaction or sharing, location sharing, or digital purchases). Expected rating: Everyone / PEGI 3. |
| Target audience | 13–15, 16–17, 18 and over. "Could your app unintentionally appeal to children?" → No |
| News app | No |
| Data safety | **"Does your app collect or share any of the required user data types?" → No.** Then submit. The listing will show "No data collected" and "No data shared". |
| Advertising ID | No, the app doesn't use advertising ID |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health | No health features |

No permission declaration forms apply: the camera needs none, and the app uses the system photo picker instead of photo/video permissions.

## 3. Grow → Store presence → Main store listing

| Field | Value |
|---|---|
| App name | `Grumpy QR Reader` |
| Short description | contents of [`short_description.txt`](../fastlane/metadata/android/en-US/short_description.txt) |
| Full description | contents of [`full_description.txt`](../fastlane/metadata/android/en-US/full_description.txt) |
| App icon | [`images/icon.png`](../fastlane/metadata/android/en-US/images/icon.png) (512 × 512) |
| Feature graphic | [`images/featureGraphic.png`](../fastlane/metadata/android/en-US/images/featureGraphic.png) (1024 × 500) |
| Phone screenshots | [`images/phoneScreenshots/1.png` … `6.png`](../fastlane/metadata/android/en-US/images/phoneScreenshots/) in that order |
| Category | Tools |
| Tags | Pick the closest offered, e.g. "QR & barcode scanner" |
| Email | your contact email |
| Website | `https://github.com/nimbice/grumpy-qr` |

## 4. Signing and the first upload (Test and release → Testing → Closed testing)

1. Create a **closed testing** track and choose countries (all is fine).
2. **Create release.** Under app signing, choose **Use a different app signing key → Export and upload a key from Java keystore**. Download `pepk.jar` and the encryption key Console offers, then run (from the folder you downloaded them to):
   ```powershell
   java -jar pepk.jar --keystore="$env:USERPROFILE\.keys\grumpy-qr-release.jks" --alias=grumpyqr --output=grumpy-qr-signing-key.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
   ```
   It asks for the keystore and key password. Both are `storePassword` in `keystore.properties`. Upload `grumpy-qr-signing-key.zip`.
   This keeps the Play version and the GitHub APK on the same signature, so people can switch between them.
3. Upload the bundle: `app/build/outputs/bundle/release/app-release.aab` (build it with `./gradlew bundleRelease`).
4. Release name: `1.0.2 (3)`. Release notes: contents of [`changelogs/3.txt`](../fastlane/metadata/android/en-US/changelogs/3.txt).
5. **Testers:** add at least **12** people by email (or a Google Group). Share the opt-in link with them. They must opt in and keep the app installed for **14 days in a row**.

## 5. Production

After 14 days with at least 12 opted-in testers: **Dashboard → Apply for production**, answer the short questionnaire about your test, and wait for review (usually up to 7 days). Then **Production → Create release**, add the same bundle from closed testing, and roll out (for example 20%, then 100%).
