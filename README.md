# ModBrowser

A lightweight Android browser (Kotlin + system WebView) built around four features,
all of which work in normal mobile mode — none of them require switching to
"desktop site":

1. **Ad blocking** — host-based request blocking in `AdBlocker.kt`, checked on
   every network request the WebView makes (`shouldInterceptRequest`).
2. **YouTube Picture-in-Picture** — native Android PIP is triggered automatically
   when you leave the app (home button / switch apps) while a video is playing.
   There's also a manual PIP button in the toolbar.
3. **Auto-select original YouTube audio track** — a script drives YouTube's own
   settings menu to pick the "Original" audio track instead of an auto-dub.
4. **Background playback when the screen turns off** — a foreground service +
   wake lock keeps the process and CPU alive, and an injected script stops the
   page from thinking it's hidden (which is what normally pauses video).
5. **Dark mode for every website** — uses WebView's built-in algorithmic
   darkening on modern Android, with a CSS-invert fallback script for older
   versions.

## How to build

1. Open this folder in Android Studio (Koala/Ladybug or newer).
2. Let Gradle sync — it will download the Android Gradle Plugin, Kotlin plugin,
   and AndroidX/Material dependencies from Google's/Maven's repositories.
3. Run on a device or emulator running Android 8.0 (API 26) or newer.

   > I built all the source/config files here, but this sandbox has no Android
   > SDK and no network access to Google's Maven repo, so I could not actually
   > run a Gradle build to compile-verify it end to end. Do a build in Android
   > Studio as your first step, and check the "Known rough edges" section below
   > if anything doesn't compile — it'll almost certainly be a small, obvious fix
   > (e.g. an SDK/AGP version mismatch with whatever you have installed).

## How each feature actually works

### Ad blocking (`AdBlocker.kt`)
Loads `assets/adblock_hosts.txt` (a plain hosts-file-style list) into a
`HashSet`, and blocks any request whose host matches an entry or a parent
domain of an entry. This is fast and needs no native filter-list parser, but
it's host-based, not full ABP-syntax (cosmetic filtering / regex rules aren't
supported). **For the strongest blocking**, replace `adblock_hosts.txt` with a
compiled hosts-file export of EasyList + EasyPrivacy + a mobile-ad list (many
free tools convert those filter lists into a plain hosts file) — the loader
works with any file in that format, one host per line.

### YouTube PIP
`MainActivity.onUserLeaveHint()` fires when you background the app; if a
`<video>` is currently playing (tracked via the JS→native bridge), it calls
`enterPictureInPictureMode()`. Because the whole WebView keeps rendering while
in PIP, the video keeps playing in the floating window — this is the same
approach other WebView-based browsers use, since a page video can't be piped
into Android's PIP surface directly.

### Original audio track auto-select
`assets/youtube_audio_track.js` runs on YouTube pages and clicks through
YouTube's own settings → "Audio track" → "Original" menu items in the DOM.
This is a UI-automation approach rather than a documented API, because
YouTube doesn't expose one on the mobile web player — **it depends on
YouTube's current menu text/class names and may need small tweaks if YouTube
changes their player UI.** It fails silently (does nothing) if it can't find
the menu, so it won't break playback.

### Background playback with the screen off
Two things work together:
- `assets/visibility_override.js` makes `document.hidden` always report
  `false` and blocks `visibilitychange`/`blur`/`pagehide` events from reaching
  the page — this is what actually stops YouTube (and most sites) from
  auto-pausing when you switch away.
- `PlaybackService.kt` is a foreground service holding a partial wake lock
  (capped at 4 hours as a battery-safety net) plus a low-priority "Playing
  media" notification, started/stopped automatically as videos play/pause.
  This keeps the CPU from sleeping once the screen turns off, which is what
  actually lets audio keep running.

### Dark mode for every site
`applyDarkMode()` uses `WebSettingsCompat.setAlgorithmicDarkeningAllowed`
(Android 13+) or the older `FORCE_DARK_ON` (Android 10–12) so WebView
darkens page colors itself. On anything older than Android 10,
`dark_mode_fallback.js` injects a CSS `invert()` filter instead. Toggle it
from the overflow menu (⋮ button).

## Known rough edges / honest caveats

- The audio-track script is inherently fragile (see above) — it's DOM
  automation against YouTube's live UI, not an official API.
- Background playback still depends on Android not killing the process under
  memory pressure; the foreground service + notification makes this unlikely
  but not impossible on very aggressive OEM battery managers (some Chinese
  OEM skins in particular need the app whitelisted from battery optimization
  manually by the user).
- The bundled `adblock_hosts.txt` is a small starter list, not a full
  EasyList compile — swap it for a bigger list for maximum blocking, per
  above.
- No tab management, bookmarks, history UI, or settings screen yet — this is
  a minimal, working starting point focused on the five features requested,
  not a feature-complete browser.
- There's no custom launcher icon artwork — it uses a simple placeholder
  vector so the project builds without needing generated image assets;
  swap in real mipmap icons whenever you're ready.
