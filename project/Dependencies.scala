import sbt._

object Dependencies {

  /** Framework de tests unitaires. */
  lazy val munit = "org.scalameta" %% "munit" % "0.7.29"

  /** Sérialisation JSON — utilisée par la couche d'export. */
  lazy val upickle = "com.lihaoyi" %% "upickle" % "3.3.1"
}
