package dev.shocky.spotifyhud;

import java.util.List;

public final class Presets {
    public record Preset(String name, String bg, String bg2, int opacity,
                         boolean border, String borderColor,
                         String title, String artist, String accent) {}

    public static final List<Preset> ALL = List.of(
            new Preset("Default",     "#121212", "",        75, true,  "#3A3A3A", "#FFFFFF", "#B3B3B3", "#1DB954"),
            new Preset("Black",       "#000000", "",       100, false, "#000000", "#FFFFFF", "#AAAAAA", "#FFFFFF"),
            new Preset("Glass",       "#FFFFFF", "",        18, true,  "#E0E0E0", "#FFFFFF", "#E8E8E8", "#FFFFFF"),
            new Preset("Transparent", "#000000", "",         0, false, "#000000", "#FFFFFF", "#CCCCCC", "#1DB954"),
            new Preset("Spotify",     "#1DB954", "#191414", 92, false, "#1DB954", "#FFFFFF", "#E0E0E0", "#FFFFFF"),
            new Preset("Green",       "#0B3D1E", "",        88, true,  "#1DB954", "#FFFFFF", "#9FE8B5", "#1DB954"),
            new Preset("Purple",      "#2B0A4A", "",        88, true,  "#B26BFF", "#F3E5FF", "#C9A6F2", "#B26BFF"),
            new Preset("Neon",        "#0A0A1A", "#1A0033", 92, true,  "#00FFEA", "#00FFEA", "#FF4DE0", "#FF00E5"),
            new Preset("Crimson",     "#8B0A1A", "",        90, true,  "#FF4D5E", "#FFFFFF", "#FFC2C8", "#FF4D5E"),
            new Preset("Dark Red",    "#2A0505", "",        90, true,  "#B71C1C", "#FFE5E5", "#D18A8A", "#E53935"),
            new Preset("Ocean",       "#003B5C", "#0077B6", 90, true,  "#90E0EF", "#FFFFFF", "#CAF0F8", "#90E0EF"),
            new Preset("Blue",        "#0D47A1", "",        90, true,  "#64B5F6", "#FFFFFF", "#BBDEFB", "#64B5F6")
    );

    private Presets() {}
}
