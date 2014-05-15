package com.github.jmccrae.yuzu

import com.github.jmccrae.yuzu.YuzuSettings._
import com.hp.hpl.jena.rdf.model.Model
import java.sql.DriverManager

class RDFBackend(db : String) {
  try {
    Class.forName("org.sqlite.JDBC")
  } catch {
    case x : ClassNotFoundException => throw new RuntimeException("No SQLite Driver", x)
  }

  def name(id : String, frag : Option[String]) = frag match {
    case Some(f) => "%s%s#%s" format (BASE_NAME, id, frag)
    case None => "%s%s" format (BASE_NAME, id)
  }

  def unname(uri : String) = if(uri.startsWith(BASE_NAME)) {
    if(uri contains '#') {
      val id = uri.slice(BASE_NAME.length, uri.indexOf('#'))
      val frag = uri.drop(uri.indexOf('#') + 1)
      Some((id, Some(frag)))
    } else {
      Some((uri.drop(BASE_NAME.length), None))
    }
  } else {
    None
  }

  def lookup(id : String) : Option[Model] = {
    val conn = DriverManager.getConnection("jdbc:sqlite:" + db)
    val ps = conn.prepareStatement("select fragment, property, object, inverse from triples where subject=?")
    ps.setString(1,id)

    return None
  }

  def listResources(offset : Int, limit : Int) : (Boolean,List[String]) = (false,Nil)

  def search(query : String, property : Option[String]) : List[String] = Nil
}
