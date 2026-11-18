package com.aislen.createindustrialdetails.content.block.supports;

import com.aislen.createindustrialdetails.content.variant.VariantItem;
import com.aislen.createindustrialdetails.registry.ModDataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;

public final class SmallSteelWallBracketBlockItem extends BlockItem implements VariantItem {
    private static final List<Variant> VARIANTS = Arrays.stream(SmallSteelWallBracketStyle.values())
            .map(style -> new Variant(style.getSerializedName(),
                    "variant.create_industrial_details.small_steel_wall_bracket." + style.getSerializedName()))
            .toList();

    public SmallSteelWallBracketBlockItem(SmallSteelWallBracketBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public List<Variant> variants() {
        return VARIANTS;
    }

    public static SmallSteelWallBracketStyle style(ItemStack stack) {
        return SmallSteelWallBracketStyle.STRAIGHT.getSerializedName().equals(stack.get(ModDataComponents.ITEM_VARIANT.get()))
                ? SmallSteelWallBracketStyle.STRAIGHT : SmallSteelWallBracketStyle.STANDARD;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        SmallSteelWallBracketBlock block = (SmallSteelWallBracketBlock) getBlock();
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (state.is(block) && !block.canAddBracket(state, context.getItemInHand())) {
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }
}
