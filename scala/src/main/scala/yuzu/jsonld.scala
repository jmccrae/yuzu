package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.hp.hpl.jena.rdf.model.{Literal, Model, RDFNode, Resource, Property}
import com.hp.hpl.jena.vocabulary._
import scala.collection.mutable.Map
import scala.collection.JavaConversions._

object JsonLDPrettySerializer {
  type Sson = Map[String, Any]

  implicit class GraphPimp(graph : Model) {
    def listProperties(subj : Resource = null, obj : RDFNode = null) = {
      (graph.listStatements().filter { st =>
        (subj == null || st.getSubject() == subj) &&
        (obj == null || st.getObject() == obj) 
      } map { st =>
        st.getPredicate()
      }).toSet }
  }

  private def isAlnum(s : String) = s.matches("\\w+")

  private def propType(p :  Property, graph : Model) : Any = {
    if(graph.listObjectsOfProperty(p).forall(_.isResource())) {
      return Map("@id" -> p.getURI(),
                 "@type" -> "@id") }
    else {
      return p.getURI() }}

  private def splitURI(value : String) : (Option[String], String) = {
    if(value.startsWith(BASE_NAME)) {
      return (None, value.drop(BASE_NAME.length)) }
    for((uri, qn) <- Seq((PREFIX1_URI, PREFIX1_QN),
                    (PREFIX2_URI, PREFIX2_QN),
                    (PREFIX3_URI, PREFIX3_QN),
                    (PREFIX4_URI, PREFIX4_QN),
                    (PREFIX5_URI, PREFIX5_QN),
                    (PREFIX6_URI, PREFIX6_QN),
                    (PREFIX7_URI, PREFIX7_QN),
                    (PREFIX8_URI, PREFIX8_QN),
                    (PREFIX9_URI, PREFIX9_QN),
                    (RDF.getURI(), "rdf"),
                    (RDFS.getURI(), "rdfs"),
                    (XSD.getURI(), "xsd"),
                    (OWL.getURI(), "owl"),
                    (DC_11.getURI(), "dc"),
                    (DCTerms.getURI(), "dct"))) {
      if(value.startsWith(uri)) {
        return (Some(qn), value.drop(uri.length)) }}
    return (None, value) }

  private def extractJsonLDContext(graph : Model, 
      query : String) : (Sson, Map[String, String]) = {
    val context = Map[String, Any](
      "@base" -> BASE_NAME,
      PREFIX1_QN -> PREFIX1_URI,
      PREFIX2_QN -> PREFIX2_URI,
      PREFIX3_QN -> PREFIX3_URI,
      PREFIX4_QN -> PREFIX4_URI,
      PREFIX5_QN -> PREFIX5_URI,
      PREFIX6_QN -> PREFIX6_URI,
      PREFIX7_QN -> PREFIX7_URI,
      PREFIX8_QN -> PREFIX8_URI,
      PREFIX9_QN -> PREFIX9_URI,
      "rdf" -> RDF.getURI(),
      "rdfs" -> RDFS.getURI(),
      "xsd" -> XSD.getURI(),
      "owl" -> OWL.getURI(),
      "dc" -> DC_11.getURI(),
      "dct" -> DCTerms.getURI())
    val props = Map[String, String]()
    for(p <- graph.listProperties()) {
      val pStr = p.getURI()
      var shortName = ""
      if(pStr.contains("#")) {
        shortName = pStr.drop(pStr.lastIndexOf("#") + 1) }
      if(!isAlnum(shortName) && pStr.contains("/")) {
        shortName = pStr.drop(pStr.lastIndexOf("/") + 1) }
      if(!isAlnum(shortName)) {
        val (pre, suf) = splitURI(pStr)
        shortName = suf }
      var sn = shortName
      var i = 2
      while(context.contains(sn)) {
        sn = "%s%d" format (shortName, i)
        i += 1 }
      context(sn) = propType(p, graph)
      props(pStr) = sn }
    (context, props) } 

  private def addProps(obj : Sson, value : Resource, context : Sson, graph : Model,
               query : String, prop2sn : Map[String, String], drb : Set[Resource]) {
    for(p <- graph.listProperties(value)) {
      val objs = graph.listObjectsOfProperty(value, p).toSeq.sortBy(_.toString)
      val isObj = context(prop2sn(p.getURI())).isInstanceOf[Map[_,_]]
      
      if(objs.size == 1) {
        graph.removeAll(value, p, objs(0))
        obj(prop2sn(p.getURI())) = jsonLDValue(objs(0), context, graph, query,
                                               prop2sn, isObj, drb) }
      else {
        for(o <- objs) {
          graph.removeAll(value, p, o) }
        obj(prop2sn(p.getURI())) = (objs.map { o => 
          jsonLDValue(o, context, graph, query, prop2sn, isObj, drb) 
        }).toList }}}


