package dev.shocky.spotifyhud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches time-synced lyrics from LRCLIB (free, no account). Only runs while the lyrics option is on.
 * LRCLIB has line timings, not word timings, so word highlighting is estimated by the renderer.
 */
public final class LyricsManager {
    public enum State { OFF, LOADING, READY, NOT_FOUND }

    private record Lyrics(long[] times, String[] texts) {}
    private record Line(long t, String s) {}

    private static final Lyrics EMPTY = new Lyrics(new long[0], new String[0]);
    private static final Pattern TS = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]");

    private static volatile State state = State.OFF;
    private static volatile Lyrics lyrics = EMPTY;
    private static volatile String currentKey = "";

    private static final Map<String, Lyrics> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Lyrics>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Lyrics> e) {
                    return size() > 30;
                }
            });

    public static State state() { return state; }

    public static void reset() {
        currentKey = "";
        state = State.OFF;
        lyrics = EMPTY;
    }

    /** Call every frame while lyrics are shown; only does work when the song changes. */
    public static void ensure(String title, String artist) {
        if (title == null || title.isEmpty()) return;
        String key = title + "|" + artist;
        if (key.equals(currentKey)) return;
        currentKey = key;
        Lyrics cached = CACHE.get(key);
        if (cached != null) {
            apply(cached);
            return;
        }
        state = State.LOADING;
        lyrics = EMPTY;
        Thread t = new Thread(() -> fetch(key, title, artist), "ShockysSpotifyHUD-lyrics");
        t.setDaemon(true);
        t.start();
    }

    private static void apply(Lyrics l) {
        lyrics = l;
        state = l.times().length == 0 ? State.NOT_FOUND : State.READY;
    }

    public static int lineIndex(long posMs) {
        long[] t = lyrics.times();
        int lo = 0, hi = t.length - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (t[mid] <= posMs) { ans = mid; lo = mid + 1; } else { hi = mid - 1; }
        }
        return ans;
    }

    public static String text(int i) {
        String[] s = lyrics.texts();
        return i >= 0 && i < s.length ? s[i] : "";
    }

    public static long start(int i) {
        long[] t = lyrics.times();
        return i >= 0 && i < t.length ? t[i] : 0;
    }

    public static int count() { return lyrics.times().length; }

    private static void fetch(String key, String title, String artist) {
        Lyrics result = EMPTY;
        boolean networkError = false;
        try {
            Thread.sleep(1200); // let Windows report the new song's length first
            if (!key.equals(currentKey)) return;
            long dur = ShockysSpotifyHud.BRIDGE.durationMs();
            String first = firstArtist(artist);
            String clean = cleanTitle(title);
            List<String[]> attempts = new ArrayList<>();
            attempts.add(new String[]{title, artist});
            if (!first.equals(artist)) attempts.add(new String[]{title, first});
            if (!clean.equals(title)) attempts.add(new String[]{clean, first});
            for (String[] q : attempts) {
                String url = "https://lrclib.net/api/search?track_name=" + enc(q[0]) + "&artist_name=" + enc(q[1]);
                Http.Response r = Http.request("GET", url, null, null);
                if (r.status() != 200) continue;
                Lyrics l = pick(r.body(), dur);
                if (l != null) { result = l; break; }
            }
        } catch (Exception e) {
            networkError = true;
            ShockysSpotifyHud.LOGGER.warn("Lyrics lookup failed: {}", e.toString());
        }
        if (!key.equals(currentKey)) return;
        if (result.times().length > 0) CACHE.put(key, result);
        apply(result);
        if (networkError && result.times().length == 0) state = State.NOT_FOUND;
    }

    private static Lyrics pick(String body, long durMs) {
        JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
        JsonObject best = null;
        double bestDiff = Double.MAX_VALUE;
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            JsonElement synced = o.get("syncedLyrics");
            if (synced == null || synced.isJsonNull() || synced.getAsString().isBlank()) continue;
            double d = 0;
            JsonElement de = o.get("duration");
            if (durMs > 0 && de != null && !de.isJsonNull()) d = Math.abs(de.getAsDouble() * 1000.0 - durMs);
            if (d < bestDiff) { bestDiff = d; best = o; }
        }
        if (best == null) return null;
        if (durMs > 0 && bestDiff > 8000) return null;
        return parseLrc(best.get("syncedLyrics").getAsString());
    }

    private static Lyrics parseLrc(String lrc) {
        List<Line> lines = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n")) {
            Matcher m = TS.matcher(raw);
            List<Long> stamps = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                stamps.add(toMs(m));
                end = m.end();
            }
            if (stamps.isEmpty()) continue;
            String text = raw.substring(end).replaceAll("<\\d+:\\d+(?:[.:]\\d+)?>", "").trim();
            for (long t : stamps) lines.add(new Line(t, text));
        }
        if (lines.isEmpty()) return null;
        lines.sort(Comparator.comparingLong(Line::t));
        long[] times = new long[lines.size()];
        String[] texts = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            times[i] = lines.get(i).t();
            texts[i] = lines.get(i).s();
        }
        return new Lyrics(times, texts);
    }

    private static long toMs(Matcher m) {
        long min = Long.parseLong(m.group(1));
        long sec = Long.parseLong(m.group(2));
        long frac = 0;
        if (m.group(3) != null) frac = Long.parseLong((m.group(3) + "00").substring(0, 3));
        return min * 60000L + sec * 1000L + frac;
    }

    private static String firstArtist(String artist) {
        String[] parts = artist.split("\\s*(,|&|;|\\bfeat\\.?\\b)\\s*");
        return parts.length > 0 && !parts[0].isBlank() ? parts[0].trim() : artist;
    }

    private static String cleanTitle(String title) {
        String t = title.replaceAll("\\s*[\\(\\[].*?[\\)\\]]", "").replaceAll("\\s+-\\s+.*$", "").trim();
        return t.isEmpty() ? title : t;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private LyricsManager() {}
}
