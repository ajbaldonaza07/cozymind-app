# Cozymind

A gentle mental-wellness app: mood check-ins, a breathing reset, comfort words and a private journal.

| Folder | What it is |
|---|---|
| `android/` | Android Studio project (Java + XML) |
| `api/` | PHP + MySQL backend the app talks to |

## Run locally (Laragon + Android Emulator)
1. Copy `api/` to `C:\laragon\www\cozymind-api\` and rename `config.example.php` → `config.php`.
2. Start Laragon (**Start All**), then open `http://localhost/cozymind-api/install.php` once to create the database.
3. Check `http://localhost/cozymind-api/api.php?action=health` shows `{"ok":true,...}`.
4. Open `android/` in Android Studio, set `USE_LOCAL_SERVER = true` in `CozymindApi.java`, and run it on an emulator.
   The app then uses `http://10.0.2.2/cozymind-api/api.php` (the emulator's address for your PC).

## Online server (default)
The app now talks to the online API at **https://cozymind.gethermedel.dev** (`ONLINE_ENDPOINT` in
`android/app/src/main/java/com/example/cozymind/CozymindApi.java`, with `USE_LOCAL_SERVER = false`).
Opening the bare domain in a browser shows *403 Forbidden*; that is normal, since the folder has no index page.
Check `https://cozymind.gethermedel.dev/api.php?action=health` instead.

To go back to Laragon, set `USE_LOCAL_SERVER = true`. For a real phone on Laragon, change
`LOCAL_ENDPOINT` to your PC's IPv4 address.

## Test with Postman
Import `api/Cozymind-API.postman_collection.json` and run the requests from top to bottom.
