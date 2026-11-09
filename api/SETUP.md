# Cozymind API — setup on ByetHost

## 1. Create the database
1. ByetHost control panel → **MySQL Databases** → create a database (e.g. `cozymind`).
2. Note the **MySQL hostname**, full **database name** (starts with `b31_…`), **username** and password.
3. Open **phpMyAdmin** → select the database → **Import** → choose `schema.sql` → **Go**.

> If you already have `users`, `journals` or `mood_trackers` tables from an older version, either
> drop them first or check that their columns match `schema.sql`. Old plain-text passwords will
> not work with the new login (passwords are now hashed), so re-register test accounts.

## 2. Fill in config.php
Copy `config.example.php` to `config.php` and replace the four values with the ones from step 1.

## 3. Upload
Using the ByetHost **Online File Manager** or FileZilla, upload into `htdocs/cozymind-api/`:

- `api.php`
- `config.php`
- `.htaccess`

Do **not** upload `schema.sql` or this file.

## 4. Check it works
Open in your phone's browser:
`https://baldonaza.byethost31.com/cozymind-api/api.php?action=health`

You should see `{"ok":true,"time":"..."}`.

## 5. Run the app
Open `CozymindJava` in Android Studio, sync Gradle, and run. Register a new account, then try
mood check-ins and the journal.

## Endpoints (for reference)
| Action | Method | Body | Returns |
|---|---|---|---|
| `health` | GET | — | `{ok}` |
| `register` | POST | `name, username, email, phone_number, password` | `{user}` |
| `login` | POST | `username` (username or email), `password` | `{user}` |
| `logout` | POST | — | `{ok}` |
| `moods` | POST | `severity` (1–5) | `{ok, mood}` |
| `moods` | GET | — | `{moods, statistics_6h}` |
| `journals` | POST | `title, body` | `{ok, journal}` |
| `journals` | GET | — | `{journals}` |

## Troubleshooting
- **"The database is not reachable right now."** → wrong values in `config.php`.
- **"Your hosting service did not return the expected API response."** → `api.php` isn't at
  the URL above, or the site is suspended/inactive.
- **"Please sign in again."** → session expired; log in again.

## Per-file endpoints (new structure)
`api.php?action=...` still works for the Android app. The same features are also available as
separate files (5 APIs per module), shared setup lives in `includes/bootstrap.php`:

| Module | Files |
|---|---|
| authentication | `login.php`, `register.php`, `logout.php` |
| users | `get-all.php`, `create.php`, `view.php`, `update.php`, `delete.php` |
| moods | `get-all.php`, `create.php`, `view.php`, `update.php`, `delete.php` |
| journals | `get-all.php`, `create.php`, `view.php`, `update.php`, `delete.php` |
| goals | `get-all.php`, `create.php`, `view.php`, `update.php`, `delete.php` |
| sleep-logs | `get-all.php`, `create.php`, `view.php`, `update.php`, `delete.php` |

Upload the `authentication/`, `users/`, `moods/`, `journals/`, `includes/` folders and `health.php`
next to `api.php`. Import `Cozymind-API-v2.postman_collection.json` into Postman.

`users/get-all`, `users/create` need an admin account. `schema.sql` seeds one, so you can log in
directly from Postman (use the **Login (admin)** request): `username` = `admin`, `password` = `admin123`.
If your database already exists, re-import `schema.sql` (or run `install.php`) to add it - it only inserts
the admin if missing. You can also promote any user in phpMyAdmin:
`UPDATE users SET is_admin = 1 WHERE username = 'testuser';`

## Testing in Postman
1. Import `Cozymind-API-v2.postman_collection.json`.
2. Set the collection variable `base_url` to where the `api` folder is served
   (e.g. `http://localhost/cozymind-app-main/api` on XAMPP, `http://localhost/cozymind-api` on Laragon).
3. Run requests top to bottom: Register (or Login) first. Postman keeps the `PHPSESSID` cookie, and
   the ids returned by each Create are saved into variables so View / Update / Delete just work.
4. Update and Delete accept the id as `?id=` or in the JSON body (`{"id": 1}`). Use PUT/PATCH for
   update and DELETE for delete (POST is still accepted for both).

## Goals and sleep logs
Two new tables (`goals`, `sleep_logs`) were added to `schema.sql`. Existing databases keep their data:
open `install.php` again (Laragon) or re-import `schema.sql` in phpMyAdmin - it only creates what is missing.
Upload the new `goals/` and `sleep-logs/` folders and the updated `includes/bootstrap.php`.

| Endpoint | Body / query |
|---|---|
| `POST goals/create.php` | `title` (required), `description`, `frequency` (daily/weekly, default daily), `start_date` (YYYY-MM-DD, default today), `end_date` (optional), `status` (active/done/dropped, default active) |
| `PUT goals/update.php` | `id` + any fields to change (`end_date: null` clears it) |
| `POST sleep-logs/create.php` | `sleep_date` (YYYY-MM-DD), `bed_time`, `wake_time` (HH:MM), `quality` (1-5), `notes` (optional) |
| `PUT sleep-logs/update.php` | `id` + any fields to change |
| `GET .../view.php?id=1`, `DELETE .../delete.php?id=1`, `GET .../get-all.php` | same as moods and journals |
