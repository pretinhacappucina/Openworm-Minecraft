package com.openwormmc.sensors;

import com.openwormmc.connectome.Connectome;

/**
 * Minecraft stimulus -> sensory neuron current injection (APPROXIMATION, documented in README).
 * Neuron choices follow WormAtlas / literature on C. elegans sensory biology and all must exist in the model:
 *  HEAD_TOUCH  : ALML, ALMR, AVM (anterior gentle touch), FLPL, FLPR (nose/harsh touch)
 *  TAIL_TOUCH  : PLML, PLMR (posterior gentle touch)
 *  BODY_TOUCH  : PVDL, PVDR (harsh body touch / proprioception-like)
 *  ODOR_LEFT/RIGHT : AWAL/AWCL and AWAR/AWCR (attractive volatile chemosensation)
 *  FOOD_CONTACT: NSML, NSMR (food-sensing serotonergic neurons)
 * Current is intensity (0..1) * MAX_PA. Current injection is the same stimulation method c302 examples use.
 */
public final class SensorMap {
    public enum Channel {
        HEAD_TOUCH("ALML", "ALMR", "AVM", "FLPL", "FLPR"), TAIL_TOUCH("PLML", "PLMR"), BODY_TOUCH("PVDL", "PVDR"),
        ODOR_LEFT("AWAL", "AWCL"), ODOR_RIGHT("AWAR", "AWCR"), FOOD_CONTACT("NSML", "NSMR");
        public final String[] neurons;
        Channel(String... n) { neurons = n; }
    }
    public static final double MAX_PA = 100.0;
    private final int[][] idx = new int[Channel.values().length][];
    public final double[] intensity = new double[Channel.values().length];

    public SensorMap(Connectome cx) {
        for (Channel c : Channel.values()) {
            int[] a = new int[c.neurons.length];
            for (int i = 0; i < a.length; i++) a[i] = cx.neuron(c.neurons[i]); // throws if a neuron is absent from the data
            idx[c.ordinal()] = a;
        }
    }
    public int[] neuronsOf(Channel c) { return idx[c.ordinal()]; }
    public void set(Channel c, double v) { intensity[c.ordinal()] = Math.max(0, Math.min(1, v)); }
    /** Write currents (pA) into the injection array of the network. */
    public void apply(double[] iInj) {
        for (Channel c : Channel.values()) for (int n : idx[c.ordinal()]) iInj[n] = 0;
        for (Channel c : Channel.values()) for (int n : idx[c.ordinal()]) iInj[n] += intensity[c.ordinal()] * MAX_PA;
    }
    /** Diffusion-like odour concentration (0..1) vs distance in blocks; gradient arises from comparing two sample points. */
    public static double odor(double distanceBlocks, double rangeBlocks) {
        return distanceBlocks >= rangeBlocks ? 0 : Math.pow(1 - distanceBlocks / rangeBlocks, 2);
    }
}
