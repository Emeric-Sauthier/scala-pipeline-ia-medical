import sbt._

object Dependencies {

  /** Sérialisation JSON — utilisée uniquement par la couche storage. */
  lazy val upickle = "com.lihaoyi" %% "upickle" % "3.3.1"

  /** Framework de tests unitaires. */
  lazy val munit = "org.scalameta" %% "munit" % "0.7.29"
}
