package com.aislen.createindustrialdetails.content.variant;

import com.aislen.createindustrialdetails.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface VariantItem {
    List<Variant> variants();

    default String selectedVariant(ItemStack stack) {
        String stored = stack.get(ModDataComponents.ITEM_VARIANT.get());
        return variants().stream().anyMatch(variant -> variant.id().equals(stored))
                ? stored : variants().getFirst().id();
    }

    default boolean selectVariant(ItemStack stack, String id) {
        if (stack.getItem() != this || variants().stream().noneMatch(variant -> variant.id().equals(id))) {
            return false;
        }
        if (variants().getFirst().id().equals(id)) {
            stack.remove(ModDataComponents.ITEM_VARIANT.get());
        } else {
            stack.set(ModDataComponents.ITEM_VARIANT.get(), id);
        }
        return true;
    }

    default ItemStack previewStack(ItemStack stack, Variant variant) {
        ItemStack preview = stack.copyWithCount(1);
        selectVariant(preview, variant.id());
        return preview;
    }

    record Variant(String id, String translationKey) {}
}
