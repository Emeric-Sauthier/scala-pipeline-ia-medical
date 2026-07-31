package storage

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import scala.jdk.CollectionConverters._
import scala.util.Try

import domain.ErreurPipeline

/**
 * Ouverture d'un fichier CSV.
 *
 * Unique responsabilité : lire le fichier et rendre son contenu, une chaîne par
 * ligne, dans l'ordre du fichier.
 *
 * Le module n'interprète RIEN : pas de découpage en colonnes, pas de validation
 * d'en-tête, pas de conversion de type, pas de filtrage des lignes vides, pas de
 * tri. Tout cela relève des étapes suivantes du pipeline.
 *
 * C'est le seul point d'accès au disque en lecture du programme.
 */
object LecteurCsv {

  private def estUnDossier(chemin: String): Boolean =
    Try(Files.isDirectory(Paths.get(chemin))).getOrElse(false)

  /**
   * Lit le fichier et renvoie ses lignes.
   *
   * Toute défaillance d'ouverture ou de lecture est convertie en
   * `ErreurPipeline.LectureImpossible` : aucune exception ne sort d'ici.
   *
   * L'encodage UTF-8 est imposé explicitement. Sans cela, les accents du fichier
   * (« Réanimation ») seraient corrompus sur un poste dont l'encodage par défaut
   * de la JVM n'est pas UTF-8.
   *
   * Un fichier vide n'est pas une erreur : il renvoie une liste vide. C'est du
   * contenu, pas un problème d'ouverture.
   */
  def lire(chemin: String): Either[ErreurPipeline, List[String]] = if estUnDossier(chemin) then {
    Left(ErreurPipeline.LectureImpossible(chemin, "le chemin désigne un dossier, pas un fichier"))
  } else {
    Try(Files.readAllLines(Paths.get(chemin), StandardCharsets.UTF_8).asScala.toList)
      .fold(
        erreur => Left(ErreurPipeline.LectureImpossible(chemin, Diagnostic.cause(chemin, erreur))),
        lignes => Right(lignes)
      )
  }
}
