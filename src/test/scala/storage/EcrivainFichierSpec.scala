package storageTest

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.scalatest.funsuite.AnyFunSuite

import domain.ErreurPipeline
import storage.EcrivainFichier

/**
 * Tests de l'effet de bord d'écriture. Tous touchent réellement au disque, dans
 * un dossier temporaire supprimé en fin de test.
 */
class EcrivainFichierSpec extends AnyFunSuite:

    /** Fournit un dossier temporaire vide, et le nettoie entièrement ensuite. */
    private def dansUnDossierTemporaire(verifier: Path => Unit): Unit =
        val dossier = Files.createTempDirectory("ecrivain-test")
        try verifier(dossier)
        finally
            Files.walk(dossier)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(fichier => Files.delete(fichier))

    private def relire(fichier: Path): String =
        String(Files.readAllBytes(fichier), StandardCharsets.UTF_8)

    // --- Écriture nominale ------------------------------------------------------

    test("écrit le contenu à l'emplacement demandé") {
        dansUnDossierTemporaire { dossier =>
            val cible = dossier.resolve("rapport.json").toString
            assert(EcrivainFichier.ecrire(cible, "{}").map(_.toString) == Right(cible))
            assert(relire(dossier.resolve("rapport.json")) == "{}")
        }
    }

    test("crée les dossiers parents manquants") {
        dansUnDossierTemporaire { dossier =>
            val cible = dossier.resolve("out/2026/rapport.json")
            assert(EcrivainFichier.ecrire(cible.toString, "{}").isRight)
            assert(Files.exists(cible))
        }
    }

    test("écrase un fichier existant sans laisser de reliquat") {
        dansUnDossierTemporaire { dossier =>
            val cible = dossier.resolve("rapport.json")
            assert(EcrivainFichier.ecrire(cible.toString, "contenu initial très long").isRight)
            assert(EcrivainFichier.ecrire(cible.toString, "court").isRight)
            assert(relire(cible) == "court")
        }
    }

    test("les accents et le symbole degré survivent à l'écriture en UTF-8") {
        dansUnDossierTemporaire { dossier =>
            val cible = dossier.resolve("accents.json")
            val contenu = """{"service":"Réanimation","unite":"°C","ecart":"σ"}"""
            assert(EcrivainFichier.ecrire(cible.toString, contenu).isRight)
            assert(relire(cible) == contenu)
        }
    }

    test("un contenu vide est écrit sans erreur") {
        dansUnDossierTemporaire { dossier =>
            val cible = dossier.resolve("vide.json")
            assert(EcrivainFichier.ecrire(cible.toString, "").isRight)
            assert(Files.size(cible) == 0L)
        }
    }

    // --- Erreurs d'écriture -----------------------------------------------------

    test("viser un dossier existant est signalé, sans exception") {
        dansUnDossierTemporaire { dossier =>
            val resultat = EcrivainFichier.ecrire(dossier.toString, "{}")
            val message = resultat.swap.toOption.map(_.message).getOrElse("")
            assert(resultat.isLeft, s"attendu Left, obtenu $resultat")
            assert(message.contains("dossier"), message)
        }
    }

    test("un chemin syntaxiquement invalide est signalé, sans exception") {
        // Le caractère nul est refusé par tous les systèmes de fichiers : il est
        // concaténé plutôt qu'échappé dans le littéral pour ne pas glisser un octet
        // nul dans ce fichier source.
        val cheminInvalide = "out/inva" + 0.toChar + "lide.json"
        val message = EcrivainFichier.ecrire(cheminInvalide, "{}").swap.toOption.map(_.message).getOrElse("")
        assert(message.contains("chemin invalide"), message)
    }

    test("l'erreur produite est bien une EcritureImpossible portant le chemin") {
        dansUnDossierTemporaire { dossier =>
            EcrivainFichier.ecrire(dossier.toString, "{}").swap.toOption match
                case Some(ErreurPipeline.EcritureImpossible(chemin, _)) => assert(chemin == dossier.toString)
                case autre => fail(s"EcritureImpossible attendue, obtenu : $autre")
        }
    }

end EcrivainFichierSpec
