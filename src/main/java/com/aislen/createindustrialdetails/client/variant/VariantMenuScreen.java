package com.aislen.createindustrialdetails.client.variant;

import com.aislen.createindustrialdetails.content.variant.SelectItemVariant;
import com.aislen.createindustrialdetails.content.variant.VariantItem;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.List;

final class VariantMenuScreen extends Screen {
    private static final int DEAD_ZONE = 14;
    private static final int SELECTED = 0xFFC6A36E;
    private static final double FULL_TURN = 2 * Math.PI;

    private final LocalPlayer player;
    private final ItemStack held;
    private final VariantItem family;
    private final int slot;
    private final String previous;
    private final InputConstants.Key binding;
    private final KeyModifier modifier;
    private final List<Option> options;
    private boolean cancelled;

    VariantMenuScreen(LocalPlayer player, ItemStack held, VariantItem family, InputConstants.Key binding, KeyModifier modifier) {
        super(Component.translatable("gui.create_industrial_details.variant_menu"));
        this.player = player;
        this.held = held;
        this.family = family;
        this.slot = player.getInventory().selected;
        this.previous = family.selectedVariant(held);
        this.binding = binding;
        this.modifier = modifier;
        this.options = family.variants().stream()
                .map(variant -> new Option(variant.id(), Component.translatable(variant.translationKey()),
                        family.previewStack(held, variant)))
                .toList();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (!minecraft.isWindowActive()) {
            cancelled = true;
        } else if (cancelled || !canSelect()) {
            onClose();
        }
    }

    private boolean canSelect() {
        return !cancelled && minecraft.screen == this && minecraft.getOverlay() == null
                && minecraft.isWindowActive() && minecraft.player == player
                && player.isAlive() && !player.isSpectator()
                && player.getInventory().selected == slot && player.getMainHandItem() == held && !held.isEmpty()
                && family.selectedVariant(held).equals(previous);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (!key.equals(binding)) {
            if (minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                onClose();
                return false;
            }
            for (KeyMapping hotbar : minecraft.options.keyHotbarSlots) {
                if (hotbar.matches(keyCode, scanCode)) {
                    onClose();
                    return false;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (key.equals(binding)) {
            var window = minecraft.getWindow();
            double x = minecraft.mouseHandler.xpos() * width / window.getScreenWidth();
            double y = minecraft.mouseHandler.ypos() * height / window.getScreenHeight();
            confirm(x, y);
            return true;
        }
        if (!modifier.isActive(null)) {
            onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (InputConstants.Type.MOUSE.getOrCreate(button).equals(binding)) {
            confirm(x, y);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        onClose();
        return true;
    }

    private void confirm(double x, double y) {
        int hovered = optionAt(x, y);
        if (canSelect() && modifier.isActive(null) && hovered >= 0) {
            String selected = options.get(hovered).id();
            if (!selected.equals(previous)) {
                PacketDistributor.sendToServer(new SelectItemVariant(
                        slot, BuiltInRegistries.ITEM.getKey(held.getItem()), previous, selected));
            }
        }
        if (minecraft.isWindowActive()) {
            onClose();
        } else {
            cancelled = true;
        }
    }

    private int optionAt(double x, double y) {
        double dx = x - width / 2.0;
        double dy = y - height / 2.0;
        if (dx * dx + dy * dy <= DEAD_ZONE * DEAD_ZONE) {
            return -1;
        }
        double sector = FULL_TURN / options.size();
        double offset = Math.atan2(dy, dx) + Math.PI + sector / 2;
        return (int) (offset / sector) % options.size();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float centerX = width / 2.0F;
        float centerY = height / 2.0F;
        float radius = Math.min(96, Math.min(width, height) * 0.46F);
        double sector = FULL_TURN / options.size();
        int hovered = optionAt(mouseX, mouseY);

        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        for (int i = 0; i < options.size(); i++) {
            double angle = -Math.PI + i * sector;
            double start = angle - sector / 2 + 0.012;
            double end = angle + sector / 2 - 0.012;
            boolean selected = options.get(i).id().equals(previous);
            band(buffer, matrix, centerX, centerY, DEAD_ZONE, radius - 2, start, end,
                    i == hovered ? 0xEA53534A : 0xDF26292C);
            band(buffer, matrix, centerX, centerY, radius - 2, radius, start, end,
                    selected ? SELECTED : 0xFF65696B);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();

        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            double angle = -Math.PI + i * sector;
            int x = Math.round(centerX + (float) Math.cos(angle) * radius * 0.58F);
            int y = Math.round(centerY + (float) Math.sin(angle) * radius * 0.58F);
            var poses = graphics.pose();
            poses.pushPose();
            poses.translate(x, y - 6, 0);
            poses.scale(2.5F, 2.5F, 1);
            graphics.renderItem(option.preview(), -8, -8);
            poses.popPose();
            graphics.drawCenteredString(font, option.name(), x, y + 13,
                    option.id().equals(previous) ? SELECTED : 0xFFF1F1EC);
        }
    }

    private static void band(BufferBuilder buffer, Matrix4f matrix, float x, float y,
                             float inner, float outer, double start, double end, int color) {
        int segments = (int) Math.ceil((end - start) * 12);
        for (int i = 0; i < segments; i++) {
            double a = start + (end - start) * i / segments;
            double b = start + (end - start) * (i + 1) / segments;
            float ax = (float) Math.cos(a);
            float ay = (float) Math.sin(a);
            float bx = (float) Math.cos(b);
            float by = (float) Math.sin(b);
            buffer.addVertex(matrix, x + inner * ax, y + inner * ay, 0).setColor(color);
            buffer.addVertex(matrix, x + inner * bx, y + inner * by, 0).setColor(color);
            buffer.addVertex(matrix, x + outer * bx, y + outer * by, 0).setColor(color);
            buffer.addVertex(matrix, x + inner * ax, y + inner * ay, 0).setColor(color);
            buffer.addVertex(matrix, x + outer * bx, y + outer * by, 0).setColor(color);
            buffer.addVertex(matrix, x + outer * ax, y + outer * ay, 0).setColor(color);
        }
    }

    private record Option(String id, Component name, ItemStack preview) {}
}
