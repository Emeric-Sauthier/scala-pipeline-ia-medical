package storage

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import domain.{ErreurParsing, Mesure}

/**
 * Configuration de la sérialisation JSON du projet.
 *
 * `upickle.AttributeTagged` est l'API d'upickle que l'on peut personnaliser.
 * Deux réglages sont faits ici, une fois pour toutes, et bénéficient
 * automatiquement à TOUS les types sérialisés par l'équipe.
 *
 * === Ajouter un type ===
 *
 * Il suffit d'une ligne par `case class` :
 *
 * {{{
 *   implicit val ecrivainStatistiquesPatient: Writer[StatistiquesPatient] = macroW
 * }}}
 *
 * ATTENTION à l'ORDRE de déclaration : un `Writer` dérivé par `macroW` capture
 * les `Writer` de ses champs au moment de son initialisation. Il faut donc
 * déclarer les types FEUILLES avant les types COMPOSITES, sinon on obtient un
 * `NullPointerException` à l'exécution — et non une erreur de compilation.
 *
 * Pour un `sealed trait` (ex. IndicateurVigilance), ne pas utiliser `macroW` :
 * préférer un writer explicite qui produit une simple chaîne, bien plus lisible
 * côté tableau de bord (voir `ecrivainErreurParsing` en exemple).
 *
 * Seuls des `Writer` sont définis, pas des `ReadWriter` : le pipeline PRODUIT du
 * JSON, il n'en consomme jamais. Déclarer des lecteurs inutilisés obligerait à
 * écrire des décodeurs factices pour les traits scellés.
 */
object JsonFormat extends upickle.AttributeTagged {

  // ---------------------------------------------------------------------------
  // Réglage 1 — Option exportée en null, et non en tableau
  // ---------------------------------------------------------------------------

  /**
   * Par défaut, upickle sérialise `Option[T]` sous forme de tableau : `[]` pour
   * `None`, `[97]` pour `Some(97)`. Un capteur débranché apparaîtrait donc comme
   * `"frequence_cardiaque": []`, obligeant le tableau de bord à lire un tableau
   * pour obtenir un scalaire.
   *
   * `null` est la représentation naturelle d'une mesure absente, et celle
   * qu'attend n'importe quel client JSON.
   *
   * Le passage par `ujson.Value` est délibéré : la solution
   * `null.asInstanceOf[T]` que suggère la documentation d'upickle produirait `0`
   * sur un type primitif — soit une fréquence cardiaque absente affichée comme
   * une fréquence cardiaque de zéro.
   */
  override implicit def OptionWriter[T: Writer]: Writer[Option[T]] =
    writer[ujson.Value].comap[Option[T]] {
      case Some(valeur) => writeJs(valeur)
      case None => ujson.Null
    }

  // ---------------------------------------------------------------------------
  // Réglage 2 — clés JSON en snake_case
  // ---------------------------------------------------------------------------

  /**
   * Les champs Scala sont en `camelCase` (convention du langage) mais les clés
   * attendues par le tableau de bord sont en `snake_case`, comme les colonnes du
   * CSV source.
   *
   * Surcharger cette méthode évite d'annoter chaque champ de chaque `case class`
   * avec `@upickle.implicits.key("...")` — une cinquantaine d'annotations à
   * maintenir sur l'ensemble du projet.
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

  implicit val ecrivainHorodatage: Writer[LocalDateTime] =
    writer[String].comap[LocalDateTime](_.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

  implicit val ecrivainErreurParsing: Writer[ErreurParsing] =
    writer[ujson.Value].comap[ErreurParsing] { erreur =>
      ujson.Obj(
        "numero_ligne" -> ujson.Num(erreur.numeroLigne.toDouble),
        "message" -> ujson.Str(erreur.message)
      )
    }

  implicit val ecrivainMesure: Writer[Mesure] = macroW
}
