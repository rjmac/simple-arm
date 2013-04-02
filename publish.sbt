publishTo <<= version { (v: String) =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

// Random stuff sonatype wants
pomExtra := (
  <url>http://www.github.com/rjmac/simple-arm</url>
  <licenses>
    <!-- I do not accept that I have the right to dictate terms of use -->
    <license>
      <name>Public domain</name>
    </license>
    <license>
      <!-- Resource.scala is derived from scala-arm -->
      <name>BSD-like</name>
      <url>http://www.scala-lang.org/downloads/license.html</url>
    </license>
  </licenses>
  <scm>
    <url>git://github.com/rjmac/simple-arm.git</url>
    <connection>scm:git://github.com/rjmac/simple-arm.git</connection>
  </scm>
  <developers>
    <developer>
      <id>robertm</id>
      <name>Robert Macomber</name>
    </developer>
  </developers>
)

// Random stuff sonatype does not want
pomIncludeRepository := { _ => false }

// These are the defaults, but set them manually just in case

publishMavenStyle := true

publishArtifact in Test := false
