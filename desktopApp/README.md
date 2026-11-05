# CloudStream for Windows (desktopApp)

An unofficial Windows (and Linux/macOS) build of CloudStream, built with Compose Multiplatform on top of
the shared `library` module. It reuses the Android app's look: same icons, colors, Product Sans font,
navigation rail, home hero banner, poster cards, result page and player controls.
Like the Android app it hosts no content: everything comes from the extensions you install.

## Features

- **Extensions**: add repositories (URL, `cloudstreamrepo://` link or short code), install, update and
  uninstall extensions. Regular Android `.cs3` extensions are loaded as-is (see below).
- **Home**: hero banner with the provider's highlights, provider picker, *Continue watching*, home sections.
- **Search** across every installed provider in parallel, with type filters (movies, series, anime, ...).
- **Details**: backdrop, plot, cast, tags, Play / Resume, bookmarks, seasons, dub/sub, episodes with progress,
  recommendations.
- **Library**: bookmarked titles.
- **Built-in player** (libVLC, bundled in the installer): CloudStream-style controls, ±10 s, seek bar, resize
  (fit/fill/zoom), speed, sources, subtitles (auto-selected in your system language), next episode, auto-next,
  resume, fullscreen, automatic fallback to the next source when one fails or stalls, separate audio tracks.
- **External player** button (mpv or VLC) for sources that need custom HTTP headers.
- **DNS over HTTPS** (Settings > Network, on by default): gets around sites blocked by the internet provider's DNS.

Keyboard in the player: `Space`/`K` play/pause, `←`/`→` ±10 s, `↑`/`↓` volume, `F`/`F11` or double-click
fullscreen, `M` mute, `N` next episode, `Esc` back.

Not supported yet: downloads, torrents, accounts sync (AniList/MAL/Simkl), extension settings screens,
Cloudflare challenges (they need Android's WebView), extensions that rely on Android UI or account APIs.

## How Android extensions run on Windows

A `.cs3` file is a zip with `manifest.json` and `classes.dex` (Android bytecode).

1. The dex is translated to a regular jar with [dex2jar](https://github.com/ThexXTURBOXx/dex2jar), once, cached next to the `.cs3`.
2. The jar is loaded in its own `URLClassLoader` whose parent provides the CloudStream `library` API.
3. Extensions extending the cross-platform `BasePlugin` work directly. Those extending the Android-only
   `Plugin` class get a desktop version of it (`com.lagradost.cloudstream3.plugins.Plugin`) plus small stubs
   of `android.content.Context`, `SharedPreferences`, `android.util.Log` and `android.util.Base64`, and
   desktop versions of the app classes extensions commonly use (`DataStore`, `AcraApplication`/`CloudStreamApp`,
   `CloudflareKiller`, `MainActivity` events, `PluginManager`).

dex2jar runs with `dontSanitizeNames`, so Kotlin's mangled names (`constructor-impl`, ...) keep their `-` and still
link against the Kotlin stdlib and the library.

All data lives in `%APPDATA%\CloudStreamPlus` (settings, extensions, history, cache).

## Build and run

Requires JDK 17+ (21 recommended) and the Android SDK, because the shared modules also target Android
(create `local.properties` with `sdk.dir=...`). Install VLC 3 to get libVLC bundled into the app
(`VLC_DIR` can point to another VLC folder); without it the app falls back to an external player.

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

Check every installed extension (load + search):

```bash
CSP_SELFTEST=installed CSP_SELFTEST_QUERY=avatar ./gradlew :desktopApp:run
```

Developer helpers: `CSP_OPEN="<provider>|<url>"` opens a title at start-up, `CSP_VLC_VERBOSE=1` prints libVLC logs.

### Troubleshooting

`java.io.IOException: Unable to establish loopback connection` when starting **Gradle** on Windows comes from the
JDK's AF_UNIX sockets: on some machines the temp folder refuses them. Point them to another folder:

```powershell
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\tmp\uds'
```

The app itself detects this and picks a working folder on its own.

The installed app writes a log to `%APPDATA%\CloudStreamPlus\logs\app.log` (Settings > Log file).
