sealed trait IndicateurVigilance

object IndicateurVigilance {
    case object Normal extends IndicateurVigilance
    case object Surveillance extends IndicateurVigilance
    case object Alerte extends IndicateurVigilance
}