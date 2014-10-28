package com.github.jmccrae.yuzu

class Template(contents : String) {
  // TODO: Optimize
  def substitute(subs : (String,String)*) = {
    var c = contents
    for((subFor,subTo) <- subs) {
      println(subFor + " -> " + subTo)
      c = c.replaceAll("\\$\\{?%s\\}?" format subFor, subTo.replaceAll("\\$","\\\\\\$"))
    }
    c.replaceAll("\\$\\$","\\$")
  }
}

object Template {
  def apply(contents : String) = new Template(contents)
}
