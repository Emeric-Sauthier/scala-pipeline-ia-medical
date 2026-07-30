package storage

import java.time.LocalDateTime

import domain.{ErreurParsing, ErreurPipeline, Mesure}

/**
 * Tests du parsing — tous HERMÉTIQUES : aucun fichier temporaire, aucun chemin
 * relatif. C'est le bénéfice direct d'avoir séparé `lire` (impur) de `parser`
 * (pur) : le contenu de test est une simple `List[String]`.
 */
class LecteurCsvSpec extends munit.FunSuite {

  private val entete =
    "patient_id,timestamp,service,age,frequence_cardiaque,tension_systolique,tension_diastolique,temperature,spo2"

  private val ligneValide = "P001,2026-07-28T08:00:00,Chirurgie A,67,78,128,82,37.1,97"

  // --- Parsing d'une ligne ---------------------------------------------------

  test("parse une ligne complète") {
    val attendue = Mesure(
      patientId = "P001",
      timestamp = LocalDateTime.parse("2026-07-28T08:00:00"),
      service = "Chirurgie A",
      age = 67,
      frequenceCardiaque = Some(78),
      tensionSystolique = Some(128),
      tensionDiastolique = Some(82),
      temperature = Some(37.1),
      spo2 = Some(97)
    )
    assertEquals(LecteurCsv.parserLigne(2, ligneValide), Right(attendue))
  }

  test("un champ vide est une absence de valeur, pas une erreur") {
    val ligne = "P001,2026-07-28T08:00:00,Chirurgie A,67,,131,85,37.2,96"
    assertEquals(LecteurCsv.parserLigne(4, ligne).map(_.frequenceCardiaque), Right(None))
  }

  test("plusieurs capteurs muets sur la même ligne") {
    val ligne = "P004,2026-07-28T08:15:00,Chirurgie B,45,,,70,36.5,99"
    val mesure = LecteurCsv.parserLigne(16, ligne)
    assertEquals(mesure.map(_.frequenceCardiaque), Right(None))
    assertEquals(mesure.map(_.tensionSystolique), Right(None))
    assertEquals(mesure.map(_.tensionDiastolique), Right(Some(70)))
  }

  test("un champ non numérique produit une erreur, pas une exception") {
    val ligne = "P001,2026-07-28T08:00:00,Chirurgie A,67,abc,128,82,37.1,97"
    assertEquals(
      LecteurCsv.parserLigne(7, ligne),
      Left(ErreurParsing.ChampNonNumerique(7, "frequence_cardiaque", "abc"))
    )
  }

  test("un horodatage illisible produit une erreur") {
    val ligne = "P001,hier matin,Chirurgie A,67,78,128,82,37.1,97"
    assertEquals(LecteurCsv.parserLigne(3, ligne), Left(ErreurParsing.HorodatageInvalide(3, "hier matin")))
  }

  test("un nombre de champs incorrect produit une erreur") {
    assertEquals(
      LecteurCsv.parserLigne(5, "P001,2026-07-28T08:00:00,Chirurgie A"),
      Left(ErreurParsing.NombreDeChampsInvalide(5, 9, 3))
    )
  }

  test("un patient_id vide produit une erreur de champ obligatoire") {
    val ligne = ",2026-07-28T08:00:00,Chirurgie A,67,78,128,82,37.1,97"
    assertEquals(LecteurCsv.parserLigne(9, ligne), Left(ErreurParsing.ChampObligatoireVide(9, "patient_id")))
  }

  test("la première erreur court-circuite les champs suivants") {
    // Deux champs fautifs : seul le premier est signalé (comportement « railway »).
    val ligne = "P001,2026-07-28T08:00:00,Chirurgie A,67,abc,xyz,82,37.1,97"
    assertEquals(
      LecteurCsv.parserLigne(11, ligne),
      Left(ErreurParsing.ChampNonNumerique(11, "frequence_cardiaque", "abc"))
    )
  }

  test("les valeurs aberrantes sont parsées sans broncher") {
    // Le parsing ne juge pas la plausibilité clinique : c'est le rôle de l'étape
    // de nettoyage. 305 bpm est un entier valide.
    val ligne = "P002,2026-07-28T08:30:00,Chirurgie A,54,305,116,74,36.7,98"
    assertEquals(LecteurCsv.parserLigne(9, ligne).map(_.frequenceCardiaque), Right(Some(305)))
  }

