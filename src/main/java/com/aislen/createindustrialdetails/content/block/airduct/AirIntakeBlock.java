package com.aislen.createindustrialdetails.content.block.airduct;

import com.aislen.createindustrialdetails.registry.ModParticles;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.SimpleParticleType;

public class AirIntakeBlock extends AirVentBlock {
    public static final MapCodec<AirIntakeBlock> CODEC = simpleCodec(AirIntakeBlock::new);

    public AirIntakeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<AirIntakeBlock> codec() {
        return CODEC;
    }

    @Override
    protected SimpleParticleType airflowParticle() {
        return ModParticles.INTAKE_AIRFLOW.get();
    }
}
