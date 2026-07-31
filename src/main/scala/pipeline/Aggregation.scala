package pipeline

import domain.Statistique
import domain.StatistiqueMesure
import domain.StatistiquePatient
import domain.StatistiqueService
import domain.MesureTransformee
import domain.ResumeStatistique
import domain.ParametreMesure
import domain.IndicateurVigilance
import domain.IndicateurVigilance._

object Aggregation {

    private final case class Accumulateur(
        effectif: Int,
        somme: Double,
        sommeCarres: Double,
        minimum: Double,
        maximum: Double
    ) {
        def ajouter(valeur: Double): Accumulateur = Accumulateur(
            effectif    = effectif + 1,
            somme       = somme + valeur,
            sommeCarres = sommeCarres + valeur * valeur,
            minimum     = Math.min(minimum, valeur),
            maximum     = Math.max(maximum, valeur)
        )
    }

    private object Accumulateur {
        val accumulateurVide: Accumulateur = Accumulateur(0, 0.0, 0.0, Double.PositiveInfinity, Double.NegativeInfinity)
    }
    
    private def medianeTriee(valeursTriees: Vector[Double]): Double = {
        val taille = valeursTriees.size
        val milieu = taille / 2

        if (taille % 2 == 1) { 
            valeursTriees(milieu) 
        } else {
            (valeursTriees(milieu - 1) + valeursTriees(milieu)) / 2.0
        }
    }

    def mediane(valeurs: List[Double]): Option[Double] = Option.when(valeurs.nonEmpty)(medianeTriee(valeurs.sorted.toVector))

    def calculerStatistiquesMesure(valeurs: List[Double]): Option[StatistiqueMesure] = {
        val accumulateur = valeurs.foldLeft(Accumulateur.accumulateurVide)((accumulateur, valeur) => accumulateur.ajouter(valeur))

        Option.when(accumulateur.effectif > 0) {
            val moyenne = accumulateur.somme / accumulateur.effectif
            val variance = accumulateur.sommeCarres / accumulateur.effectif - moyenne * moyenne
            val mediane = medianeTriee(valeurs.sorted.toVector)
            val ecartType = Math.sqrt(Math.max(variance, 0.0))

            StatistiqueMesure(
                accumulateur.effectif,
                Transformation.arrondir(moyenne, 2),
                Transformation.arrondir(mediane, 2),
                Transformation.arrondir(ecartType, 2),
                accumulateur.minimum,
                accumulateur.maximum
            )
        }
    }

    def calculerDistributionVigilance(indicateurs: List[IndicateurVigilance]): Map[IndicateurVigilance, Int] = {
        val distributionVide: Map[IndicateurVigilance, Int] = Map(Normal -> 0, Surveillance -> 0, Alerte -> 0)

        indicateurs.foldLeft(distributionVide)((distribution, indicateur) => distribution.updatedWith(indicateur)(valeur => Some(valeur.getOrElse(0) + 1)))
    }

    def calculerStatistiquesParametres(mesures: List[MesureTransformee]): Map[ParametreMesure, StatistiqueMesure] = {
        ParametreMesure.parametres.foldLeft(Map.empty[ParametreMesure, StatistiqueMesure])(
            (statistiques, parametre) => 
                calculerStatistiquesMesure(mesures.flatMap(parametre.extraireValeur)) match {
                    case Some(valeur) => statistiques.updated(parametre, valeur)
                    case None => statistiques
                }
        )
    }

    def calculerResumeStatistique(mesures: List[MesureTransformee]): ResumeStatistique = {
        val statistiquesMesure: Map[ParametreMesure, StatistiqueMesure] = calculerStatistiquesParametres(mesures)
        val distributionVigilances: Map[IndicateurVigilance, Int] = calculerDistributionVigilance(mesures.flatMap(_.vigilanceGlobale))

        ResumeStatistique(mesures.size, statistiquesMesure, distributionVigilances)
    }

    def calculerStatistiquesParPatient(mesures: List[MesureTransformee]): List[StatistiquePatient] = {
        mesures
            .groupBy(_.mesure.patientId)
            .toList
            .sortBy(_._1)
            .map {
                case (patientId, mesuresPatient) => {
                    val servicesPatient: List[String] = mesuresPatient.map(_.mesure.service).distinct.sorted
                    val statistiques: ResumeStatistique = calculerResumeStatistique(mesuresPatient)

                    StatistiquePatient(patientId, servicesPatient, statistiques)
                }
            }
    }

    def calculerStatistiquesParService(mesures: List[MesureTransformee]): List[StatistiqueService] = {
        mesures
            .groupBy(_.mesure.service)
            .toList
            .sortBy(_._1)
            .map {
                case (service, mesuresService) => {
                    val nombrePatients = mesuresService.map(_.mesure.patientId).distinct.size
                    val statistiques: ResumeStatistique = calculerResumeStatistique(mesuresService)

                    StatistiqueService(service, nombrePatients, statistiques)
                }
            }
    }

    def aggreger(mesures: List[MesureTransformee]): Statistique = {
        val statistiquesGlobales: ResumeStatistique = calculerResumeStatistique(mesures)
        val statistiquesPatients: List[StatistiquePatient] = calculerStatistiquesParPatient(mesures)
        val statistiquesServices: List[StatistiqueService] = calculerStatistiquesParService(mesures)

        Statistique(statistiquesGlobales, statistiquesPatients, statistiquesServices)
    }
}
