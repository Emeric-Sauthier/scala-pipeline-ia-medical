package storage

import java.nio.file.Path

import scala.util.Try

import domain.ErreurPipeline

/**
 * Export JSON.
 *
 * Trois étapes, dont deux PURES :
 *
 *  - `encoder` (pure) : valeur -> texte JSON ;
 *  - `valider` (pure) : vérifie que le texte produit est du JSON relisible ;
 *  - `exporter` : enchaîne les deux puis délègue l'écriture à
 *    [[EcrivainFichier]], seul détenteur de l'effet de bord.
 *
 * === Pourquoi cette couche est terminée alors que `Statistiques` n'existe pas ===
 *
 * `encoder` et `exporter` sont GÉNÉRIQUES : ils acceptent n'importe quel type `A`
 * pour lequel un `JsonFormat.Writer[A]` est disponible. Ils n'ont donc aucune
 * connaissance du type exporté.
 *
 * Le jour où `Statistiques` existe, il suffit d'ajouter dans [[JsonFormat]] :
 *
 * {{{
 *   implicit val ecrivainStatistiques: Writer[Statistiques] = macroW
 * }}}
 *
 * puis d'appeler `EcrivainJson.exporter("out/rapport.json", statistiques)`.
 * Aucune ligne de ce fichier n'aura à changer.
 */
object EcrivainJson {

  /** Encode n'importe quelle valeur disposant d'un `Writer`, en JSON indenté. */
  def encoder[A](valeur: A)(implicit ecrivain: JsonFormat.Writer[A]): String =
    JsonFormat.write(valeur, indent = 2)

  /**
   * Relit le JSON produit pour vérifier qu'il est syntaxiquement valide.
   *
   * Le sujet demande explicitement de « vérifier la validité du JSON produit ».
   * Sans ce contrôle, un document mal formé serait silencieusement accepté par
   * l'écriture fichier, et le tableau de bord recevrait un document cassé.
   */
  def valider(json: String): Either[ErreurPipeline, ujson.Value] =
    Try(ujson.read(json)).toEither.left.map { erreur =>
      ErreurPipeline.JsonInvalide(
        Option(erreur.getMessage).filter(_.nonEmpty).getOrElse(erreur.getClass.getSimpleName)
      )
    }

  /**
   * Encode, valide, puis écrit.
   *
   * La `for`-comprehension sur `Either` court-circuite dès la première erreur :
   * si le JSON produit est invalide, il n'est PAS écrit. L'ordre des étapes est
   * ce qui garantit qu'aucun fichier corrompu n'atteint le disque.
   */
  def exporter[A](chemin: String, valeur: A)(implicit ecrivain: JsonFormat.Writer[A]): Either[ErreurPipeline, Path] = {
    val json = encoder(valeur)
    for {
      _ <- valider(json)
      ecrit <- EcrivainFichier.ecrire(chemin, json)
    } yield ecrit
  }
}
