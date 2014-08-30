mappings in (Compile, packageSrc) <++= (sourceManaged in Compile, managedSources in Compile) map { (base, srcs) =>
  import Path.{flat, relativeTo}
  println(srcs)
  srcs x (relativeTo(base) | flat)
}

sourceGenerators in Compile <+= (baseDirectory, version) map { (baseDirectory, version) =>
  if(!version.endsWith("-SNAPSHOT")) {
    val in = scala.io.Source.fromFile(baseDirectory / "README.markdown.in")
    try {
      val out = new java.io.PrintWriter(baseDirectory / "README.markdown")
      try {
        for(line <- in.getLines()) {
          out.println(line.replaceAll(java.util.regex.Pattern.quote("%VERSION%"), java.util.regex.Matcher.quoteReplacement(version)))
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

sourceGenerators in Compile <+= (sourceManaged in Compile) map (GenPackage)

