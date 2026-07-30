package storage

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import scala.util.Try

import domain.ErreurPipeline

object EcrivainJson {

  def chemin(valeur: String): Path = Paths.get(valeur)

  def encoder[A](valeur: A)(implicit ecrivain: JsonFormat.Writer[A]): String =
    JsonFormat.write(valeur, indent = 2)

  
  def valider(json: String): Either[ErreurPipeline, ujson.Value] =
    Try(ujson.read(json)).toEither.left.map { erreur =>
      ErreurPipeline.JsonInvalide(
        Option(erreur.getMessage).filter(_.nonEmpty).getOrElse(erreur.getClass.getSimpleName)
      )
    }

  def ecrire(fichier: Path, contenu: String): Either[ErreurPipeline, Path] =
    Try {
      Option(fichier.toAbsolutePath.getParent).foreach(parent => Files.createDirectories(parent))
      Files.write(fichier, contenu.getBytes(StandardCharsets.UTF_8))
    }.fold(
      erreur => Left(ErreurPipeline.EcritureImpossible(fichier.toString, description(erreur))),
      ecrit => Right(ecrit)
    )

  def exporter[A](fichier: Path, valeur: A)(implicit ecrivain: JsonFormat.Writer[A]): Either[ErreurPipeline, Path] = {
    val json = encoder(valeur)
    for {
      _ <- valider(json)
      ecrit <- ecrire(fichier, json)
    } yield ecrit
  }

  private def description(erreur: Throwable): String =
    erreur match {
      case _: java.nio.file.AccessDeniedException => "accès refusé"
      case _: java.nio.file.FileSystemException => "chemin invalide ou inaccessible"
      case _ => Option(erreur.getMessage).filter(_.nonEmpty).getOrElse(erreur.getClass.getSimpleName)
    }
}
