ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10" // with "3.2.2" parallel collection hangs

lazy val root = (project in file("."))
  .settings(
    name := "substack1"
  )

fork := true
run / connectInput := true // to use stdin from "sbt run"

libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.1.0"

libraryDependencies ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, major)) if major <= 12 =>
      Seq()
    case _ =>
      Seq("org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4")
  }
}

