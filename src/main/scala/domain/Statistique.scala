package domain

final case class Statistique(
    statistiquesGlobales: ResumeStatistique,
    statistiquesPatients: List[StatistiquePatient],
    statistiquesServices: List[StatistiqueService]
)

final case class StatistiquePatient(
    patientId: String,
    services: List[String],
    statistiques: ResumeStatistique
)

final case class StatistiqueService(
    service: String,
    nombrePatients: Int,
    statistiques: ResumeStatistique
)

final case class ResumeStatistique(
    nombreMesures: Int,
    statistiquesMesure: Map[ParametreMesure, StatistiqueMesure],
    distributionVigilances: Map[IndicateurVigilance, Int]
)

final case class StatistiqueMesure(
    effectif: Int,
    moyenne: Double,
    mediane: Double,
    ecartType: Double,
    minimum: Double,
    maximum: Double
)

sealed trait ParametreMesure {
    def nom: String

    def extraireValeur(mesureTransformee: MesureTransformee): Option[Double]
}

object ParametreMesure {

    val parametres: List[ParametreMesure] = List(FrequenceCardiaque, TensionSystolique, TensionDiastolique, Temperature, Spo2, PressionPulsee, PressionArterielleMoyenne)

    case object FrequenceCardiaque extends ParametreMesure {
        val nom = "frequence_cardiaque"
        def extraireValeur(mesureTransformee: MesureTransformee): Option[Double] = mesureTransformee.mesure.frequenceCardiaque.map(_.toDouble)
    }

    case object TensionSystolique extends ParametreMesure {
        val nom = "tension_systolique"
        def extraireValeur(mesureTransformee: MesureTransformee): Option[Double] = mesureTransformee.mesure.tensionSystolique.map(_.toDouble)
    }

    case object TensionDiastolique extends ParametreMesure {
        val nom = "tension_diastolique"
        def extraireValeur(mesureTransformee: MesureTransformee): Option[Double] = mesureTransformee.mesure.tensionDiastolique.map(_.toDouble)
    }

    case object Temperature extends ParametreMesure {
        val nom = "temperature"
        def extraireValeur(mesureTransformee: MesureTransformee): Option[Double] = mesureTransformee.mesure.temperature
    }

    case object Spo2 extends ParametreMesure {
        val nom = "spo2"
        def extraireValeur(mesureTransformee: MesureTransformee): Option[Double] = mesureTransformee.mesure.spo2.map(_.toDouble)
    }

    case object PressionPulsee extends ParametreMesure {
        val nom = "pression_pulsee"
        def extraireValeur(mesureTransformee: MesureTransformee): Option[Double] = mesureTransformee.pressionPulsee.map(_.toDouble)
    }

    case object PressionArterielleMoyenne extends ParametreMesure {
        val nom = "pression_arterielle_moyenne"
        def extraireValeur(mesureTransformee: MesureTransformee): Option[Double] = mesureTransformee.pressionArterielleMoyenne
    }
}