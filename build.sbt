name := "simple-arm"

version := "1.1.9"

organization := "com.rojoma"

scalaVersion := "2.9.2"

crossScalaVersions := Seq("2.8.1", "2.8.2", "2.9.0", "2.9.0-1", "2.9.1", "2.9.1-1", "2.9.2")

// This exists because of scala bug SI-4782 (see SBT issue 85).  If the bug is
// fixed, or this project ever grows a compile-scope dependency, it can be
// removed.
unmanagedClasspath in Compile <+= (baseDirectory) map { root => Attributed.blank(root / "does-not-exist") }

libraryDependencies <+= scalaVersion { sv =>
  sv match {
    case "2.9.1-1" => "org.scalatest" % "scalatest_2.9.1" % "1.7.1" % "test"
    case _ => "org.scalatest" %% "scalatest" % "1.7.1" % "test"
  }
}
