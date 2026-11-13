package com.openwormmc.entity;

import com.openwormmc.brain.Brain;
import com.openwormmc.sensors.SensorMap;
import com.openwormmc.sensors.SensorMap.Channel;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * C. elegans. Server side: samples Minecraft stimuli -> SensorMap (documented approximations), lets the shared
 * SimulationService step the Brain, then moves the entity with the velocity SOLVED by the body physics
 * (resistive force theory) from muscle-driven curvature. There is no behaviour state machine.
 * AUXILIARY (not emergent): the Minecraft collision/odour sampling, and WORLD_SPEED_GAIN that maps body-lengths per
 * simulated second to blocks per tick (the simulation runs slower than real time; see README).
 * UNCOMPILED in the build environment.
 */
public class CElegansEntity extends PathAwareEntity {
    public static final double BODY_LENGTH_BLOCKS = 1.0, ODOR_RANGE = 10, WORLD_SPEED_GAIN = 8.0;
    /** joint angles of the 11 joints, quantised to 1/100 rad and sent to the client as a string (render only) */
    public static final TrackedData<String> SHAPE = DataTracker.registerData(CElegansEntity.class, TrackedDataHandlerRegistry.STRING);
    private Brain brain; private boolean registered;
    private double lastHeading; private long feedUntil;

    public CElegansEntity(EntityType<? extends PathAwareEntity> type, World world) { super(type, world); }
    public static DefaultAttributeContainer.Builder attributes() { return PathAwareEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 4.0).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1); }
    @Override protected void initDataTracker() { super.initDataTracker(); dataTracker.startTracking(SHAPE, ""); }
    public Brain brain() { return brain; }

    @Override public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        SimulationService svc = SimulationService.INSTANCE;
        if (!registered) { brain = new Brain(); registered = svc.register(getId(), brain); if (!registered) { discard(); return; } lastHeading = getYaw(); }
        sampleStimuli(brain.sensors);
        svc.requestTick(getId());
        var snap = brain.snapshot();
        // body solved velocity (BL/ms) -> blocks/tick ; direction = body heading relative to spawn yaw
        double speedBlocksPerTick = brain.body.forwardSpeed() * brain.simMsPerTick * BODY_LENGTH_BLOCKS * WORLD_SPEED_GAIN;
        float yaw = (float) (lastHeading - Math.toDegrees(snap.heading));
        setYaw(yaw); setBodyYaw(yaw); setHeadYaw(yaw);
        double rad = Math.toRadians(yaw);
        Vec3d v = new Vec3d(-Math.sin(rad) * speedBlocksPerTick, getVelocity().y, Math.cos(rad) * speedBlocksPerTick);
        setVelocity(v); velocityModified = true;
        StringBuilder sb = new StringBuilder(); for (double t : brain.body.theta) sb.append(Math.round(t * 100)).append(',');
        dataTracker.set(SHAPE, sb.toString());
    }

    /** AUXILIARY sensing: Minecraft world -> stimulus intensities (0..1). */
    private void sampleStimuli(SensorMap s) {
        s.set(Channel.HEAD_TOUCH, horizontalCollision ? 1 : 0);
        s.set(Channel.BODY_TOUCH, 0); s.set(Channel.TAIL_TOUCH, 0);
        double yawRad = Math.toRadians(getYaw()); Vec3d fwd = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad)), left = new Vec3d(fwd.z, 0, -fwd.x);
        BlockPos c = getBlockPos(); double bestL = 0, bestR = 0, bestD = 1e9; int r = (int) ODOR_RANGE;
        for (BlockPos p : BlockPos.iterateOutwards(c, r, 2, r)) {
            if (!getWorld().getBlockState(p).isOf(com.openwormmc.registry.ModRegistry.BACTERIAL_COLONY)) continue;
            Vec3d d = Vec3d.ofCenter(p).subtract(getPos()); double dist = d.length(); bestD = Math.min(bestD, dist);
            double conc = SensorMap.odor(dist, ODOR_RANGE); double side = d.dotProduct(left) / Math.max(dist, 1e-6);
            bestL += conc * (0.5 + 0.5 * side); bestR += conc * (0.5 - 0.5 * side);
        }
        s.set(Channel.ODOR_LEFT, Math.min(1, bestL)); s.set(Channel.ODOR_RIGHT, Math.min(1, bestR));
        s.set(Channel.FOOD_CONTACT, bestD < 1.2 ? 1 : 0);
        if (getWorld().getTime() < feedUntil) { s.set(Channel.ODOR_LEFT, 1); s.set(Channel.ODOR_RIGHT, 1); s.set(Channel.FOOD_CONTACT, 1); }
    }
    /** Debug/command hook: experimental food stimulus applied to this worm. */
    public void feedStimulus() { feedUntil = getWorld().getTime() + 100; }

    @Override public void remove(RemovalReason reason) { if (!getWorld().isClient && registered) SimulationService.INSTANCE.unregister(getId()); super.remove(reason); }
}
