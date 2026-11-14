package com.aislen.createindustrialdetails.content.block.rivetedsteel.trench;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

public final class ServiceTrenchBlockItem extends BlockItem {
    public ServiceTrenchBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!AllBlocks.GLASS_FLUID_PIPE.has(state)) return super.useOn(context);
        var player = context.getPlayer();
        if (player == null || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))
            return InteractionResult.FAIL;
        ModBlocks.SERVICE_TRENCH_GLASS_PIPE.get().encase(state, level, pos, player, context.getItemInHand());
        if (!level.isClientSide) {
            var sound = ModBlocks.SERVICE_TRENCH.get().defaultBlockState().getSoundType();
            level.playSound(null, pos, sound.getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                    (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
