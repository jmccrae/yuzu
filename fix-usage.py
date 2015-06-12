import sys

refcontexts = set([])

for line in open("usage2.nt").readlines():
    elems = line.split(" ")
    if elems[1] == "<http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#referenceContext>":
        refcontexts.add(elems[2])

for line in open("usage2.nt").readlines():
    elems = line.strip().split(" ")
    if elems[0] in refcontexts:
        elems[0] = elems[0][:elems[0].index('#')] + ">"
    if elems[2] in refcontexts:
        elems[2] = elems[2][:elems[2].index('#')] + ">"
    print(" ".join(elems))


