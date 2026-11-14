package com.aislen.createindustrialdetails.content.block.industrialsteel.trench;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

final class ServiceTrenchFluidInspection {
    private ServiceTrenchFluidInspection() {}

    static boolean addToTooltip(SmartBlockEntity pipe, List<Component> tooltip) {
        List<Component> information = new ArrayList<>();
        information.add(Component.translatable("block.create_industrial_details.service_trench"));
        information.add(Component.translatable("create_industrial_details.goggles.encased_pipe"));
        var transport = pipe.getBehaviour(FluidTransportBehaviour.TYPE);
        boolean found = false;
        if (transport != null) {
            for (Direction direction : Direction.values()) {
                var flow = transport.getFlow(direction);
                if (flow == null || flow.fluid.isEmpty()) continue;
                Component line = Component.translatable("create_industrial_details.goggles.fluid", flow.fluid.getHoverName());
                if (!information.contains(line)) information.add(line);
                found = true;
            }
        }
        if (!found) information.add(Component.translatable("create_industrial_details.goggles.fluid",
                Component.translatable("create_industrial_details.goggles.none")));
        for (Component line : information)
            CreateLang.builder().add(line.copy()).forGoggles(tooltip);
        return true;
    }
}
