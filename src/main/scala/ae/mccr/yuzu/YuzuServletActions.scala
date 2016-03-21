package ae.mccr.yuzu

trait YuzuServletActions extends YuzuStack {
  import YuzuSettings._
  import YuzuUserText._

  def backend : Backend

  def quotePlus(s : String) = java.net.URLEncoder.encode(s, "UTF-8")

  def search(query : String, property : Option[String], offset : Int) : Any = {
    val limit = 20
    val buf = new StringBuilder()
    val results = backend.search(query, property, offset, limit + 1)
    val prev = math.max(0, offset - limit)
    val next = offset + limit
    val pages = "%d - %d" format (offset + 1, offset + math.min(limit, results.size))
    val hasPrev = if(offset == 0) { " disabled" } else { "" }
    val hasNext = if(results.size <= limit) { " disabled" } else { "" }
    val qs = "&query=" + quotePlus(query) + (
      property match {
        case Some(p) => "&property=" + quotePlus(p).drop(1).dropRight(1)
        case None => ""
      })
    val results2 = for(result <- results) yield {
      Map(
        "title" -> result.label,
        "link" -> result.link,
        "model" -> backend.summarize(result.id)) }
    contentType = "text/html"
    mustache("/search",
      "results" -> results2.take(limit),
      "prev" -> prev,
      "has_prev" -> hasPrev,
      "next" -> next,
      "has_next" -> hasNext,
      "pages" -> pages,
      "query" -> qs
    )
  }

  def sparqlQuery(query : String, mime : ResultType, defaultUrl : Option[String]) {}

  def listResources(offset : Int, property : Option[String], obj : 
                    Option[String], objOffset : Int) {}

  def render(model : Map[String, Any], mime : ResultType) {} 

}
