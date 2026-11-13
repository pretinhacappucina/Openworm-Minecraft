package com.openwormmc;

import static org.junit.Assert.*;

import com.openwormmc.brain.Brain;
import com.openwormmc.connectome.Connectome;
import com.openwormmc.neural.C1Network;
import com.openwormmc.physics.WormBody;
import com.openwormmc.sensors.SensorMap;
import com.openwormmc.sensors.SensorMap.Channel;
import java.util.*;
import org.junit.Test;

/** Tests of the Minecraft-independent scientific engine. No result is hard-coded: everything is computed. */
public class EngineTest {
    static final Connectome CX = new Connectome();

    @Test public void t01_modelLoads() { assertNotNull(CX); assertTrue(CX.chemical.length > 0); }
    @Test public void t02_neuronCountIs302AndMusclesAre95() {
        assertEquals(302, CX.neurons.length); assertEquals(95, CX.muscles.length);
        assertEquals(302, new HashSet<>(Arrays.asList(CX.neurons)).size());
    }
    @Test public void t03_connectionIdentifiersResolveAndAreDirected() {
        for (int[] e : CX.chemical) { assertTrue(e[0] >= 0 && e[0] < 302 && e[1] >= 0 && e[1] < 302); assertTrue(e[2] > 0); }
        for (int[] e : CX.neuromuscular) { assertTrue(e[1] >= 0 && e[1] < 95); }
        // a known published chemical synapse of the Cook data set (DA1 -> DD1) is present with weight 12
        boolean found = false; for (int[] e : CX.chemical) if (e[0] == CX.neuron("DA1") && e[1] == CX.neuron("DD1")) { found = true; assertEquals(12, e[2]); }
        assertTrue(found);
        // gap junction matrix is symmetric as published
        Map<Long,Integer> m = new HashMap<>(); for (int[] e : CX.gap) m.put((long) e[0] * 1000 + e[1], e[2]);
        for (int[] e : CX.gap) assertEquals(Integer.valueOf(e[2]), m.get((long) e[1] * 1000 + e[0]));
    }
    @Test public void t04_parametersLoaded() {
        C1Network net = new C1Network(CX);
        assertEquals(-45, C1Network.INIT_V, 0); assertEquals(0.09, C1Network.SYN_G_NS, 0);
        assertEquals(CX.chemical.length + CX.neuromuscular.length, net.chemicalSynapseCount());
        assertEquals(CX.gap.length, net.gapJunctionCount());
        assertTrue(CX.isGaba(CX.neuron("DD1"))); assertFalse(CX.isGaba(CX.neuron("AVBL")));
    }
    @Test public void t05_t06_numericalStabilityNoNaN() {
        C1Network net = new C1Network(CX);
        for (double amp : new double[]{0, 100, 1000}) {
            net.reset(); for (String s : new String[]{"AVBL", "AVBR", "PVCL", "PVCR", "ALML"}) net.iInj[CX.neuron(s)] = amp;
            net.step(1000, 0.25); assertTrue("finite at " + amp, net.isFinite());
            // 1000 pA is deliberately unphysiological (V ~ I/g ~ 300 mV): only finiteness is required there
            if (amp <= 300) for (int i = 0; i < net.nC; i++) assertTrue(net.v[i] > -100 && net.v[i] < 100);
        }
    }
    @Test public void t06b_timestepConvergence() {
        double[] r = new double[2]; int k = 0;
        for (double dt : new double[]{0.5, 0.05}) {
            C1Network net = new C1Network(CX); net.iInj[CX.neuron("AVBL")] = 100; net.iInj[CX.neuron("PVCL")] = 100;
            net.step(500, dt); r[k++] = net.v[CX.neuron("DB1")];
        }
        assertEquals("solution must not depend on the step size", r[0], r[1], 0.1);
    }
    @Test public void t07_t08_stimulusChangesActivity() {
        C1Network a = new C1Network(CX), b = new C1Network(CX);
        b.iInj[CX.neuron("AVBL")] = 100; b.iInj[CX.neuron("PVCL")] = 100;
        a.step(500, 0.25); b.step(500, 0.25);
        assertTrue(b.v[CX.neuron("AVBL")] > a.v[CX.neuron("AVBL")] + 20);       // direct effect
        int changed = 0; for (int i = 0; i < 302; i++) if (Math.abs(a.v[i] - b.v[i]) > 1.0) changed++;
        assertTrue("downstream neurons must change, got " + changed, changed > 50); // synaptic propagation
    }
    @Test public void t09_t10_motorNeuronsAndMusclesRespond() {
        C1Network a = new C1Network(CX), b = new C1Network(CX);
        for (String s : new String[]{"AVBL", "AVBR", "PVCL", "PVCR"}) b.iInj[CX.neuron(s)] = 100;
        a.step(1000, 0.25); b.step(1000, 0.25);
        double dm = 0; for (int i = 0; i < 302; i++) if (CX.neurons[i].matches("^(DB|VB|DA|VA)\\d+")) dm += b.v[i] - a.v[i];
        assertTrue("motor neurons depolarize", dm > 0);
        double ma = 0, mb = 0; for (int i = 0; i < 95; i++) { ma += a.muscleActivation(i); mb += b.muscleActivation(i); }
        assertTrue("muscle activation rises: " + ma + " -> " + mb, mb > ma + 1.0);
    }
    @Test public void t11_physicsMovesBodyAndReversesWithWaveDirection() {
        double[] disp = new double[2]; int k = 0;
        for (int dir : new int[]{1, -1}) {
            WormBody w = new WormBody(12); double[] th = new double[11];
            for (int s = 0; s < 4000; s++) { for (int j = 0; j < 11; j++) th[j] = 0.4 * Math.sin(2 * Math.PI * (s / 1000.0 - dir * j / 11.0)); w.stepPrescribed(th, 1.0); }
            disp[k++] = w.x;
        }
        assertTrue(Math.abs(disp[0]) > 0.1 && Math.abs(disp[1]) > 0.1);
        assertTrue("opposite waves move in opposite directions", disp[0] * disp[1] < 0);
        WormBody still = new WormBody(12); for (int s = 0; s < 100; s++) still.stepPrescribed(new double[11], 1.0);
        assertEquals(0, still.x, 1e-9);
    }
    @Test public void t11b_brainDrivenBodyBendsAndTurnsFromNeuralOutputOnly() {
        Brain idle = new Brain(), touched = new Brain();
        touched.sensors.set(Channel.HEAD_TOUCH, 1);
        double maxIdle = 0, maxTouched = 0;
        for (int i = 0; i < 800; i++) {
            idle.tick(); touched.tick();
            for (double t : idle.body.theta) maxIdle = Math.max(maxIdle, Math.abs(t));
            for (double t : touched.body.theta) maxTouched = Math.max(maxTouched, Math.abs(t));
        }
        assertEquals(0, maxIdle, 1e-9);
        assertTrue("touch must curve the body via brain->muscle path: " + maxTouched, maxTouched > 0.05);
        assertNotEquals(0.0, touched.body.heading, 1e-6);
    }
    @Test public void t12_shutdownClosesBrain() {
        Brain b = new Brain(); b.tick(); b.close(); assertTrue(b.isClosed());
        try { b.tick(); fail(); } catch (IllegalStateException expected) { }
    }
    @Test public void t13_determinism() {
        Brain a = new Brain(), b = new Brain(); a.sensors.set(Channel.TAIL_TOUCH, 1); b.sensors.set(Channel.TAIL_TOUCH, 1);
        for (int i = 0; i < 200; i++) { a.tick(); b.tick(); }
        assertArrayEquals(a.snapshot().v, b.snapshot().v, 0.0);
    }
    @Test public void t14_sensorNeuronsExistInModel() {
        SensorMap sm = new SensorMap(CX);
        for (Channel c : Channel.values()) assertEquals(c.neurons.length, sm.neuronsOf(c).length);
    }

