name := "simple-arm"

version := "1.1.5"

organization := "com.rojoma"

crossScalaVersions := Seq("2.8.1", "2.9.0", "2.9.0-1", "2.9.1")

// This exists because of scala bug SI-4782 (see SBT issue 85).  If the bug is
// fixed, or this project ever grows a compile-scope dependency, it can be
// removed.
unmanagedClasspath in Compile <+= (baseDirectory) map { root => Attributed.blank(root / "does-not-exist") }

libraryDependencies <++= scalaVersion { sv =>
  sv match {
    case "2.8.1" => Seq(
      "org.scalatest" % "scalatest_2.8.1" % "1.5.1" % "test"
    )
    case "2.9.0" | "2.9.0-1" | "2.9.1" => Seq(
      "org.scalatest" % "scalatest_2.9.0" % "1.6.1" % "test"
    )
    case _ => error("Dependencies not set for scala version " + sv)
  }
}
