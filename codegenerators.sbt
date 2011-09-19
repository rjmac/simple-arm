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
  Nil
}

sourceGenerators in Compile <+= (sourceManaged in Compile) map { output =>
  def genUsing(n: Int): String = {
    val typeVars = (0 until n).map(i => ('A' + i).toChar).toIndexedSeq
    val evVars = (1 to n).map("ev" + _).toIndexedSeq
    val paramVars = (1 to n).map("r" + _).toIndexedSeq
    val forcedVars = (1 to n).map("fr" + _).toIndexedSeq
    val resultVars = (1 to n).map("res" + _).toIndexedSeq
    val sb = new StringBuilder
    //
    val formals = paramVars.zip(typeVars).map { case (p,t) => p + ": =>" + t }
    val evs = evVars.zip(typeVars).map { case (e,t) => e + ": Resource[" + t + "]" }
    //
    sb.append("def using[").append(typeVars.mkString(", ")).append(", RR](").append(formals.mkString(", ")).append(")(f: (").append(typeVars.mkString(", ")).append(") => RR)(implicit ").append(evs.mkString(", ")).append("): RR = {\n")
    //
    def loop(i: Int, indent: Int) {
      def idt() = sb.append(" " * indent)
      if(i == n) {
        idt().append("f(").append(forcedVars.mkString(", ")).append(")\n")
      } else {
        idt().append("val ").append(forcedVars(i)).append(" = ").append(paramVars(i)).append("\n")
        idt().append(evVars(i)).append(".open(").append(forcedVars(i)).append(")\n")
        idt().append("val ").append(resultVars(i)).append(" = try {\n")
        loop(i + 1, indent + 2)
        idt().append("} catch {\n")
        idt().append("  case e: Throwable =>\n")
        idt().append("    ").append(evVars(i)).append(".closeAbnormally(").append(forcedVars(i)).append(", e)\n")
        idt().append("    throw e\n")
        idt().append("}\n")
        idt().append(evVars(i)).append(".close(").append(forcedVars(i)).append(")\n")
        idt().append(resultVars(i)).append("\n")
      }
    }
    //
    loop(0, 2)
    //
    sb.append("}\n\n")
    //
    sb.toString
  }
  output.mkdirs()
  val outfile = output / "simplearm-util.scala"
  val f = new java.io.FileWriter(outfile)
  try {
    f.write("package com.rojoma\n")
    f.write("package simplearm\n")
    f.write("package object util {\n")
    f.write("def managed[A: Resource](x: =>A) = new SimpleArm(x)\n\n")
    for(i <- 1 to 22) f.write(genUsing(i))
    f.write("}\n")
  } finally {
    f.close()
  }
  Seq(outfile)
}

