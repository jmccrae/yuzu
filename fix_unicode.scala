val regex = java.util.regex.Pattern.compile("""%5Cu([0-9abcdefABCDEF]{4})""")

for(line <- io.Source.stdin.getLines()) {
  var l = line
  var m = regex.matcher(line)
  while(m.find()) {
    val c = Integer.parseInt(m.group(1), 16).toChar.toString 
    l = m.replaceFirst(java.net.URLEncoder.encode(c, "UTF-8"))
    m = regex.matcher(l) }
  println(l) }
