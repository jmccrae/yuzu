for(line <- io.Source.stdin.getLines) {
  val elems = line.split(" ")
  if(elems(1) == "<http://www.w3.org/2000/01/rdf-schema#label>") {
    var obj = elems.drop(2).dropRight(1).mkString(" ")
    obj = obj.replaceAll("\\\\[u]","u")
    val lang = elems(0).drop("<http://data.lider-project.eu/emn/".size).take(2)
    println("%s %s %s@%s ." format (elems(0), elems(1), obj, lang))
    println("%s <http://lemon-model.net/lemon#language> \"%s\" ." format (elems(0), lang))
  } else {
    println(line)
  }
}


