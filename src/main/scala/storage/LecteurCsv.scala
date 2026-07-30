package storage

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.LocalDateTime

import scala.jdk.CollectionConverters._
import scala.util.Try

import domain.{ErreurParsing, ErreurPipeline, Mesure}

/**
 * Lecture du CSV de relevés de moniteurs de chevet.
 *
 * Deux responsabilités, volontairement séparées :
 *
 *  - `lire` : le SEUL point d'accès au disque en lecture de tout le programme.
 *    C'est l'unique fonction impure du fichier.
 *  - `parser` / `parserLigne` : fonctions PURES qui transforment du texte en
 *    `Mesure`. Elles se testent sans fichier temporaire, ce qui rend la
 *    quasi-totalité des tests de cette couche hermétiques.
 *
 * Aucune exception ne sort de ce module : les échecs sont représentés par
 * `Either`, conformément au sujet.
 */
object LecteurCsv {

  /** En-tête attendu du fichier. */
  val colonnesAttendues: List[String] = List(
    "patient_id",
    "timestamp",
    "service",
    "age",
    "frequence_cardiaque",
    "tension_systolique",
    "tension_diastolique",
    "temperature",
    "spo2"
  )

  /**
   * Résultat de la lecture.
   *
   * Les erreurs de lignes sont conservées à côté des mesures : elles alimentent
   * le rapport de nettoyage et le JSON final. Une ligne illisible doit être
   * traçable, pas silencieusement perdue.
   */
  case class ResultatLecture(
      chemin: String,
      lignesLues: Int,
      mesures: List[Mesure],
      erreurs: List[ErreurParsing]
  )

  def chemin(valeur: String): Path = Paths.get(valeur)

  /**
   * Lit le fichier puis délègue au parsing pur.
   *
   * L'encodage UTF-8 est imposé explicitement : le CSV contient des accents
   * (« Réanimation ») que l'encodage par défaut de la JVM corromprait sur un
   * poste Windows.
   */
  def lire(fichier: Path): Either[ErreurPipeline, ResultatLecture] =
    Try(Files.readAllLines(fichier, StandardCharsets.UTF_8).asScala.toList)
      .fold(
        erreur => Left(ErreurPipeline.LectureImpossible(fichier.toString, description(erreur))),
        lignes => parser(fichier.toString, lignes)
      )

  private def description(erreur: Throwable): String =
    erreur match {
      case _: java.nio.file.NoSuchFileException => "fichier introuvable"
      case _: java.nio.file.AccessDeniedException => "accès refusé"
      case _: java.nio.charset.CharacterCodingException => "le fichier n'est pas encodé en UTF-8"
      case _ => Option(erreur.getMessage).filter(_.nonEmpty).getOrElse(erreur.getClass.getSimpleName)
    }

  /**
   * Parse un contenu déjà chargé en mémoire.
   *
   * Deux natures d'échec sont distinguées :
   *  - une LIGNE illisible est collectée dans `erreurs`, le traitement continue ;
   *  - un FICHIER inexploitable (vide, en-tête inattendu, aucune ligne valide)
   *    renvoie `Left` et court-circuite le pipeline.
   */
  def parser(chemin: String, lignes: List[String]): Either[ErreurPipeline, ResultatLecture] = {
    // On numérote AVANT de filtrer les lignes vides, pour que les numéros signalés
    // correspondent bien aux lignes du fichier ouvert dans un éditeur.
    val numerotees = lignes.zipWithIndex
      .map { case (ligne, index) => (index + 1, retirerBom(ligne)) }
      .filter { case (_, ligne) => ligne.trim.nonEmpty }

    numerotees match {
      case Nil =>
        Left(ErreurPipeline.FichierVide(chemin))

      case (_, entete) :: donnees =>
        validerEntete(entete).flatMap { _ =>
          val (erreurs, mesures) = donnees
            .map { case (numeroLigne, ligne) => parserLigne(numeroLigne, ligne) }
            .partitionMap(identity)

          if (mesures.isEmpty) Left(ErreurPipeline.AucuneLigneExploitable(erreurs))
          else Right(ResultatLecture(chemin, donnees.size, mesures, erreurs))
        }
    }
  }

