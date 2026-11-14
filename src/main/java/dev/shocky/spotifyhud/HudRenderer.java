package dev.shocky.spotifyhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public final class HudRenderer {
    private static final Map<String, String> FIT_CACHE = new HashMap<>();

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        HudConfig c = HudConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!c.visible || mc.options.hudHidden) return;
        draw(ctx, mc.textRenderer, c);
    }

    public static void draw(DrawContext ctx, TextRenderer tr, HudConfig c) {
        SpotifyBridge b = ShockysSpotifyHud.BRIDGE;
        String title;
        String artist;
        float progress = -1f;
        boolean playing = false;

        if (!b.isAvailable()) {
            title = "Spotify HUD";
            artist = b.errorMessage();
        } else if (b.status().equals("NONE")) {
            title = "Spotify not detected";
            artist = "Open Spotify and play a song";
        } else {
            title = b.title().isEmpty() ? "Unknown title" : b.title();
            artist = b.artist().isEmpty() ? "Unknown artist" : b.artist();
            playing = b.isPlaying();
            long dur = b.durationMs();
            if (dur > 0) progress = Math.min(1f, Math.max(0f, b.positionMs() / (float) dur));
        }

        final int pad = 6;
        int w = c.width;
        int h = c.showProgress ? 34 : 28;
        int maxText = w - pad * 2;

        Style style = HudFonts.style(c.font);
        String t = fit(tr, style, title, maxText, c.font);
        String a = fit(tr, style, artist, maxText, c.font);

        int alpha = Math.round(c.opacity * 255f / 100f);
        int bg1 = (alpha << 24) | HudConfig.rgb(c.bgColor, 0x121212);
        int bg2 = c.bgColor2.isEmpty() ? bg1 : (alpha << 24) | HudConfig.rgb(c.bgColor2, 0x121212);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) c.x, (float) c.y);
        ctx.getMatrices().scale(c.scale, c.scale);

        if (c.shadow) {
            int sa = Math.round(0x55 * c.opacity / 100f);
            int sc = sa << 24;
            fillShape(ctx, 2, 2, w, h, c.rounded, sc, sc);
        }
        fillShape(ctx, 0, 0, w, h, c.rounded, bg1, bg2);
        if (c.border) {
            strokeShape(ctx, 0, 0, w, h, c.rounded, 0xFF000000 | HudConfig.rgb(c.borderColor, 0x3A3A3A));
        }

        int titleCol = 0xFF000000 | HudConfig.rgb(c.titleColor, 0xFFFFFF);
        int artistCol = 0xFF000000 | HudConfig.rgb(c.artistColor, 0xB3B3B3);
        int accentCol = 0xFF000000 | HudConfig.rgb(c.accentColor, 0x1DB954);

        ctx.drawText(tr, Text.literal(t).setStyle(style), pad, 5, titleCol, true);
        ctx.drawText(tr, Text.literal(a).setStyle(style), pad, 17, artistCol, true);

        if (c.showProgress) {
            int barX = pad;
            int barY = h - 6;
            int barW = w - pad * 2;
            ctx.fill(barX, barY, barX + barW, barY + 2, 0x55FFFFFF);
            if (progress >= 0f) {
                int filled = Math.round(barW * progress);
                int col = playing ? accentCol : 0xFF888888;
                ctx.fill(barX, barY, barX + filled, barY + 2, col);
            }
        }

        ctx.getMatrices().popMatrix();
    }

    private static int inset(int row, int h, boolean rounded) {
        if (!rounded) return 0;
        int d = Math.min(row, h - 1 - row);
        return d == 0 ? 3 : d == 1 ? 2 : d == 2 ? 1 : 0;
    }

    private static int lerp(int c1, int c2, float t) {
        int a = (int) (((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = (int) (((c1 >>> 16) & 255) * (1 - t) + ((c2 >>> 16) & 255) * t);
        int g = (int) (((c1 >>> 8) & 255) * (1 - t) + ((c2 >>> 8) & 255) * t);
        int bl = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | bl;
    }

    private static void fillShape(DrawContext ctx, int x, int y, int w, int h, boolean rounded, int c1, int c2) {
        for (int r = 0; r < h; r++) {
            int ins = inset(r, h, rounded);
            float t = h > 1 ? r / (float) (h - 1) : 0f;
            ctx.fill(x + ins, y + r, x + w - ins, y + r + 1, c1 == c2 ? c1 : lerp(c1, c2, t));
        }
    }

    private static void strokeShape(DrawContext ctx, int x, int y, int w, int h, boolean rounded, int col) {
        for (int r = 0; r < h; r++) {
            int ins = inset(r, h, rounded);
            if (r == 0 || r == h - 1) {
                ctx.fill(x + ins, y + r, x + w - ins, y + r + 1, col);
            } else {
                ctx.fill(x + ins, y + r, x + ins + 1, y + r + 1, col);
                ctx.fill(x + w - ins - 1, y + r, x + w - ins, y + r + 1, col);
            }
        }
    }

    private static String fit(TextRenderer tr, Style st, String s, int maxW, String fontId) {
        if (s == null) return "";
        String key = fontId + "|" + maxW + "|" + s;
        String cached = FIT_CACHE.get(key);
        if (cached != null) return cached;
        String result = s;
        if (tr.getWidth(Text.literal(s).setStyle(st)) > maxW) {
            int n = s.length();
            while (n > 0 && tr.getWidth(Text.literal(s.substring(0, n) + "...").setStyle(st)) > maxW) n--;
            result = s.substring(0, n) + "...";
        }
        if (FIT_CACHE.size() > 300) FIT_CACHE.clear();
        FIT_CACHE.put(key, result);
        return result;
    }

    private HudRenderer() {}
}
