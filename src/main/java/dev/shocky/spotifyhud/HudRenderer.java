package dev.shocky.spotifyhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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
        long posMs = 0;
        long durMs = 0;

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
            durMs = b.durationMs();
            posMs = b.positionMs();
            if (durMs > 0) progress = Math.min(1f, Math.max(0f, posMs / (float) durMs));
        }

        final int pad = 4;
        int w = c.width;
        int h = c.showProgress ? 40 : 30;
        int artSize = h - pad * 2;
        int tx = c.showArt ? pad + artSize + 5 : pad + 2;
        int iconsW = c.showButtons ? 30 : 0;
        int rightEdge = w - pad - 2;

        Style style = HudFonts.style(c.font);
        String t = fit(tr, style, title, rightEdge - tx - (iconsW > 0 ? iconsW + 4 : 0), c.font);
        String a = fit(tr, style, artist, (int) ((rightEdge - tx) / 0.8f), c.font);

        int alpha = Math.round(c.opacity * 255f / 100f);
        int bg1 = (alpha << 24) | HudConfig.rgb(c.bgColor, 0x121212);
        int bg2 = c.bgColor2.isEmpty() ? bg1 : (alpha << 24) | HudConfig.rgb(c.bgColor2, 0x121212);

        int titleCol = 0xFF000000 | HudConfig.rgb(c.titleColor, 0xFFFFFF);
        int artistCol = 0xFF000000 | HudConfig.rgb(c.artistColor, 0xB3B3B3);
        int accentCol = 0xFF000000 | HudConfig.rgb(c.accentColor, 0x1DB954);

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

        // Album art
        if (c.showArt) {
            Identifier art = AlbumArt.current(b.artPath());
            if (art != null) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, art, pad, pad, 0f, 0f,
                        artSize, artSize, 64, 64, 64, 64);
            } else {
                ctx.fill(pad, pad, pad + artSize, pad + artSize, 0xFF2A2A2A);
            }
        }

        // Title
        ctx.drawText(tr, Text.literal(t).setStyle(style), tx, 5, titleCol, true);

        // Artist (smaller)
        drawScaled(ctx, tr, Text.literal(a).setStyle(style), tx, 16, 0.8f, artistCol);

        // Control icons (display only)
        if (c.showButtons) {
            int ix = rightEdge - iconsW;
            icon(ctx, 0, ix, 5, titleCol);
            icon(ctx, playing ? 2 : 1, ix + 12, 5, titleCol);
            icon(ctx, 3, ix + 24, 5, titleCol);
        }

        // Progress bar + times
        if (c.showProgress) {
            int barX = tx;
            int barY = h - 17;
            int barW = rightEdge - tx;
            ctx.fill(barX, barY, barX + barW, barY + 2, 0x55FFFFFF);
            if (progress >= 0f) {
                int filled = Math.round(barW * progress);
                int col = playing ? accentCol : 0xFF888888;
                ctx.fill(barX, barY, barX + filled, barY + 2, col);
            }
            String left = fmtTime(posMs);
            String right = fmtTime(durMs);
            drawScaled(ctx, tr, Text.literal(left), barX, barY + 5, 0.7f, artistCol);
            int rw = Math.round(tr.getWidth(right) * 0.7f);
            drawScaled(ctx, tr, Text.literal(right), barX + barW - rw, barY + 5, 0.7f, artistCol);
        }

        ctx.getMatrices().popMatrix();
    }

    private static void drawScaled(DrawContext ctx, TextRenderer tr, Text text, int x, int y, float scale, int color) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(tr, text, 0, 0, color, true);
        ctx.getMatrices().popMatrix();
    }

    private static String fmtTime(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    /** 0 = previous, 1 = play, 2 = pause, 3 = next. Icons are 6x7 pixels. */
    private static void icon(DrawContext ctx, int kind, int x, int y, int col) {
        switch (kind) {
            case 0 -> {
                ctx.fill(x, y, x + 1, y + 7, col);
                for (int i = 0; i < 4; i++) ctx.fill(x + 5 - i, y + i, x + 6 - i, y + 7 - i, col);
            }
            case 1 -> {
                for (int i = 0; i < 4; i++) ctx.fill(x + 1 + i, y + i, x + 2 + i, y + 7 - i, col);
            }
            case 2 -> {
                ctx.fill(x + 1, y, x + 3, y + 7, col);
                ctx.fill(x + 4, y, x + 6, y + 7, col);
            }
            default -> {
                for (int i = 0; i < 4; i++) ctx.fill(x + i, y + i, x + i + 1, y + 7 - i, col);
                ctx.fill(x + 5, y, x + 6, y + 7, col);
            }
        }
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
