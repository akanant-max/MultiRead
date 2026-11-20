# MultiRead

Offline, view-only document viewer for Android. No editing.

Opens: PDF, DOCX, XLSX/XLS/ODS/CSV, PPTX, HTML, images, plain text.
Old `.doc` / `.ppt`: text only (no layout or images).

Open from inside the app, or choose "Viewer" from "Open with" in any file manager.

## Build
Push to `main`; GitHub Actions builds `viewer.apk` and attaches it to a release.
Install it from the Releases page.

## Notes
- Rendering uses bundled JS libraries (pdf.js, docx-preview, SheetJS, pptx-preview) inside a WebView.
- HTML files render with scripts disabled.
- Spreadsheets show values and merged cells but not cell colours; capped at 5,000 rows per sheet.

## Features
Page counter and go-to-page, find in document with highlights, pinch / double-tap / button zoom, night mode, remembers your place in each file, recent files, keeps the screen on while reading.
