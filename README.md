# Wrist Notes

Wrist Notes is an Android application that turns a collection of Markdown notes into a self-contained VelaOS RPK package and hands it to Gadgetbridge for installation on a watch or band.

The Android application and the generated VelaOS application both use the package ID `org.execbit.rpker` and the display name **Wrist Notes**.

## Why this project exists

VelaOS provides Interconnect, a protocol intended for exchanging data between applications running on a phone and a watch. In the current ecosystem, however, that integration is tied to Mi Fitness and an application using it requires approval from Xiaomi.

That is a poor fit for a small personal utility such as sending a note to a wearable. Until a practical open alternative to Interconnect becomes available, Wrist Notes takes a deliberately simpler route: it puts the text directly inside a VelaOS application package.

Instead of synchronizing data at runtime, Wrist Notes:

1. stores and edits Markdown notes on the Android phone;
2. rebuilds an RPK locally with all saved notes embedded in it;
3. passes the generated package to Gadgetbridge;
4. lets Gadgetbridge install the package on the wearable.

This is not live synchronization. Each change produces a new version of the same RPK application and installs it over the previous version.

## How it works

```text
Saved Markdown notes
    ↓
CommonMark parser
    ↓
VelaOS-compatible text/span blocks
    ↓
Precompiled RPK template + generated manifests
    ↓
Hashing, signing, and local verification
    ↓
Gadgetbridge FileInstallerActivity
    ↓
VelaOS wearable
```

### 1. Editing

The Android UI is written with Jetpack Compose. Saved notes are stored as an ordered JSON collection in private Android `SharedPreferences`, so they survive process restarts and APK updates. Saving is explicit: the editor only writes a new or changed note when **Save** is tapped.

The main screen renders a Markdown preview of the first two non-empty lines of each note. It provides actions to add a note and to send the full collection to the wearable. Tapping a row opens that note for editing, dragging its handle changes the saved order, and the delete icon removes it after confirmation.

When the software keyboard is visible, the editor shows a formatting strip with shortcuts for:

- headings;
- bold, italic, and strikethrough text;
- inline code;
- bulleted and numbered lists;
- block quotes;
- horizontal rules.

Formatting actions understand the current selection. Inline markers wrap selected text, while list and quote markers are inserted at the beginning of every selected line.

The Android interface uses English as its default locale and includes a Russian translation. On supported Android versions, the language can also be selected through the system's per-app language settings.

### 2. Markdown conversion

Markdown is parsed with CommonMark and the GFM strikethrough extension. The parser first produces escaped HTML, which is then converted into the limited `text`/`span` model used by the VelaOS template.

HTML is only an intermediate representation. Wrist Notes does not send arbitrary HTML to VelaOS, and raw HTML entered by the user is escaped.

Supported formatting includes:

- paragraphs and explicit line breaks;
- headings;
- bold and italic text;
- strikethrough;
- inline and fenced code;
- ordered, unordered, and nested lists;
- block quotes;
- links;
- QR codes and Code 128 barcodes;
- horizontal rules.

QR codes and barcodes use the conventional fenced-code extension pattern also used by Markdown extensions such as Mermaid. The content between the fences becomes the native VelaOS component value:

````markdown
```qrcode
https://example.com/ticket/42
```

```barcode
ABC-123
```
````

Barcode values use Code 128 and may contain at most 20 UTF-8 bytes, matching the VelaOS component limit. Other fenced language identifiers remain ordinary code blocks.

Markdown images are not downloaded or rendered as bitmaps. Their alt text and source URL are converted to a textual description instead.

### 3. Embedded VelaOS template

The editable VelaOS project lives in [`vela-template`](vela-template). It is compiled ahead of time with `aiot-toolkit`, and the compiled files are embedded in the Android APK under [`app/src/main/assets/rpk_template`](app/src/main/assets/rpk_template).

The phone does **not** run Node.js or the VelaOS compiler. At runtime, the Android application only replaces a single JSON marker in the precompiled page, updates the manifests, and packages the resulting files.

The VelaOS layout uses a horizontal `swiper`: the first note opens immediately, and left/right swipes switch notes without an intermediate menu. Each note retains its own vertical `scroll`, and a numeric counter shows the current position. Because the swiper consumes the usual swipe-to-exit gesture, a large back-style button calls VelaOS's documented `this.$app.exit()` method. Shape media queries adjust safe areas, controls, counter placement, and font sizes for rectangular, circular, and pill-shaped screens while leaving line wrapping to the VelaOS text renderer.

### 4. RPK assembly

`RpkBuilder` performs the complete build on the phone:

- validates that the collection is not empty, contains no blank notes, and is no larger than 1 MB in total;
- converts every Markdown note into JSON blocks;
- injects those blocks into `pages/index/index.js`;
- updates `manifest.json` and `manifest-watch.json`;
- assigns a monotonically increasing version code;
- adds the application icon and build metadata;
- calculates SHA-256 digests;
- creates the RPK ZIP structures;
- signs the package in an `aiotpack`-compatible format;
- verifies the generated signatures and file digests before installation.

The output file is written atomically to the application's cache as:

```text
cache/rpk/org.execbit.rpker.rpk
```

### 5. Gadgetbridge handoff

The generated file is exposed through a narrowly scoped Android `FileProvider`. Wrist Notes grants temporary read access only to that RPK and starts the exported Gadgetbridge installer activity explicitly:

