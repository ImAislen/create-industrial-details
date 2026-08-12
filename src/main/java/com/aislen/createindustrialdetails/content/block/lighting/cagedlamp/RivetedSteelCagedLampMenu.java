package com.aislen.createindustrialdetails.content.block.lighting.cagedlamp;

import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.aislen.createindustrialdetails.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.state.BlockState;

public class RivetedSteelCagedLampMenu extends AbstractContainerMenu {

    public static final int SET_NORMAL = 0;
    public static final int SET_INVERTED = 1;
    public static final int SET_FIRST_FREQUENCY = 2;
    public static final int SET_SECOND_FREQUENCY = 3;

    public static final int FIRST_FREQUENCY_SLOT = 36;
    public static final int SECOND_FREQUENCY_SLOT = 37;

    private static final int PLAYER_INVENTORY_X = 14;
    private static final int PLAYER_INVENTORY_Y = 144;
    private static final int FIRST_FREQUENCY_X = 72;
    private static final int SECOND_FREQUENCY_X = 100;
    private static final int FREQUENCY_Y = 112;

    private final BlockPos blockPos;
    private final ContainerLevelAccess access;
    private final SimpleContainer frequencyItems;

    public RivetedSteelCagedLampMenu(
            int containerId,
            Inventory inventory,
            RegistryFriendlyByteBuf buffer
    ) {
        this(containerId, inventory, buffer.readBlockPos(), null);
    }

    public RivetedSteelCagedLampMenu(
            int containerId,
            Inventory inventory,
            RivetedSteelCagedLampBlockEntity blockEntity
    ) {
        this(containerId, inventory, blockEntity.getBlockPos(), blockEntity);
    }

    private RivetedSteelCagedLampMenu(
            int containerId,
            Inventory inventory,
            BlockPos blockPos,
            RivetedSteelCagedLampBlockEntity blockEntity
    ) {
        super(ModMenus.RIVETED_STEEL_CAGED_LAMP.get(), containerId);
        this.blockPos = blockPos.immutable();
        this.access = ContainerLevelAccess.create(
                inventory.player.level(),
                this.blockPos
        );
        this.frequencyItems = new SimpleContainer(2);

        addPlayerInventorySlots(inventory);
        addSlot(new FrequencySlot(
                frequencyItems,
                0,
                FIRST_FREQUENCY_X,
                FREQUENCY_Y
        ));
        addSlot(new FrequencySlot(
                frequencyItems,
                1,
                SECOND_FREQUENCY_X,
                FREQUENCY_Y
        ));

        if (blockEntity != null)
            copyFrequenciesFrom(blockEntity);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public ItemStack getFrequencyStack(int index) {
        if (index < 0 || index >= frequencyItems.getContainerSize())
            return ItemStack.EMPTY;
        return frequencyItems.getItem(index).copy();
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
    public boolean clickMenuButton(Player player, int actionId) {
        if (actionId < SET_NORMAL || actionId > SET_SECOND_FREQUENCY)
            return false;

        if (!stillValid(player))
            return false;

        // The client only requests one of the known actions. The authoritative
        // carried stack lookup and mutation happen when it reaches the server.
        if (player.level().isClientSide)
            return true;

        boolean handled = access.evaluate((level, pos) -> {
            BlockState state = level.getBlockState(pos);

            if (!state.is(ModBlocks.RIVETED_STEEL_CAGED_LAMP.get())
                    || !(level.getBlockEntity(pos)
                    instanceof RivetedSteelCagedLampBlockEntity lampBlockEntity))
                return false;

            switch (actionId) {
                case SET_NORMAL -> RivetedSteelCagedLampBlock.setInverted(
                        state,
                        level,
                        pos,
                        false
                );
                case SET_INVERTED -> RivetedSteelCagedLampBlock.setInverted(
                        state,
                        level,
                        pos,
                        true
                );
                case SET_FIRST_FREQUENCY ->
                        lampBlockEntity.setFirstFrequency(getCarried());
                case SET_SECOND_FREQUENCY ->
                        lampBlockEntity.setSecondFrequency(getCarried());
                default -> {
                    return false;
                }
            }
            return true;
        }, false);

        if (handled && actionId >= SET_FIRST_FREQUENCY)
            broadcastChanges();

        return handled;
    }

    @Override
    public void broadcastChanges() {
        access.execute((level, pos) -> {
            if (!level.isClientSide
                    && level.getBlockEntity(pos)
                    instanceof RivetedSteelCagedLampBlockEntity lamp) {
                copyFrequenciesFrom(lamp);
            }
        });

        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private void addPlayerInventorySlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18,
                        PLAYER_INVENTORY_Y + row * 18
                ));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    inventory,
                    column,
                    PLAYER_INVENTORY_X + column * 18,
                    PLAYER_INVENTORY_Y + 58
            ));
        }
    }

    private void copyFrequenciesFrom(
            RivetedSteelCagedLampBlockEntity blockEntity
    ) {
        setFrequencyDisplay(0, blockEntity.getFirstFrequencyStack());
        setFrequencyDisplay(1, blockEntity.getSecondFrequencyStack());
    }

    private void setFrequencyDisplay(int index, ItemStack stack) {
        ItemStack current = frequencyItems.getItem(index);
        if (!ItemStack.isSameItemSameComponents(current, stack)
                || current.getCount() != stack.getCount()) {
            frequencyItems.setItem(index, stack);
        }
    }

    private static class FrequencySlot extends Slot {

        private FrequencySlot(
                SimpleContainer container,
                int index,
                int x,
                int y
        ) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