  // --- Tolérances ------------------------------------------------------------

  test("tolère un entier écrit sous forme décimale (export Excel)") {
    val ligne = "P001,2026-07-28T08:00:00,Chirurgie A,67,78.0,128,82,37.1,97"
    assertEquals(LecteurCsv.parserLigne(2, ligne).map(_.frequenceCardiaque), Right(Some(78)))
  }

  test("refuse un décimal non entier dans un champ entier") {
    val ligne = "P001,2026-07-28T08:00:00,Chirurgie A,67,78.5,128,82,37.1,97"
    assert(LecteurCsv.parserLigne(2, ligne).isLeft)
  }

  test("tolère un horodatage séparé par une espace") {
    val ligne = "P001,2026-07-28 08:00:00,Chirurgie A,67,78,128,82,37.1,97"
    assertEquals(
      LecteurCsv.parserLigne(2, ligne).map(_.timestamp),
      Right(LocalDateTime.parse("2026-07-28T08:00:00"))
    )
  }

  test("tolère la virgule décimale dans un champ cité") {
    val ligne = """P001,2026-07-28T08:00:00,Chirurgie A,67,78,128,82,"37,1",97"""
    assertEquals(LecteurCsv.parserLigne(2, ligne).map(_.temperature), Right(Some(37.1)))
  }

  // --- Parsing d'un fichier --------------------------------------------------

  test("parse un contenu complet") {
    val resultat = LecteurCsv.parser("test.csv", List(entete, ligneValide, ligneValide))
    assertEquals(resultat.map(_.lignesLues), Right(2))
    assertEquals(resultat.map(_.mesures.size), Right(2))
    assertEquals(resultat.map(_.erreurs), Right(Nil))
  }

  test("une ligne illisible est écartée mais le reste est conservé") {
    val resultat = LecteurCsv.parser("test.csv", List(entete, ligneValide, "trop,peu,de,champs"))
    assertEquals(resultat.map(_.mesures.size), Right(1))
    assertEquals(resultat.map(_.erreurs.size), Right(1))
    assertEquals(resultat.map(_.erreurs.map(_.numeroLigne)), Right(List(3)))
  }

  test("un fichier vide est une erreur de pipeline") {
    assertEquals(LecteurCsv.parser("vide.csv", Nil), Left(ErreurPipeline.FichierVide("vide.csv")))
  }

  test("un en-tête inattendu est une erreur de pipeline") {
    val resultat = LecteurCsv.parser("mauvais.csv", List("a,b,c", "1,2,3"))
    assert(resultat.swap.exists(_.isInstanceOf[ErreurPipeline.EnTeteInvalide]))
  }

  test("un en-tête est reconnu quelle que soit la casse") {
    val resultat = LecteurCsv.parser("majuscules.csv", List(entete.toUpperCase, ligneValide))
    assertEquals(resultat.map(_.mesures.size), Right(1))
  }

  test("un fichier sans aucune ligne exploitable est une erreur de pipeline") {
    val resultat = LecteurCsv.parser("casse.csv", List(entete, "trop,peu"))
    assert(resultat.swap.exists(_.isInstanceOf[ErreurPipeline.AucuneLigneExploitable]))
  }

  test("les lignes vides sont ignorées sans décaler la numérotation") {
    val resultat = LecteurCsv.parser("trous.csv", List(entete, "", "trop,peu"))
    val numeros = resultat.swap.toOption.collect { case ErreurPipeline.AucuneLigneExploitable(e) =>
      e.map(_.numeroLigne)
    }
    // La ligne fautive est la 3e du fichier, pas la 2e.
    assertEquals(numeros, Some(List(3)))
  }

  test("ignore un BOM UTF-8 en tête de fichier") {
    val bom = 0xFEFF.toChar.toString
    val resultat = LecteurCsv.parser("bom.csv", List(bom + entete, ligneValide))
    assertEquals(resultat.map(_.mesures.size), Right(1))
  }
}
