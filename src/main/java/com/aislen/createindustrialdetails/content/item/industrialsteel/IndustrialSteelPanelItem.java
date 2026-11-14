package com.aislen.createindustrialdetails.content.item.industrialsteel;

import com.aislen.createindustrialdetails.content.block.industrialsteel.panel.IndustrialSteelPanelShaftPenetrationBlock;
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


public final class IndustrialSteelPanelItem extends BlockItem {

    public IndustrialSteelPanelItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState targetState = level.getBlockState(pos);

        IndustrialSteelPanelShaftPenetrationBlock penetrationBlock =
            ModBlocks.INDUSTRIAL_STEEL_PANEL_SHAFT_PENETRATION.get();

        Optional<BlockState> suitable =
            penetrationBlock.getSuitableBracket(
                targetState,
                context.getClickedFace()
            );


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
