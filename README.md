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
4. Open `android/` in Android Studio and run it on an emulator.
   The app uses `http://10.0.2.2/cozymind-api/api.php` (the emulator's address for your PC).

For a real phone, change `LOCAL_ENDPOINT` in `CozymindApi.java` to your PC's IPv4 address.
To use the online server, set `USE_LOCAL_SERVER = false` and follow `api/SETUP.md`.

## Test with Postman
Import `api/Cozymind-API.postman_collection.json` and run the requests from top to bottom.
