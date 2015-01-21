import sys

for line in sys.stdin.readlines():
    elems = line.split(" ")

    if len(elems) > 2:
        elems[-2] = elems[-2].replace("@ara", "@ar")
        elems[-2] = elems[-2].replace("@cat", "@ca")
        elems[-2] = elems[-2].replace("@dan", "@da")
        elems[-2] = elems[-2].replace("@eng", "@en")
        elems[-2] = elems[-2].replace("@eus", "@eu")
        elems[-2] = elems[-2].replace("@fin", "@fi")
        elems[-2] = elems[-2].replace("@fra", "@fr")
        elems[-2] = elems[-2].replace("@glg", "@gl")
        elems[-2] = elems[-2].replace("@heb", "@he")
        elems[-2] = elems[-2].replace("@ind", "@id")
        elems[-2] = elems[-2].replace("@ita", "@it")
        elems[-2] = elems[-2].replace("@jpn", "@ja")
        elems[-2] = elems[-2].replace("@nno", "@nn")
        elems[-2] = elems[-2].replace("@nob", "@nb")
        elems[-2] = elems[-2].replace("@pol", "@pl")
        elems[-2] = elems[-2].replace("@por", "@pt")
        elems[-2] = elems[-2].replace("@spa", "@es")
        elems[-2] = elems[-2].replace("@sqi", "@sq")
        elems[-2] = elems[-2].replace("@tha", "@th")
        elems[-2] = elems[-2].replace("@zho", "@zh")
        elems[-2] = elems[-2].replace("@zsm", "@ms")

        sys.stdout.write(" ".join(elems))
        if elems[1] == "<http://lemon-model.net/lemon#writtenRep>":
            s = elems[0][:elems[0].index('#')] + ">"
            sys.stdout.write(s)
            sys.stdout.write(" ")
            sys.stdout.write("<http://www.w3.org/2000/01/rdf-schema#label> ")
            sys.stdout.write(" ".join(elems[2:]))
