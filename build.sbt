name := "simple-arm-v2"

version := "2.3.1"

organization := "com.rojoma"

scalaVersion := "2.13.0"

// crossScalaVersions := Seq("2.10.4", "2.11.2", "2.12.0", scalaVersion.value)

// mimaPreviousArtifacts := Set("com.rojoma" % ("simple-arm-v2_" + scalaBinaryVersion.value) % "2.2.0")

scalacOptions ++= Seq("-feature", "-deprecation", "-Xlint", "-Xlint:-nonlocal-return")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
)
