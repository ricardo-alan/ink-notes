# App Functions (assistant actions)

Ink exposes a few actions to system AI agents through
[Android App Functions](https://developer.android.com/ai/appfunctions) (`androidx.appfunctions`,
currently **1.0.0-alpha13**, experimental). They only work on **Android 16+ (API 36)**; on older
versions the service is never bound and nothing changes.

## Functions

Function ids are `app.inknotes.appfunctions.InkAppFunctions#<name>`.

| Function | Parameters | Needs read opt-in |
| --- | --- | --- |
| `createNote` | `title`, `body?` | no |
| `createChecklist` | `title`, `items` (list of strings) | no |
| `addChecklistItems` | `items`, `noteId?`, `noteTitle?` | no |
| `searchNotes` | `query`, `limit?` (1–50, default 10) | **yes** |
| `getNote` | `noteId` | **yes** |

- Write functions return `{ id, title, itemsAdded }`.
- `addChecklistItems` uses `noteId` if given, otherwise `noteTitle`: an exact title match (ignoring
  case and accents) wins, otherwise the single note whose title contains it. Several partial matches
  are an error; the error names the candidates only when the read opt-in is on. Items are added as
  unchecked lines at the end of the note's last checklist (above its trailing checked items when
  "Move checked items to bottom" is on), or as a new checklist at the end of the note.
- `searchNotes` returns `{ notes: [{ id, title, preview, isPinned, modifiedAt }], totalMatches }`.
- `getNote` returns `{ id, title, text, checklistItems: [{ text, checked }], isPinned, modifiedAt }`.
- Notes in Recently Deleted are never returned or changed.

### Privacy

Reading is off by default. **Settings → Assistants → Allow assistants to read notes** (shown on
Android 16+ only) turns on `searchNotes` and `getNote`. While it's off they fail with
`AppFunctionPermissionRequiredException` (code 3500) and a message telling the agent where the
setting is. The preference is device-local (`shared_prefs/assistants.xml`) and isn't synced.

## Code

- `app/src/main/java/app/inknotes/appfunctions/InkAppFunctions.kt`: the abstract service with the
  `@AppFunctionDeclaration` functions and the `@AppFunctionSerializable` result types. Their KDoc is
  what agents read (`isDescribedByKDoc = true`), so keep it precise.
- KSP generates `InkAppFunctionService` (declared in the manifest) and `assets/ink_app_functions.xml`.
  `ksp { arg("appfunctions:aggregateAppFunctions", "true") }` in `app/build.gradle.kts` is required.
- Pure logic, unit tested: `model/NoteChecklists.kt` (build/append/read checklists) and
  `appfunctions/NoteMatching.kt` (title matching).
- `appfunctions-service` (last release alpha09) is obsolete: since alpha13 the service classes live in
  `androidx.appfunctions:appfunctions` itself. Don't add it; it pins `appfunctions` to alpha09.

## Trying it with adb (emulator `Medium_Phone_API_37.0`)

Start the emulator with `-gpu host` (never `-no-snapshot-save`), then install the debug build:

```bash
./gradlew :app:installDebug
```

List Ink's functions (the JSON includes descriptions and parameter schemas):

```bash
adb shell cmd app_function list-app-functions --package app.inknotes.debug
```

Create a note:

```bash
adb shell "cmd app_function execute-app-function --package app.inknotes.debug --function 'app.inknotes.appfunctions.InkAppFunctions#createNote' --parameters '{\"title\":\"Test note\",\"body\":\"Hello\"}' --brief-yaml"
```

Create a checklist:

```bash
adb shell "cmd app_function execute-app-function --package app.inknotes.debug --function 'app.inknotes.appfunctions.InkAppFunctions#createChecklist' --parameters '{\"title\":\"Groceries\",\"items\":[\"Milk\",\"Bread\"]}' --brief-yaml"
```

Add items by title (or pass `"noteId"` instead):

```bash
adb shell "cmd app_function execute-app-function --package app.inknotes.debug --function 'app.inknotes.appfunctions.InkAppFunctions#addChecklistItems' --parameters '{\"items\":[\"Eggs\"],\"noteTitle\":\"groceries\"}' --brief-yaml"
```

Search and read (turn the setting on first, or they fail with code 3500):

```bash
adb shell "cmd app_function execute-app-function --package app.inknotes.debug --function 'app.inknotes.appfunctions.InkAppFunctions#searchNotes' --parameters '{\"query\":\"groceries\",\"limit\":5}' --brief-yaml"
```

```bash
adb shell "cmd app_function execute-app-function --package app.inknotes.debug --function 'app.inknotes.appfunctions.InkAppFunctions#getNote' --parameters '{\"noteId\":\"<id>\"}' --brief-yaml"
```

On the debug build the setting can also be flipped without the UI (stop the app first):

```bash
adb shell "run-as app.inknotes.debug sed -i 's/value=\"false\"/value=\"true\"/' shared_prefs/assistants.xml"
```

### Known emulator issue: `Timed out`

On the API 37 emulator image, `execute-app-function` often prints `Timed out` for **every** app,
including the system's own (`com.google.android.permissioncontroller` / `getPermissionsDeviceState`).
In that state the platform never binds Ink's service (no log from Ink, nothing in
`dumpsys activity services app.inknotes.debug`), so it isn't an Ink bug. Right after boot it
answers `User is not unlocked`; in our testing a reboot (`adb reboot`, which keeps data), then
`adb shell wm dismiss-keyguard`, sometimes gave a window where calls worked, but not reliably.
`list-app-functions`, `is-enabled` and `set-enabled` keep working throughout.

### Without App Functions: debug-only external edits

To test how an open editor copes with writes from elsewhere when `execute-app-function` times out,
debug builds have `ExternalEditReceiver` (src/debug), which writes through the repository just like
`addChecklistItems`:

```bash
adb shell am broadcast -n app.inknotes.debug/app.inknotes.debug.ExternalEditReceiver -a add --es title "Groceries" --es items "Eggs,Jam"
```

`-a replace --es from Milk --es to "Oat milk"` rewrites a line instead (an edit the editor can't
merge, so it keeps a conflict copy; see `NoteMerge`).

### Verified on the emulator (2026-10-19)

- `list-app-functions` shows all five functions with their descriptions.
- `createNote` and `createChecklist` created notes that appear in Ink with the title, body lines and
  unchecked items.
- `addChecklistItems` by title (case-insensitive) and by id appended items to the checklist, seen in
  the app; an ambiguous title and an unknown title failed with clear errors.
- `getNote` with the setting off failed with the permission message.
- Not verified on the emulator: `searchNotes`/`getNote` succeeding with the setting on (the platform
  timed out every call during that test; the logic is the same repository queries the app uses).
