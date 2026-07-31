package storage

import java.nio.file.{Files, Paths}

import scala.util.Try

/**
 * Traduction des exceptions du système de fichiers en causes lisibles.
 *
 * Mutualisé entre la lecture et l'écriture pour qu'une même défaillance se lise
 * de la même façon dans les deux sens : un « accès refusé » à l'ouverture et à
 * l'écriture doit produire le même vocabulaire.
 */
private[storage] object Diagnostic {

  /**
   * Le `getMessage` de `NoSuchFileException` ne contient que le chemin : sans
   * cette traduction, le message final répéterait deux fois le même chemin sans
   * rien expliquer.
   */
  def cause(chemin: String, erreur: Throwable): String =
    erreur match {
      case _: java.nio.file.NoSuchFileException => "fichier introuvable"
      case _: java.nio.file.InvalidPathException => "chemin invalide"
      case _: java.nio.charset.CharacterCodingException => "le fichier n'est pas encodé en UTF-8"
      case _: java.nio.file.AccessDeniedException => "accès refusé"

      case _ => Option(erreur.getMessage).filter(_.nonEmpty).getOrElse(erreur.getClass.getSimpleName)
    }
}
