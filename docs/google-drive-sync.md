# Google Drive sync

Sync is optional and off by default. Users turn it on in **Settings → Sync with Google Drive**.
There is no Ink server and no Firebase: each note and folder is a small JSON file in the user's
own Drive `appDataFolder`, a hidden folder only Ink can read.

## How it works

- **Auth:** `AuthorizationClient` (Google Identity Services, `play-services-auth`) requests only
  the `https://www.googleapis.com/auth/drive.appdata` scope. It shows its own account picker and
  consent screen, so there is no separate sign-in step and no Credential Manager / ID token
  (that would also require a Web client ID). No token is stored by Ink; Play services caches it.
- **Files:** `note-<uuid>.json` and `folder-<uuid>.json`. Each file's `appProperties.updatedAt`
  mirrors the row's `updatedAt`, so a sync can decide what to move from the file list alone.
- **Merge** (`sync/SyncMerge.kt`, unit tested): last writer wins by `updatedAt`. Tombstones
  (`isDeleted`) are ordinary rows, so deletions propagate. Duplicate files for one id keep the
  newest and delete the rest. A downloaded row is only applied if no newer local edit landed
  meanwhile (`NotesDao.applyRemoteNote`).
- **When:** on app foreground and background (`ProcessLifecycleOwner`), every hour
  (WorkManager, needs a network), and on "Sync now". Passes are serialized.

## Google Cloud Console setup (one time)

1. Go to <https://console.cloud.google.com/> and create a project (e.g. "Ink").
2. **APIs & Services → Library →** enable **Google Drive API**.
3. **Google Auth Platform → Branding** (OAuth consent screen): user type **External**, app name
   "Ink", support email, developer contact. Add a privacy policy URL before publishing.
4. **Google Auth Platform → Data access → Add or remove scopes:** add
   `.../auth/drive.appdata`. It is a non-sensitive scope, so no Google security review is needed.
5. **Google Auth Platform → Audience:** while in *Testing*, add your Google account(s) as test
   users. Click **Publish app** before the Play Store release.
6. **Google Auth Platform → Clients → Create client → Android**, once per signing key:

   | Client          | Package name           | SHA-1 from                                  |
   |-----------------|------------------------|---------------------------------------------|
   | Debug           | `app.inknotes.debug`   | your debug keystore (below)                 |
   | Release (local) | `app.inknotes`         | your upload/release keystore                |
   | Play Store      | `app.inknotes`         | Play Console → Test and release → App integrity → **App signing key** SHA-1 |

   Debug SHA-1:

   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
   ```

   Or run `./gradlew :app:signingReport`.

Nothing from the console goes into the app: Android OAuth clients are matched by package name +
signing certificate, so there is no client ID or secret to add to the repo.

If the SHA-1 or package is wrong, turning sync on fails with "Couldn't connect to Google Drive"
and logcat (tag `InkSync`) shows an `ApiException` (usually status 10, `DEVELOPER_ERROR`).

## Play Store notes

- Data safety form: notes are stored in the user's own Google Drive, not collected by the developer.
- The app needs the `INTERNET` permission, used only by sync.