    /** Integration: worm + bacterial colony nearby -> odour -> sensory neurons -> network -> muscles -> motion. */
    @Test public void t15_integrationFoodScenario() {
        Brain worm = new Brain(), control = new Brain();
        double colonyDistance = 3.0, range = 8.0;                         // blocks
        double odor = SensorMap.odor(colonyDistance, range);
        assertTrue(odor > 0 && odor <= 1);
        worm.sensors.set(Channel.ODOR_LEFT, odor); worm.sensors.set(Channel.ODOR_RIGHT, odor * 0.5);
        int[] left = worm.sensors.neuronsOf(Channel.ODOR_LEFT);
        double before = worm.net.v[left[0]];
        double maxResp = 0, muscleSum = 0, bend = 0;
        for (int i = 0; i < 600; i++) { worm.tick(); control.tick();
            maxResp = Math.max(maxResp, worm.net.v[left[0]] - before);
            for (int m = 0; m < 95; m++) muscleSum += worm.net.muscleActivation(m);
            for (double t : worm.body.theta) bend = Math.max(bend, Math.abs(t)); }
        assertTrue("sensory neurons respond to odour", maxResp > 5);
        assertTrue("muscles recruited", muscleSum > 1.0);
        assertTrue("body deformed by motor output", bend > 0.02);
        assertEquals("control without food stays silent", 0, control.snapshot().active);
        assertTrue("worm has active neurons", worm.snapshot().active > 10);
    }
}
