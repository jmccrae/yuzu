package ae.mccr.yuzu

object DataConversions {
  def toJson(model : Map[String, Any]) = {
    "{}"
  }

  def toRDFXML(model : Map[String, Any]) = {
      "<rdf:RDF/>"
  }

  def toTurtle(model : Map[String, Any]) = {
    ""
  }

  def toNTriples(model : Map[String, Any]) = {
    ""
  }

  def toHtml(model : Map[String, Any]) = {
    //Val title = model.get(LABEL_PROP) match {
      //case Some(x : String) =>
        //x
      //case Some(x : Seq[_]) =>
        //x.mkString(", ")
      //case _ =>
        //model.getOrElse("@id", "")
    //}
    ""
  }


}
