# Privacy policy for Grumpy QR

_Last updated: October 4, 2026_

**Short version: Grumpy QR doesn't collect, send, sell or share any data. It can't: the app has no permission to use the internet.**

This policy covers the Grumpy QR Reader app for Android (package `io.github.nimbice.grumpyqr`).

## What the app touches, and what happens to it

| What | When | What happens to it |
|---|---|---|
| **Camera** | Only while the scanner screen is open, and only if you allow the camera permission. | Each camera frame is searched for codes in memory and immediately thrown away. No photos or video are ever saved. |
| **Pictures, screenshots and PDFs** | Only the single file you pick in the system picker or share to Grumpy QR. | The file is read once to look for codes. PDFs are briefly copied into the app's private cache (Android requires a seekable file to render a PDF) and deleted as soon as scanning finishes. Nothing is kept. |
| **Scan history** | When you scan a code, if "Keep history" is on (it is by default). | Saved only in the app's private storage on your phone. It's excluded from cloud backups and device-to-device transfers, you can turn it off, export it or clear it at any time, and it's deleted when you uninstall the app. Two-factor setup codes, passkey codes and anything that looks like a crypto recovery phrase are never saved. |
| **Settings** | When you change them. | Saved only in the app's private storage on your phone, excluded from backups. |
| **Clipboard** | Only when you tap a "Copy" button. | The text you chose is placed on the clipboard. Passwords and other secrets are marked as sensitive so Android hides them from clipboard previews. Grumpy QR never reads your clipboard. |

## What the app never does

- No internet access of any kind. The app's manifest removes the `INTERNET` permission, and every release is checked for it before it's published.
- No ads, analytics, crash reporting, tracking, fingerprinting or third-party SDKs that collect data.
- No accounts or sign-ins.
- No location, contacts, calendar, phone, storage or media permissions.

## Opening things in other apps

When you tap an action like "Open link", "Add to contacts" or "Connect", Grumpy QR hands that one item to another app on your phone (your browser, contacts app, Wi-Fi settings and so on) using a standard Android intent. From then on, that app's own privacy policy applies. Grumpy QR never opens anything without you tapping a button.

## Children

Grumpy QR collects no personal information from anyone, including children.

## Changes

If this policy ever changes, the new version will be published at this address with a new "Last updated" date. The full history of changes is visible in the project's Git history.

## Contact

Questions or concerns: open an issue at https://github.com/nimbice/grumpy-qr/issues
