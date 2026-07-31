package storage

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import scala.util.Try

import domain.ErreurPipeline

/**
 * Écriture de fichiers texte.
 *
 * Seul point d'accès au disque en écriture du programme. Cet objet ne connaît ni
 * le JSON ni les statistiques : il reçoit du texte et l'écrit. C'est ce qui le
 * rend réutilisable si un autre format d'export s'ajoute (HTML, CSV...) et
 * testable sans rien sérialiser.
 */
object EcrivainFichier {

  private def estUnDossier(chemin: String): Boolean =
    Try(Files.isDirectory(Paths.get(chemin))).getOrElse(false)

  /**
   * Écrit le contenu, en créant les dossiers parents manquants.
   *
   * Les dossiers sont créés pour qu'une sortie du type `out/rapport.json`
   * fonctionne sur un dépôt fraîchement cloné, sans étape manuelle.
   *
   * L'encodage UTF-8 est imposé explicitement : les libellés du rapport
   * contiendront des accents et « °C », que l'encodage par défaut de la JVM
   * pourrait corrompre.
   *
   * Un fichier existant est écrasé. Toute défaillance est convertie en
   * `ErreurPipeline.EcritureImpossible` : aucune exception ne sort d'ici.
   */
  def ecrire(chemin: String, contenu: String): Either[ErreurPipeline, Path] =
    if estUnDossier(chemin) then {
      Left(ErreurPipeline.EcritureImpossible(chemin, "le chemin désigne un dossier, pas un fichier"))
    } else {
      Try {
        val fichier = Paths.get(chemin)
        Option(fichier.toAbsolutePath.getParent).foreach(parent => Files.createDirectories(parent))
        Files.write(fichier, contenu.getBytes(StandardCharsets.UTF_8))
      }.fold(
        erreur => Left(ErreurPipeline.EcritureImpossible(chemin, Diagnostic.cause(chemin, erreur))),
        ecrit => Right(ecrit)
      )
    }
}
