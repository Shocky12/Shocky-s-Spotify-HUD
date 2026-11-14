package dev.shocky.spotifyhud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Talks to the Spotify desktop app through Windows' media controls using a small
 * hidden PowerShell helper (bridge.ps1). No API key or login needed.
 */
public final class SpotifyBridge {
    private volatile boolean available = true;
    private volatile String error = "";
    private volatile String status = "NONE";
    private volatile String title = "";
    private volatile String artist = "";
    private volatile long positionMs = 0;
    private volatile long durationMs = 0;
    private volatile long stampNanos = System.nanoTime();

    private volatile Process process;
    private volatile Path cmdDir;
    private volatile Path workDir;
    private final AtomicLong counter = new AtomicLong();

    public void start() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            available = false;
            error = "Windows only";
            return;
        }
        Thread t = new Thread(this::run, "ShockysSpotifyHUD-bridge");
        t.setDaemon(true);
        t.start();
    }

    private void run() {
        try {
            Path dir = Files.createTempDirectory("shockys_spotify_hud");
            workDir = dir;
            Path script = dir.resolve("bridge.ps1");
            try (InputStream in = SpotifyBridge.class.getResourceAsStream("/assets/shockysspotifyhud/bridge.ps1")) {
                if (in == null) throw new IOException("bridge.ps1 missing from jar");
                Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
            }
            Path cmds = dir.resolve("cmds");
            Files.createDirectories(cmds);
            cmdDir = cmds;

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                    "-File", script.toString(),
                    "-ParentPid", Long.toString(ProcessHandle.current().pid()),
                    "-CmdDir", cmds.toString());
            pb.redirectErrorStream(true);
            process = pb.start();

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    parse(line);
                }
            }
            available = false;
            error = "Media helper stopped";
        } catch (Exception e) {
            available = false;
            error = "Could not start helper";
            ShockysSpotifyHud.LOGGER.error("Spotify bridge failed", e);
        }
    }

    private void parse(String line) {
        if (!line.startsWith("STATE|")) return;
        String[] p = line.split("\\|", -1);
        if (p.length < 2 || p[1].equals("NONE")) {
            status = "NONE";
            return;
        }
        if (p.length < 6) return;
        try {
            title = decode(p[2]);
            artist = decode(p[3]);
            positionMs = Long.parseLong(p[4]);
            durationMs = Long.parseLong(p[5]);
            stampNanos = System.nanoTime();
            status = p[1];
        } catch (Exception ignored) {
        }
    }

    private static String decode(String b64) {
        if (b64.isEmpty()) return "";
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    public void send(String command) {
        Path dir = cmdDir;
        if (dir == null || !available) return;
        try {
            long n = counter.incrementAndGet();
            Path tmp = dir.resolve(String.format("%010d.tmp", n));
            Path fin = dir.resolve(String.format("%010d.cmd", n));
            Files.writeString(tmp, command, StandardCharsets.UTF_8);
            Files.move(tmp, fin, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            ShockysSpotifyHud.LOGGER.warn("Could not send command {}", command, e);
        }
    }

    /** Opens Spotify's search page for the query (Spotify must be installed). */
    public void search(String query) {
        String enc = URLEncoder.encode(query, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A");
        send("search:" + enc);
    }

    public void stop() {
        Process p = process;
        if (p != null) p.destroyForcibly();
        Path d = workDir;
        if (d != null) {
            try {
                try (var s = Files.walk(d)) {
                    s.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
                }
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isAvailable() { return available; }
    public String errorMessage() { return error; }
    public String status() { return status; }
    public String title() { return title; }
    public String artist() { return artist; }
    public long durationMs() { return durationMs; }

    public boolean isPlaying() { return status.equals("Playing"); }

    public long positionMs() {
        long pos = positionMs;
        if (isPlaying()) pos += (System.nanoTime() - stampNanos) / 1_000_000L;
        long dur = durationMs;
        if (dur > 0 && pos > dur) pos = dur;
        return Math.max(0, pos);
    }
}
