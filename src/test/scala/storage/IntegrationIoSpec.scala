package storage

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import domain.ErreurPipeline

/**
 * La SEULE suite qui touche réellement au disque : elle valide les deux effets
 * de bord (`LecteurCsv.lire` et `EcrivainJson.ecrire`) et l'aller-retour
 * complet sur le jeu de données fourni.
 */
class IntegrationIoSpec extends munit.FunSuite {

  private val cheminExemple = LecteurCsv.chemin("data/dataset_patients_exemple.csv")

  test("le jeu de données fourni est lu sans erreur de ligne") {
    val resultat = LecteurCsv.lire(cheminExemple)
    assert(resultat.isRight, s"lecture en échec : $resultat")
    resultat.foreach { lecture =>
      assertEquals(lecture.lignesLues, 25)
      assertEquals(lecture.mesures.size, 25)
      assertEquals(lecture.erreurs, Nil)
    }
  }

  test("les valeurs manquantes du fichier fourni sont bien des None") {
    val mesures = LecteurCsv.lire(cheminExemple).fold(erreur => fail(erreur.message), _.mesures)
    // Le CSV contient 2 fréquences cardiaques et 1 tension systolique absentes.
    assertEquals(mesures.count(_.frequenceCardiaque.isEmpty), 2)
    assertEquals(mesures.count(_.tensionSystolique.isEmpty), 1)
  }

  test("un fichier inexistant renvoie une erreur explicite, sans exception") {
    val fichier = LecteurCsv.chemin("data/absent.csv")
    assertEquals(
      LecteurCsv.lire(fichier).swap.toOption,
      Some(ErreurPipeline.LectureImpossible(fichier.toString, "fichier introuvable"))
    )
  }

  test("un fichier qui n'est pas en UTF-8 est signalé comme tel") {
    val dossier = Files.createTempDirectory("io-test")
    val fichier = dossier.resolve("latin1.csv")
    try {
      // « Tempé » en CP-1252 : l'octet 0xE9 isolé n'est pas une séquence UTF-8 valide.
      Files.write(fichier, Array(0x54, 0x65, 0x6D, 0x70, 0xE9, 0x0A).map(_.toByte)): Unit
      val message = LecteurCsv.lire(fichier).swap.toOption.map(_.message).getOrElse("")
      assert(message.contains("n'est pas encodé en UTF-8"), message)
    } finally {
      supprimerRecursivement(dossier)
    }
  }

  test("aller-retour complet : lecture CSV puis export JSON relisible") {
    val dossier = Files.createTempDirectory("io-test")
    val sortie = dossier.resolve("out/rapport.json")

    val resultat = for {
      lecture <- LecteurCsv.lire(cheminExemple)
      ecrit <- EcrivainJson.exporter(sortie, lecture.mesures)
    } yield ecrit

    try {
      resultat.fold(erreur => fail(erreur.message), _ => ())

      val relu = new String(Files.readAllBytes(sortie), StandardCharsets.UTF_8)
      assert(EcrivainJson.valider(relu).isRight, "le JSON relu depuis le disque doit être valide")
      assertEquals(ujson.read(relu).arr.size, 25)
      // L'encodage UTF-8 survit à l'aller-retour disque.
      assert(relu.contains("Reanimation"), "contenu inattendu")
    } finally {
      supprimerRecursivement(dossier)
    }
  }

  test("l'écriture crée les dossiers parents manquants") {
    val dossier = Files.createTempDirectory("io-test")
    val fichier = dossier.resolve("a/b/c/rapport.json")
    try {
      assert(EcrivainJson.ecrire(fichier, "{}").isRight)
      assert(Files.exists(fichier))
    } finally {
      supprimerRecursivement(dossier)
    }
  }

  test("un JSON invalide est détecté avant toute écriture") {
    // `exporter` valide AVANT d'écrire : un document cassé ne parvient jamais au
    // disque, donc aucun fichier partiel n'est laissé derrière.
    assert(EcrivainJson.valider("{ ceci n'est pas du json").isLeft)
  }

  private def supprimerRecursivement(dossier: java.nio.file.Path): Unit =
    Files.walk(dossier).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
}
