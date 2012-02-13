name := "simple-arm"

version := "1.1.8"

organization := "com.rojoma"

crossScalaVersions := Seq("2.8.1", "2.8.2", "2.9.0", "2.9.0-1", "2.9.1")

// This exists because of scala bug SI-4782 (see SBT issue 85).  If the bug is
// fixed, or this project ever grows a compile-scope dependency, it can be
// removed.
unmanagedClasspath in Compile <+= (baseDirectory) map { root => Attributed.blank(root / "does-not-exist") }

libraryDependencies += "org.scalatest" %% "scalatest" % "1.7.1" % "test"
