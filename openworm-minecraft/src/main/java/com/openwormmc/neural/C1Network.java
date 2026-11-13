package com.openwormmc.neural;

import com.openwormmc.connectome.Connectome;

/**
 * c302 "Level C1" network: single-compartment conductance-based cells (leak, k_slow, k_fast, ca_boyle with
 * Ca pool and customHGate), graded (analogue) chemical synapses and linear gap junctions.
 * Parameters and equations follow c302/parameters_C.py, parameters_C1.py, cell_C.xml and the NeuroML2
 * gradedSynapse / gapJunction / fixedFactorConcentrationModel definitions.
 *
 * Units: mV, ms, nS, pA, pF, mM. (nS*mV = pA, pA/pF = mV/ms.)
 * Deviations from c302 (documented in README): fixed-step integrator (implicit-in-V, exponential gates/synapses)
 * instead of LEMS/jNeuroML; muscle-muscle gap junctions not included; GABA annotation from literature list.
 * Deterministic: no random numbers are used anywhere.
 */
public final class C1Network {
    // ---- c302 bioparameters (Level C / C1 defaults) ----
    public static final double INIT_V = -45, LEAK_E = -50, KS_E = -60, KF_E = -60, CA_E = 40;
    public static final double CAP_UF_CM2 = 1.0;
    public static final double CA_DECAY_MS = 11.5943, CA_RHO = 0.000238919;
    public static final double H_ALPHA = 0.282473, H_CA_HALF = 6.41889e-8, H_K = -1.00056e-8;
    // synapses (C1)
    public static final double SYN_G_NS = 0.09, SYN_DELTA = 5, SYN_VTH = 0, SYN_K_PER_MS = 0.025;
    public static final double EXC_E = 0, INH_E = -70;
    public static final double GAP_G_NS = 0.00052;
    // geometry (um): neuron = sphere d=5; muscle = cylinder d=5, L=20 (NeuroML surface area)
    static final double NEURON_AREA_UM2 = Math.PI * 25.0, MUSCLE_AREA_UM2 = Math.PI * 5.0 * 20.0;

    public final Connectome cx;
    public final int nN, nM, nC;
    // cell state
    public final double[] v, p, q, n, e, f, ca, iInj;
    final double[] cap, gLeak, gKs, gKf, gCa, area;
    // synapse state
    final int[] cPre, cPost; final double[] cW, cS, cE; final int nChem;
    final int[] gPre, gPost; final double[] gW; final int nGap;
    final double[] num, den;
    public double timeMs = 0;

    public C1Network(Connectome cx) {
        this.cx = cx; nN = cx.neurons.length; nM = cx.muscles.length; nC = nN + nM;
        v = new double[nC]; p = new double[nC]; q = new double[nC]; n = new double[nC]; e = new double[nC]; f = new double[nC];
        ca = new double[nC]; iInj = new double[nC]; cap = new double[nC]; area = new double[nC];
        gLeak = new double[nC]; gKs = new double[nC]; gKf = new double[nC]; gCa = new double[nC];
        num = new double[nC]; den = new double[nC];
        for (int i = 0; i < nC; i++) {
            boolean mus = i >= nN;
            double a = mus ? MUSCLE_AREA_UM2 : NEURON_AREA_UM2;
            area[i] = a;
            double cm2 = a * 1e-8; // um^2 -> cm^2
            cap[i] = CAP_UF_CM2 * cm2 * 1e6;                  // uF -> pF
            // densities in mS/cm^2 -> nS : * cm2 * 1e6
            gLeak[i] = (mus ? 5e-4 : 0.005) * cm2 * 1e6;
            gKs[i]   = (mus ? 0.6 : 3.0) * cm2 * 1e6;
            gKf[i]   = (mus ? 0.1 : 0.0711643917483308) * cm2 * 1e6;
            gCa[i]   = (mus ? 0.7 : 3.0) * cm2 * 1e6;
        }
        nChem = cx.chemical.length + cx.neuromuscular.length;
        cPre = new int[nChem]; cPost = new int[nChem]; cW = new double[nChem]; cS = new double[nChem]; cE = new double[nChem];
        int k = 0;
        for (int[] ed : cx.chemical) { cPre[k] = ed[0]; cPost[k] = ed[1]; cW[k] = ed[2]; cE[k] = cx.isGaba(ed[0]) ? INH_E : EXC_E; k++; }
        for (int[] ed : cx.neuromuscular) { cPre[k] = ed[0]; cPost[k] = nN + ed[1]; cW[k] = ed[2]; cE[k] = cx.isGaba(ed[0]) ? INH_E : EXC_E; k++; }
        nGap = cx.gap.length; gPre = new int[nGap]; gPost = new int[nGap]; gW = new double[nGap];
        for (int i = 0; i < nGap; i++) { gPre[i] = cx.gap[i][0]; gPost[i] = cx.gap[i][1]; gW[i] = cx.gap[i][2]; }
        reset();
    }

