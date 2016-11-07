import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact

mimaDefaultSettings

name := "simple-arm"

version := "1.2.0"

organization := "com.rojoma"

scalaVersion := "2.10.2"

crossScalaVersions := Seq("2.8.1", "2.8.2", "2.9.0", "2.9.0-1", "2.9.1", "2.9.1-1", "2.9.2", "2.10.2", "2.11.2", "2.12.0")

previousArtifact <<= scalaBinaryVersion { sv => Some("com.rojoma" % ("simple-arm_" + sv) % "1.1.10") }

// This exists because of scala bug SI-4782 (see SBT issue 85).  If the bug is
// fixed, or this project ever grows a compile-scope dependency, it can be
// removed.
unmanagedClasspath in Compile <+= (baseDirectory) map { root => Attributed.blank(root / "does-not-exist") }

scalacOptions <++= (scalaVersion) map {
  case "2.10.2" | "2.11.2" | "2.12.0" => Seq("-feature", "-language:reflectiveCalls")
  case _ => Nil
}

libraryDependencies <+= scalaVersion {
  case "2.12.0" => "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  case "2.11.2" => "org.scalatest" %% "scalatest" % "2.1.3" % "test"
  case "2.10.2" => "org.scalatest" %% "scalatest" % "1.9.1" % "test"
  case "2.9.1-1" => "org.scalatest" % "scalatest_2.9.1" % "1.8" % "test"
  case _ => "org.scalatest" %% "scalatest" % "1.8" % "test"
}
