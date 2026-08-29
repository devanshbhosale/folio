# Folio — Architecture

## 1. Layers

```
┌─────────────────────────────────────────────┐
│  UI (Jetpack Compose, Material 3)           │
│  ─ MainActivity + NavHost                   │
│  ─ HomeScreen  · SettingsScreen             │
│  ─ ViewerScreen → ViewerRouter → 10× viewer │
├─────────────────────────────────────────────┤
│  Data                                       │
│  ─ DocumentRepository (Room, SAF grants)    │
│  ─ PreferencesRepository (DataStore)        │
├─────────────────────────────────────────────┤
│  Domain                                     │
│  ─ DocumentFormat (MIME/ext detection)      │
├─────────────────────────────────────────────┤
│  Parsers (offline, on-device)               │
│  ─ PDF: android.graphics.pdf.PdfRenderer    │
│  ─ Office: Apache POI (HWPF, XWPF, HSSF,    │
│            XSSF, HSLF, XSLF)                │
│  ─ ODT: ZipInputStream + SAX on content.xml │
│  ─ RTF: hand-rolled control-word extractor  │
│  ─ iWork: ZIP → preview.jpg/preview.pdf     │
│  ─ CSV: RFC-4180 mini-parser                │
└─────────────────────────────────────────────┘
```

## 2. Rules that hold everywhere

- **No `INTERNET` permission** in the manifest. The whole app is offline.
  If a library tries to reach the network, the request fails.
- **Read-only file access.** SAF grants are requested with
  `FLAG_GRANT_READ_URI_PERMISSION` only; every `openInputStream` runs in
  read mode. Sharing uses `ACTION_SEND` with a granted read flag.
- **Never copy file bytes into app storage** unless a renderer needs a
  seekable `File` (POI, ODF, iWork ZIP). Cache-only copies land in
  `cacheDir` under `doc-<sha1>.<ext>` and are cleared on Home return.
- **Metadata only** in Room — display name, MIME, size, timestamps,
  favorite flag. No content, no thumbnails of user data.
- **UI is single-source-of-truth.** All state flows out of Room / DataStore
  via cold `Flow`s + `collectAsState`. No shared static state.

## 3. Adding a new format

1. Add an entry to `DocumentFormat`.
2. Create `viewer/<name>/<Name>Viewer.kt` — a single composable taking
   `(uri: Uri)`.
3. Route it in `ViewerRouter`.
4. Extend the MIME + `pathPattern` blocks in `AndroidManifest.xml`.
5. Add the human-readable label to `strings.xml`.

## 4. Known trade-offs

- **PowerPoint** shows a *structured slide layout* (title + bullets + notes),
  not a pixel-accurate render. Full-fidelity requires Java2D, which Android
  doesn't ship.
- **iWork Numbers / Pages** shows the QuickLook preview Apple embeds in
  every iWork file. Full IWA/protobuf parsing is a v2 candidate.
- **ODT** is rendered as a linear text layout (headings + paragraphs);
  tables and floats are flattened.
- **Very large Excel sheets** are capped at 5,000 rows per sheet to keep
  scroll responsive. The remaining rows are simply not loaded (no truncation
  of files — the source file is untouched).
