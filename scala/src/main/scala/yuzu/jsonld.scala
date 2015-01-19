package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.hp.hpl.jena.rdf.model.{Literal, Model, RDFNode, Resource, Property}
import com.hp.hpl.jena.vocabulary._
import java.io.Writer
import scala.collection.JavaConversions._
import scala.collection.mutable.{Map => MutableMap}

object JsonLDPrettySerializer {
  sealed trait JsonObj {
    def write(out : Writer, indent : Int) : Unit
    override def toString = {
      val sw = new java.io.StringWriter()
      write(sw, 0)
      sw.toString }
  }

  object JsonObj {
    def apply(value : Any) : JsonObj = value match {
      case o : JsonObj => 
        o
      case s : String =>
        JsonString(s)
      case s : Seq[_] =>
        JsonList(s.map(JsonObj(_)))
      case m : Map[_, _] =>
        JsonMap(MutableMap((m.map {
          case (k, v) => k.toString -> JsonObj(v)
        }).toSeq:_*))
      case m : MutableMap[_, _] =>
        JsonMap(m.map {
          case (k, v) => k.toString -> JsonObj(v)
        })
    }
    def apply(vals : (String, Any)*) : JsonMap = JsonMap((vals.map {
      case (k, v) => k -> JsonObj(v)
    }):_*)
  }
  case class JsonString(value : String) extends JsonObj {
    def write(out : Writer, indent : Int) {
      out.write("\"%s\"" format (value.replaceAll("\\\\","\\\\\\\\").
        replaceAll("\"", "\\\\\""))) }
  }

  class JsonMap(val value : MutableMap[String, JsonObj]) extends JsonObj {
    def write(out : Writer, indent : Int) {
      out.write("{\n")
      val elems = value.toSeq.sortBy(_._1)
      if(!elems.isEmpty) {
        val h = elems.head
        out.write("  " * (indent + 1))
        out.write("\"%s\": " format h._1)
        h._2.write(out, indent + 2)
        for((k, v) <- elems.tail) {
          out.write(",\n")
          out.write("  " * (indent + 1))
          out.write("\"%s\": " format k)
          v.write(out, indent + 2) }}
      out.write("\n")
      out.write("  " * indent)
      out.write("}") }

    def update(key : String, v : JsonObj) = value.update(key, v)
    def update(key : String, v : String) = value.update(key, JsonString(v))
    def contains(key : String) = value.contains(key)
    def apply(key : String) = value(key)
    def remove(key : String) = value.remove(key)
    def keys = value.keys
    override def equals(o : Any) = o match {
      case null => 
        false
      case jm : JsonMap =>
        value == jm.value
      case _ =>
        false }
  }

  object JsonMap {
    def apply(value : MutableMap[String, JsonObj]) = new JsonMap(value)
    def apply(vals : (String, JsonObj)*) = new JsonMap(
      MutableMap(vals:_*))
  }

  case class JsonList(value : Seq[JsonObj]) extends JsonObj {
    def write(out : Writer, indent : Int) {
      out.write("[\n")
      if(!value.isEmpty) {
        val h = value.head
        out.write("  " * (indent + 1))
        h.write(out, indent + 1)
        for(v <- value.tail) {
          out.write(",\n")
          out.write("  " * (indent + 1))
          v.write(out, indent + 1) }}
      out.write("\n")
      out.write("  " * indent)
      out.write("]") }

    def :+(elem : JsonObj) = JsonList(value :+ elem)
    def +:(elem : JsonObj) = JsonList(elem +: value)
    def apply(i : Int) = value(i)
  }

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

