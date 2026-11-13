# Glass Clock Weather

A Samsung / Android home-screen widget: a Material-style analog clock on the left and
weather on the right (temperature, condition, humidity, and four days of high and low).

Needs Android 12 or newer.

## Install

1. Open **Releases > Latest build** in this repo and download `glass-clock-weather.apk`.
2. Open the file on the phone and allow installing from that app if Android asks.
3. Long-press the home screen > Widgets > Glass Clock Weather, and add it (4 x 2 or bigger).

Every push to `main` rebuilds the APK and replaces the "latest" release. All builds use the
same signing key, so a new APK installs over the old one and the widget stays in place.

## Settings

Open the app, or tap the clock in the widget.

- Background: liquid glass, glass, solid or clear, with a transparency slider
- Dark or light theme, accent colour, clock shape, second hand on or off
- Units, and the city used for weather
- Google Weather API key

Tap the weather side of the widget to switch to feels-like and wind.

## Weather data

- With a key, the widget uses the Google Weather API. Create the key in Google Cloud
  (enable "Weather API" on a project with billing) and paste it into the app. The key is
  stored only on the phone, never in this repo.
- Without a key, or if Google returns an error, it uses Open-Meteo (https://open-meteo.com,
  CC BY 4.0), which is free for non-commercial use.
- Weather refreshes every 15 minutes, the shortest interval Android allows for background
  work. That is two Google calls per refresh, about 5,800 a month.

## Notes

- Android does not let a widget blur or refract the wallpaper, so the glass styles are
  translucent cards with drawn highlights.
- `signing/widget.jks` is a self-made key kept in the repo so every build is signed the
  same way. Keep the repo private, and do not reuse this key for a Play Store app.
- The two widget layouts are generated: `python3 tools/layout.py app/src/main/res/layout`.
