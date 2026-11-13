package com.openwormmc.muscle;

import com.openwormmc.connectome.Connectome;
import com.openwormmc.neural.C1Network;

/**
 * Converts body-wall muscle state (95 cells MDL/MDR/MVL/MVR01..24 from Cook 2019) into per-segment
 * dorsal-ventral contraction drive. Muscle cell electrophysiology is the c302 muscle model (Boyle & Cohen 2008
 * channels); the Ca->activation Hill map and the 12-segment grouping are APPROXIMATIONS made for Minecraft.
 */
public final class MuscleSystem {
    public final int segments;
    private final int[] segOf;           // muscle index -> segment (anterior=0)
    private final boolean[] dorsal;
    private final int[] cntD, cntV;
    public final double[] dorsalAct, ventralAct, drive; // drive in [-1,1], + = dorsal contraction

    public MuscleSystem(Connectome cx, int segments) {
        this.segments = segments;
        int m = cx.muscles.length;
        segOf = new int[m]; dorsal = new boolean[m]; cntD = new int[segments]; cntV = new int[segments];
        for (int i = 0; i < m; i++) {
            String s = cx.muscles[i];
            int num = Integer.parseInt(s.substring(3)); // MDL01 -> 1 (1 = head, 24 = tail)
            segOf[i] = Math.min(segments - 1, (num - 1) * segments / 24);
            dorsal[i] = s.startsWith("MD");
            if (dorsal[i]) cntD[segOf[i]]++; else cntV[segOf[i]]++;
        }
        dorsalAct = new double[segments]; ventralAct = new double[segments]; drive = new double[segments];
    }

    public void update(C1Network net) {
        java.util.Arrays.fill(dorsalAct, 0); java.util.Arrays.fill(ventralAct, 0);
        for (int i = 0; i < segOf.length; i++) {
            double a = net.muscleActivation(i);
            if (dorsal[i]) dorsalAct[segOf[i]] += a; else ventralAct[segOf[i]] += a;
        }
        for (int s = 0; s < segments; s++) {
            if (cntD[s] > 0) dorsalAct[s] /= cntD[s];
            if (cntV[s] > 0) ventralAct[s] /= cntV[s];
            drive[s] = dorsalAct[s] - ventralAct[s];
        }
    }
}
