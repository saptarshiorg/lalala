# EV SPORTS — Android (Jetpack Compose + embedded LibVLC)

Open this folder in Android Studio (Narwhal or newer), let Gradle sync, press Run.

- `data/` — CMS (Google Sheet CSV), FanCode / SonyLIV / Willow feeds, catalog + VIP playlist loaders (same endpoints as the HTML).
- `player/` — EV StreamPulse: direct HLS/DASH plays in embedded LibVLC; iframe/embed URLs stay in a WebView; Multiview.
- `ui/EvApp.kt` — app shell: top bar, bottom tabs, drawer, home sections, Matches / Live TV / Movies / TV / Search / Favourites / OTT / VIP / Community pages.
- `ui/components/` — hero banner, cards, modals, logo + animated loading splash (`res/drawable-nodpi/ev_logo.png`).
