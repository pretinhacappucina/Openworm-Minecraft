# OpenWorm Minecraft (`openwormmc`)

A *C. elegans* with a virtual brain for Minecraft Java 1.20.1 (Fabric): sensory input from the Minecraft world drives a
conductance-based neural network built from OpenWorm / c302 data; motor/muscle activity bends an articulated body and the
body physics produces the motion.

## BUILD STATUS (read this first)
| Part | Status |
|---|---|
| Scientific engine (`connectome`, `neural`, `brain`, `muscle`, `physics`, `sensors`) | **Compiled and tested** (14 JUnit tests, all passing) with JDK 21 `javac`. Packaged as `openworm-engine-1.0.0.jar` (**this is NOT the Minecraft mod**). |
| Minecraft layer (`entity`, `client`, `gui`, `commands`, `registry`, `net`) + `build.gradle` | **Written but NEVER compiled or run.** The build sandbox could not reach `maven.fabricmc.net`, `libraries.minecraft.net`, `repo.maven.apache.org` or `plugins.gradle.org` (HTTP 403 from the egress proxy), so Fabric Loom could not download Minecraft 1.20.1/Yarn/Fabric API. Expect compile errors to fix on first build. |
| `openworm-minecraft-1.0.0.jar` (installable mod) | **Not produced.** Build it yourself (below). |

## Build the mod
Requires JDK 17+ and internet access to the Fabric/Maven repositories.
```
./gradlew clean test build      # Windows: gradlew.bat clean test build
```
The mod JAR appears in `build/libs/openworm-minecraft-1.0.0.jar` (put it in `mods/` with Fabric Loader >=0.14.21 and Fabric API).
Version numbers in `gradle.properties`/`build.gradle` are the usual 1.20.1 set but were not verified.

## Commands (permission level 2)
`/openworm spawn [n]`, `stats`, `brain`, `debug`, `feed`, `pause`, `resume`. Spawn egg: creative "Spawn Eggs" tab. Block: **Bacterial Colony**.
Brain viewer key: **K** (rebindable in Controls).

## What reproduces OpenWorm (verified in the engine)
* **Connectome**: Cook et al. 2019 hermaphrodite, as used by c302 via `cect`: 302 neurons, 3,709 neuron->neuron chemical edges, 2,196 gap-junction rows (symmetric matrix as published), 95 body-wall muscles, 956 neuron->muscle chemical edges. Weights = EM section counts, unmodified. Names such as `DA01` were normalised to `DA1` with the same rule `cect` uses.
* **Cell model (c302 Level C)**: single compartment, leak + k_slow + k_fast + ca_boyle (with Ca pool and `customHGate`), all constants from `parameters_C.py` / `cell_C.xml`; neuron = 5 um sphere, muscle = 5x20 um cylinder.
* **Synapses (c302 Level C1)**: NeuroML2 `gradedSynapse` (conductance 0.09 nS, delta 5 mV, Vth 0 mV, k 0.025/ms, erev 0/-70 mV; effective conductance = weight x g) and `gapJunction` (0.00052 nS x weight).

## What is an approximation (NOT identical to OpenWorm)
* **Integrator**: own fixed-step scheme (implicit in V, exact exponential gates and synapses), dt = 0.25 ms; not jNeuroML/LEMS. Verified step-size independent (dt 0.5 vs 0.05 ms differ by ~0.003 mV).
* **Not included**: muscle-muscle gap junctions, pharyngeal/other non-body-wall cells, neuropeptides, neurotransmitter data beyond the GABA list, the Level C2/FW Worm2D tuning. Gap junction rows are applied as one-way current per directed row (the matrix is symmetric, so both cells receive current once).
* **GABA/inhibition**: Cook data carry no sign; GABAergic neurons (DD, VD, RME, AVL, DVB, RIS) are inhibitory by our annotation, all others excitatory. This is a simplification.
* **Muscle -> contraction**: Hill(2) of muscle Ca around the h-gate half-activation Ca; 95 muscles grouped into 12 segments; dorsal minus ventral activation drives joint angle (first-order, tau 120 ms, max 0.45 rad).
* **Body**: planar 12-segment chain with resistive force theory (c_perp/c_par = 2), force-free/torque-free. Not Sibernetic. It does preserve causality: prescribed opposite traveling waves give opposite net motion (tested).
* **Time**: each server tick simulates 10 ms of worm time (50 ms real) so the worm runs ~5x slower than real time; configurable.
* **Sensors (documented approximations)**: head contact -> ALML, ALMR, AVM, FLPL, FLPR; tail -> PLML, PLMR; body -> PVDL, PVDR; odour -> AWAL/AWCL (left), AWAR/AWCR (right); food contact -> NSML, NSMR. Current injection 100 pA x intensity (the stimulation method c302 uses). Odour intensity = (1 - d/range)^2 sampled around the worm.
* **Auxiliary (not emergent) parts**: Minecraft collision/odour sampling, the `WORLD_SPEED_GAIN` factor converting body-lengths/s to blocks/tick, entity yaw following body heading. There are no behaviour state machines.

## Honest limitations of the simulated behaviour
Measured in the engine: with sensory stimuli the network depolarises 150-200 neurons and bends the body (up to 0.45 rad per joint) and turns the worm, but the **net forward propulsion is small** (about 0.02-0.11 body lengths in 20 s of simulated time) because untuned c302 C1 does not produce a sustained travelling wave by itself, and posterior muscles are only weakly driven. Forward vs. reversal is therefore not reliably emergent. NSM stimulation (food contact) does not drive muscles in this model. Real crawling would need parameter tuning (e.g. the OpenWorm Worm2D/C2_FW work) which was not done.

## Architecture
`connectome` (data) -> `neural.C1Network` (ODEs) -> `brain.Brain` (sensors->network->muscle->body, snapshot for UI) ; `entity.SimulationService` runs all brains on **one** daemon thread, max active brains configurable (default 16), server authoritative; clients only get a snapshot (shorts per neuron) every 5 ticks for the nearest worm within 48 blocks. Scientific packages do not depend on Minecraft.

## Tests (`src/test/java/.../EngineTest.java`)
Model loading, 302 neurons, connection identifiers/direction/symmetry, parameters, numerical stability and no NaN/Inf, step-size convergence, stimulus -> activity change, motor neuron and muscle response, physics direction, brain->body bending, shutdown, determinism, sensor neurons exist, and a food-source integration scenario.

## Sources
https://openworm.org/ - https://github.com/openworm/c302 - https://github.com/openworm/CElegansNeuroML - https://github.com/openworm/muscle_model - https://github.com/openworm/sibernetic (not ported) - https://github.com/openworm/ConnectomeToolbox - FruitFly Brain mod (https://modrinth.com/mod/flyproject-mod) as functional inspiration only.
Licences: see `LICENSE` and `ATTRIBUTION.md`.
