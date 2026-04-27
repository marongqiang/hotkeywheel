package fi.dy.masa.hotkeywheel.util;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Suggested item icons for vanilla key translation ids when user has not set {@code hotkeywheel.json#iconItemIds}.
 */
public final class HotkeyWheelDefaultIcons
{
    private static final Map<String, String> BY_ACTION_ID = new HashMap<>();

    static
    {
        put("key.attack", "minecraft:golden_sword");
        put("key.use", "minecraft:ender_pearl");
        put("key.pickItem", "minecraft:stick");
        put("key.drop", "minecraft:bone");
        put("key.inventory", "minecraft:chest");
        put("key.swapOffhand", "minecraft:totem_of_undying");
        put("key.sneak", "minecraft:leather_boots");
        put("key.sprint", "minecraft:sugar");
        put("key.jump", "minecraft:rabbit_foot");
        put("key.forward", "minecraft:arrow");
        put("key.back", "minecraft:arrow");
        put("key.left", "minecraft:arrow");
        put("key.right", "minecraft:arrow");
        put("key.loadToolbarActivator", "minecraft:knowledge_book");
        put("key.saveToolbarActivator", "minecraft:writable_book");
        put("key.playerlist", "minecraft:player_head");
        put("key.chat", "minecraft:oak_sign");
        put("key.command", "minecraft:repeating_command_block");
        put("key.screenshot", "minecraft:map");
        put("key.smoothCamera", "minecraft:spyglass");
        put("key.advancements", "minecraft:book");
    }

    private static void put(String actionId, String itemId)
    {
        BY_ACTION_ID.put(actionId, itemId);
    }

    @Nullable
    public static String itemIdForAction(@Nullable String actionId)
    {
        if (actionId == null || actionId.isEmpty()) return null;
        return BY_ACTION_ID.get(actionId);
    }

    private HotkeyWheelDefaultIcons() { }
}
