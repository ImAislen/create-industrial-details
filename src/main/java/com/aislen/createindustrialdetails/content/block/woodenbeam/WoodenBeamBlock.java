package com.aislen.createindustrialdetails.content.block.woodenbeam;

import com.aislen.createindustrialdetails.registry.ModBlockEntities;
import com.aislen.createindustrialdetails.content.block.woodenbeam.structure.WoodenBeamStructureShapes;
import com.cake.struts.content.StrutModelType;
import com.cake.struts.content.block.StrutBlock;
import com.cake.struts.content.block.StrutBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WoodenBeamBlock extends StrutBlock {

    private final int fireSpreadSpeed;
    private final int flammability;

    public WoodenBeamBlock(
            Properties properties,
            StrutModelType modelType,
            int fireSpreadSpeed,
            int flammability
    ) {
        super(properties, modelType);
        this.fireSpreadSpeed = fireSpreadSpeed;
        this.flammability = flammability;
    }

    @Override
    protected BlockEntityType<? extends StrutBlockEntity>
    getStrutBlockEntityType() {
        return ModBlockEntities.WOODEN_BEAM.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WoodenBeamBlockEntity(pos, state);
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        VoxelShape attachment = getAttachmentBaseShape(state.getValue(FACING), false);
        if (!(level instanceof Level world)) {
            return attachment;
        }
        VoxelShape beam = WoodenBeamStructureShapes.getShape(world, pos);
        return beam.isEmpty() ? attachment : Shapes.or(attachment, beam);
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return fireSpreadSpeed;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return flammability;
    }
}
