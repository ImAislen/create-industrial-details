package com.aislen.createindustrialdetails.client.screen;

import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampBlock;
import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampMenu;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.state.BlockState;

public class RivetedSteelCagedLampScreen
        extends AbstractContainerScreen<RivetedSteelCagedLampMenu> {

    private static final int PANEL_WIDTH = 190;
    private static final int PANEL_HEIGHT = 222;
    private static final int MODE_BUTTON_WIDTH = 72;

    private Button normalButton;
    private Button invertedButton;

    public RivetedSteelCagedLampScreen(
            RivetedSteelCagedLampMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        int buttonY = topPos + 68;
        normalButton = addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "menu.create_industrial_details.value.normal"
                                ),
                                button -> sendMenuAction(
                                        RivetedSteelCagedLampMenu.SET_NORMAL
                                )
                        )
                        .bounds(leftPos + 18, buttonY, MODE_BUTTON_WIDTH, 20)
                        .build()
        );
        invertedButton = addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        "menu.create_industrial_details.value.inverted"
                                ),
                                button -> sendMenuAction(
                                        RivetedSteelCagedLampMenu.SET_INVERTED
                                )
                        )
                        .bounds(
                                leftPos + imageWidth - 18 - MODE_BUTTON_WIDTH,
                                buttonY,
                                MODE_BUTTON_WIDTH,
                                20
                        )
                        .build()
        );
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        updateModeButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {
        graphics.fill(
                leftPos,
                topPos,
                leftPos + imageWidth,
                topPos + imageHeight,
                0xFF12161B
        );
        graphics.fill(
                leftPos + 2,
                topPos + 2,
                leftPos + imageWidth - 2,
                topPos + imageHeight - 2,
                0xFF303740
        );
        graphics.fill(
                leftPos + 2,
                topPos + 2,
                leftPos + imageWidth - 2,
                topPos + 31,
                0xFF242A31
        );

        for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
            Slot slot = menu.slots.get(slotIndex);
            boolean frequencySlot =
                    slotIndex == RivetedSteelCagedLampMenu.FIRST_FREQUENCY_SLOT
                            || slotIndex
                            == RivetedSteelCagedLampMenu.SECOND_FREQUENCY_SLOT;
            int x = leftPos + slot.x;
            int y = topPos + slot.y;

            graphics.fill(
                    x - 1,
                    y - 1,
                    x + 17,
                    y + 17,
                    frequencySlot ? 0xFFB59A62 : 0xFF11151A
            );
            graphics.fill(
                    x,
                    y,
                    x + 16,
                    y + 16,
                    0xFF252B32
            );
        }
    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        graphics.drawCenteredString(
                font,
                title,
                imageWidth / 2,
                11,
                0xFFF2E7CA
        );

        BlockState state = minecraft.level == null
                ? null
                : minecraft.level.getBlockState(menu.getBlockPos());

        boolean lampPresent =
                state != null
                        && state.is(ModBlocks.RIVETED_STEEL_CAGED_LAMP.get());

        boolean lit =
                lampPresent
                        && state.getValue(RivetedSteelCagedLampBlock.LIT);

        Component status = Component.translatable(
                "menu.create_industrial_details.riveted_steel_caged_lamp.status",
                Component.translatable(
                        lit
                                ? "menu.create_industrial_details.value.on"
                                : "menu.create_industrial_details.value.off"
                )
        );

        graphics.drawString(font, status, 18, 41, 0xFFE6E9ED, false);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "menu.create_industrial_details.riveted_steel_caged_lamp.redstone_mode"
                ),
                imageWidth / 2,
                55,
                0xFFE6E9ED
        );
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "menu.create_industrial_details.riveted_steel_caged_lamp.wireless_frequency"
                ),
                imageWidth / 2,
                98,
                0xFFE6E9ED
        );
        graphics.drawString(
                font,
                playerInventoryTitle,
                14,
                133,
                0xFFE6E9ED,
                false
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1)
                && clickFrequencySlot(
                        RivetedSteelCagedLampMenu.FIRST_FREQUENCY_SLOT,
                        RivetedSteelCagedLampMenu.SET_FIRST_FREQUENCY,
                        mouseX,
                        mouseY
                )) {
            return true;
        }

        if ((button == 0 || button == 1)
                && clickFrequencySlot(
                        RivetedSteelCagedLampMenu.SECOND_FREQUENCY_SLOT,
                        RivetedSteelCagedLampMenu.SET_SECOND_FREQUENCY,
                        mouseX,
                        mouseY
                )) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendMenuAction(int actionId) {
        if (minecraft.player != null
                && minecraft.gameMode != null
                && menu.clickMenuButton(minecraft.player, actionId)) {
            minecraft.gameMode.handleInventoryButtonClick(
                    menu.containerId,
                    actionId
            );
        }
    }

    private boolean clickFrequencySlot(
            int slotIndex,
            int actionId,
            double mouseX,
            double mouseY
    ) {
        Slot slot = menu.getSlot(slotIndex);
        if (!isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY))
            return false;

        sendMenuAction(actionId);
        return true;
    }

    private void updateModeButtons() {
        if (normalButton == null || invertedButton == null)
            return;

        BlockState state = minecraft.level == null
                ? null
                : minecraft.level.getBlockState(menu.getBlockPos());

        if (state == null
                || !state.is(ModBlocks.RIVETED_STEEL_CAGED_LAMP.get())) {
            normalButton.active = false;
            invertedButton.active = false;
            return;
        }

        boolean inverted = state.getValue(RivetedSteelCagedLampBlock.INVERTED);
        normalButton.active = inverted;
        invertedButton.active = !inverted;
    }
}
