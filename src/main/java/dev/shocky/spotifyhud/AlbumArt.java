package dev.shocky.spotifyhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the album cover PNG written by the helper and turns it into a Minecraft texture. */
public final class AlbumArt {
    private static String loadedPath = "";
    private static Identifier currentId = null;
    private static int counter = 0;

    /** Returns the texture id for the current cover, or null if there is none. Call on the render thread. */
    public static Identifier current(String path) {
        if (path == null) path = "";
        if (path.equals(loadedPath)) return currentId;
        loadedPath = path;
        release();
        if (path.isEmpty()) return null;
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            NativeImage img = NativeImage.read(in);
            Identifier id = Identifier.of(ShockysSpotifyHud.MOD_ID, "album_art_" + (++counter));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "Shocky's Spotify HUD album art", img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            tex.upload();
            currentId = id;
            ShockysSpotifyHud.LOGGER.info("Album art loaded: {}", path);
        } catch (Exception e) {
            ShockysSpotifyHud.LOGGER.warn("Could not load album art", e);
            currentId = null;
        }
        return currentId;
    }

    private static void release() {
        if (currentId != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(currentId);
            currentId = null;
        }
    }

    private AlbumArt() {}
}
