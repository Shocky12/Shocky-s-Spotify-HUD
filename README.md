# Shocky's Spotify HUD

Fabric mod for Minecraft 1.21.11. Shows the Spotify song you're playing in a customizable HUD and lets you
play/pause, skip, go back, change volume and search. No API key or login: it reads Spotify through the
Windows media controls (Windows + Spotify desktop app only).

- Press **K** in-game for the controls screen (rebindable under Controls > Shocky's Spotify HUD).
- "Customize HUD..." has presets, opacity, colors (HEX), rounded corners, border, shadow, fonts, scale, size, position.
- Volume changes your Windows system volume (affects all audio). Search opens Spotify's search page.

## Build
GitHub: push this folder to a repo, open the Actions tab, download the `ShockysSpotifyHUD` artifact.
Local: install JDK 21 + Gradle 9.2.1, then `gradle build`. The jar is in `build/libs/`.

Requires Fabric Loader 0.18+ and Fabric API for 1.21.11.
Fonts are downloaded at build time from Google Fonts (SIL Open Font License).