  /**
   * Parse une ligne de données.
   *
   * La `for`-comprehension sur `Either` est un enchaînement de `flatMap` : dès
   * qu'un champ échoue, les suivants ne sont pas évalués et l'erreur remonte
   * telle quelle.
   */
  def parserLigne(numeroLigne: Int, ligne: String): Either[ErreurParsing, Mesure] = {
    val champs = Csv.decouper(ligne).map(_.trim)
    for {
      _ <- verifierNombreDeChamps(numeroLigne, champs)
      patientId <- texteObligatoire(numeroLigne, "patient_id", champs(0))
      timestamp <- horodatage(numeroLigne, champs(1))
      service <- texteObligatoire(numeroLigne, "service", champs(2))
      age <- entierObligatoire(numeroLigne, "age", champs(3))
      frequenceCardiaque <- entierOptionnel(numeroLigne, "frequence_cardiaque", champs(4))
      tensionSystolique <- entierOptionnel(numeroLigne, "tension_systolique", champs(5))
      tensionDiastolique <- entierOptionnel(numeroLigne, "tension_diastolique", champs(6))
      temperature <- decimalOptionnel(numeroLigne, "temperature", champs(7))
      spo2 <- entierOptionnel(numeroLigne, "spo2", champs(8))
    } yield Mesure(
      patientId = patientId,
      timestamp = timestamp,
      service = service,
      age = age,
      frequenceCardiaque = frequenceCardiaque,
      tensionSystolique = tensionSystolique,
      tensionDiastolique = tensionDiastolique,
      temperature = temperature,
      spo2 = spo2
    )
  }

  private def validerEntete(entete: String): Either[ErreurPipeline, Unit] = {
    val obtenues = Csv.decouper(entete).map(_.trim.toLowerCase)
    if (obtenues == colonnesAttendues) Right(())
    else Left(ErreurPipeline.EnTeteInvalide(colonnesAttendues, obtenues))
  }

  private def verifierNombreDeChamps(numeroLigne: Int, champs: List[String]): Either[ErreurParsing, Unit] =
    if (champs.sizeIs == colonnesAttendues.size) Right(())
    else Left(ErreurParsing.NombreDeChampsInvalide(numeroLigne, colonnesAttendues.size, champs.size))

  private def texteObligatoire(numeroLigne: Int, champ: String, brut: String): Either[ErreurParsing, String] =
    if (brut.nonEmpty) Right(brut)
    else Left(ErreurParsing.ChampObligatoireVide(numeroLigne, champ))

  private def horodatage(numeroLigne: Int, brut: String): Either[ErreurParsing, LocalDateTime] =
    if (brut.isEmpty) Left(ErreurParsing.ChampObligatoireVide(numeroLigne, "timestamp"))
    else {
      val normalise = brut.replaceFirst(" ", "T")
      Try(LocalDateTime.parse(normalise))
        .fold(_ => Left(ErreurParsing.HorodatageInvalide(numeroLigne, brut)), Right(_))
    }

  private def entierObligatoire(numeroLigne: Int, champ: String, brut: String): Either[ErreurParsing, Int] =
    if (brut.isEmpty) Left(ErreurParsing.ChampObligatoireVide(numeroLigne, champ))
    else entierOptionnel(numeroLigne, champ, brut).map(_.getOrElse(0))

  private def entierOptionnel(numeroLigne: Int, champ: String, brut: String): Either[ErreurParsing, Option[Int]] =
    if (brut.isEmpty) Right(None)
    else
      Try(brut.toInt).toOption
        .orElse(Try(brut.toDouble).toOption.filter(d => d == math.rint(d)).map(_.toInt))
        .fold[Either[ErreurParsing, Option[Int]]](
          Left(ErreurParsing.ChampNonNumerique(numeroLigne, champ, brut))
        )(valeur => Right(Some(valeur)))

  private def decimalOptionnel(numeroLigne: Int, champ: String, brut: String): Either[ErreurParsing, Option[Double]] =
    if (brut.isEmpty) Right(None)
    else
      Try(brut.replace(',', '.').toDouble)
        .fold(_ => Left(ErreurParsing.ChampNonNumerique(numeroLigne, champ, brut)), d => Right(Some(d)))

  private val Bom: Char = 0xFEFF.toChar

  private def retirerBom(ligne: String): String =
    if (ligne.headOption.contains(Bom)) ligne.drop(1) else ligne
}
