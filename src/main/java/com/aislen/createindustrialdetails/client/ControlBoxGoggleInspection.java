package com.aislen.createindustrialdetails.client;

import com.aislen.createindustrialdetails.content.block.cable.ControlBoxBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public final class ControlBoxGoggleInspection {
    private ControlBoxGoggleInspection() {
    }

    public static void addInformation(ControlBoxBlockEntity box, List<Component> tooltip) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof BlockHitResult hit && box.addToGoggleTooltip(tooltip, mc.player, hit)) {
            Component line = tooltip.removeLast();
            CreateLang.builder().add(line.copy()).forGoggles(tooltip);
        }
    }
}
