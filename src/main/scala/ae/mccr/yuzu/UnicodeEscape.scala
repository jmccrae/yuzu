package org.insightcentre.nlp.yuzu

import java.util.regex.Pattern

object UnicodeEscape {
  /** Fix unicode escape characters */
  def unescape(str : String) : String = {
    val sb = new StringBuilder(str)
    var i = sb.indexOf('\\')
    while(i >= 0 && i < sb.length - 5) {
      if(sb.charAt(i) == '\\' && sb.charAt(i+1) == 'u') {
        try {
          sb.replace(i,i+6, 
            Integer.parseInt(sb.slice(i+2,i+6).toString, 16).toChar.toString) }
        catch {
        case x : NumberFormatException =>
          System.err.println("Bad unicode string %s" format sb.slice(i,i+6)) }}
      i = sb.indexOf('\\', i + 1) }
    sb.toString }


  private def encodeDangerous(s : String) = {
    val p = Pattern.compile("([\"<>{}\\[\\]|\\\\\\p{IsWhite_Space}])")
    val m = p.matcher(s)
    val sb = new StringBuffer()
    while(m.find()) {
      m.appendReplacement(sb, java.net.URLEncoder.encode(m.group(1), "UTF-8"))
    }
    m.appendTail(sb)
    sb }

  private def doubleEncode(s : CharSequence) = {
    // Double encode already encoded special characters to avoid 
    // creating invalid URIs
    val p = Pattern.compile(
      "(%23|%25|%2F|%3B|%3F|%2B|%22|%3C|%3E|%7B|%7D|%5C|%5E|%5B|%5D|" +
       "%C2%A0|%E1%9A%80|%E1%A0%8E|%E2%80%8[0-9AB]|" +
       "%E2%80%AF|%E2%81%9F|%E3%80%80|%EF%BB%BF)", Pattern.CASE_INSENSITIVE)
    val m = p.matcher(s)
    val sb = new StringBuffer()
    while(m.find()) {
      m.appendReplacement(sb, m.group(1).replaceAll("%", "%25")) }
    m.appendTail(sb)
    sb.toString }

  /**
   * Make a URI safe in that it avoids all of the most unsafe characters.
   * The following character are unsafe and should always be 
   * encoded
   *   " < > { } | \ ^ [ ] 
   *   Anything matching \p{IsWhite_Space}
   * The following should never be decoded to avoid ambiguity
   *   %23 (#) %2F (/) %3B (;) %3F (?) %2B (+)  %25 (%) */
  def safeURI(uri : String) =
    java.net.URLDecoder.decode(
      doubleEncode(
        encodeDangerous(uri)), "UTF-8").replaceAll(" ", "+")

//  def fixURI(n : Node) = if(n.isURI()) {
//    NodeFactory.createURI(safeURI(n.getURI())) }
//  else { n }

  /**
   * Make a path safe by encoding all dangerous characters
   */
  def safePath(s : String) = encodeDangerous(s).toString()

}


