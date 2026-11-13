package com.openwormmc.entity;

import com.openwormmc.brain.Brain;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One shared worker thread runs all brains (never one thread per neuron or per worm), off the Minecraft main thread.
 * The server is authoritative; clients only receive snapshots. Configurable cap on active brains.
 */
public final class SimulationService {
    public static final SimulationService INSTANCE = new SimulationService();
    public volatile int maxActiveBrains = 16;
    public volatile boolean paused, debug;
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "openworm-sim"); t.setDaemon(true); return t; });
    private final Map<Integer, Brain> brains = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicBoolean> busy = new ConcurrentHashMap<>();
    private final long startNanos = System.nanoTime();
    private volatile double measuredHz;

    public boolean register(int id, Brain b) { if (brains.size() >= maxActiveBrains) return false; brains.put(id, b); busy.put(id, new AtomicBoolean()); return true; }
    public void unregister(int id) { Brain b = brains.remove(id); busy.remove(id); if (b != null) b.close(); }
    public Brain get(int id) { return brains.get(id); }
    public int count() { return brains.size(); }
    public double updateHz() { return measuredHz; }
    public double uptimeSeconds() { return (System.nanoTime() - startNanos) / 1e9; }

    /** Called once per server tick per worm; schedules at most one pending simulation step per brain. */
    public void requestTick(int id) {
        Brain b = brains.get(id); AtomicBoolean flag = busy.get(id);
        if (b == null || paused || flag == null || !flag.compareAndSet(false, true)) return;
        pool.execute(() -> {
            long t0 = System.nanoTime();
            try { if (!b.isClosed()) b.tick(); } finally { flag.set(false); }
            double dt = (System.nanoTime() - t0) / 1e9; if (dt > 0) measuredHz = 0.9 * measuredHz + 0.1 / dt;
        });
    }
    public void shutdown() { brains.values().forEach(Brain::close); brains.clear(); pool.shutdownNow(); }
}
