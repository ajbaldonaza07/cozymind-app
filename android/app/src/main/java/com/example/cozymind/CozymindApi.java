package com.example.cozymind;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** HTTPS client for the Cozymind API. The MySQL password remains on the server, never in the app. */
public final class CozymindApi {
    /*
     * Which server the app talks to. Set USE_LOCAL_SERVER = false to go back to ByetHost.
     *  - Android Emulator → Laragon on this PC: http://10.0.2.2/cozymind-api/api.php
     *  - Real phone on the same Wi-Fi: replace 10.0.2.2 with your PC's IPv4 address (run ipconfig).
     */
    private static final boolean USE_LOCAL_SERVER = true;
    private static final String LOCAL_ENDPOINT = "http://10.0.2.2/cozymind-api/api.php";
    private static final String ONLINE_ENDPOINT = "https://baldonaza.byethost31.com/cozymind-api/api.php";
    private static final String ENDPOINT = USE_LOCAL_SERVER ? LOCAL_ENDPOINT : ONLINE_ENDPOINT;
    private static final String BYETHOST_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 "
                    + "Chrome/132.0 Mobile Safari/537.36";
    private static final Pattern BYETHOST_CHALLENGE = Pattern.compile(
            "var a=toNumbers\\(\\\"([0-9a-f]+)\\\"\\),b=toNumbers\\(\\\"([0-9a-f]+)\\\"\\),c=toNumbers\\(\\\"([0-9a-f]+)\\\"\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BYETHOST_NEXT_STEP = Pattern.compile("[?&]i=(\\d+)");

    private boolean hostVerified;
    private int verifiedRequestStep;
    private String hostVerificationCookie = "";
    private String sessionCookie = "";

    public CozymindApi() {

    }

    public UserProfile register(String name, String username, String email, String phoneNumber,
                                String password) throws ApiException {
        try {
            JSONObject body = new JSONObject();
            body.put("name", name);
            body.put("username", username);
            body.put("email", email);
            body.put("phone_number", phoneNumber);
            body.put("password", password);
            return UserProfile.from(request("register", "POST", body).getJSONObject("user"));
        } catch (JSONException exception) {
            throw new ApiException("Could not read the server response.", -1, exception);
        }
    }

    public UserProfile login(String identity, String password) throws ApiException {
        try {
            JSONObject body = new JSONObject();
            body.put("identity", identity);
            body.put("password", password);
            return UserProfile.from(request("login", "POST", body).getJSONObject("user"));
        } catch (JSONException exception) {
            throw new ApiException("Could not read the server response.", -1, exception);
        }
    }

    public void logout() throws ApiException {
        request("logout", "POST", new JSONObject());
    }

    public void saveMood(int severity) throws ApiException {
        try {
            JSONObject body = new JSONObject();
            body.put("severity", severity);
            request("moods", "POST", body);
        } catch (JSONException exception) {
            throw new ApiException("Could not prepare the mood check-in.", -1, exception);
        }
    }

    public MoodResponse getMoods() throws ApiException {
        try {
            JSONObject response = request("moods", "GET", null);
            List<Mood> moods = new ArrayList<>();
            JSONArray moodArray = response.optJSONArray("moods");
            if (moodArray != null) {
                for (int index = 0; index < moodArray.length(); index++) {
                    moods.add(Mood.from(moodArray.getJSONObject(index)));
                }
            }

            List<MoodStatistic> statistics = new ArrayList<>();
            JSONArray statisticsArray = response.optJSONArray("statistics_6h");
            if (statisticsArray != null) {
                for (int index = 0; index < statisticsArray.length(); index++) {
                    statistics.add(MoodStatistic.from(statisticsArray.getJSONObject(index)));
                }
            }
            return new MoodResponse(moods, statistics);
        } catch (JSONException exception) {
            throw new ApiException("Could not read the server response.", -1, exception);
        }
    }

    public void saveJournal(String title, String body) throws ApiException {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("title", title);
            requestBody.put("body", body);
            request("journals", "POST", requestBody);
        } catch (JSONException exception) {
            throw new ApiException("Could not prepare the journal entry.", -1, exception);
        }
    }

    public List<Journal> getJournals() throws ApiException {
        try {
            JSONArray journals = request("journals", "GET", null).optJSONArray("journals");
            List<Journal> result = new ArrayList<>();
            if (journals != null) {
                for (int index = 0; index < journals.length(); index++) {
                    result.add(Journal.from(journals.getJSONObject(index)));
                }
            }
            return result;
        } catch (JSONException exception) {
            throw new ApiException("Could not read the server response.", -1, exception);
        }
    }

    public void clearSession() {
        hostVerified = false;
        verifiedRequestStep = 0;
        hostVerificationCookie = "";
        sessionCookie = "";
    }

