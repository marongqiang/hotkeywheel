package fi.dy.masa.hotkeywheel.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import fi.dy.masa.hotkeywheel.HotkeyWheelReference;

/**
 * Loads custom icon PNGs from config directory and registers them as dynamic textures.
 *
 * Icon id format: {@code custom:<fileName.png>} (case-insensitive prefix).
 */
public final class HotkeyWheelCustomIconUtil
{
    private static final String PREFIX = "custom:";
    private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();

    private HotkeyWheelCustomIconUtil() { }

    public static void clearTextures()
    {
        TEXTURES.clear();
    }

    public static Path iconsDir()
    {
        return FabricLoader.getInstance().getConfigDir().resolve(HotkeyWheelReference.MOD_ID).resolve("icons");
    }

    public static List<String> listPngFiles()
    {
        Path dir = iconsDir();
        if (Files.isDirectory(dir) == false) return List.of();
        try
        {
            List<String> out = new ArrayList<>();
            try (var stream = Files.list(dir))
            {
                stream.filter(p -> p != null && Files.isRegularFile(p))
                        .map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                        .forEach(out::add);
            }
            out.sort(Comparator.naturalOrder());
            return out;
        }
        catch (IOException e)
        {
            return List.of();
        }
    }

    @Nullable
    public static Identifier textureIdForIconId(@Nullable String iconId)
    {
        if (iconId == null) return null;
        String s = iconId.trim();
        if (s.length() < PREFIX.length() + 1) return null;
        if (s.regionMatches(true, 0, PREFIX, 0, PREFIX.length()) == false) return null;
        String fn = s.substring(PREFIX.length()).trim();
        if (fn.isEmpty()) return null;
        return getOrLoad(fn);
    }

    public static String toIconId(String fileName)
    {
        return PREFIX + fileName;
    }

    @Nullable
    private static Identifier getOrLoad(String fileName)
    {
        return TEXTURES.computeIfAbsent(fileName, HotkeyWheelCustomIconUtil::loadTexture);
    }

    @Nullable
    private static Identifier loadTexture(String fileName)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getTextureManager() == null) return null;
        Path p = iconsDir().resolve(fileName);
        if (Files.isRegularFile(p) == false) return null;
        try (InputStream in = Files.newInputStream(p))
        {
            NativeImage img = NativeImage.read(in);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            String safe = fileName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
            Identifier id = new Identifier(HotkeyWheelReference.MOD_ID, "custom_icons/" + safe);
            mc.getTextureManager().registerTexture(id, tex);
            return id;
        }
        catch (Throwable t)
        {
            return null;
        }
    }
}