  private def addInverseProps(obj : Sson, value : Resource, context : Sson, 
                      graph : Model, query : String, 
                      prop2sn : Map[String, String], drb : Set[Resource]) {
    for(p <- graph.listProperties()) {
      val objs = graph.listObjectsOfProperty(value, p).toSeq.sortBy(_.toString)
      for(o <- objs) {
        if(o.isResource()) {
          addInverseProps(obj(prop2sn(p.getURI())).asInstanceOf[Sson], value, 
                          context, graph, query, prop2sn, drb) }}}

    if(!graph.listProperties(null, value).isEmpty) {
      val robj = Map[String, Any]()
      obj("@reverse") = robj
      for(p <- graph.listProperties(null, value)) {
        val objs = graph.listSubjectsWithProperty(p, value).toSeq.
          sortBy(_.toString).filter { o =>
            o.isResource() && !o.asResource().getURI().startsWith(query) 
          } map { o =>
            o.asResource() }

        if(objs.size == 1) {
          graph.removeAll(objs(0), p, value)
          robj(prop2sn(p.getURI())) = jsonLDValue(objs(0), context, graph,
                                                  query, prop2sn, false, drb) }
        else if(objs.size > 1) {
          for(o <- objs) {
            graph.removeAll(o, p, value) }
          robj(prop2sn(p.getURI())) = (objs.map { o =>
            jsonLDValue(o, context, graph, query, prop2sn, false, drb) 
          }).toList }}}}

  private def jsonLDValue(value : Any, context : Sson, graph : Model, 
                  query : String, prop2sn : Map[String, String], 
                  isObj : Boolean, drb : Set[Resource]) : Any = {
    value match {
      case list : Seq[_] => 
        if(list.size == 1) {
          jsonLDValue(list(0), context, graph, query, prop2sn, isObj, drb) }
        else {
          list.map { v =>
            jsonLDValue(v, context, graph, query, prop2sn, isObj, drb) }}
      case r : Resource if r.isURIResource() =>
        if(graph.listStatements(r, null, null : RDFNode).isEmpty && isObj) {
          val (pre, suf) = splitURI(r.getURI())
          pre match {
            case Some(pre) =>
              "%s:%s" format (pre, suf)
            case None => 
              suf }}
        else {
          val (pre, suf) = splitURI(r.getURI())
          val obj : Sson = pre match {
            case Some(pre) =>
              Map("@id" -> ("%s:%s" format (pre, suf)))
            case None =>
              Map("@id" -> suf) } 

          addProps(obj, r, context, graph, query, prop2sn, drb)

          obj }
      case r : Resource =>
        if(graph.listStatements(r, null, null : RDFNode).isEmpty && isObj) {
          if(drb.contains(r)) {
            "_:" + r.getId().getLabelString() }
          else {
            Map[String, Any]() }}
        else {
          val obj : Sson = if(drb.contains(r)) {
            Map("@id" -> ("_:" + r.getId().getLabelString())) }
          else {
            Map[String, Any]() }

          addProps(obj, r, context, graph, query, prop2sn, drb)

          obj }
      case l : Literal =>
        if(l.getLanguage() != null && l.getLanguage() != "") {
          Map("@value" -> l.getLexicalForm(),
              "@language" -> l.getLanguage())  }
        else if(l.getDatatype() != null) {
          val (pre, suf) = splitURI(l.getDatatype().getURI())
          pre match {
            case Some(pre) =>
              Map("@value" -> l.getLexicalForm(),
                  "@type" -> ("%s:%s" format (pre, suf)))
            case None =>
              Map("@value" -> l.getLexicalForm(),
                  "@type" -> suf) }}
        else {
          return l.getLexicalForm() }}}

  private def doubleReffedBNodes(graph : Model) : Set[Resource] = {
    (for {
      o <- graph.listObjects()
      if o.isResource() && !o.isURIResource()
      if graph.listStatements(null, null, o).size > 1
    } yield o.asResource()).toSet }

  def jsonLDfromModel(graph : Model, query : String) = {
    val (context, prop2sn) = extractJsonLDContext(graph, query)
    val theId = if(query.startsWith(BASE_NAME)) {
        query.drop(BASE_NAME.size) }
      else {
        query }

    var theObj : Sson = Map(
      "@context" -> context,
      "@id" -> theId
    )
    val elem = graph.createResource(query)

    val drb = doubleReffedBNodes(graph)
    addProps(theObj, elem, context, graph, query, prop2sn, drb)
    addInverseProps(theObj, elem, context, graph, query, prop2sn, drb)

    var rest = graph.listSubjects().toList
    if(!rest.isEmpty()) {
      val graphObj : Sson = Map(
        "@context" -> context,
        "@graph" -> Seq(theObj))
      theObj.remove("@context")
      theObj = graphObj
      while(!rest.isEmpty()) {
        theObj("@graph") = theObj("@graph").asInstanceOf[Seq[Any]] :+
          jsonLDValue(rest.head, context, graph, query, prop2sn, true, drb)
        rest = graph.listSubjects().toList }}

    theObj }
}
