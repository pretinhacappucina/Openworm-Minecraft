package com.openwormmc.connectome;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Hermaphrodite C. elegans connectome (Cook et al. 2019, as distributed in the cect package used by c302).
 * Pure Java, no Minecraft dependency. Data are read from the JAR resources; nothing is downloaded.
 */
public final class Connectome {
    public static final String BASE = "/data/openwormmc/connectome/";
    /** GABAergic neurons (inhibitory chemical synapses in c302: conn.synclass contains "GABA"). Annotation, not in Cook data. */
    public static final Set<String> GABA = Set.of(
        "DD1","DD2","DD3","DD4","DD5","DD6","VD1","VD2","VD3","VD4","VD5","VD6","VD7","VD8","VD9","VD10","VD11","VD12","VD13",
        "RMED","RMEV","RMEL","RMER","AVL","DVB","RIS");

    public final String[] neurons;
    public final String[] muscles;
    public final Map<String,Integer> neuronIndex = new HashMap<>();
    public final Map<String,Integer> muscleIndex = new HashMap<>();
    /** each edge: {pre, post, weight}. Chemical and gap are neuron->neuron; neuromuscular is neuron->muscle index. */
    public final int[][] chemical, gap, neuromuscular;

    public Connectome() { this(Connectome.class); }

    public Connectome(Class<?> loader) {
        neurons = names(loader, "neurons.tsv");
        muscles = names(loader, "muscles.tsv");
        for (int i = 0; i < neurons.length; i++) neuronIndex.put(neurons[i], i);
        for (int i = 0; i < muscles.length; i++) muscleIndex.put(muscles[i], i);
        chemical = edges(loader, "chemical.tsv", neuronIndex, neuronIndex);
        gap = edges(loader, "gap.tsv", neuronIndex, neuronIndex);
        neuromuscular = edges(loader, "neuromuscular.tsv", neuronIndex, muscleIndex);
    }

    public boolean isGaba(int neuron) { return GABA.contains(neurons[neuron]); }
    public int neuron(String name) {
        Integer i = neuronIndex.get(name);
        if (i == null) throw new IllegalArgumentException("Unknown neuron: " + name);
        return i;
    }

    private static List<String[]> rows(Class<?> loader, String file) {
        try (InputStream in = loader.getResourceAsStream(BASE + file)) {
            if (in == null) throw new IllegalStateException("Missing resource " + BASE + file);
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String[]> out = new ArrayList<>();
            for (String l; (l = br.readLine()) != null; ) {
                if (l.isEmpty() || l.startsWith("#")) continue;
                out.add(l.split("\t"));
            }
            return out;
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static String[] names(Class<?> loader, String file) {
        List<String[]> r = rows(loader, file);
        String[] n = new String[r.size()];
        for (String[] x : r) n[Integer.parseInt(x[0])] = x[1];
        return n;
    }
    private static int[][] edges(Class<?> loader, String file, Map<String,Integer> pre, Map<String,Integer> post) {
        List<String[]> r = rows(loader, file);
        int[][] e = new int[r.size()][];
        for (int i = 0; i < e.length; i++) {
            String[] x = r.get(i);
            Integer a = pre.get(x[0]), b = post.get(x[1]);
            if (a == null || b == null) throw new IllegalStateException("Unknown cell in " + file + ": " + Arrays.toString(x));
            e[i] = new int[]{a, b, Integer.parseInt(x[2])};
        }
        return e;
    }
}
