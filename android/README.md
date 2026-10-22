# Cozymind — Java Android Studio project

This is a Java and XML implementation of the accessible Figma login/signup flow. It contains:

- Login and sign-up layouts styled after the supplied Cozymind Figma file.
- Input validation and a local demo account saved with `SharedPreferences`.
- A functional dashboard with mood check-ins, a breathing reset, and a private journal.
- An on-device Room database for mood and journal data. The app works without an internet connection.

## Open it in Android Studio

1. In Android Studio, choose **Open** and select this `CozymindJava` folder.
2. Let Gradle sync. Use Android SDK 35 and JDK 17 if Android Studio prompts you.
3. Run the `app` configuration on an emulator or Android phone running Android 7.0 or newer.

The account is stored only on the device running the prototype. It has no backend yet, which makes it safe for a class demo but not suitable for real user accounts. For a real release, move account sign-in to Firebase Authentication or another secure authentication service; do not store real passwords locally.
