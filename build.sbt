name := "Text2GeoLocation"

version := "0.1"

scalaVersion := "2.12.6"

dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % "2.8.4",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.4",
)

// country codes
libraryDependencies += "com.vitorsvieira" %% "scala-iso" % "0.1.2"

// SPARQL queries
// https://mvnrepository.com/artifact/org.apache.jena/jena-arq
libraryDependencies += "org.apache.jena" % "jena-arq" % "3.6.0"

// Scalatests
// https://mvnrepository.com/artifact/org.scalatest/scalatest
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.5"
logBuffered in Test := false

// REST api
// REST api
// https://mvnrepository.com/artifact/com.github.finagle/finch-core
libraryDependencies += "com.github.finagle" %% "finch-core" % "0.22.0"
// https://mvnrepository.com/artifact/com.github.finagle/finch-circe
libraryDependencies += "com.github.finagle" %% "finch-circe" % "0.22.0"
// https://mvnrepository.com/artifact/io.circe/circe-generic-extras
libraryDependencies += "io.circe" %% "circe-generic-extras" % "0.10.0-M1"
