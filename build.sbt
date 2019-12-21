name := "simple-arm-v2"

version := "2.3.3-SNAPSHOT"

organization := "com.rojoma"

scalaVersion := "2.13.1"

// crossScalaVersions := Seq("2.10.4", "2.11.2", "2.12.0", scalaVersion.value)

mimaPreviousArtifacts := Set("com.rojoma" %% "simple-arm-v2" % "2.3.2")

scalacOptions ++= Seq("-feature", "-deprecation", "-Xlint", "-Xlint:-nonlocal-return", "-opt:l:inline", "-opt-inline-from:com.rojoma.simplearm.v2.**")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
)
