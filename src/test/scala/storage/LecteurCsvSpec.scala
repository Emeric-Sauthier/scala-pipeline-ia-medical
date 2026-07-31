package storageTest

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import org.scalatest.funsuite.AnyFunSuite

import domain.ErreurPipeline
import storage.LecteurCsv

/**
 * Tests du lecteur.
 *
 * `lire` étant la seule fonction de la couche et étant impure, tous les tests
 * touchent réellement au disque — soit le jeu de données livré, soit un fichier
 * temporaire supprimé en fin de test.
 */
class LecteurCsvSpec extends AnyFunSuite:

    private val cheminExemple = "data/dataset_patients_exemple.csv"

    private def lignesExemple: List[String] =
        LecteurCsv.lire(cheminExemple).fold(erreur => fail(erreur.message), identity)

    /** Crée un fichier temporaire, exécute le test, puis nettoie. */
    private def avecFichier(nom: String, octets: Array[Byte])(verifier: Path => Unit): Unit =
        val dossier = Files.createTempDirectory("lecteur-test")
        val fichier = dossier.resolve(nom)
        try
            Files.write(fichier, octets): Unit
            verifier(fichier)
        finally
            Files.deleteIfExists(fichier): Unit
            Files.deleteIfExists(dossier): Unit

    private def avecContenu(nom: String, contenu: String)(verifier: Path => Unit): Unit =
        avecFichier(nom, contenu.getBytes(StandardCharsets.UTF_8))(verifier)

    // --- Lecture nominale -------------------------------------------------------

    test("aucune ligne du fichier livré n'est perdue ni ajoutée") {
        val brut = String(Files.readAllBytes(Paths.get(cheminExemple)), StandardCharsets.UTF_8)
        assert(lignesExemple.size == brut.linesIterator.size)
        assert(lignesExemple.size > 1, "le fichier doit contenir un en-tête et au moins un relevé")
    }

    test("les lignes sont rendues dans l'ordre du fichier") {
        val lignes = lignesExemple
        assert(lignes.head.startsWith("patient_id"), lignes.head)
        assert(lignes(1).startsWith("P001"), lignes(1))
        assert(lignes.last.startsWith("P006"), lignes.last)
    }

    test("aucun découpage n'est fait : une ligne reste une chaîne unique") {
        // Les virgules sont toujours là : c'est à l'étape de parsing de découper.
        val premiereDonnee = lignesExemple(1)
        assert(premiereDonnee.count(_ == ',') == 8, premiereDonnee)
        assert(premiereDonnee == "P001,2026-07-28T08:00:00,Chirurgie A,67,78,128,82,37.1,97")
    }

    test("les champs vides du fichier sont conservés tels quels") {
        // Ligne 4 du fichier : la fréquence cardiaque est absente (deux virgules
        // consécutives). Rien n'est comblé ni signalé ici.
        assert(lignesExemple.exists(_.contains(",,")), "aucune ligne à champ vide trouvée")
    }

    test("les accents sont préservés grâce à la lecture en UTF-8") {
        avecContenu("accents.csv", "service\nRéanimation\nChirurgie") { fichier =>
            assert(LecteurCsv.lire(fichier.toString) == Right(List("service", "Réanimation", "Chirurgie")))
        }
    }

    test("les lignes vides ne sont pas filtrées") {
        avecContenu("trous.csv", "a\n\nb\n   \nc") { fichier =>
            assert(LecteurCsv.lire(fichier.toString) == Right(List("a", "", "b", "   ", "c")))
        }
    }

    test("l'ordre n'est pas modifié : aucun tri n'est appliqué") {
        avecContenu("desordre.csv", "c\na\nb") { fichier =>
            assert(LecteurCsv.lire(fichier.toString) == Right(List("c", "a", "b")))
        }
    }

    test("un fichier vide renvoie une liste vide, pas une erreur") {
        avecContenu("vide.csv", "") { fichier =>
            assert(LecteurCsv.lire(fichier.toString) == Right(Nil))
        }
    }

    // --- Erreurs d'ouverture ----------------------------------------------------

    test("un fichier introuvable est signalé sans exception") {
        assert(
            LecteurCsv.lire("data/absent.csv") ==
                Left(ErreurPipeline.LectureImpossible("data/absent.csv", "fichier introuvable"))
        )
    }

    test("un chemin désignant un dossier est signalé comme tel") {
        val message = LecteurCsv.lire("data").swap.toOption.map(_.message).getOrElse("")
        assert(message.contains("dossier"), message)
    }

    test("un fichier qui n'est pas en UTF-8 est signalé comme tel") {
        // « Tempé » en CP-1252 : l'octet 0xE9 isolé n'est pas une séquence UTF-8 valide.
        avecFichier("latin1.csv", Array(0x54, 0x65, 0x6D, 0x70, 0xE9, 0x0A).map(_.toByte)) { fichier =>
            val message = LecteurCsv.lire(fichier.toString).swap.toOption.map(_.message).getOrElse("")
            assert(message.contains("n'est pas encodé en UTF-8"), message)
        }
    }

    test("un chemin syntaxiquement invalide est signalé sans exception") {
        // Le caractère nul est refusé par tous les systèmes de fichiers : il est
        // concaténé plutôt qu'échappé dans le littéral pour ne pas glisser un octet
        // nul dans ce fichier source.
        val cheminInvalide = "data/inva" + 0.toChar + "lide.csv"
        val message = LecteurCsv.lire(cheminInvalide).swap.toOption.map(_.message).getOrElse("")
        assert(message.contains("chemin invalide"), message)
    }

    test("le message d'erreur contient le chemin fautif") {
        val message = LecteurCsv.lire("data/absent.csv").swap.toOption.map(_.message).getOrElse("")
        assert(message.contains("data/absent.csv"), message)
    }

end LecteurCsvSpec
