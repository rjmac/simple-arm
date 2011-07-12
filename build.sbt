name := "simple-arm"

version := "1.0.0"

organization := "com.rojoma"

crossScalaVersions := Seq("2.8.1", "2.9.0", "2.9.0-1")

libraryDependencies <++= scalaVersion { sv =>
  sv match {
    case "2.8.1" => Seq(
      "org.scala-tools.testing" % "scalacheck_2.8.1" % "1.8" % "optional",
      "org.scalatest" % "scalatest_2.8.1" % "1.5.1" % "test"
    )
    case "2.9.0" | "2.9.0-1" => Seq(
      "org.scala-tools.testing" % "scalacheck_2.9.0" % "1.9" % "optional",
      "org.scalatest" % "scalatest_2.9.0" % "1.6.1" % "test"
    )
    case _ => error("Dependencies not set for scala version " + sv)
  }
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
    f.write("object util {\n")
    f.write("def managed[A: Resource](x: =>A) = new SimpleArm(x)\n\n")
    for(i <- 1 to 22) f.write(genUsing(i))
    f.write("}\n")
  } finally {
    f.close()
  }
  Seq(outfile)
}

publishTo <<= (version) { version =>
  val suffix = if(version.trim.endsWith("SNAPSHOT")) "snapshots" else "releases"
  Some("Sonatype Nexus Repository Manager" at ("http://maven.rojoma.com/content/repositories/" + suffix + "/"))
}

credentials <+= baseDirectory map { root => Credentials(root / "nexus.credentials") }