  private def propType(p :  Property, graph : Model) : JsonObj = {
    if(graph.listObjectsOfProperty(p).forall(_.isResource())) {
      JsonObj("@id" -> p.getURI(),
              "@type" -> "@id") }
    else {
      JsonString(p.getURI()) }}

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
      if(value.startsWith(uri) && uri != "http://www.example.com/") {
        return (Some(qn), value.drop(uri.length)) }}
    return (None, value) }

  private def extractJsonLDContext(graph : Model, 
      query : String) : (JsonMap, Map[String, String]) = {
    val context = JsonObj(
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
    for(k <- context.keys) {
      if(context(k) == JsonString("http://www.example.com/")) {
        context.remove(k) }}
    val props = MutableMap[String, String]()
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
      if(p == RDF.`type`) {
        sn = "@type" }
      else {
        context(sn) = propType(p, graph) }
      props(pStr) = sn }
    (context, props.toMap) } 

  private def addProps(obj : JsonMap, value : Resource, context : JsonMap, graph : Model,
               query : String, prop2sn : Map[String, String], drb : Set[Resource],
               stack : List[Resource]) {
    if(!(stack contains value)) {
      for(p <- graph.listProperties(value)) {
        val objs = graph.listObjectsOfProperty(value, p).toList.sortBy(_.toString)
        val isObj = p == RDF.`type` ||
          context(prop2sn(p.getURI())).isInstanceOf[JsonMap] 
        
        if(objs.size == 1) {
          graph.removeAll(value, p, objs(0))
          obj(prop2sn(p.getURI())) = jsonLDValue(objs(0), context, graph, query,
                                                 prop2sn, isObj, drb, 
                                                 value :: stack) }
        else {
          for(o <- objs) {
            graph.removeAll(value, p, o) }
          obj(prop2sn(p.getURI())) = JsonList((objs.map { o => 
            jsonLDValue(o, context, graph, query, prop2sn, 
                        isObj, drb, value :: stack) 
          }).toList) }}}}


  private def addInverseProps(obj : JsonMap, value : Resource, context : JsonMap, 
                      graph : Model, query : String, 
                      prop2sn : Map[String, String], drb : Set[Resource],
                      stack : List[Resource]) {
    if(!(stack contains value)) {
      for(p <- graph.listProperties()) {
        val objs = graph.listObjectsOfProperty(value, p).toList.sortBy(_.toString)
        for(o <- objs) {
          if(o.isResource()) {
            obj(prop2sn(p.getURI())) match {
              case jm : JsonMap =>
                addInverseProps(jm, value, context, graph, query, prop2sn, drb,
                                value :: stack) 
              case _ =>
                // ignore
      }}}}

      if(!graph.listProperties(null, value).isEmpty) {
        val robj = JsonMap()
        obj("@reverse") = robj
        for(p <- graph.listProperties(null, value)) {
          val objs = graph.listSubjectsWithProperty(p, value).toList.
            sortBy(_.toString).filter { o =>
              o.isResource() //&& !o.asResource().getURI().startsWith(query) 
            } map { o =>
              o.asResource() }

          if(objs.size == 1) {
            graph.removeAll(objs(0), p, value)
            robj(prop2sn(p.getURI())) = jsonLDValue(objs(0), context, graph,
                                                    query, prop2sn, false, drb,
                                                    value :: stack) }
          else if(objs.size > 1) {
            for(o <- objs) {
              graph.removeAll(o, p, value) }
            robj(prop2sn(p.getURI())) = JsonList(objs.map { o =>
              jsonLDValue(o, context, graph, query, prop2sn, 
                          false, drb, value :: stack) 
            }) }}}}}

  private def jsonLDValue(value : Any, context : JsonMap, graph : Model, 
                  query : String, prop2sn : Map[String, String], 
                  isObj : Boolean, drb : Set[Resource],
                  stack : List[Resource]) : JsonObj = {
    value match {
      case list : Seq[_] => 
        if(list.size == 1) {
          jsonLDValue(list(0), context, graph, query, prop2sn, 
                      isObj, drb, stack) }
        else {
          JsonList(list.map { v =>
            jsonLDValue(v, context, graph, query, prop2sn, 
                        isObj, drb, stack) })}
      case r : Resource if r.isURIResource() =>
        if(graph.listStatements(r, null, null : RDFNode).isEmpty && isObj) {
          val (pre, suf) = splitURI(r.getURI())
          pre match {
            case Some(pre) =>
              JsonString("%s:%s" format (pre, suf))
            case None => 
              JsonString(suf) }}
        else {
          val (pre, suf) = splitURI(r.getURI())
          val obj = pre match {
            case Some(pre) =>
              JsonObj("@id" -> ("%s:%s" format (pre, suf)))
            case None =>
              JsonObj("@id" -> suf) } 

          addProps(obj, r, context, graph, query, prop2sn, drb, stack)

          obj }
      case r : Resource =>
        if(graph.listStatements(r, null, null : RDFNode).isEmpty && isObj) {
          if(drb.contains(r)) {
            JsonString("_:" + r.getId().getLabelString()) }
          else {
            JsonMap() }}
        else {
          val obj = if(drb.contains(r)) {
            JsonObj("@id" -> ("_:" + r.getId().getLabelString())) }
          else {
            JsonMap() }

          addProps(obj, r, context, graph, query, prop2sn, drb, stack)

          obj }
      case l : Literal =>
        if(l.getLanguage() != null && l.getLanguage() != "") {
          JsonObj("@value" -> l.getLexicalForm(),
                  "@language" -> l.getLanguage())  }
        else if(l.getDatatype() != null) {
          val (pre, suf) = splitURI(l.getDatatype().getURI())
          pre match {
            case Some(pre) =>
              JsonObj("@value" -> l.getLexicalForm(),
                      "@type" -> ("%s:%s" format (pre, suf)))
            case None =>
              JsonObj("@value" -> l.getLexicalForm(),
                      "@type" -> suf) }}
        else {
          return JsonString(l.getLexicalForm()) }}}

  private def doubleReffedBNodes(graph : Model) : Set[Resource] = {
    (for {
      o <- graph.listObjects()
      if o.isResource() && !o.isURIResource()
      if graph.listStatements(null, null, o).size > 1
    } yield o.asResource()).toSet }

  def write(out : java.io.Writer, graph : Model, query : String) {
    val obj = jsonLDfromModel(graph, query)
    obj.write(out, 0)
    out.write("\n")
    out.flush()
  }

  def jsonLDfromModel(graph : Model, query : String) : JsonObj = {
    val (context, prop2sn) = extractJsonLDContext(graph, query)
    val theId = if(query.startsWith(BASE_NAME)) {
        query.drop(BASE_NAME.size) }
      else {
        query }

    var theObj = JsonObj(
      "@context" -> context,
      "@id" -> JsonString(theId)
    )
    val elem = graph.createResource(query)

    val drb = doubleReffedBNodes(graph)
    addProps(theObj, elem, context, graph, query, prop2sn, drb, Nil)
    addInverseProps(theObj, elem, context, graph, query, prop2sn, drb, Nil)

    var rest = graph.listSubjects().toList
    if(!rest.isEmpty()) {
      val graphObj = JsonObj(
        "@context" -> context,
        "@graph" -> JsonList(Seq(theObj)))
      theObj.remove("@context")
      theObj = graphObj
      while(!rest.isEmpty()) {
        theObj("@graph") = theObj("@graph").asInstanceOf[JsonList] :+
          jsonLDValue(rest.head, context, graph, query, prop2sn, true, drb, Nil)
        rest = graph.listSubjects().toList }}

    theObj }
}
