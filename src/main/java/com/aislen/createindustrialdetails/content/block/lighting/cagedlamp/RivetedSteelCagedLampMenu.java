package com.aislen.createindustrialdetails.content.block.lighting.cagedlamp;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.aislen.createindustrialdetails.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class RivetedSteelCagedLampMenu extends AbstractContainerMenu {

    private final BlockPos blockPos;
    private final ContainerLevelAccess access;

    public RivetedSteelCagedLampMenu(
            int containerId,
            Inventory inventory,
            RegistryFriendlyByteBuf buffer
    ) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    public RivetedSteelCagedLampMenu(
            int containerId,
            Inventory inventory,
            RivetedSteelCagedLampBlockEntity blockEntity
    ) {
        this(containerId, inventory, blockEntity.getBlockPos());
    }

    private RivetedSteelCagedLampMenu(
            int containerId,
            Inventory inventory,
            BlockPos blockPos
    ) {
        super(ModMenus.RIVETED_STEEL_CAGED_LAMP.get(), containerId);
        this.blockPos = blockPos.immutable();
        this.access = ContainerLevelAccess.create(
                inventory.player.level(),
                this.blockPos
        );
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                access,
                player,
                ModBlocks.RIVETED_STEEL_CAGED_LAMP.get()
        );
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
