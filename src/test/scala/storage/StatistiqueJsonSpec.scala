package storageTest

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.scalatest.funsuite.AnyFunSuite

import domain.*
import storage.EcrivainJson

/**
 * Sérialisation des statistiques.
 *
 * Le point sensible est le traitement des deux `Map` de `ResumeStatistique` :
 * leurs clés sont des ADT, pas des `String`. Sans traitement explicite, upickle
 * les exporterait en tableaux de paires, inexploitables par un tableau de bord.
 */
class StatistiqueJsonSpec extends AnyFunSuite:

    private val resume = ResumeStatistique(
        nombreMesures = 4,
        statistiquesMesure = Map(
            // Volontairement dans le désordre et incomplet : on vérifie plus bas
            // que la sortie est réordonnée et que les paramètres absents le restent.
            ParametreMesure.Spo2 -> StatistiqueMesure(4, 87.5, 87.5, 1.12, 86.0, 89.0),
            ParametreMesure.FrequenceCardiaque -> StatistiqueMesure(4, 115.75, 116.5, 3.83, 110.0, 120.0)
        ),
        // Seul « Alerte » est renseigné : les deux autres niveaux doivent quand
        // même apparaître, à 0.
        distributionVigilances = Map(IndicateurVigilance.Alerte -> 4)
    )

    private val statistique = Statistique(
        statistiquesGlobales = resume,
        statistiquesPatients = List(StatistiquePatient("P005", List("Reanimation"), resume)),
        statistiquesServices = List(StatistiqueService("Reanimation", 1, resume))
    )

    private val arbre: ujson.Value = ujson.read(EcrivainJson.encoder(statistique))

    private def dansUnDossierTemporaire(verifier: Path => Unit): Unit =
        val dossier = Files.createTempDirectory("stats-test")
        try verifier(dossier)
        finally
            Files.walk(dossier)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(fichier => Files.delete(fichier))

    // --- Structure générale -----------------------------------------------------

    test("le JSON produit est valide") {
        assert(EcrivainJson.valider(EcrivainJson.encoder(statistique)).isRight)
    }

    test("les trois sections attendues sont présentes à la racine") {
        assert(arbre.obj.keys.toSet == Set("statistiques_globales", "statistiques_patients", "statistiques_services"))
    }

    test("les clés sont en snake_case") {
        val json = EcrivainJson.encoder(statistique)
        assert(json.contains("\"nombre_mesures\""), json)
        assert(json.contains("\"patient_id\""), json)
        assert(json.contains("\"nombre_patients\""), json)
        assert(!json.contains("\"nombreMesures\""), "une clé camelCase a fuité")
    }

    // --- Le point sensible : les Map à clés ADT ---------------------------------

    test("les statistiques par paramètre forment un objet JSON, pas un tableau de paires") {
        val statistiquesMesure = arbre("statistiques_globales")("statistiques_mesure")
        assert(statistiquesMesure.objOpt.isDefined, s"attendu un objet, obtenu : $statistiquesMesure")
        assert(statistiquesMesure.arrOpt.isEmpty, "upickle a produit un tableau de paires")
    }

    test("les paramètres sont indexés par leur nom métier") {
        val cles = arbre("statistiques_globales")("statistiques_mesure").obj.keys.toSet
        assert(cles == Set("frequence_cardiaque", "spo2"), cles)
    }

    test("un paramètre jamais mesuré est absent, pas exporté à zéro") {
        val cles = arbre("statistiques_globales")("statistiques_mesure").obj.keys.toSet
        assert(!cles.contains("temperature"), "un paramètre non mesuré ne doit pas apparaître")
    }

    test("l'ordre des paramètres est déterministe, pas celui de la table de hachage") {
        // Ordre de ParametreMesure.parametres : fréquence cardiaque avant SpO2,
        // alors que la Map les déclare dans l'ordre inverse.
        val cles = arbre("statistiques_globales")("statistiques_mesure").obj.keys.toList
        assert(cles == List("frequence_cardiaque", "spo2"), cles)
    }

    test("les valeurs statistiques d'un paramètre sont complètes") {
        val spo2 = arbre("statistiques_globales")("statistiques_mesure")("spo2")
        assert(spo2.obj.keys.toSet == Set("effectif", "moyenne", "mediane", "ecart_type", "minimum", "maximum"))
        assert(spo2("effectif").num == 4.0)
        assert(spo2("moyenne").num == 87.5)
        assert(spo2("minimum").num == 86.0)
    }

    test("la distribution de vigilance forme un objet JSON indexé par niveau") {
        val distribution = arbre("statistiques_globales")("distribution_vigilances")
        assert(distribution.objOpt.isDefined, s"attendu un objet, obtenu : $distribution")
        assert(distribution.obj.keys.toList == List("Normal", "Surveillance", "Alerte"))
    }

    test("les niveaux de vigilance absents sont exportés à zéro, jamais omis") {
        val distribution = arbre("statistiques_globales")("distribution_vigilances")
        assert(distribution("Normal").num == 0.0)
        assert(distribution("Surveillance").num == 0.0)
        assert(distribution("Alerte").num == 4.0)
    }

    // --- Patients et services ---------------------------------------------------

    test("un patient porte son identifiant, ses services et son résumé") {
        val patient = arbre("statistiques_patients")(0)
        assert(patient("patient_id").str == "P005")
        assert(patient("services").arr.map(_.str).toList == List("Reanimation"))
        assert(patient("statistiques")("nombre_mesures").num == 4.0)
    }

    test("un service porte son nom, son effectif de patients et son résumé") {
        val service = arbre("statistiques_services")(0)
        assert(service("service").str == "Reanimation")
        assert(service("nombre_patients").num == 1.0)
        assert(service("statistiques")("distribution_vigilances")("Alerte").num == 4.0)
    }

    // --- Export sur disque ------------------------------------------------------

    test("l'export complet écrit un fichier relisible") {
        dansUnDossierTemporaire { dossier =>
            val cible = dossier.resolve("out/statistiques.json")
            assert(EcrivainJson.exporter(cible.toString, statistique).isRight)

            val relu = String(Files.readAllBytes(cible), StandardCharsets.UTF_8)
            assert(EcrivainJson.valider(relu).isRight, "le JSON relu depuis le disque doit être valide")
            assert(ujson.read(relu)("statistiques_globales")("nombre_mesures").num == 4.0)
        }
    }

    test("l'encodage est déterministe : deux appels donnent le même texte") {
        assert(EcrivainJson.encoder(statistique) == EcrivainJson.encoder(statistique))
    }

end StatistiqueJsonSpec
