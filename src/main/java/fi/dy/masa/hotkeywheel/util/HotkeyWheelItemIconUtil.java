package fi.dy.masa.hotkeywheel.util;

import javax.annotation.Nullable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Resolves an {@link ItemStack} for optional wheel icons (config item id string).
 */
public final class HotkeyWheelItemIconUtil
{
    private HotkeyWheelItemIconUtil() { }

    public static ItemStack stackForItemId(@Nullable String id)
    {
        if (id == null || id.trim().isEmpty()) return new ItemStack(Items.COBBLESTONE);
        String s = id.trim();
        try
        {
            Identifier rid = new Identifier(s);
            Item it = Registries.ITEM.get(rid);
            if (it == null || it == Items.AIR) return new ItemStack(Items.COBBLESTONE);
            return new ItemStack(it);
        }
        catch (Exception e)
        {
            return new ItemStack(Items.COBBLESTONE);
        }
    }
}
