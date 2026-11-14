package dev.shocky.spotifyhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public final class HudRenderer {
    private static final Map<String, String> FIT_CACHE = new HashMap<>();

    // Mouse state (only used while the chat screen is open)
    private static boolean wasDown = false;
    private static int hoverIcon = -1; // 0 prev, 1 play/pause, 2 next
    private static boolean hoverBar = false;

    private record Layout(int w, int h, int pad, int art, int tx, int right, int row3, int barY, int barX, int barW) {}

    private static Layout layout(HudConfig c) {
        int pad = 3;
        boolean bar = c.showProgress;
        boolean row3 = c.showButtons || c.showProgress;
        int h = row3 ? (bar ? 36 : 30) : 24;
        int art = h - pad * 2;
        int tx = c.showArt ? pad + art + 4 : pad + 2;
        int right = c.width - pad - 1;
        return new Layout(c.width, h, pad, art, tx, right, bar ? 21 : 20, h - 5, tx, right - tx);
    }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        HudConfig c = HudConfig.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!c.visible || mc.options.hudHidden) return;
        handleMouse(mc, c);
        draw(ctx, mc.textRenderer, c);
    }

    /** While chat is open, the icons and the timeline can be clicked. */
    private static void handleMouse(MinecraftClient mc, HudConfig c) {
        hoverIcon = -1;
        hoverBar = false;
        if (!(mc.currentScreen instanceof ChatScreen)) {
            wasDown = false;
            return;
        }
        long handle = mc.getWindow().getHandle();
        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double mx = mc.mouse.getScaledX(mc.getWindow());
        double my = mc.mouse.getScaledY(mc.getWindow());
        double lx = (mx - c.x) / c.scale;
        double ly = (my - c.y) / c.scale;
        Layout L = layout(c);

        if (c.showButtons) {
            for (int i = 0; i < 3; i++) {
                int ix = L.tx() + i * 12;
                if (lx >= ix - 3 && lx < ix + 9 && ly >= L.row3() - 2 && ly < L.row3() + 9) {
                    hoverIcon = i;
                }
            }
        }
        if (hoverIcon < 0 && c.showProgress
                && lx >= L.barX() - 2 && lx <= L.barX() + L.barW() + 2
                && ly >= L.barY() - 2 && ly < L.h()) {
            hoverBar = true;
        }

        SpotifyBridge b = ShockysSpotifyHud.BRIDGE;
        if (down && !wasDown) {
            if (hoverIcon == 0) b.send("prev");
            else if (hoverIcon == 1) b.send("playpause");
            else if (hoverIcon == 2) b.send("next");
            else if (hoverBar && b.durationMs() > 0 && !b.status().equals("NONE")) {
                double frac = Math.min(1.0, Math.max(0.0, (lx - L.barX()) / (double) L.barW()));
                b.seek((long) (frac * b.durationMs()));
            }
        }
        wasDown = down;
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

        Layout L = layout(c);
        final float titleScale = 0.9f;
        final float artistScale = 0.75f;

        Style style = HudFonts.style(c.font);
        String t = fit(tr, style, title, (int) ((L.right() - L.tx()) / titleScale), c.font);
        String a = fit(tr, style, artist, (int) ((L.right() - L.tx()) / artistScale), c.font);

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
            fillShape(ctx, 2, 2, L.w(), L.h(), c.rounded, sc, sc);
        }
        fillShape(ctx, 0, 0, L.w(), L.h(), c.rounded, bg1, bg2);
        if (c.border) {
            strokeShape(ctx, 0, 0, L.w(), L.h(), c.rounded, 0xFF000000 | HudConfig.rgb(c.borderColor, 0x3A3A3A));
        }

        // Album art
        if (c.showArt) {
            Identifier art = AlbumArt.current(b.artPath());
            if (art != null) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, art, L.pad(), L.pad(), 0f, 0f,
                        L.art(), L.art(), 64, 64, 64, 64);
            } else {
                ctx.fill(L.pad(), L.pad(), L.pad() + L.art(), L.pad() + L.art(), 0xFF2A2A2A);
            }
        }

        drawScaled(ctx, tr, Text.literal(t).setStyle(style), L.tx(), 3, titleScale, titleCol);
        drawScaled(ctx, tr, Text.literal(a).setStyle(style), L.tx(), 12, artistScale, artistCol);

        // Control icons
        if (c.showButtons) {
            for (int i = 0; i < 3; i++) {
                int kind = i == 0 ? 0 : i == 1 ? (playing ? 2 : 1) : 3;
                int col = hoverIcon == i ? accentCol : titleCol;
                icon(ctx, kind, L.tx() + i * 12, L.row3(), col);
            }
        }

        // Progress bar + times
        if (c.showProgress) {
            int by = L.barY();
            ctx.fill(L.barX(), by, L.barX() + L.barW(), by + 2, 0x55FFFFFF);
            if (progress >= 0f) {
                int filled = Math.round(L.barW() * progress);
                int col = playing ? accentCol : 0xFF888888;
                ctx.fill(L.barX(), by, L.barX() + filled, by + 2, col);
                if (hoverBar) {
                    ctx.fill(L.barX() + filled - 1, by - 1, L.barX() + filled + 2, by + 3, titleCol);
                }
            }
            String time = fmtTime(posMs) + " / " + fmtTime(durMs);
            int tw = Math.round(tr.getWidth(time) * 0.7f);
            drawScaled(ctx, tr, Text.literal(time), L.right() - tw, L.row3() + 1, 0.7f, artistCol);
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
