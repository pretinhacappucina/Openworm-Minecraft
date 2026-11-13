"""Extract the hermaphrodite Cook et al. 2019 connectome (as shipped in the cect package,
the same data source c302 uses) into compact TSV files for the Java engine.

Usage: python -I extract_connectome.py <cect_package_dir> <out_dir>
Only neurons (302) and body-wall muscles are kept. Weights are the raw Cook et al. values
(number of EM serial sections). Nothing is invented or smoothed.
"""
import os
import re
import sys

from openpyxl import load_workbook

cect_dir, out_dir = sys.argv[1], sys.argv[2]
os.makedirs(out_dir, exist_ok=True)

sys.path.insert(0, os.path.dirname(cect_dir))
# cect.Cells imports pandas at module import time; we only need lists, so parse them textually.
cells_src = open(os.path.join(cect_dir, "Cells.py"), encoding="utf-8").read()


def grab_list(name):
    m = re.search(r"^%s\s*=\s*\[(.*?)\n\]" % re.escape(name), cells_src, re.S | re.M)
    if not m:
        raise SystemExit("list not found: " + name)
    return re.findall(r'"([^"]+)"', m.group(1))


neurons = grab_list("PREFERRED_HERM_NEURON_NAMES")
bwm = grab_list("BODY_WALL_MUSCLE_NAMES")
print("neurons in cect list:", len(neurons), "unique:", len(set(neurons)))
print("body wall muscles in cect list:", len(bwm))

def norm_neuron(n):
    # same as cect.Cells.remove_leading_index_zero: DA01 -> DA1, AS09 -> AS9 (VD10 unchanged)
    return re.sub(r"^([A-Za-z]{2,3})0(\d)$", r"\1\2", n)


def norm_muscle(m):
    # same rule as cect.Cells.convert_to_preferred_muscle_name: dBWML1 -> MDL01, vBWMR10 -> MVR10
    if m.startswith("dBWM") or m.startswith("vBWM"):
        pre = "MD" if m[0] == "d" else "MV"
        return pre + m[4:] if len(m) == 7 else "%s%s0%s" % (pre, m[4], m[5])
    return m




xlsx = os.path.join(cect_dir, "data", "SI 5 Connectome adjacency matrices, corrected July 2020.xlsx")
wb = load_workbook(xlsx, read_only=False, data_only=True)
HERM_CHEM = "hermaphrodite chemical"
HERM_GAP = "hermaphrodite gap jn symmetric"
if HERM_GAP not in wb.sheetnames:
    HERM_GAP = "herm gap jn symmetric"


def read_sheet(name, n_pre_rows, n_post_cols):
    ws = wb[name]
    pre = [ws["C%d" % r].value for r in range(4, 4 + n_pre_rows)]
    post = [ws.cell(row=3, column=c).value for c in range(4, 4 + n_post_cols)]
    out = []
    pre = [norm_neuron(str(p).strip()) if p else p for p in pre]
    for i, p in enumerate(pre):
        for j, q in enumerate(post):
            v = ws.cell(row=4 + i, column=4 + j).value
            if v is not None and int(v) > 0 and p and q:
                qs = str(q).strip()
                out.append((str(p).strip(), qs if qs.startswith(("dBWM", "vBWM")) else norm_neuron(qs), int(v)))
    return out


# Sheet extents taken from cect's Cook2019DataReader (corrected file): chem 300 x 454, gap 469 x 469
chem = read_sheet(HERM_CHEM, 300, 454)
gap = read_sheet(HERM_GAP, 469, 469)
print("raw chem edges:", len(chem), "raw gap edges:", len(gap))

neuron_set = set(neurons)


bwm_set = set(bwm)
# names in the spreadsheet use e.g. 'MDL01' / 'mu_bod' styles -- map only exact matches, report the rest
pairs_nn_chem, pairs_nn_gap, pairs_nm = [], [], []
unmatched_post = {}
for p, q, w in chem:
    qn = norm_muscle(q)
    if p in neuron_set and q in neuron_set:
        pairs_nn_chem.append((p, q, w))
    elif p in neuron_set and qn in bwm_set:
        pairs_nm.append((p, qn, w))
    elif p in neuron_set:
        unmatched_post[q] = unmatched_post.get(q, 0) + 1
for p, q, w in gap:
    if p in neuron_set and q in neuron_set:
        pairs_nn_gap.append((p, q, w))

print("neuron->neuron chemical:", len(pairs_nn_chem))
print("neuron<->neuron gap (directed rows, symmetric):", len(pairs_nn_gap))
print("neuron->body-wall-muscle chemical:", len(pairs_nm))
print("post cells not matched (non-neuron, non-BWM), sample:", list(unmatched_post.items())[:12])

with open(os.path.join(out_dir, "neurons.tsv"), "w", encoding="utf-8") as f:
    f.write("# index\tname\n")
    for i, n in enumerate(neurons):
        f.write("%d\t%s\n" % (i, n))
with open(os.path.join(out_dir, "muscles.tsv"), "w", encoding="utf-8") as f:
    f.write("# index\tname\n")
    for i, n in enumerate(bwm):
        f.write("%d\t%s\n" % (i, n))
with open(os.path.join(out_dir, "chemical.tsv"), "w", encoding="utf-8") as f:
    f.write("# pre\tpost\tweight_EM_sections\n")
    for p, q, w in sorted(pairs_nn_chem):
        f.write("%s\t%s\t%d\n" % (p, q, w))
with open(os.path.join(out_dir, "gap.tsv"), "w", encoding="utf-8") as f:
    f.write("# a\tb\tweight_EM_sections (symmetric matrix as published; both directions present)\n")
    for p, q, w in sorted(pairs_nn_gap):
        f.write("%s\t%s\t%d\n" % (p, q, w))
with open(os.path.join(out_dir, "neuromuscular.tsv"), "w", encoding="utf-8") as f:
    f.write("# neuron\tmuscle\tweight_EM_sections\n")
    for p, q, w in sorted(pairs_nm):
        f.write("%s\t%s\t%d\n" % (p, q, w))
print("done ->", out_dir)
