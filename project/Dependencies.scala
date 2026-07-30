import sbt._

object Dependencies {

  /** Framework de tests unitaires. */
  lazy val munit = "org.scalameta" %% "munit" % "0.7.29"

  /**
   * Sérialisation JSON — non utilisée pour l'instant, la couche d'export ayant
   * été retirée. À rebrancher dans `build.sbt` le jour où l'export JSON revient.
   */
  lazy val upickle = "com.lihaoyi" %% "upickle" % "3.3.1"
}
