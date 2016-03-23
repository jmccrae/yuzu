package ae.mccr.yuzu.jsonld

import spray.json._
import java.net.URL

class JsonLDContext(val definitions : Map[String, JsonLDDefinition],
  val base : Option[URL], val vocab : Option[String])

object JsonLDContext {
  def apply(data : JsObject) : JsonLDContext = {
    new JsonLDContext(data.fields.filter({
      case (key, value) => !key.startsWith("@")  
    }).mapValues({
      case JsString(s) =>
        JsonLDAbbreviation(s)
      case JsObject(data) if (data.contains("@id") && data.contains("@type") &&
          data("@type") == JsString("@id")) =>
        data("@id") match {
          case JsString(s) =>
            JsonLDURIProperty(s)
          case _ =>
            throw new JsonLDException("@id must be a string")
        }
      case JsNull =>
        JsonLDIgnore
      case s =>
        throw new JsonLDException("Unexpected definition: " + s)
    }),
    data.fields.get("@base").map({
      case JsString(s) => new URL(s)
      case _ => throw new JsonLDException("@base is not a string")
    }),
    data.fields.get("@vocab").map({
      case JsString(s) => s
      case _ => throw new JsonLDException("@vocab is not a string")
    })
  
    )
  }
}

sealed trait JsonLDDefinition
case class JsonLDAbbreviation(val full : String) extends JsonLDDefinition
case class JsonLDURIProperty(val full : String) extends JsonLDDefinition
object JsonLDIgnore extends JsonLDDefinition
