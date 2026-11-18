package com.aislen.createindustrialdetails.content.block.cable;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID)
public final class CableNetworkInspection {
    private CableNetworkInspection() {}

    public static Component describe(CableNetworkManager.NetworkState state) {
        var text = Component.translatable("create_industrial_details.cable_network.title");
        if (!state.connected()) return text.append("\n").append(Component.translatable("create_industrial_details.cable_network.pending"));
        text.append(" #" + state.id());
        for (ControlChannel channel : ControlChannel.values())
            text.append("\n").append(Component.translatable("create_industrial_details.control_box.value",
                    Component.translatable(channel.translationKey()), state.signal(channel)));
        if (state.signals() == 0) text.append("\n").append(Component.translatable("create_industrial_details.cable_network.zero"));
        return text;
    }

    public static Component describeChannel(com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableColor color, CableNetworkManager.ChannelState info) {
        return Component.literal(color.name() + " #" + info.network().id() + ": " + info.network().signal(color.channel)
                + " source=" + info.hasSource() + " tray=" + info.hasTray() + " control_box_port=" + info.hasControlBox());
    }
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("cid").then(Commands.literal("cable_signals")
                .requires(source -> source.hasPermission(2)).executes(context -> {
                    var source = context.getSource();
                    var player = source.getPlayerOrException();
                    var end = player.getEyePosition().add(player.getViewVector(1).scale(player.blockInteractionRange()));
                    var blockHit = player.pick(player.blockInteractionRange(), 1, false);
                    if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) end = blockHit.getLocation();
                    var span = com.aislen.createindustrialdetails.content.block.cable.individual.CableSpans.get(source.getLevel()).spatial().trace(player.getEyePosition(), end);
                    if (span != null) {
                        var value = CableNetworks.get(source.getLevel()).inspect(span.span());
                        source.sendSuccess(() -> describeChannel(span.span().color(), value), false); return 1;
                    }
                    if (!(blockHit instanceof BlockHitResult hit)) { source.sendFailure(Component.translatable("create_industrial_details.cable_network.target")); return 0; }
                    if (source.getLevel().getBlockState(hit.getBlockPos()).getBlock() instanceof com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableBlock) {
                        var component = com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableComponents.target(source.getLevel(), hit.getBlockPos(), source.getLevel().getBlockState(hit.getBlockPos()), player.getEyePosition(), end.add(end.subtract(player.getEyePosition()).normalize().scale(.01)));
                        if (component == null) return 0;
                        var info = CableNetworks.get(source.getLevel()).inspect(hit.getBlockPos(), component.color());
                        source.sendSuccess(() -> describeChannel(component.color(), info), false); return 1;
                    }
                    if (!(source.getLevel().getBlockState(hit.getBlockPos()).getBlock() instanceof CableTrayBlock)) { source.sendFailure(Component.translatable("create_industrial_details.cable_network.target")); return 0; }
                    var text = Component.translatable("create_industrial_details.cable_network.title");
                    for (var color : com.aislen.createindustrialdetails.content.block.cable.individual.IndividualCableColor.values())
                        text.append("\n").append(describeChannel(color, CableNetworks.get(source.getLevel()).inspect(hit.getBlockPos(), color)));
                    source.sendSuccess(() -> text, false);
                    return 1;
                })));
    }
}
