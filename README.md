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

Open the app, or long-press the widget and choose its settings.

Tap the clock in the widget to open your Clock app's alarms.

- Background: liquid glass, glass, solid or clear, with a transparency slider
- Dark or light theme, accent colour, clock shape, second hand on or off
- Units, and where the weather is for: your precise location (default) or a city

## Location

On first open the app asks for location. Choose **Precise**. For the widget to follow you
while the app is closed, also choose **Allow all the time** when the app asks. Without that,
the location updates whenever you open the app, and the widget uses the last place in between.
- Google Weather API key

Tap the weather side of the widget to switch to feels-like and wind.

## Weather data

- With a key, the widget uses the Google Weather API. Create the key in Google Cloud
  (enable "Weather API" on a project with billing) and paste it into the app. The key is
  stored only on the phone, never in this repo.
- Without a key, or if Google returns an error, it uses Open-Meteo (https://open-meteo.com,
  CC BY 4.0), which is free for non-commercial use.
- Weather refreshes every 30 minutes, plus when you tap the widget if the data is older
  than 10 minutes. That is two Google calls per refresh, about 2,900 a month, inside the
  free 10,000.

## Notes

- Android does not let a widget blur or refract the wallpaper, so the glass styles are
  translucent cards with drawn highlights.
- `signing/widget.jks` is a self-made key kept in the repo so every build is signed the
  same way. Keep the repo private, and do not reuse this key for a Play Store app.
- The two widget layouts are generated: `python3 tools/layout.py app/src/main/res/layout`.
