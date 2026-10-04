# Ideas for later

Ideas that came out of surveying other open-source scanners (Binary Eye, SecUSo's Privacy Friendly QR Scanner, QrAndBarcodeScanner, GrapheneOS Camera and others) and their issue trackers, but didn't make 1.0. Each one has to fit the rules: no network, no new permissions, and no clutter.

**Most requested elsewhere**
- **History import** (the counterpart to CSV export), and labels or notes on saved scans.
- **Make a QR code**, for example to share your Wi-Fi with a guest. It can be generated offline.
- **Batch mode:** keep scanning and collect a list of codes without stopping at each one.

**Scanning quality**
- Close-focus help: many newer phones' main cameras can't focus closer than about 10 cm. Auto-zoom or switching lenses would let you hold the phone farther away.
- Highlight the detected code on screen, and let you tap to choose when there are several.
- Spoken aiming hints for TalkBack users ("code to the left, move closer").

**Content types**
- Better passkey (`FIDO:/`) hand-off, and Wi-Fi Enterprise (EAP) setup.
- More payment formats (for example Brazil's Pix and Singapore's PayNow), still display-only.

**Project**
- Translations.
- Reproducible builds and an F-Droid listing.
- Dependency verification (`gradle/verification-metadata.xml`) to pin every downloaded artifact by checksum.
