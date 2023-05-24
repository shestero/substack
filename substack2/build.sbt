ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.2"

lazy val root = (project in file("."))
  .settings(
    name := "substack2"
  )

fork := true
run / connectInput := true // to use stdin from "sbt run"

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.1.0"
libraryDependencies += "dev.zio" %% "zio" % "2.0.13"
libraryDependencies += "dev.zio" %% "zio-streams" % "2.0.13"


