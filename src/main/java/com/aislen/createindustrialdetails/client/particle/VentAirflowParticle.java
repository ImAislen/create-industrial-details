package com.aislen.createindustrialdetails.client.particle;

import com.aislen.createindustrialdetails.CreateIndustrialDetails;
import com.aislen.createindustrialdetails.registry.ModParticles;
import com.simibubi.create.content.kinetics.fan.AirFlowParticle;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = CreateIndustrialDetails.MOD_ID, value = Dist.CLIENT)
public class VentAirflowParticle extends AirFlowParticle {
    private final Direction direction;
    private final double originX;
    private final double originY;
    private final double originZ;
    private final float maxDistance;

    private VentAirflowParticle(ClientLevel level, double x, double y, double z,
                               Direction direction, SpriteSet sprites) {
        // Inherit Create's appearance and spread; our tick needs no fan block entity or air current.
        super(level, null, x, y, z, sprites);
        this.direction = direction;
        originX = x;
        originY = y;
        originZ = z;
        var kinetics = AllConfigs.server().kinetics;
        float factor = Math.min(16.0f / kinetics.fanRotationArgmax.get(), 1);
        maxDistance = Mth.lerp(factor, 3, kinetics.fanPushDistance.get());
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        double distance = (x - originX) * direction.getStepX()
                + (y - originY) * direction.getStepY()
                + (z - originZ) * direction.getStepZ();
        if (age++ >= lifetime || distance < -.25 || distance > maxDistance + .25) {
            remove();
            return;
        }
        setSprite(sprites.get((int) Mth.clamp(distance / maxDistance * 8 + random.nextInt(4), 0, 7), 8));
        double speed = (maxDistance - distance + 1) / 16;
        xd = direction.getStepX() * speed;
        yd = direction.getStepY() * speed;
        zd = direction.getStepZ() * speed;
        move(xd, yd, zd);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.VENT_AIRFLOW.get(), Factory::new);
    }

    private record Factory(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new VentAirflowParticle(level, x, y, z,
                    Direction.getNearest(xSpeed, ySpeed, zSpeed), sprites);
        }
    }
}
