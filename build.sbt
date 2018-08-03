name := "Text2GeoLocation"

version := "0.1"

scalaVersion := "2.12.6"

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