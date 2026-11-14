package dev.shocky.spotifyhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class HudConfig {
    public boolean visible = true;
    public int x = 6;
    public int y = 6;
    public int width = 190;
    public float scale = 1.0f;

    public String preset = "Default";
    public String bgColor = "#121212";
    public String bgColor2 = "";
    public int opacity = 75;
    public boolean rounded = true;
    public boolean border = true;
    public String borderColor = "#3A3A3A";
    public boolean shadow = true;

    public String titleColor = "#FFFFFF";
    public String artistColor = "#B3B3B3";
    public String accentColor = "#1DB954";
    public String font = "minecraft:default";
    public boolean showProgress = true;
    public boolean showArt = true;
    public boolean showButtons = true;
    public int configVersion = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern HEX = Pattern.compile("^#?[0-9A-Fa-f]{6}$");
    private static HudConfig instance = new HudConfig();

    public static HudConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("shockysspotifyhud.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                HudConfig c = GSON.fromJson(Files.readString(p), HudConfig.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            ShockysSpotifyHud.LOGGER.warn("Could not read config, using defaults", e);
        }
        instance.sanitize();
    }

    public static void save() {
        try {
            instance.sanitize();
            Files.writeString(path(), GSON.toJson(instance));
        } catch (Exception e) {
            ShockysSpotifyHud.LOGGER.warn("Could not save config", e);
        }
    }

    public static void reset() {
        instance = new HudConfig();
        save();
    }

    private void sanitize() {
        if (configVersion < 2) { width = 190; configVersion = 2; }
        opacity = Math.max(0, Math.min(100, opacity));
        scale = Math.max(0.3f, Math.min(4.0f, scale));
        width = Math.max(80, Math.min(320, width));
        x = Math.max(0, x);
        y = Math.max(0, y);
        if (font == null || font.isEmpty()) font = "minecraft:default";
        if (!isHex(bgColor)) bgColor = "#121212";
        if (bgColor2 == null || (!bgColor2.isEmpty() && !isHex(bgColor2))) bgColor2 = "";
        if (!isHex(borderColor)) borderColor = "#3A3A3A";
        if (!isHex(titleColor)) titleColor = "#FFFFFF";
        if (!isHex(artistColor)) artistColor = "#B3B3B3";
        if (!isHex(accentColor)) accentColor = "#1DB954";
        if (preset == null) preset = "Custom";
    }

    public void applyPreset(Presets.Preset p) {
        preset = p.name();
        bgColor = p.bg();
        bgColor2 = p.bg2();
        opacity = p.opacity();
        border = p.border();
        borderColor = p.borderColor();
        titleColor = p.title();
        artistColor = p.artist();
        accentColor = p.accent();
    }

    public static boolean isHex(String s) {
        return s != null && HEX.matcher(s).matches();
    }

    public static String normalizeHex(String s) {
        s = s.trim();
        if (!s.startsWith("#")) s = "#" + s;
        return s.toUpperCase();
    }

    public static int rgb(String s, int fallback) {
        try {
            String h = s.startsWith("#") ? s.substring(1) : s;
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return fallback;
        }
    }
}
