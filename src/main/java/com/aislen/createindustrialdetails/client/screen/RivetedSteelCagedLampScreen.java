package com.aislen.createindustrialdetails.client.screen;

import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampBlock;
import com.aislen.createindustrialdetails.content.block.lighting.cagedlamp.RivetedSteelCagedLampMenu;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;

public class RivetedSteelCagedLampScreen
        extends AbstractContainerScreen<RivetedSteelCagedLampMenu> {

    private static final int PANEL_WIDTH = 190;
    private static final int PANEL_HEIGHT = 110;

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
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
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

        boolean inverted =
                lampPresent
                        && state.getValue(RivetedSteelCagedLampBlock.INVERTED);

        Component status = Component.translatable(
                "menu.create_industrial_details.riveted_steel_caged_lamp.status",
                Component.translatable(
                        lit
                                ? "menu.create_industrial_details.value.on"
                                : "menu.create_industrial_details.value.off"
                )
        );

        Component mode = Component.translatable(
                "menu.create_industrial_details.riveted_steel_caged_lamp.mode",
                Component.translatable(
                        inverted
                                ? "menu.create_industrial_details.value.inverted"
                                : "menu.create_industrial_details.value.normal"
                )
        );

        graphics.drawString(font, status, 18, 47, 0xFFE6E9ED, false);
        graphics.drawString(font, mode, 18, 69, 0xFFE6E9ED, false);
    }
}
