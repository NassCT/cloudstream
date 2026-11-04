# CloudStream Plus — Windows desktop app

An unofficial Windows (and Linux/macOS) build of CloudStream, built with Compose Multiplatform on top of
the shared `library` module. Like the Android app it hosts no content: everything comes from the
extensions you install.

## What works

- **Extensions**: add repositories (URL, `cloudstreamrepo://` link or short code), install, update and uninstall extensions.
  Regular Android `.cs3` extensions are loaded as-is (see below).
- **Home**: the home page sections of any installed provider.
- **Search** across every installed provider in parallel.
- **Details**: poster, plot, tags, cast, seasons, episodes, dub/sub for anime, recommendations.
- **Playback** in an external player: **mpv** (recommended, forwards every HTTP header) or **VLC**
  (forwards referer and user agent only). Subtitles found by the extension are downloaded and passed to the player.

Not supported yet: built-in player, downloads, torrents, accounts sync (AniList/MAL/Simkl), extension settings screens,
extensions that rely on Android APIs beyond `Context`, `SharedPreferences`, `Log` and `Base64`.

## How Android extensions run on Windows

A `.cs3` file is a zip with `manifest.json` and `classes.dex` (Android bytecode).

1. The dex is translated to a regular jar with [dex2jar](https://github.com/ThexXTURBOXx/dex2jar), once, cached next to the `.cs3`.
2. The jar is loaded in its own `URLClassLoader` whose parent provides the CloudStream `library` API.
3. Extensions extending the cross-platform `BasePlugin` work directly. Those extending the Android-only
   `Plugin` class get a desktop version of it (`com.lagradost.cloudstream3.plugins.Plugin`) plus small stubs
   of `android.content.Context`, `SharedPreferences`, `android.util.Log` and `android.util.Base64`.

All data lives in `%APPDATA%\CloudStreamPlus` (settings, extensions, cache).

## Build and run

Requires JDK 17+ (21 recommended). The Android SDK is needed because the shared modules also target Android
(create `local.properties` with `sdk.dir=...`).

```bash
./gradlew :desktopApp:run
```

Windows installer (`.exe`, WiX is downloaded automatically) and portable app:

```bash
./gradlew :desktopApp:packageExe :desktopApp:createDistributable
```

Outputs go to `desktopApp/build/compose/binaries/main/`.

Headless end-to-end check (installs an extension from the official repository, searches, loads, lists links):

```bash
CSP_SELFTEST=1 CSP_SELFTEST_PLUGIN=InternetArchiveProvider CSP_SELFTEST_QUERY=nosferatu ./gradlew :desktopApp:run
```

### Troubleshooting

`java.io.IOException: Unable to establish loopback connection` when starting Gradle on Windows comes from the JDK's
AF_UNIX sockets in the temp folder. Point them to a simple folder:

```powershell
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\tmp\uds'
```
