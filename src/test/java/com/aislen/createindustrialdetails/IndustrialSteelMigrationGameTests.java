package com.aislen.createindustrialdetails;

import com.aislen.createindustrialdetails.content.block.industrialsteel.lighting.cagedlamp.IndustrialSteelCagedLampBlockEntity;
import com.aislen.createindustrialdetails.content.block.industrialsteel.lighting.cagedlamp.IndustrialSteelCagedLampFrequencies;
import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.aislen.createindustrialdetails.registry.ModBlocks;
import com.aislen.createindustrialdetails.registry.ModDataComponents;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.bracket.BracketBlock;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(CreateIndustrialDetails.MOD_ID)
@PrefixGameTestTemplate(false)
public class IndustrialSteelMigrationGameTests {
    private static final List<String> ITEMS = List.of("panel", "grate", "hatch", "beam", "caged_lamp");

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyRegistriesAndBlockStates(GameTestHelper helper) {
        for (String suffix : List.of("panel", "grate", "hatch", "beam", "caged_lamp", "panel_shaft_penetration")) {
            assertAlias(helper, BuiltInRegistries.BLOCK, suffix);
            var block = BuiltInRegistries.BLOCK.get(canonical(suffix));
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                var legacyState = NbtUtils.writeBlockState(state);
                legacyState.putString("Name", legacy(suffix).toString());
                var loaded = NbtUtils.readBlockState(helper.getLevel().holderLookup(Registries.BLOCK), legacyState);
                helper.assertTrue(loaded == state, "Legacy block palette preserves every property: " + suffix + " " + state);
                var codecState = BlockState.CODEC.parse(NbtOps.INSTANCE, legacyState).getOrThrow();
                helper.assertTrue(codecState == state, "Native BlockState codec resolves the alias: " + suffix);
                helper.assertTrue(NbtUtils.writeBlockState(loaded).getString("Name").equals(canonical(suffix).toString()),
                        "Reloaded block states save using canonical IDs");
            }
        }
        for (String suffix : ITEMS) assertAlias(helper, BuiltInRegistries.ITEM, suffix);
        assertAlias(helper, BuiltInRegistries.BLOCK_ENTITY_TYPE, "caged_lamp");
        assertAlias(helper, BuiltInRegistries.MENU, "caged_lamp");
        assertAlias(helper, BuiltInRegistries.DATA_COMPONENT_TYPE, "caged_lamp_frequencies");
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(ModBlocks.BLOCK_OF_INDUSTRIAL_STEEL.get()).getPath().equals("block_of_industrial_steel")
                && BuiltInRegistries.BLOCK.getKey(ModBlocks.BLOCK_OF_PLATED_INDUSTRIAL_STEEL.get()).getPath().equals("block_of_plated_industrial_steel")
                && ModBlocks.BLOCK_OF_INDUSTRIAL_STEEL.get() != ModBlocks.BLOCK_OF_PLATED_INDUSTRIAL_STEEL.get(),
                "Established steel blocks remain distinct canonical registrations");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyItemsAndFrequencyComponents(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        for (String suffix : ITEMS) {
            ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(canonical(suffix)), 7);
            item.set(DataComponents.CUSTOM_NAME, Component.literal("Migration test"));
            var saved = (CompoundTag) item.saveOptional(registries);
            saved.putString("id", legacy(suffix).toString());
            var loaded = ItemStack.parseOptional(registries, saved);
            helper.assertTrue(ItemStack.isSameItemSameComponents(item, loaded) && loaded.getCount() == 7,
                    "Old inventory stacks retain item, count, and components: " + suffix);
            helper.assertTrue(((CompoundTag) loaded.saveOptional(registries)).getString("id").equals(canonical(suffix).toString()),
                    "Legacy inventory items serialize with canonical IDs");
        }
        ItemStack lamp = new ItemStack(ModBlocks.INDUSTRIAL_STEEL_CAGED_LAMP.get(), 3);
        var frequencies = new IndustrialSteelCagedLampFrequencies(new ItemStack(Items.REDSTONE), new ItemStack(Items.AMETHYST_SHARD));
        lamp.set(ModDataComponents.INDUSTRIAL_STEEL_CAGED_LAMP_FREQUENCIES.get(), frequencies);
        var legacyItem = (CompoundTag) lamp.saveOptional(registries);
        legacyItem.putString("id", legacy("caged_lamp").toString());
        var components = legacyItem.getCompound("components");
        var payload = components.get(canonical("caged_lamp_frequencies").toString());
        helper.assertTrue(payload != null, "Frequency component is present in serialized item data");
        components.remove(canonical("caged_lamp_frequencies").toString());
        components.put(legacy("caged_lamp_frequencies").toString(), payload);
        ItemStack loaded = ItemStack.parseOptional(registries, legacyItem);
        helper.assertTrue(ItemStack.isSameItemSameComponents(lamp, loaded) && loaded.getCount() == 3,
                "Old lamp ID and old frequency component ID decode together without losing payload");
        var written = ((CompoundTag) loaded.saveOptional(registries)).getCompound("components");
        helper.assertTrue(written.contains(canonical("caged_lamp_frequencies").toString())
                && !written.contains(legacy("caged_lamp_frequencies").toString()), "Frequency component is saved under its canonical ID");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyLampBlockEntity(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var state = ModBlocks.INDUSTRIAL_STEEL_CAGED_LAMP.get().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        var lamp = (IndustrialSteelCagedLampBlockEntity) level.getBlockEntity(pos);
        lamp.setFirstFrequency(new ItemStack(ModBlocks.INDUSTRIAL_STEEL_PANEL.get()));
        lamp.setSecondFrequency(new ItemStack(Items.REDSTONE));
        var saved = lamp.saveWithFullMetadata(level.registryAccess());
        saved.putString("id", legacy("caged_lamp").toString());
        saved.getCompound("FrequencyFirst").putString("id", legacy("panel").toString());
        var loaded = BlockEntity.loadStatic(pos, state, saved, level.registryAccess());
        helper.assertTrue(loaded instanceof IndustrialSteelCagedLampBlockEntity migrated
                && migrated.getType() == ModBlockEntities.INDUSTRIAL_STEEL_CAGED_LAMP.get()
                && migrated.getFirstFrequencyStack().is(ModBlocks.INDUSTRIAL_STEEL_PANEL.get().asItem())
                && migrated.getSecondFrequencyStack().is(Items.REDSTONE), "Old lamp block entity and nested old item IDs preserve both frequencies");
        helper.assertTrue(loaded.saveWithFullMetadata(level.registryAccess()).getString("id").equals(canonical("caged_lamp").toString()),
                "Migrated lamp block entity saves its canonical type ID");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void legacyCreatePanelBracket(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3));
        var shaftState = AllBlocks.SHAFT.getDefaultState();
        level.setBlockAndUpdate(pos, shaftState);
        var bracket = BlockEntityBehaviour.get(level, pos, BracketedBlockEntityBehaviour.TYPE);
        helper.assertTrue(bracket != null, "Real Create shaft exposes bracket storage");
        var panel = ModBlocks.INDUSTRIAL_STEEL_PANEL_SHAFT_PENETRATION.get().defaultBlockState()
                .setValue(BracketBlock.AXIS_ALONG_FIRST_COORDINATE, true);
        bracket.applyBracket(panel);
        var saved = level.getBlockEntity(pos).saveWithFullMetadata(level.registryAccess());
        saved.getCompound("Bracket").putString("Name", legacy("panel_shaft_penetration").toString());
        helper.assertTrue(saved.getCompound("Bracket").getCompound("Properties").contains("axis_along_first"),
                "Existing Create bracket property names remain unchanged");
        var loaded = BlockEntity.loadStatic(pos, shaftState, saved, level.registryAccess());
        var restored = BlockEntityBehaviour.get(loaded, BracketedBlockEntityBehaviour.TYPE);
        helper.assertTrue(restored != null && restored.getBracket() == panel,
                "Create shaft restores its legacy hidden panel state and orientation");
        helper.assertTrue(panel.getBlock().asItem() == ModBlocks.INDUSTRIAL_STEEL_PANEL.get().asItem(),
                "Hidden penetration still returns the normal panel item");
        helper.succeed();
    }

    private static void assertAlias(GameTestHelper helper, Registry<?> registry, String suffix) {
        var previous = legacy(suffix);
        var current = canonical(suffix);
        helper.assertTrue(registry.keySet().contains(current) && !registry.keySet().contains(previous),
                "Only the canonical entry is registered: " + registry.key() + " " + suffix);
        helper.assertTrue(registry.get(previous) == registry.get(current), "Legacy ID resolves to the same registry object: " + suffix);
    }

    private static ResourceLocation legacy(String suffix) {
        return ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "riveted_steel_" + suffix);
    }

    private static ResourceLocation canonical(String suffix) {
        return ResourceLocation.fromNamespaceAndPath(CreateIndustrialDetails.MOD_ID, "industrial_steel_" + suffix);
    }
}
