package dev.shocky.spotifyhud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AppearanceScreen extends Screen {
    private final Screen parent;
    private final List<Object[]> labels = new ArrayList<>(); // {String, Integer x, Integer y}

    public AppearanceScreen(Screen parent) {
        super(Text.literal("HUD Appearance"));
        this.parent = parent;
    }

    /** Simple labelled slider. */
    private static final class Slide extends SliderWidget {
        private final String label;
        private final double min;
        private final double max;
        private final boolean integer;
        private final Consumer<Double> onChange;

        Slide(int x, int y, int w, String label, double min, double max, double cur,
              boolean integer, Consumer<Double> onChange) {
            super(x, y, w, 20, Text.empty(), (Math.min(max, Math.max(min, cur)) - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.onChange = onChange;
            updateMessage();
        }

        private double current() {
            double v = min + this.value * (max - min);
            return integer ? Math.round(v) : Math.round(v * 100.0) / 100.0;
        }

        @Override
        protected void updateMessage() {
            if (label == null) return;
            double v = current();
            String s = integer ? Long.toString((long) v) : String.format("%.2f", v);
            setMessage(Text.literal(label + ": " + s));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }
    }

    private static Text toggleLabel(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    private void toggle(int x, int y, int w, String name, boolean initial, Consumer<Boolean> setter) {
        final boolean[] state = {initial};
        addDrawableChild(ButtonWidget.builder(toggleLabel(name, state[0]), btn -> {
            state[0] = !state[0];
            setter.accept(state[0]);
            btn.setMessage(toggleLabel(name, state[0]));
        }).dimensions(x, y, w, 20).build());
    }

    private void hexField(int x, int y, int w, String label, String current, Consumer<String> setter) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, 18, Text.literal(label));
        f.setMaxLength(7);
        f.setText(current);
        f.setChangedListener(s -> {
            if (HudConfig.isHex(s)) {
                setter.accept(HudConfig.normalizeHex(s));
                HudConfig.get().preset = "Custom";
            }
        });
        addDrawableChild(f);
        labels.add(new Object[]{label, x, y - 11});
    }

    @Override
    protected void init() {
        labels.clear();
        HudConfig c = HudConfig.get();

        int colW = 150;
        int left = Math.max(8, this.width - (colW * 2 + 8) - 6);
        int right = left + colW + 8;
        int top = Math.max(4, (this.height - 236) / 2);
        int y = top + 18;
        int step = 23;

        // --- Left column: background ---
        addDrawableChild(ButtonWidget.builder(Text.literal("Style: " + c.preset), btn -> {
            int idx = -1;
            for (int i = 0; i < Presets.ALL.size(); i++) {
                if (Presets.ALL.get(i).name().equals(HudConfig.get().preset)) idx = i;
            }
            HudConfig.get().applyPreset(Presets.ALL.get((idx + 1) % Presets.ALL.size()));
            this.clearAndInit();
        }).dimensions(left, y, colW, 20).build());

        addDrawableChild(new Slide(left, y + step, colW, "Opacity %", 0, 100, c.opacity, true,
                v -> { HudConfig.get().opacity = v.intValue(); HudConfig.get().preset = "Custom"; }));
        toggle(left, y + step * 2, colW, "Rounded", c.rounded, v -> HudConfig.get().rounded = v);
        toggle(left, y + step * 3, colW, "Border", c.border, v -> HudConfig.get().border = v);
        toggle(left, y + step * 4, colW, "Shadow", c.shadow, v -> HudConfig.get().shadow = v);
        toggle(left, y + step * 5, colW, "Progress bar", c.showProgress, v -> HudConfig.get().showProgress = v);

        // --- Right column: font + layout ---
        addDrawableChild(ButtonWidget.builder(Text.literal("Font: " + HudFonts.name(c.font)), btn -> {
            HudConfig.get().font = HudFonts.next(HudConfig.get().font);
            btn.setMessage(Text.literal("Font: " + HudFonts.name(HudConfig.get().font)));
        }).dimensions(right, y, colW, 20).build());

        addDrawableChild(new Slide(right, y + step, colW, "Scale", 0.5, 3.0, c.scale, false,
                v -> HudConfig.get().scale = v.floatValue()));
        addDrawableChild(new Slide(right, y + step * 2, colW, "Width", 100, 300, c.width, true,
                v -> HudConfig.get().width = v.intValue()));
        addDrawableChild(new Slide(right, y + step * 3, colW, "X", 0, Math.max(50, this.width - 60), c.x, true,
                v -> HudConfig.get().x = v.intValue()));
        addDrawableChild(new Slide(right, y + step * 4, colW, "Y", 0, Math.max(50, this.height - 40), c.y, true,
                v -> HudConfig.get().y = v.intValue()));
        toggle(right, y + step * 5, colW, "HUD visible", c.visible, v -> HudConfig.get().visible = v);

        // --- Hex colors ---
        int fy = y + step * 6 + 14;
        int fw = 56;
        int gap = 6;
        int fx = left;
        hexField(fx, fy, fw, "Background", c.bgColor, s -> { HudConfig.get().bgColor = s; HudConfig.get().bgColor2 = ""; });
        hexField(fx + (fw + gap), fy, fw, "Border", c.borderColor, s -> HudConfig.get().borderColor = s);
        hexField(fx + (fw + gap) * 2, fy, fw, "Title", c.titleColor, s -> HudConfig.get().titleColor = s);
        hexField(fx + (fw + gap) * 3, fy, fw, "Artist", c.artistColor, s -> HudConfig.get().artistColor = s);
        hexField(fx + (fw + gap) * 4, fy, fw, "Accent", c.accentColor, s -> HudConfig.get().accentColor = s);

        // --- Bottom buttons ---
        int by = fy + 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset to default"), btn -> {
            HudConfig.reset();
            this.clearAndInit();
        }).dimensions(left, by, colW, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> this.close())
                .dimensions(right, by, colW, 20).build());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // No blur or dimming, so the live HUD preview looks exactly like it will in-game.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int left = Math.max(8, this.width - (150 * 2 + 8) - 6);
        int top = Math.max(4, (this.height - 236) / 2);
        ctx.drawTextWithShadow(this.textRenderer, this.title, left, top + 4, 0xFFFFFFFF);
        for (Object[] l : labels) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal((String) l[0]),
                    (Integer) l[1], (Integer) l[2], 0xFFDDDDDD);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
        HudConfig.save();
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
