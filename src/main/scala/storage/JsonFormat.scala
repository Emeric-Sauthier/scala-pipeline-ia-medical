package storage

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Configuration de la sérialisation JSON du projet.
 *
 * `upickle.AttributeTagged` est l'API d'upickle que l'on peut personnaliser. Les
 * deux réglages faits ici valent une fois pour toutes et bénéficient
 * automatiquement à TOUS les types sérialisés, y compris ceux qui n'existent pas
 * encore.
 *
 * === Brancher un nouveau type ===
 *
 * Une ligne par `case class` :
 *
 * {{{
 *   implicit val ecrivainStatistiques: Writer[Statistiques] = macroW
 * }}}
 *
 * ATTENTION à l'ORDRE : un `Writer` dérivé par `macroW` capture les `Writer` de
 * ses champs au moment de son initialisation. Il faut déclarer les types
 * FEUILLES avant les types COMPOSITES, sinon on obtient un
 * `NullPointerException` à l'exécution — et non une erreur de compilation.
 *
 * Pour un `sealed trait` (ex. `IndicateurVigilance`), ne pas utiliser `macroW` :
 * un writer explicite produisant une simple chaîne est bien plus lisible côté
 * tableau de bord.
 *
 * {{{
 *   implicit val ecrivainVigilance: Writer[IndicateurVigilance] =
 *     writer[String].comap[IndicateurVigilance](_.toString)
 * }}}
 *
 * Seuls des `Writer` sont définis, pas des `ReadWriter` : le pipeline PRODUIT du
 * JSON, il n'en consomme jamais.
 */
object JsonFormat extends upickle.AttributeTagged {

  /**
   * `Option[T]` est exporté en `null` / valeur, et non dans le format tableau
   * (`[]` / `[valeur]`) retenu par défaut par upickle.
   *
   * Sans cette surcharge, un capteur débranché apparaîtrait comme
   * `"frequence_cardiaque": []` : le tableau de bord devrait lire un tableau pour
   * obtenir un scalaire. `null` est la représentation naturelle d'une mesure
   * absente.
   *
   * Le passage par `ujson.Value` est délibéré : le `null.asInstanceOf[T]` que
   * suggère la documentation d'upickle produirait `0` sur un type primitif — soit
   * une fréquence cardiaque absente affichée comme une fréquence de zéro.
   */
  override implicit def OptionWriter[T: Writer]: Writer[Option[T]] =
    writer[ujson.Value].comap[Option[T]] {
      case Some(valeur) => writeJs(valeur)
      case None => ujson.Null
    }

  /**
   * Les champs Scala sont en `camelCase` (convention du langage) mais les clés
   * attendues par le tableau de bord sont en `snake_case`, comme les colonnes du
   * CSV source.
   *
   * Surcharger cette méthode évite d'annoter chaque champ de chaque `case class`
   * avec `@upickle.implicits.key("...")`.
   */
  override def objectAttributeKeyWriteMap(clef: CharSequence): CharSequence =
    camelVersSnake(clef.toString)

  override def objectAttributeKeyReadMap(clef: CharSequence): CharSequence =
    snakeVersCamel(clef.toString)

  /** `patientId` -> `patient_id`, `ecartType` -> `ecart_type`, `spo2` -> `spo2`. */
  private def camelVersSnake(nom: String): String =
    nom.flatMap(caractere => if (caractere.isUpper) s"_${caractere.toLower}" else caractere.toString)

  private def snakeVersCamel(nom: String): String =
    nom.split('_').toList match {
      case Nil => nom
      case premier :: suite => premier + suite.map(_.capitalize).mkString
    }

  /** Horodatage au format ISO 8601 : « 2026-07-28T08:00:00 ». */
  implicit val ecrivainHorodatage: Writer[LocalDateTime] =
    writer[String].comap[LocalDateTime](_.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
}