```text
nodomain.freeyourgadget.gadgetbridge
  .activities.install.FileInstallerActivity
```

Gadgetbridge then performs the device-specific installation flow.

## Requirements

### To use the app

- Android 8.0 or newer;
- a recent Gadgetbridge release that exports `FileInstallerActivity` (the app currently expects Gadgetbridge 0.93+);
- a compatible VelaOS watch or band paired with Gadgetbridge.

### To build the Android app

- the Android SDK with API 37 available;
- a JDK compatible with the included Gradle and Android Gradle Plugin versions (JDK 21 is recommended);
- no Node.js installation is required unless the VelaOS template is being changed.

## Building the APK

From the project root:

```sh
./gradlew :app:assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected Android device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The `-r` flag updates the existing installation and preserves the saved notes.

## Using Wrist Notes

1. Install and configure Gadgetbridge, then pair the wearable.
2. Open Wrist Notes.
3. Tap **Add note**, enter or paste Markdown, then tap **Save**.
   You can also share text from another Android application to **Wrist Notes**; the app opens a new note with that text ready to edit or save.
4. Add or edit any other notes you want to include.
5. Tap the sync icon (**Send to watch**) on the main screen.
6. Review and confirm the installation in Gadgetbridge.
7. Open Wrist Notes on the wearable. Swipe left or right to switch notes.

Editing the collection on the phone does not immediately change the wearable application. Tap **Send to watch** again to build and install a new RPK version.

## Modifying the VelaOS template

Node.js 16 or newer is required only for this workflow.

```sh
cd vela-template
npm ci
npm run build
cd ..
```

The compiled template is written to `vela-template/build`. After changing the template, synchronize the relevant generated files into the Android assets:

```sh
cp vela-template/build/app.js \
  app/src/main/assets/rpk_template/app.js
cp vela-template/build/pages/index/index.js \
  app/src/main/assets/rpk_template/pages/index/index.js
cp vela-template/build/common/logo.png \
  app/src/main/assets/rpk_template/common/logo.png
cp vela-template/build/manifest.json \
  app/src/main/assets/rpk_template/manifest.json
cp vela-template/build/manifest-watch.json \
  app/src/main/assets/rpk_template/manifest-watch.json
```

Run the Android tests after every template update. The generated page must contain exactly one quoted `"__RPKER_NOTES__"` marker; `RpkBuilder` replaces that marker with the complete note collection.

## Tests

Run JVM tests and Android Lint with:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Run the RPK builder, signature, Unicode, icon, Markdown, and Gadgetbridge intent tests on a connected Android device with:

```sh
./gradlew :app:connectedDebugAndroidTest
```

The tested Wrist Notes build remains installed on the device after the instrumented tests finish.

## Project structure

```text
app/src/main/java/org/execbit/rpker/
├── MainActivity.kt             Android note list, editor, and Markdown strip
├── MarkdownEditing.kt          Selection-aware Markdown editing actions
├── Note.kt                     Stored note model and list preview
├── NoteStore.kt                Persistent local note collection
├── WristNoteViewModel.kt       List and editor state management
├── GadgetbridgeInstaller.kt    FileProvider handoff to Gadgetbridge
└── rpk/
    ├── MarkdownRenderer.kt     CommonMark to VelaOS block conversion
    ├── RpkBuilder.kt           On-device package assembly
    └── RpkSigner.kt            aiotpack-compatible signing and verification

app/src/main/assets/
├── rpk_template/               Precompiled VelaOS application template
└── rpk_signing/                Certificate and private key used for packaging

vela-template/                  Editable VelaOS source project
artwork/                        Android and RPK icon sources
```

## Signing and security notes

The RPK signing implementation follows the format used by `@aiot-toolkit/aiotpack` 2.0.5. Wrist Notes signs every generated package and verifies it before handing it to Gadgetbridge.

The signing key is embedded in the APK because signing must happen on the phone. It is therefore **not secret** and must not be treated as a publisher identity or trust anchor. In this project, signing primarily provides package-format compatibility and integrity checking during generation.

The Android application declares no Internet permission. Saved Markdown is processed locally. Raw HTML is escaped, and the FileProvider exposes only files from the generated RPK cache directory.

## Current limitations

- There is no live phone-to-watch synchronization.
- Only one wearable application exists because every generated RPK uses the fixed package ID `org.execbit.rpker`; that application can contain multiple notes.
- Updating the note collection requires rebuilding and reinstalling the RPK.
- The combined text limit is 1 MB before packaging.
- VelaOS performs its own line wrapping; Wrist Notes does not add language-specific hyphenation.
- Images are represented as text and URLs rather than rendered on the wearable.
- Gadgetbridge's installer activity is an integration point, not a stable cross-application API, and may change in future releases.
- Screen adaptation is based on VelaOS shape media queries rather than a database of individual device models.

## Status and disclaimer

Wrist Notes is an experimental, unofficial project. It is not affiliated with or endorsed by Xiaomi, VelaOS, Mi Fitness, or Gadgetbridge.

Third-party license information is available in [`app/src/main/assets/THIRD_PARTY_NOTICES.txt`](app/src/main/assets/THIRD_PARTY_NOTICES.txt). A repository-wide license has not yet been declared.
