# Folio — Universal Document Viewer (Android)

**Every document. One reader.**

Folio is a fast, native, offline-only Android app that opens documents in **read-only** mode. No editing. No cloud. No ads. No telemetry.

## Supported formats

| Category | Extensions |
|---|---|
| PDF | `.pdf` |
| Word | `.doc`, `.docx` |
| Excel | `.xls`, `.xlsx` |
| PowerPoint | `.ppt`, `.pptx` |
| Plain / rich text | `.txt`, `.rtf` |
| CSV | `.csv` |
| OpenDocument | `.odt` |
| Apple iWork | `.numbers`, `.pages` |

## Build

Requires Android Studio Koala (or newer) with the Android Gradle Plugin 8.6+.

```bash
./gradlew :app:assembleRelease
```

The release APK lands in `app/build/outputs/apk/release/`.

## Architecture

- **UI:** Jetpack Compose + Material 3 (single-activity, Nav-Compose)
- **Storage:** Room (metadata only — never file bytes)
- **File access:** Storage Access Framework (SAF) — persisted URI permissions, read-only flags
- **PDF:** `androidx.pdf:pdf-viewer` (Google's official Compose PDF viewer)
- **Office (docx/xlsx/pptx):** Apache POI OOXML, on-device parsing
- **Legacy Office (doc/xls/ppt):** Apache POI HSSF/HWPF/HSLF
- **ODT:** ODF Toolkit
- **iWork (.numbers / .pages):** ZIP unpack + IWA/protobuf decoding → HTML render
- **RTF:** javax.swing.text.rtf (headless) → styled Compose text
- **CSV / TXT:** built-in

## Design principles

- Single-activity, single-source-of-truth UI in Compose.
- No `INTERNET` permission in the manifest — enforced at package level.
- No file is ever copied unless the source URI is not directly readable by the renderer; when it is, it lands only in `cacheDir` and is purged on viewer exit.
- All controls auto-hide after 2s of no interaction.

See `docs/ARCHITECTURE.md` for detail.
