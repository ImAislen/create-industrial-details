package com.aislen.createindustrialdetails.client.variant;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.block.supports.SmallSteelWallBracketBlockItem;
import com.aislen.createindustrialdetails.content.block.supports.SmallSteelWallBracketStyle;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID, value = Dist.CLIENT)
public final class VariantItemModels {
    private VariantItemModels() {
    }

    @SubscribeEvent
    public static void registerItemProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModBlocks.SMALL_STEEL_WALL_BRACKET.asItem(),
                ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "small_steel_wall_bracket_straight"),
                (ClampedItemPropertyFunction) (stack, level, entity, seed) ->
                        SmallSteelWallBracketBlockItem.style(stack) == SmallSteelWallBracketStyle.STRAIGHT ? 1.0F : 0.0F));
    }
}
