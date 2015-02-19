import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact

mimaDefaultSettings

name := "simple-arm-v2"

version := "2.1.0"

organization := "com.rojoma"

scalaVersion := "2.11.2"

crossScalaVersions := Seq("2.10.4", scalaVersion.value)

previousArtifact := Some("com.rojoma" % ("simple-arm-v2_" + scalaBinaryVersion.value) % "2.0.0")

scalacOptions ++= Seq("-feature", "-deprecation")

libraryDependencies ++= Seq(
  "com.rojoma" %% "simple-arm" % "1.2.0" % "optional",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "org.scalacheck" %% "scalacheck" % "1.11.4" % "test"
)
