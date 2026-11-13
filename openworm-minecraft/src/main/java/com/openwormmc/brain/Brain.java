package com.openwormmc.brain;

import com.openwormmc.connectome.Connectome;
import com.openwormmc.muscle.MuscleSystem;
import com.openwormmc.neural.C1Network;
import com.openwormmc.physics.WormBody;
import com.openwormmc.sensors.SensorMap;

/** One virtual worm: sensors -> C1 network -> muscles -> body. Independent of Minecraft. Single writer thread. */
public final class Brain implements AutoCloseable {
    public static final int SEGMENTS = 12;
    public static final double DT_MS = 0.25, ACTIVE_MV = -30;
    private static volatile Connectome shared;
    public static synchronized Connectome sharedConnectome() { if (shared == null) shared = new Connectome(); return shared; }

    public final Connectome cx; public final C1Network net; public final SensorMap sensors;
    public final MuscleSystem muscles; public final WormBody body;
    /** simulated milliseconds advanced per tick() call */
    public double simMsPerTick = 10;
    private volatile boolean closed;
    private long ticks;
    private volatile Snapshot snapshot;

    /** Immutable copy for UI/render threads. */
    public static final class Snapshot {
        public final double[] v, muscleDrive, dorsal, ventral, stimulus; public final int active; public final double timeMs;
        public final double x, y, heading, speed; public final long ticks;
        Snapshot(Brain b) {
            v = java.util.Arrays.copyOf(b.net.v, b.net.nN); muscleDrive = b.muscles.drive.clone();
            dorsal = b.muscles.dorsalAct.clone(); ventral = b.muscles.ventralAct.clone(); stimulus = b.sensors.intensity.clone();
            int a = 0; for (double x : v) if (x > ACTIVE_MV) a++; active = a; timeMs = b.net.timeMs;
            x = b.body.x; y = b.body.y; heading = b.body.heading; speed = b.body.forwardSpeed(); ticks = b.ticks;
        }
    }

    public Brain() { this(sharedConnectome()); }
    public Brain(Connectome cx) {
        this.cx = cx; net = new C1Network(cx); sensors = new SensorMap(cx); muscles = new MuscleSystem(cx, SEGMENTS); body = new WormBody(SEGMENTS);
        net.step(200, DT_MS); // settle to the model's resting state (deterministic)
        publish();
    }

    /** Advance the whole worm by simMsPerTick of simulated time. */
    public void tick() {
        if (closed) throw new IllegalStateException("brain closed");
        sensors.apply(net.iInj);
        net.step(simMsPerTick, DT_MS);
        muscles.update(net);
        body.step(muscles.drive, simMsPerTick);
        ticks++; publish();
    }
    private void publish() { snapshot = new Snapshot(this); }
    public Snapshot snapshot() { return snapshot; }
    public boolean isClosed() { return closed; }
    @Override public void close() { closed = true; }
}
