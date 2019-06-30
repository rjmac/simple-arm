name := "simple-arm-v2"

version := "2.3.0"

organization := "com.rojoma"

scalaVersion := "2.12.0"

crossScalaVersions := Seq("2.10.4", "2.11.2", scalaVersion.value)

mimaPreviousArtifacts := Set("com.rojoma" % ("simple-arm-v2_" + scalaBinaryVersion.value) % "2.2.0")

scalacOptions ++= Seq("-feature", "-deprecation")

libraryDependencies ++= Seq(
  "com.rojoma" %% "simple-arm" % "1.2.0" % "optional",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"
)
