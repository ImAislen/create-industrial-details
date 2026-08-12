package com.aislen.createindustrialdetails.content.block.lighting.cagedlamp;

import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RivetedSteelCagedLampBlockEntity extends BlockEntity implements MenuProvider {

    public RivetedSteelCagedLampBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RIVETED_STEEL_CAGED_LAMP.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(
                "menu.create_industrial_details.riveted_steel_caged_lamp"
        );
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId,
            Inventory inventory,
            Player player
    ) {
        return new RivetedSteelCagedLampMenu(containerId, inventory, this);
    }
}
