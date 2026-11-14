package dev.shocky.spotifyhud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ControlsScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget search;

    public ControlsScreen(Screen parent) {
        super(Text.literal("Shocky's Spotify HUD"));
        this.parent = parent;
    }

    private static Text visibleLabel() {
        return Text.literal("HUD: " + (HudConfig.get().visible ? "Shown" : "Hidden"));
    }

    @Override
    protected void init() {
        SpotifyBridge b = ShockysSpotifyHud.BRIDGE;
        int cx = this.width / 2;
        int top = this.height / 2 - 89;

        addDrawableChild(ButtonWidget.builder(Text.literal("<<"), btn -> b.send("prev"))
                .dimensions(cx - 102, top + 36, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Play / Pause"), btn -> b.send("playpause"))
                .dimensions(cx - 32, top + 36, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">>"), btn -> b.send("next"))
                .dimensions(cx + 38, top + 36, 64, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Vol -"), btn -> b.send("vol:down"))
                .dimensions(cx - 102, top + 62, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Mute"), btn -> b.send("vol:mute"))
                .dimensions(cx - 32, top + 62, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Vol +"), btn -> b.send("vol:up"))
                .dimensions(cx + 38, top + 62, 64, 20).build());

        search = new TextFieldWidget(this.textRenderer, cx - 102, top + 94, 138, 20, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search a song..."));
        search.setMaxLength(100);
        addDrawableChild(search);
        addDrawableChild(ButtonWidget.builder(Text.literal("Search"), btn -> {
            String q = search.getText().trim();
            if (!q.isEmpty()) b.search(q);
        }).dimensions(cx + 40, top + 94, 62, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Customize HUD..."),
                        btn -> this.client.setScreen(new AppearanceScreen(this)))
                .dimensions(cx - 102, top + 122, 204, 20).build());

        addDrawableChild(ButtonWidget.builder(visibleLabel(), btn -> {
            HudConfig.get().visible = !HudConfig.get().visible;
            btn.setMessage(visibleLabel());
            HudConfig.save();
        }).dimensions(cx - 102, top + 148, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> this.close())
                .dimensions(cx + 2, top + 148, 100, 20).build());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // No blur: keeps the HUD readable behind this screen.
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cx = this.width / 2;
        int top = this.height / 2 - 89;
        ctx.fill(cx - 112, top, cx + 112, top + 178, 0xC0000000);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, top + 7, 0xFFFFFFFF);

        SpotifyBridge b = ShockysSpotifyHud.BRIDGE;
        String line;
        if (!b.isAvailable()) line = b.errorMessage();
        else if (b.status().equals("NONE")) line = "Spotify not detected";
        else line = b.title() + " - " + b.artist();
        if (this.textRenderer.getWidth(line) > 200) {
            while (line.length() > 1 && this.textRenderer.getWidth(line + "...") > 200) {
                line = line.substring(0, line.length() - 1);
            }
            line = line + "...";
        }
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(line), cx, top + 21, 0xFFB3B3B3);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
