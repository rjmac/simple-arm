name := "simple-arm-v2"

version := "2.3.3-SNAPSHOT"

organization := "com.rojoma"

scalaVersion := "2.12.17"

crossScalaVersions := Seq("2.10.4", "2.11.2", scalaVersion.value)

mimaPreviousArtifacts := Set("com.rojoma" %% "simple-arm-v2" % "2.3.2")

scalacOptions ++= {
  val base = Seq("-feature", "-deprecation", "-Xlint")
  val extra =
    scalaBinaryVersion.value match {
      case "2.10" | "2.11" => Nil
      case "2.12" => Seq("-opt:l:inline", "-opt-inline-from:com.rojoma.simplearm.v2.**")
      case other => throw new Exception("Unknown scala version " + other)
    }
  base ++ extra
}

libraryDependencies ++= Seq(
  "com.rojoma" %% "simple-arm" % "1.2.0" % "optional",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
)
