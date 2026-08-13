package com.aislen.createindustrialdetails.client.screen;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.client.RivetedSteelCagedLampColors;
import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampBlock;
import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampColor;
import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampMenu;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.state.BlockState;

public class RivetedSteelCagedLampScreen
        extends AbstractContainerScreen<RivetedSteelCagedLampMenu> {

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    CreateIndustrialDetails.MOD_ID,
                    "textures/gui/riveted_steel_caged_lamp.png"
            );

    private static final int PANEL_WIDTH = 190;
    private static final int PANEL_HEIGHT = 222;
    private static final int MODE_BUTTON_WIDTH = 72;

    private static final int PRIMARY_TEXT = 0xFFE3DED2;
    private static final int SECONDARY_TEXT = 0xFFB8B3A8;
    private static final int VALUE_TEXT = 0xFFD8D4CA;

    private static final Component STATUS_LABEL = Component.translatable(
            "menu.create_industrial_details.riveted_steel_caged_lamp.lamp_status"
    );
    private static final Component MODE_LABEL = Component.translatable(
            "menu.create_industrial_details.riveted_steel_caged_lamp.redstone_mode"
    );
    private static final Component FREQUENCY_LABEL = Component.translatable(
            "menu.create_industrial_details.riveted_steel_caged_lamp.wireless_frequency"
    );
    private static final Component NORMAL_LABEL = Component.translatable(
            "menu.create_industrial_details.value.normal"
    );
    private static final Component INVERTED_LABEL = Component.translatable(
            "menu.create_industrial_details.value.inverted"
    );
    private static final Component ON_LABEL = Component.translatable(
            "menu.create_industrial_details.value.on"
    );
    private static final Component OFF_LABEL = Component.translatable(
            "menu.create_industrial_details.value.off"
    );
    private static final Component INVENTORY_LABEL = Component.translatable(
            "container.inventory"
    );

    private ModeButton normalButton;
    private ModeButton invertedButton;

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
        normalButton = addRenderableWidget(new ModeButton(
                leftPos + 18,
                buttonY,
                MODE_BUTTON_WIDTH,
                NORMAL_LABEL,
                RivetedSteelCagedLampMenu.SET_NORMAL,
                Component.translatable(
                        "menu.create_industrial_details.riveted_steel_caged_lamp.normal.tooltip"
                )
        ));
        invertedButton = addRenderableWidget(new ModeButton(
                leftPos + imageWidth - 18 - MODE_BUTTON_WIDTH,
                buttonY,
                MODE_BUTTON_WIDTH,
                INVERTED_LABEL,
                RivetedSteelCagedLampMenu.SET_INVERTED,
                Component.translatable(
                        "menu.create_industrial_details.riveted_steel_caged_lamp.inverted.tooltip"
                )
        ));
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        updateModeButtons();
        renderBackground(graphics, mouseX, mouseY, partialTick);
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
        graphics.blit(
                GUI_TEXTURE,
                leftPos,
                topPos,
                0,
                0,
                imageWidth,
                imageHeight,
                256,
                256
        );

        drawFrequencyConnector(graphics);

        for (int slotIndex = 0; slotIndex < menu.slots.size(); slotIndex++) {
            Slot slot = menu.slots.get(slotIndex);
            boolean frequencySlot =
                    slotIndex == RivetedSteelCagedLampMenu.FIRST_FREQUENCY_SLOT
                            || slotIndex
                            == RivetedSteelCagedLampMenu.SECOND_FREQUENCY_SLOT;
            boolean hovered = frequencySlot
                    && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY);
            drawSlotFrame(graphics, slot.x, slot.y, frequencySlot, hovered);
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
                12,
                PRIMARY_TEXT
        );

        BlockState state = getLampState();
        boolean lampPresent = state != null;
        boolean lit = lampPresent
                && state.getValue(RivetedSteelCagedLampBlock.LIT);
        RivetedSteelCagedLampColor color = lampPresent
                ? state.getValue(RivetedSteelCagedLampBlock.COLOR)
                : RivetedSteelCagedLampColor.NATURAL;

        drawStatusIndicator(
                graphics,
                19,
                42,
                getIndicatorColor(color, lit)
        );
        graphics.drawString(font, STATUS_LABEL, 31, 39, SECONDARY_TEXT, false);
        graphics.drawString(
                font,
                lit ? ON_LABEL : OFF_LABEL,
                159,
                39,
                VALUE_TEXT,
                false
        );

        graphics.drawCenteredString(font, MODE_LABEL, imageWidth / 2, 56, PRIMARY_TEXT);
        graphics.drawCenteredString(
                font,
                FREQUENCY_LABEL,
                imageWidth / 2,
                97,
                PRIMARY_TEXT
        );
        graphics.drawString(font, INVENTORY_LABEL, 14, 132, SECONDARY_TEXT, false);
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

    private BlockState getLampState() {
        if (minecraft.level == null)
            return null;

        BlockState state = minecraft.level.getBlockState(menu.getBlockPos());
        return state.is(ModBlocks.RIVETED_STEEL_CAGED_LAMP.get())
                ? state
                : null;
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

        BlockState state = getLampState();
        if (state == null) {
            normalButton.setSelected(false);
            invertedButton.setSelected(false);
            normalButton.active = false;
            invertedButton.active = false;
            return;
        }

        boolean inverted = state.getValue(RivetedSteelCagedLampBlock.INVERTED);
        normalButton.setSelected(!inverted);
        invertedButton.setSelected(inverted);
        normalButton.active = inverted;
        invertedButton.active = !inverted;
    }

    private void drawSlotFrame(
            GuiGraphics graphics,
            int x,
            int y,
            boolean frequency,
            boolean hovered
    ) {
        int left = leftPos + x;
        int top = topPos + y;
        int rim = frequency ? 0xFF9A7D4C : 0xFF11151A;
        int highlight = frequency
                ? hovered ? 0xFFE0C17A : 0xFFC5A462
                : 0xFF444B52;

        graphics.fill(left - 2, top - 2, left + 18, top + 18, 0xFF0D1014);
        graphics.fill(left - 1, top - 1, left + 17, top + 17, rim);
        graphics.fill(left, top, left + 16, top + 16, frequency
                ? 0xFF20262C
                : 0xFF1D2227);
        graphics.fill(left, top, left + 16, top + 1, highlight);
        graphics.fill(left, top, left + 1, top + 16, highlight);
    }

    private void drawFrequencyConnector(GuiGraphics graphics) {
        int centerX = leftPos + 94;
        int centerY = topPos + 120;
        graphics.fill(centerX - 4, centerY - 1, centerX + 5, centerY + 2, 0xFF0C1014);
        graphics.fill(centerX - 1, centerY - 4, centerX + 2, centerY + 5, 0xFF0C1014);
        graphics.fill(centerX - 3, centerY, centerX + 4, centerY + 1, 0xFFC2A05E);
        graphics.fill(centerX, centerY - 3, centerX + 1, centerY + 4, 0xFFC2A05E);
    }

    private void drawStatusIndicator(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int color
    ) {
        int opaqueColor = 0xFF000000 | (color & 0x00FFFFFF);
        graphics.fill(centerX - 5, centerY - 3, centerX + 6, centerY + 4, 0xFF0B0E11);
        graphics.fill(centerX - 3, centerY - 5, centerX + 4, centerY + 6, 0xFF0B0E11);
        graphics.fill(centerX - 4, centerY - 2, centerX + 5, centerY + 3, 0xFFA8874D);
        graphics.fill(centerX - 2, centerY - 4, centerX + 3, centerY + 5, 0xFFA8874D);
        graphics.fill(centerX - 3, centerY - 2, centerX + 4, centerY + 3, opaqueColor);
        graphics.fill(centerX - 2, centerY - 3, centerX + 3, centerY + 4, opaqueColor);
        graphics.fill(centerX - 1, centerY - 2, centerX + 1, centerY, 0x66FFFFFF);
    }

    private int getIndicatorColor(
            RivetedSteelCagedLampColor color,
            boolean lit
    ) {
        if (color == RivetedSteelCagedLampColor.NATURAL && lit)
            return 0xFFF2E7CA;
        return RivetedSteelCagedLampColors.getDisplayColor(color, lit);
    }

    private final class ModeButton extends AbstractButton {

        private final int actionId;
        private boolean selected;

        private ModeButton(
                int x,
                int y,
                int width,
                Component label,
                int actionId,
                Component tooltip
        ) {
            super(x, y, width, 18, label);
            this.actionId = actionId;
            setTooltip(Tooltip.create(tooltip));
        }

        private void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public void onPress() {
            sendMenuAction(actionId);
        }

        @Override
        protected void renderWidget(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            int x = getX();
            int y = getY();
            int width = getWidth();
            int height = getHeight();

            if (selected) {
                graphics.fill(x, y, x + width, y + height, 0xFF0C0F12);
                graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF11161B);
                graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF1C2228);
                graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, 0xFF414950);
                graphics.fill(x + 3, y + height - 2, x + width - 3, y + height - 1, 0xFF8E754A);
            } else {
                int edge = isHovered ? 0xFFB09259 : 0xFF555D64;
                graphics.fill(x, y, x + width, y + height, 0xFF101419);
                graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, edge);
                graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF343B42);
                graphics.fill(x + 2, y + 2, x + width - 2, y + 3, 0xFF60686F);
                graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, 0xFF171C21);
            }

            int textColor = selected ? PRIMARY_TEXT : SECONDARY_TEXT;
            graphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    getMessage(),
                    x + width / 2,
                    y + 5,
                    textColor
            );
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
