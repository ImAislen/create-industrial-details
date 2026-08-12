package com.aislen.createindustrialdetails.content.block.lighting.cagedlamp;

import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.aislen.createindustrialdetails.registry.ModDataComponents;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public class RivetedSteelCagedLampBlockEntity
        extends BlockEntity
        implements MenuProvider, IRedstoneLinkable {

    private static final String FREQUENCY_FIRST_TAG = "FrequencyFirst";
    private static final String FREQUENCY_SECOND_TAG = "FrequencySecond";

    private ItemStack frequencyFirst = ItemStack.EMPTY;
    private ItemStack frequencySecond = ItemStack.EMPTY;
    // The sentinel forces a newly loaded receiver to reconcile a saved LIT state,
    // even when the current network strength is zero.
    private int wirelessPower = -1;
    private boolean networkRegistered;

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

    public ItemStack getFirstFrequencyStack() {
        return frequencyFirst.copy();
    }

    public ItemStack getSecondFrequencyStack() {
        return frequencySecond.copy();
    }

    public void setFirstFrequency(ItemStack stack) {
        setFrequency(true, stack);
    }

    public void setSecondFrequency(ItemStack stack) {
        setFrequency(false, stack);
    }

    public boolean hasWirelessPower() {
        return wirelessPower > 0;
    }

    private void setFrequency(boolean first, ItemStack stack) {
        ItemStack normalized = normalizeFrequency(stack);
        setFrequencies(
                first ? normalized : frequencyFirst,
                first ? frequencySecond : normalized
        );
    }

    private void setFrequencies(ItemStack first, ItemStack second) {
        ItemStack normalizedFirst = normalizeFrequency(first);
        ItemStack normalizedSecond = normalizeFrequency(second);

        if (ItemStack.isSameItemSameComponents(
                frequencyFirst,
                normalizedFirst
        ) && ItemStack.isSameItemSameComponents(
                frequencySecond,
                normalizedSecond
        )) {
            return;
        }

        boolean rejoinNetwork = networkRegistered;
        if (rejoinNetwork)
            unregisterFromNetwork();

        frequencyFirst = normalizedFirst;
        frequencySecond = normalizedSecond;
        setChanged();

        if (rejoinNetwork)
            registerWithNetwork();
    }

    private static ItemStack normalizeFrequency(ItemStack stack) {
        if (stack.isEmpty())
            return ItemStack.EMPTY;

        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    @Override
    public int getTransmittedStrength() {
        return 0;
    }

    @Override
    public void setReceivedStrength(int power) {
        if (level == null || level.isClientSide)
            return;

        int normalizedPower = Math.max(0, Math.min(15, power));
        if (wirelessPower == normalizedPower)
            return;

        wirelessPower = normalizedPower;
        RivetedSteelCagedLampBlock.updateLitState(
                getBlockState(),
                level,
                worldPosition
        );
    }

    @Override
    public boolean isListening() {
        return true;
    }

    @Override
    public boolean isAlive() {
        Level currentLevel = level;
        return networkRegistered
                && currentLevel != null
                && !isRemoved()
                && currentLevel.isLoaded(worldPosition)
                && currentLevel.getBlockEntity(worldPosition) == this;
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return Couple.create(
                Frequency.of(frequencyFirst),
                Frequency.of(frequencySecond)
        );
    }

    @Override
    public BlockPos getLocation() {
        return worldPosition;
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (level != null && !level.isClientSide)
            registerWithNetwork();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterFromNetwork();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterFromNetwork();
        super.setRemoved();
    }

    private void registerWithNetwork() {
        if (networkRegistered || level == null || level.isClientSide || isRemoved())
            return;

        networkRegistered = true;
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, this);

        // Create 6.0.10 immediately refreshes newly added LinkBehaviour actors,
        // but not other direct IRedstoneLinkable implementations.
        refreshWirelessPower();
    }

    private void unregisterFromNetwork() {
        if (!networkRegistered || level == null || level.isClientSide)
            return;

        networkRegistered = false;
        Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this);
    }

    private void refreshWirelessPower() {
        if (!networkRegistered || level == null || level.isClientSide)
            return;

        RedstoneLinkNetworkHandler handler =
                Create.REDSTONE_LINK_NETWORK_HANDLER;
        Set<IRedstoneLinkable> network = handler.getNetworkOf(level, this);
        int power = 0;

        for (IRedstoneLinkable actor : network) {
            if (!actor.isAlive()
                    || !RedstoneLinkNetworkHandler.withinRange(this, actor))
                continue;

            power = Math.max(power, actor.getTransmittedStrength());
            if (power >= 15)
                break;
        }

        setReceivedStrength(power);
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.saveAdditional(tag, registries);
        tag.put(FREQUENCY_FIRST_TAG, frequencyFirst.saveOptional(registries));
        tag.put(FREQUENCY_SECOND_TAG, frequencySecond.saveOptional(registries));
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.loadAdditional(tag, registries);
        frequencyFirst = normalizeFrequency(
                ItemStack.parseOptional(
                        registries,
                        tag.getCompound(FREQUENCY_FIRST_TAG)
                )
        );
        frequencySecond = normalizeFrequency(
                ItemStack.parseOptional(
                        registries,
                        tag.getCompound(FREQUENCY_SECOND_TAG)
                )
        );
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);

        RivetedSteelCagedLampFrequencies frequencies =
                new RivetedSteelCagedLampFrequencies(
                        frequencyFirst,
                        frequencySecond
                );
        if (!frequencies.isEmpty()) {
            builder.set(
                    ModDataComponents.RIVETED_STEEL_CAGED_LAMP_FREQUENCIES.get(),
                    frequencies
            );
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);

        RivetedSteelCagedLampFrequencies frequencies = input.get(
                ModDataComponents.RIVETED_STEEL_CAGED_LAMP_FREQUENCIES.get()
        );
        if (frequencies != null)
            setFrequencies(frequencies.first(), frequencies.second());
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(FREQUENCY_FIRST_TAG);
        tag.remove(FREQUENCY_SECOND_TAG);
    }
}
