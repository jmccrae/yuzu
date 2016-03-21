var last = ""

for(line <- io.Source.stdin.getLines) {
  val elems = line.split(" ")
  if(elems(1) == "<http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#referenceContext>") {
    val obj = elems(2).takeWhile(_ != '#')
    println("%s %s %s> ." format (elems(0), elems(1), obj))
    if(obj != last) {
      val label = elems(2).drop(elems(2).lastIndexOf('/') + 1).takeWhile(_ != '#').replaceAll("_", " ")
      println("%s> <http://www.w3.org/2000/01/rdf-schema#label> \"%s\"@en ." format
               (elems(2).takeWhile(_ != '#'), label))
      last = obj
    }
  } else {
    println(line)
  }
}
