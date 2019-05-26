mappings in (Compile, packageSrc) ++= {
  import Path.{flat, relativeTo}
  println((managedSources in Compile).value)
  (managedSources in Compile).value pair (relativeTo((sourceManaged in Compile).value) | flat)
}

sourceGenerators in Compile += Def.task {
  if(!version.value.endsWith("-SNAPSHOT")) {
    val in = scala.io.Source.fromFile(baseDirectory.value / "README.markdown.in")
    try {
      val out = new java.io.PrintWriter(baseDirectory.value / "README.markdown")
      try {
        for(line <- in.getLines()) {
          out.println(line.replaceAll(java.util.regex.Pattern.quote("%VERSION%"), java.util.regex.Matcher.quoteReplacement(version.value)))
        }
      } finally {
        out.close()
      }
    } finally {
      in.close()
    }
  }
  Seq.empty[File]
}

sourceGenerators in Compile += Def.task { GenPackage((sourceManaged in Compile).value) }

