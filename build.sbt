import Dependencies._

ThisBuild / scalaVersion     := "3.3.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "scala-pipeline-ia-medical",
    libraryDependencies ++= Seq(
      upickle,
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),
    // UTF-8 explicite : les sources contiennent des accents et « °C ».
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-encoding", "utf-8")
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
