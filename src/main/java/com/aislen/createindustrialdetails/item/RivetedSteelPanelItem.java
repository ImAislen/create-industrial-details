package com.aislen.createindustrialdetails.item;

import com.aislen.createindustrialdetails.block.RivetedSteelPanelShaftPenetrationBlock;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Normal Riveted Steel Panel item plus penetration placement on Create shafts.
 */
public final class RivetedSteelPanelItem extends BlockItem {

    public RivetedSteelPanelItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState targetState = level.getBlockState(pos);

        RivetedSteelPanelShaftPenetrationBlock penetrationBlock =
            ModBlocks.RIVETED_STEEL_PANEL_SHAFT_PENETRATION.get();

        Optional<BlockState> suitable =
            penetrationBlock.getSuitableBracket(
                targetState,
                context.getClickedFace()
            );

        // Not a standard Create shaft end: behave exactly like a normal
        // Riveted Steel Panel item.
        if (suitable.isEmpty()) {
            return super.useOn(context);
        }

        BracketedBlockEntityBehaviour behaviour = BlockEntityBehaviour.get(
            level,
            pos,
            BracketedBlockEntityBehaviour.TYPE
        );

        if (behaviour == null || !behaviour.canHaveBracket()) {
            return super.useOn(context);
        }

        // Do not replace an existing Create bracket / panel penetration.
        if (behaviour.isBracketPresent()) {
            return InteractionResult.SUCCESS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockState penetration = suitable.get();

        level.playSound(
            null,
            pos,
            penetration.getSoundType().getPlaceSound(),
            SoundSource.BLOCKS,
            0.75F,
            1.0F
        );

        behaviour.applyBracket(penetration);

        Player player = context.getPlayer();
        if (player == null || !player.isCreative()) {
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.SUCCESS;
    }
}