    /** Return to the initial condition (V=-45 mV, gates at their steady state, no Ca, s=0). */
    public void reset() {
        timeMs = 0;
        for (int i = 0; i < nC; i++) {
            v[i] = INIT_V; ca[i] = 0; iInj[i] = 0;
            p[i] = pInf(INIT_V); q[i] = qInf(INIT_V); n[i] = nInf(INIT_V); e[i] = eInf(INIT_V); f[i] = fInf(INIT_V);
        }
        java.util.Arrays.fill(cS, 0);
    }

    static double sig(double x) { return 1.0 / (1.0 + Math.exp(-x)); }
    static double pInf(double V) { return sig((V + 8.05232) / 7.42636); }
    static double qInf(double V) { return sig((V + 15.6456) / -9.97468); }
    static double nInf(double V) { return sig((V - 19.8741) / 15.8512); }
    static double eInf(double V) { return sig((V + 3.3568) / 6.74821); }
    static double fInf(double V) { return sig((V - 25.1815) / -5.03176); }
    static final double TAU_P = 2.25518, TAU_Q = 149.963, TAU_N = 25.0007, TAU_E = 0.100027, TAU_F = 150.88;

    /** Advance by dt milliseconds. */
    public void step(double dt) {
        java.util.Arrays.fill(num, 0); java.util.Arrays.fill(den, 0);
        final double ap = 1 - Math.exp(-dt / TAU_P), aq = 1 - Math.exp(-dt / TAU_Q), an = 1 - Math.exp(-dt / TAU_N),
                     ae = 1 - Math.exp(-dt / TAU_E), af = 1 - Math.exp(-dt / TAU_F);
        // chemical (graded) synapses: ds/dt = (inf-s)/tau, tau=(1-inf)/k ; s=inf when (1-inf)<=1e-4
        for (int i = 0; i < nChem; i++) {
            double inf = 1.0 / (1.0 + Math.exp((SYN_VTH - v[cPre[i]]) / SYN_DELTA));
            double s;
            if (1 - inf > 1e-4) { double tau = (1 - inf) / SYN_K_PER_MS; s = cS[i] + (inf - cS[i]) * (1 - Math.exp(-dt / tau)); }
            else s = inf;
            cS[i] = s;
            double g = cW[i] * SYN_G_NS * s;
            num[cPost[i]] += g * cE[i]; den[cPost[i]] += g;
        }
        for (int i = 0; i < nGap; i++) { double g = gW[i] * GAP_G_NS; num[gPost[i]] += g * v[gPre[i]]; den[gPost[i]] += g; }
        for (int i = 0; i < nC; i++) {
            double V = v[i];
            // gates (exact exponential update toward steady state at current V)
            p[i] += (pInf(V) - p[i]) * ap; q[i] += (qInf(V) - q[i]) * aq; n[i] += (nInf(V) - n[i]) * an;
            e[i] += (eInf(V) - e[i]) * ae; f[i] += (fInf(V) - f[i]) * af;
            double hInf = 1.0 / (1.0 + Math.exp((H_CA_HALF - ca[i]) / H_K));
            double fcond = 1 + (hInf - 1) * H_ALPHA;
            double p2 = p[i] * p[i];
            double gkf = gKf[i] * p2 * p2 * q[i], gks = gKs[i] * n[i], gca = gCa[i] * e[i] * e[i] * f[i] * fcond;
            double gsum = gLeak[i] + gkf + gks + gca;
            double gE = gLeak[i] * LEAK_E + gkf * KF_E + gks * KS_E + gca * CA_E;
            double iCa = gca * (CA_E - V);                                   // pA, inward positive
            double dCa = 1e-3 * (iCa / area[i]) * CA_RHO - ca[i] / CA_DECAY_MS; // mM/ms
            ca[i] = Math.max(0, ca[i] + dt * dCa);
            double k = dt / cap[i];
            v[i] = (V + k * (gE + num[i] + iInj[i])) / (1 + k * (gsum + den[i]));
        }
        timeMs += dt;
    }

    public void step(double totalMs, double dt) {
        int steps = (int) Math.max(1, Math.round(totalMs / dt));
        for (int i = 0; i < steps; i++) step(dt);
    }
    public boolean isFinite() {
        for (int i = 0; i < nC; i++) if (!Double.isFinite(v[i]) || !Double.isFinite(ca[i]) || !Double.isFinite(p[i])) return false;
        return true;
    }
    /** Calcium-based muscle activation in [0,1): Hill(2) around the h-gate half-activation Ca of Boyle & Cohen 2008. */
    public double muscleActivation(int muscle) { double c = ca[nN + muscle], c2 = c * c, k2 = H_CA_HALF * H_CA_HALF; return c2 / (c2 + k2); }
    public int chemicalSynapseCount() { return nChem; }
    public int gapJunctionCount() { return nGap; }
}
