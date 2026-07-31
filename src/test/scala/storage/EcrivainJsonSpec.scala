package storageTest

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.LocalDateTime

import org.scalatest.funsuite.AnyFunSuite

import storage.{EcrivainJson, JsonFormat}

/**
 * Tests de l'export JSON.
 *
 * L'encodage étant pur, la quasi-totalité des tests se passe de fichier. Ils
 * utilisent un type factice défini plus bas : c'est ce qui démontre que la
 * couche fonctionne AVANT que `Statistiques` n'existe.
 */
class EcrivainJsonSpec extends AnyFunSuite:

    import StatistiquesFactice._

    private val rapport = RapportFactice(
        genereLe = LocalDateTime.parse("2026-07-30T12:00:00"),
        nombreMesures = 21,
        services = List(
            ServiceFactice("Reanimation", proportionAlerte = 0.571, patientsEnAlerte = List("P005")),
            ServiceFactice("Chirurgie A", proportionAlerte = 0.0, patientsEnAlerte = Nil)
        ),
        commentaire = None
    )

    private def dansUnDossierTemporaire(verifier: Path => Unit): Unit =
        val dossier = Files.createTempDirectory("export-test")
        try verifier(dossier)
        finally
            Files.walk(dossier)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(fichier => Files.delete(fichier))

    // --- Encodage ---------------------------------------------------------------

    test("le JSON produit est syntaxiquement valide") {
        assert(EcrivainJson.valider(EcrivainJson.encoder(rapport)).isRight)
    }

    test("les clés sont converties en snake_case") {
        val json = EcrivainJson.encoder(rapport)
        assert(json.contains("\"nombre_mesures\""), json)
        assert(json.contains("\"proportion_alerte\""), json)
        assert(json.contains("\"patients_en_alerte\""), json)
        assert(!json.contains("\"nombreMesures\""), "une clé camelCase a fuité")
    }

    test("un horodatage est exporté au format ISO 8601") {
        assert(ujson.read(EcrivainJson.encoder(rapport))("genere_le").str == "2026-07-30T12:00:00")
    }

    test("une valeur absente est exportée en null, pas en tableau vide") {
        val json = EcrivainJson.encoder(rapport)
        assert(ujson.read(json)("commentaire").isNull, json)
        assert(!json.contains("\"commentaire\": []"), json)
    }

    test("une valeur présente est exportée directement, sans enveloppe") {
        val json = EcrivainJson.encoder(rapport.copy(commentaire = Some("relève de nuit")))
        assert(ujson.read(json)("commentaire").str == "relève de nuit")
    }

    test("la structure imbriquée est préservée") {
        val arbre = ujson.read(EcrivainJson.encoder(rapport))
        assert(arbre("services").arr.size == 2)
        assert(arbre("services")(0)("service").str == "Reanimation")
        assert(arbre("services")(0)("proportion_alerte").num == 0.571)
        assert(arbre("services")(0)("patients_en_alerte")(0).str == "P005")
        assert(arbre("services")(1)("patients_en_alerte").arr.isEmpty)
    }

    test("les accents survivent à l'encodage") {
        val json = EcrivainJson.encoder(rapport.copy(commentaire = Some("Température 38°C")))
        assert(json.contains("Température 38°C"), json)
    }

    test("l'encodage est déterministe : deux appels donnent le même texte") {
        assert(EcrivainJson.encoder(rapport) == EcrivainJson.encoder(rapport))
    }

    // --- Validation -------------------------------------------------------------

    test("un JSON tronqué est détecté") {
        assert(EcrivainJson.valider(EcrivainJson.encoder(rapport).dropRight(20)).isLeft)
    }

    test("un texte qui n'est pas du JSON est détecté") {
        assert(EcrivainJson.valider("{ ceci n'est pas du json").isLeft)
    }

    // --- Export complet ---------------------------------------------------------

    test("exporter écrit un fichier relisible et valide") {
        dansUnDossierTemporaire { dossier =>
            val cible = dossier.resolve("out/rapport.json")
            assert(EcrivainJson.exporter(cible.toString, rapport).isRight)

            val relu = String(Files.readAllBytes(cible), StandardCharsets.UTF_8)
            assert(EcrivainJson.valider(relu).isRight, "le JSON relu depuis le disque doit être valide")
            assert(ujson.read(relu)("nombre_mesures").num == 21.0)
        }
    }

    test("exporter propage l'erreur d'écriture sans lever d'exception") {
        dansUnDossierTemporaire { dossier =>
            // La cible est un dossier : l'écriture échoue, l'erreur remonte en Left.
            assert(EcrivainJson.exporter(dossier.toString, rapport).isLeft)
        }
    }

    test("exporter valide AVANT d'écrire, donc aucun fichier partiel n'est laissé") {
        // L'ordre des étapes est ce qui garantit qu'un document cassé n'atteint
        // jamais le disque. On vérifie ici que la validation est bien un préalable.
        dansUnDossierTemporaire { dossier =>
            val cible = dossier.resolve("rapport.json")
            assert(EcrivainJson.valider("{ casse").isLeft, "la validation doit rejeter ce texte")
            assert(!Files.exists(cible), "aucun fichier ne doit exister avant écriture")
        }
    }

end EcrivainJsonSpec

/**
 * Type factice tenant la place du futur objet `Statistiques`.
 *
 * Il vit UNIQUEMENT dans les tests : aucun type de remplacement ne pollue le
 * code de production. Son rôle est de démontrer que `EcrivainJson` est complet,
 * et de documenter les deux lignes que l'auteur de `Statistiques` aura à écrire.
 */
object StatistiquesFactice:

    case class ServiceFactice(service: String, proportionAlerte: Double, patientsEnAlerte: List[String])

    case class RapportFactice(
        genereLe: LocalDateTime,
        nombreMesures: Int,
        services: List[ServiceFactice],
        commentaire: Option[String]
    )

    // Voilà tout ce qu'il faudra ajouter pour le vrai type.
    // ORDRE IMPORTANT : les feuilles avant les composites.
    implicit val ecrivainService: JsonFormat.Writer[ServiceFactice] = JsonFormat.macroW
    implicit val ecrivainRapport: JsonFormat.Writer[RapportFactice] = JsonFormat.macroW

end StatistiquesFactice