    private JSONObject request(String action, String method, JSONObject requestBody) throws ApiException {
        HttpURLConnection connection = null;
        try {
            ensureByetHostVerified();
            URL url = new URL(ENDPOINT + "?action=" + action + "&i=" + verifiedRequestStep);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(12_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", BYETHOST_USER_AGENT);
            applyCookies(connection);
            connection.setDoInput(true);

            if (requestBody != null) {
                byte[] body = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
            }

            int responseCode = connection.getResponseCode();
            captureSessionCookie(connection);
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = readResponse(stream);
            JSONObject response = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);

            if (responseCode < 200 || responseCode >= 300) {
                throw new ApiException(response.optString("error", "Server request failed."), responseCode, null);
            }
            return response;
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | JSONException exception) {
            throw new ApiException("Could not reach your Cozymind server. Check your connection and try again.",
                    -1, exception);
        } catch (RuntimeException exception) {
            // Never let an unexpected response crash the background thread (and the app).
            Log.e("CozymindApi", "Unexpected error during " + action, exception);
            throw new ApiException("Something went wrong talking to the server. Please try again.",
                    -1, exception);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * ByetHost places a small JavaScript cookie challenge in front of free-hosted sites.
     * Android's HTTP client cannot execute that JavaScript, so solve its documented AES
     * challenge once, store the resulting first-party cookie, then continue with JSON calls.
     */
    private void ensureByetHostVerified() throws ApiException {
        if (hostVerified) return;

        int nextStep = -1;
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection connection = null;
            try {
                String suffix = nextStep < 0 ? "" : "&i=" + nextStep;
                connection = (HttpURLConnection) new URL(ENDPOINT + "?action=health" + suffix).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12_000);
                connection.setReadTimeout(12_000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", BYETHOST_USER_AGENT);
                applyCookies(connection);

                int responseCode = connection.getResponseCode();
                captureSessionCookie(connection);
                InputStream stream = responseCode >= 200 && responseCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String responseText = readResponse(stream);

                if (responseCode >= 200 && responseCode < 300 && responseText.trim().startsWith("{")) {
                    hostVerified = true;
                    verifiedRequestStep = Math.max(nextStep, 0);
                    return;
                }

                Matcher challenge = BYETHOST_CHALLENGE.matcher(responseText);
                Matcher redirect = BYETHOST_NEXT_STEP.matcher(responseText);
                if (!challenge.find() || !redirect.find()) {
                    throw new ApiException("Your hosting service did not return the expected API response.",
                            responseCode, null);
                }

                addHostVerificationCookie(solveByetHostChallenge(challenge));
                nextStep = Integer.parseInt(redirect.group(1));
            } catch (ApiException exception) {
                throw exception;
            } catch (Exception exception) {
                Log.e("CozymindApi", "ByetHost verification failed at step " + nextStep, exception);
                throw new ApiException("Could not verify the connection to your Cozymind server.",
                        -1, exception);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        throw new ApiException("Could not verify the connection to your Cozymind server.", -1, null);
    }

    private void addHostVerificationCookie(String value) {
        hostVerificationCookie = value;
    }

    private void applyCookies(HttpURLConnection connection) {
        StringBuilder header = new StringBuilder();
        if (!hostVerificationCookie.isEmpty()) header.append("__test=").append(hostVerificationCookie);
        if (!sessionCookie.isEmpty()) {
            if (header.length() > 0) header.append("; ");
            header.append(sessionCookie);
        }
        if (header.length() > 0) connection.setRequestProperty("Cookie", header.toString());
    }

    private void captureSessionCookie(HttpURLConnection connection) {
        List<String> setCookies = null;
        for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
            if ("Set-Cookie".equalsIgnoreCase(header.getKey())) {
                setCookies = header.getValue();
                break;
            }
        }
        if (setCookies == null) return;
        for (String value : setCookies) {
            if (value == null || !value.startsWith("PHPSESSID=")) continue;
            int delimiter = value.indexOf(';');
            sessionCookie = delimiter >= 0 ? value.substring(0, delimiter) : value;
            return;
        }
    }

    private String solveByetHostChallenge(Matcher challenge) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(hexToBytes(challenge.group(1)), "AES"),
                new IvParameterSpec(hexToBytes(challenge.group(2))));
        return bytesToHex(cipher.doFinal(hexToBytes(challenge.group(3))));
    }

    private byte[] hexToBytes(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < value.length(); index += 2) {
            result[index / 2] = (byte) Integer.parseInt(value.substring(index, index + 2), 16);
        }
        return result;
    }

    private String bytesToHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte current : value) result.append(String.format("%02x", current & 0xff));
        return result.toString();
    }

    private String readResponse(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    public static final class ApiException extends Exception {
        public final int statusCode;

        ApiException(String message, int statusCode, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
        }
    }

    public static final class UserProfile {
        public final long id;
        public final String name;
        public final String email;

        private UserProfile(long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }

        static UserProfile from(JSONObject user) {
            return new UserProfile(user.optLong("id"), user.optString("name"), user.optString("email"));
        }
    }

    public static final class Mood {
        public final int severity;
        public final String datetimeMood;

        private Mood(int severity, String datetimeMood) {
            this.severity = severity;
            this.datetimeMood = datetimeMood;
        }

        static Mood from(JSONObject mood) {
            return new Mood(mood.optInt("severity"), mood.optString("datetime_mood"));
        }
    }

    public static final class MoodStatistic {
        public final double averageSeverity;
        public final int entryCount;

        private MoodStatistic(double averageSeverity, int entryCount) {
            this.averageSeverity = averageSeverity;
            this.entryCount = entryCount;
        }

        static MoodStatistic from(JSONObject statistic) {
            return new MoodStatistic(statistic.optDouble("average_severity"),
                    statistic.optInt("entry_count"));
        }
    }

    public static final class MoodResponse {
        public final List<Mood> moods;
        public final List<MoodStatistic> statistics;

        private MoodResponse(List<Mood> moods, List<MoodStatistic> statistics) {
            this.moods = moods;
            this.statistics = statistics;
        }
    }

    public static final class Journal {
        public final String title;
        public final String body;
        public final String createdAt;

        private Journal(String title, String body, String createdAt) {
            this.title = title;
            this.body = body;
            this.createdAt = createdAt;
        }

        static Journal from(JSONObject journal) {
            return new Journal(journal.optString("title"), journal.optString("body"),
                    journal.optString("created_at"));
        }
    }
}
