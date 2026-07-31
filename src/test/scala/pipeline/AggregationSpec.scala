package pipelineTest

import org.scalatest.funsuite.AnyFunSuite
import java.time.LocalDateTime
import domain.IndicateurVigilance
import domain.IndicateurVigilance._
import domain.Mesure
import domain.MesureTransformee
import domain.ParametreMesure
import pipeline.Aggregation
import pipeline.Transformation

class AggregationSpec extends AnyFunSuite:

    val timestamp = LocalDateTime.parse("2026-07-28T08:00:00")

    // Les jeux nommes passent par la factory et non par .copy : MesureTransformee ne porte
    // pas les champs capteurs, et un .copy ne recalculerait pas les indicateurs derives.
    val mesureVide = mesure()
    val mesureNormale = mesure(
        Some(78),
        Some(128),
        Some(82),
        Some(37.1),
        Some(97)
    )
    val mesureAlerte = mesure(Some(78), Some(128), Some(82), Some(37.1), Some(89))
    val mesureSurveillance = mesure(Some(78), Some(128), Some(82), Some(37.1), Some(94))

    // P001 deux releves en Chirurgie A dont un en alerte (SpO2 89), P002 un releve normal
    // en Chirurgie A, P003 un releve en alerte en Reanimation (SpO2 88).
    val listeMesures = List(
        mesure(Some(60), Some(120), Some(80), Some(37.0), Some(97), "P001", "Chirurgie A"),
        mesure(Some(90), Some(130), Some(85), Some(37.4), Some(89), "P001", "Chirurgie A"),
        mesure(Some(70), Some(125), Some(82), Some(37.2), Some(96), "P002", "Chirurgie A"),
        mesure(Some(80), Some(128), Some(84), Some(37.3), Some(88), "P003", "Reanimation")
    )

    val distributionVide: Map[IndicateurVigilance, Int] =
        Map(Normal -> 0, Surveillance -> 0, Alerte -> 0)

    /** Comparaison des Double non arrondis : proportions brutes uniquement. */
    def proche(obtenu: Double, attendu: Double): Boolean = Math.abs(obtenu - attendu) < 1e-9


    def mesure(
        frequenceCardiaque: Option[Int] = None,
        tensionSystolique: Option[Int] = None,
        tensionDiastolique: Option[Int] = None,
        temperature: Option[Double] = None,
        spo2: Option[Int] = None,
        patientId: String = "P001",
        service: String = "Chirurgie A"
    ): MesureTransformee =
        Transformation.transformerMesure(
            Mesure(
                patientId          = patientId,
                timestamp          = timestamp,
                service            = service,
                age                = 67,
                frequenceCardiaque = frequenceCardiaque,
                tensionSystolique  = tensionSystolique,
                tensionDiastolique = tensionDiastolique,
                temperature        = temperature,
                spo2               = spo2
            )
        )

    // Test - Mediane

    test("Mediane - liste vide") {
        assert(Aggregation.mediane(Nil).isEmpty)
    }

    test("Mediane - effectif impair sur une serie non triee") {
        assert(Aggregation.mediane(List(5.0, 1.0, 3.0)) == Some(3.0))
    }

    test("Mediane - effectif pair : moyenne des deux valeurs centrales") {
        assert(Aggregation.mediane(List(1.0, 2.0, 3.0, 4.0)) == Some(2.5))
    }

    // Test - Statistiques d'une serie numerique

    test("Statistiques mesure - serie vide") {
        assert(Aggregation.calculerStatistiquesMesure(Nil).isEmpty)
    }

    test("Statistiques mesure - effectif pair") {
        // Somme 300, moyenne 75 ; somme des carres 23000 ; variance 23000/4 - 75² = 125 ; racine 11.18034
        val statistiques = Aggregation.calculerStatistiquesMesure(List(60.0, 70.0, 80.0, 90.0)).get
        assert(statistiques.effectif == 4)
        assert(statistiques.moyenne == 75.0)
        assert(statistiques.mediane == 75.0) // (70 + 80) / 2
        assert(statistiques.ecartType == 11.18)
        assert(statistiques.minimum == 60.0)
        assert(statistiques.maximum == 90.0)
    }

    test("Statistiques mesure - effectif impair") {
        // Somme 220, moyenne 73.3333 ; variance 16600/3 - 73.3333² = 155.5556 ; racine 12.47219
        val statistiques = Aggregation.calculerStatistiquesMesure(List(60.0, 70.0, 90.0)).get
        assert(statistiques.effectif == 3)
        assert(statistiques.moyenne == 73.33)
        assert(statistiques.mediane == 70.0)
        assert(statistiques.ecartType == 12.47)
    }

    test("Statistiques mesure - une seule valeur") {
        // Ecart-type de population : defini a n = 1, contrairement a la correction de Bessel
        val statistiques = Aggregation.calculerStatistiquesMesure(List(78.0)).get
        assert(statistiques.effectif == 1)
        assert(statistiques.moyenne == 78.0)
        assert(statistiques.mediane == 78.0)
        assert(statistiques.minimum == 78.0)
        assert(statistiques.maximum == 78.0)
        assert(statistiques.ecartType == 0.0)
    }

    test("Statistiques mesure - valeurs identiques : pas de NaN") {
        // Sans la garde Math.max(variance, 0.0), l'annulation peut donner sqrt(-1e-16) = NaN
        val statistiques = Aggregation.calculerStatistiquesMesure(List(78.0, 78.0, 78.0)).get
        assert(!statistiques.ecartType.isNaN)
        assert(statistiques.ecartType == 0.0)
    }

    test("Statistiques mesure - pas de division entiere") {
        // Somme des carres 7321 ; variance 7321/2 - 60.5² = 0.25 ; racine 0.5
        val statistiques = Aggregation.calculerStatistiquesMesure(List(60.0, 61.0)).get
        assert(statistiques.moyenne == 60.5)
        assert(statistiques.mediane == 60.5)
        assert(statistiques.ecartType == 0.5)
    }

    test("Statistiques mesure - valeurs decimales") {
        // Somme des carres 5630 ; variance 5630/4 - 37.5² = 1.25 ; racine 1.118034
        val statistiques = Aggregation.calculerStatistiquesMesure(List(36.0, 37.0, 38.0, 39.0)).get
        assert(statistiques.moyenne == 37.5)
        assert(statistiques.mediane == 37.5)
        assert(statistiques.ecartType == 1.12)
    }

    test("Statistiques mesure - l'ordre d'entree est indifferent") {
        val croissant = Aggregation.calculerStatistiquesMesure(List(60.0, 70.0, 80.0, 90.0))
        val melange   = Aggregation.calculerStatistiquesMesure(List(90.0, 60.0, 80.0, 70.0))
        assert(croissant == melange)
    }

    // Test - Distribution des vigilances

    test("Distribution - liste vide : les trois categories sont presentes a zero") {
        assert(Aggregation.calculerDistributionVigilance(Nil) == distributionVide)
    }

    test("Distribution - lot mixte") {
        val distribution = Aggregation.calculerDistributionVigilance(List(Normal, Surveillance, Surveillance, Alerte))
        assert(distribution(Normal) == 1)
        assert(distribution(Surveillance) == 2)
        assert(distribution(Alerte) == 1)
    }

    test("Distribution - une seule categorie observee : les autres restent a zero") {
        // La graine garantit les trois cles, donc distribution(Normal) ne leve pas
        val distribution = Aggregation.calculerDistributionVigilance(List(Alerte, Alerte))
        assert(distribution.keySet.size == 3)
        assert(distribution(Normal) == 0)
        assert(distribution(Alerte) == 2)
    }

    // Test - Statistiques des sept parametres

    test("Statistiques parametres - lot vide") {
        assert(Aggregation.calculerStatistiquesParametres(Nil).isEmpty)
    }

    test("Statistiques parametres - mesure complete : les sept parametres sont resumes") {
        assert(Aggregation.calculerStatistiquesParametres(List(mesureNormale)).size == 7)
    }

    test("Statistiques parametres - parametre jamais renseigne : cle absente de la map") {
        val statistiques = Aggregation.calculerStatistiquesParametres(List(mesure(spo2 = Some(97))))
        assert(statistiques.size == 1)
        assert(statistiques.get(ParametreMesure.Temperature).isEmpty)
        assert(statistiques.get(ParametreMesure.Spo2).map(_.effectif) == Some(1))
    }

    test("Statistiques parametres - l'effectif compte les valeurs, pas les mesures") {
        val mesures      = List(mesureNormale, mesure(spo2 = Some(95)))
        val statistiques = Aggregation.calculerStatistiquesParametres(mesures)
        assert(statistiques.get(ParametreMesure.Spo2).map(_.effectif) == Some(2))
        assert(statistiques.get(ParametreMesure.Spo2).map(_.moyenne) == Some(96.0)) // (97 + 95) / 2
        assert(statistiques.get(ParametreMesure.FrequenceCardiaque).map(_.effectif) == Some(1))
    }

    test("Statistiques parametres - tension incomplete : derives absents, tension presente") {
        val statistiques = Aggregation.calculerStatistiquesParametres(List(mesure(tensionSystolique = Some(120))))
        assert(statistiques.get(ParametreMesure.PressionPulsee).isEmpty)
        assert(statistiques.get(ParametreMesure.PressionArterielleMoyenne).isEmpty)
        assert(statistiques.get(ParametreMesure.TensionSystolique).map(_.moyenne) == Some(120.0))
    }

    // Test - Resume d'un groupe de mesures

    test("Resume - lot vide") {
        val resume = Aggregation.calculerResumeStatistique(Nil)
        assert(resume.nombreMesures == 0)
        assert(resume.statistiquesMesure.isEmpty)
        assert(resume.distributionVigilances == distributionVide)
    }

    test("Resume - nombreMesures compte les mesures, pas les valeurs") {
        val resume = Aggregation.calculerResumeStatistique(List(mesureNormale, mesure(spo2 = Some(95))))
        assert(resume.nombreMesures == 2)
        assert(resume.statistiquesMesure.size == 7)
    }

    test("Resume - distribution des vigilances globales") {
        val mesures = List(mesureNormale, mesureSurveillance, mesureAlerte, mesureAlerte)
        val resume  = Aggregation.calculerResumeStatistique(mesures)
        assert(resume.distributionVigilances(Normal) == 1)
        assert(resume.distributionVigilances(Surveillance) == 1)
        assert(resume.distributionVigilances(Alerte) == 2)
    }

    test("Resume - mesure vide comptee mais non classee") {
        // L'ecart entre nombreMesures et la somme de la distribution mesure la qualite de collecte
        val resume = Aggregation.calculerResumeStatistique(List(mesureNormale, mesureVide))
        assert(resume.nombreMesures == 2)
        assert(resume.distributionVigilances.values.sum == 1)
    }

    test("Resume - mesure vide n'interrompt pas le lot") {
        val resume = Aggregation.calculerResumeStatistique(List(mesureNormale, mesureVide, mesureAlerte))
        assert(resume.statistiquesMesure.size == 7)
        assert(resume.statistiquesMesure(ParametreMesure.Spo2).effectif == 2)
    }

    // Test - Statistiques par patient

    test("Par patient - lot vide") {
        assert(Aggregation.calculerStatistiquesParPatient(Nil).isEmpty)
    }

    test("Par patient - un groupe par patient") {
        assert(Aggregation.calculerStatistiquesParPatient(listeMesures).size == 3)
    }

    test("Par patient - ordre deterministe malgre une entree melangee") {
        // groupBy rend une Map d'ordre non specifie : le tri explicite est indispensable
        val identifiants = Aggregation.calculerStatistiquesParPatient(listeMesures.reverse).map(_.patientId)
        assert(identifiants == List("P001", "P002", "P003"))
    }

    test("Par patient - agregation des releves du patient") {
        // P001 : frequences cardiaques 60 et 90 ; variance 11700/2 - 75² = 225 ; racine 15
        val patient      = Aggregation.calculerStatistiquesParPatient(listeMesures).head
        val statistiques = patient.statistiques.statistiquesMesure(ParametreMesure.FrequenceCardiaque)
        assert(patient.patientId == "P001")
        assert(patient.statistiques.nombreMesures == 2)
        assert(statistiques.effectif == 2)
        assert(statistiques.moyenne == 75.0)
        assert(statistiques.mediane == 75.0)
        assert(statistiques.ecartType == 15.0)
        assert(statistiques.minimum == 60.0)
        assert(statistiques.maximum == 90.0)
    }

    test("Par patient - un seul service") {
        assert(Aggregation.calculerStatistiquesParPatient(listeMesures).head.services == List("Chirurgie A"))
    }

    test("Par patient - patient transfere : un seul groupe, services distincts et tries") {
        val transfert = List(
            mesure(spo2 = Some(97), patientId = "P001", service = "Reanimation"),
            mesure(spo2 = Some(96), patientId = "P001", service = "Chirurgie A"),
            mesure(spo2 = Some(95), patientId = "P001", service = "Reanimation")
        )
        val patients = Aggregation.calculerStatistiquesParPatient(transfert)
        assert(patients.size == 1)
        assert(patients.head.services == List("Chirurgie A", "Reanimation"))
        assert(patients.head.statistiques.nombreMesures == 3)
    }

    // Test - Statistiques par service

    test("Par service - lot vide") {
        assert(Aggregation.calculerStatistiquesParService(Nil).isEmpty)
    }

    test("Par service - ordre deterministe malgre une entree melangee") {
        val services = Aggregation.calculerStatistiquesParService(listeMesures.reverse).map(_.service)
        assert(services == List("Chirurgie A", "Reanimation"))
    }

    test("Par service - les patients sont dedupliques") {
        val chirurgie = Aggregation.calculerStatistiquesParService(listeMesures).head
        assert(chirurgie.service == "Chirurgie A")
        assert(chirurgie.nombrePatients == 2) // P001 releve deux fois, P002 une fois
        assert(chirurgie.statistiques.nombreMesures == 3)
    }

    test("Par service - le compte d'alertes permet de deriver la proportion") {
        // Chirurgie A : 1 alerte sur 3 releves ; Reanimation : 1 sur 1
        val services    = Aggregation.calculerStatistiquesParService(listeMesures)
        val chirurgie   = services.head.statistiques
        val reanimation = services.last.statistiques
        assert(chirurgie.distributionVigilances(Alerte) == 1)
        assert(chirurgie.nombreMesures == 3)
        assert(proche(chirurgie.distributionVigilances(Alerte).toDouble / chirurgie.nombreMesures, 1.0 / 3))
        assert(reanimation.distributionVigilances(Alerte) == 1)
        assert(reanimation.nombreMesures == 1)
    }

    test("Par service - les groupes sont independants") {
        val reanimation  = Aggregation.calculerStatistiquesParService(listeMesures).last
        val statistiques = reanimation.statistiques.statistiquesMesure(ParametreMesure.FrequenceCardiaque)
        assert(reanimation.nombrePatients == 1)
        assert(statistiques.effectif == 1)
        assert(statistiques.moyenne == 80.0)
    }

    test("Par service - un service sans donnee exploitable") {
        val avecServiceMuet = List(
            mesure(spo2 = Some(89), patientId = "P001", service = "Chirurgie A"),
            mesure(patientId = "P002", service = "Reanimation")
        )
        val reanimation = Aggregation.calculerStatistiquesParService(avecServiceMuet).last
        assert(reanimation.statistiques.nombreMesures == 1)
        assert(reanimation.statistiques.statistiquesMesure.isEmpty)
        assert(reanimation.statistiques.distributionVigilances == distributionVide)
    }

    // Test - Rapport complet

    test("Aggreger - lot vide") {
        val statistique = Aggregation.aggreger(Nil)
        assert(statistique.statistiquesGlobales.nombreMesures == 0)
        assert(statistique.statistiquesPatients.isEmpty)
        assert(statistique.statistiquesServices.isEmpty)
    }

    test("Aggreger - les trois axes sont produits") {
        val statistique = Aggregation.aggreger(listeMesures)
        assert(statistique.statistiquesGlobales.nombreMesures == 4)
        assert(statistique.statistiquesPatients.size == 3)
        assert(statistique.statistiquesServices.size == 2)
    }

    test("Aggreger - statistiques globales sur l'ensemble du lot") {
        // Frequences cardiaques 60, 90, 70, 80 : moyenne 75 ; variance 23000/4 - 75² = 125 ; racine 11.18034
        val global       = Aggregation.aggreger(listeMesures).statistiquesGlobales
        val statistiques = global.statistiquesMesure(ParametreMesure.FrequenceCardiaque)
        assert(statistiques.effectif == 4)
        assert(statistiques.moyenne == 75.0)
        assert(statistiques.mediane == 75.0) // (70 + 80) / 2
        assert(statistiques.ecartType == 11.18)
        assert(global.distributionVigilances(Normal) == 2)
        assert(global.distributionVigilances(Alerte) == 2)
    }

    test("Aggreger - la somme des releves par service egale le total global") {
        val statistique = Aggregation.aggreger(listeMesures)
        val parService  = statistique.statistiquesServices.map(_.statistiques.nombreMesures).sum
        assert(parService == statistique.statistiquesGlobales.nombreMesures)
    }

end AggregationSpec