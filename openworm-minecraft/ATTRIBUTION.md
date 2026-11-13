# Attribution and licences of scientific data / models

| Item | Source | Licence / status |
|---|---|---|
| Hermaphrodite connectome (chemical + gap junction adjacency matrices, `src/main/resources/data/openwormmc/connectome/*.tsv`) | Cook, S.J. et al. (2019) *Whole-animal connectomes of both Caenorhabditis elegans sexes*, Nature 571, 63-71 (Supplementary Information 5, "corrected July 2020" version from WormWiring), read through the `cect` package | Data redistributed as published by the authors. **Check the original publication's terms before redistributing.** |
| Extraction toolchain | `cect` (OpenWorm Connectome Toolbox) 0.3.5 from PyPI, https://github.com/openworm/ConnectomeToolbox | MIT (verified from the package metadata) |
| Level C1 parameters and equations, cell_C.xml channels | openworm/c302 (`parameters_C.py`, `parameters_C1.py`, `cell_C.xml`) - copies kept in `tools/reference_*` | Upstream licence **not re-verified in this build**; see https://github.com/openworm/c302 |
| gradedSynapse / gapJunction / fixedFactorConcentrationModel equations | NeuroML2 core types (https://github.com/NeuroML/NeuroML2) | Re-implemented from the published definitions |
| Ion channel kinetics (k_fast, k_slow, ca_boyle) | Boyle & Cohen (2008), via openworm/muscle_model and c302 | Values as used in c302 |
| Graded synapse formulation | Methods of Nature Neuroscience 7:1352 (as cited in the NeuroML2 definition) | - |
| GABAergic neuron list (inhibitory synapses) | Annotation by this project from the literature (DD, VD, RME, AVL, DVB, RIS); c302 itself marks synapses of GABA neurons inhibitory | Not part of the Cook data |

Not used: Sibernetic (C++/OpenCL), CE_locomotion. Textures are procedurally generated here (original).
No code or visuals from the FruitFly Brain mod were used.
