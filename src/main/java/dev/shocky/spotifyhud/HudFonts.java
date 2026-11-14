package dev.shocky.spotifyhud;

import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fonts that only affect this HUD, never Minecraft's normal text. */
public final class HudFonts {
    public record Entry(String id, String name) {}

    public static final List<Entry> ALL = List.of(
            new Entry("minecraft:default", "Minecraft"),
            new Entry("shockysspotifyhud:cursive", "Cursive"),
            new Entry("shockysspotifyhud:script", "Script"),
            new Entry("shockysspotifyhud:pixel", "Pixel"),
            new Entry("shockysspotifyhud:mono", "Monospace"),
            new Entry("shockysspotifyhud:bold", "Bold"),
            new Entry("minecraft:uniform", "Unifont")
    );

    private static final Map<String, Style> CACHE = new HashMap<>();

    public static Style style(String id) {
        if (id == null || id.equals("minecraft:default")) return Style.EMPTY;
        return CACHE.computeIfAbsent(id, k -> {
            Identifier ident = Identifier.tryParse(k);
            if (ident == null) return Style.EMPTY;
            return Style.EMPTY.withFont(new StyleSpriteSource.Font(ident));
        });
    }

    public static String name(String id) {
        for (Entry e : ALL) if (e.id().equals(id)) return e.name();
        return "Minecraft";
    }

    public static String next(String id) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).id().equals(id)) return ALL.get((i + 1) % ALL.size()).id();
        }
        return ALL.get(0).id();
    }

    private HudFonts() {}
}
