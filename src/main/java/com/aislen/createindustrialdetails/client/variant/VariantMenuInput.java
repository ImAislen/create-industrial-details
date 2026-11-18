package com.aislen.createindustrialdetails.client.variant;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.content.variant.VariantItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID, value = Dist.CLIENT)
public final class VariantMenuInput {
    private static final KeyMapping OPEN = new KeyMapping("key.create_industrial_details.variant_menu",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.create_industrial_details");

    private VariantMenuInput() {
    }

    @SubscribeEvent
    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }

    @SubscribeEvent
    public static void keyPressed(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            open(InputConstants.getKey(event.getKey(), event.getScanCode()));
        }
    }

    @SubscribeEvent
    public static void mousePressed(InputEvent.MouseButton.Post event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            open(InputConstants.Type.MOUSE.getOrCreate(event.getButton()));
        }
    }

    private static void open(InputConstants.Key pressed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.getOverlay() != null || !minecraft.isWindowActive()
                || !minecraft.mouseHandler.isMouseGrabbed() || minecraft.player == null
                || minecraft.level == null || !minecraft.player.isAlive()
                || minecraft.player.isSpectator() || !OPEN.isActiveAndMatches(pressed)) {
            return;
        }
        ItemStack held = minecraft.player.getMainHandItem();
        if (!held.isEmpty() && held.getItem() instanceof VariantItem family && family.variants().size() > 1) {
            minecraft.setScreen(new VariantMenuScreen(minecraft.player, held, family, pressed, OPEN.getKeyModifier()));
        }
    }
}
