package storage

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import domain.*

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

  // ===========================================================================
  // Statistiques des types feuilles vers les types composites
  // ===========================================================================

  /**
   * Nom stable d'un niveau de vigilance.
   *
   * `IndicateurVigilance` n'expose que `severite` : le nom affiché est donc
   * décidé ici, côté sérialisation. Le `match` est exhaustif, donc ajouter un
   * quatrième niveau casserait la compilation à cet endroit précis plutôt que de
   * produire silencieusement un JSON incomplet.
   */
  private def nomVigilance(indicateur: IndicateurVigilance): String = indicateur match {
    case IndicateurVigilance.Normal => "Normal"
    case IndicateurVigilance.Surveillance => "Surveillance"
    case IndicateurVigilance.Alerte => "Alerte"
  }

  /** Ordre de gravité croissante, pour un JSON déterministe. */
  private val vigilancesOrdonnees: List[IndicateurVigilance] =
    List(IndicateurVigilance.Normal, IndicateurVigilance.Surveillance, IndicateurVigilance.Alerte)

  /**
   * Sérialisation d'un ADT en simple chaîne.
   *
   * ATTENTION : ces deux `Writer` ne suffisent PAS à sérialiser correctement une
   * `Map` dont ils sont la clé. upickle ne produit un objet JSON que pour les
   * clés `String` ; pour tout autre type de clé il émet un TABLEAU DE PAIRES
   * (`[["spo2", {...}], ...]`), qu'un tableau de bord ne sait pas exploiter.
   * Voir `objetStatistiquesMesure` et `objetDistributionVigilances`.
   */
  implicit val ecrivainVigilance: Writer[IndicateurVigilance] =
    writer[String].comap[IndicateurVigilance](nomVigilance)

  implicit val ecrivainParametreMesure: Writer[ParametreMesure] =
    writer[String].comap[ParametreMesure](_.nom)

  implicit val ecrivainStatistiqueMesure: Writer[StatistiqueMesure] = macroW

  /**
   * `Map[ParametreMesure, StatistiqueMesure]` -> objet JSON indexé par nom.
   *
   * On parcourt `ParametreMesure.parametres` plutôt que la `Map` elle-même : au
   * delà de quatre entrées une `Map` immuable est une table de hachage, dont
   * l'ordre d'itération n'est pas spécifié. Passer par la liste canonique rend
   * l'ordre des clés déterministe (donc les diffs de JSON lisibles) et suit
   * l'ordre clinique des paramètres.
   *
   * Un paramètre jamais mesuré est simplement absent du JSON.
   */
  private def objetStatistiquesMesure(valeurs: Map[ParametreMesure, StatistiqueMesure]): ujson.Value =
    ujson.Obj.from(
      ParametreMesure.parametres.flatMap { parametre =>
        valeurs.get(parametre).map(statistique => parametre.nom -> writeJs(statistique))
      }
    )

  /**
   * `Map[IndicateurVigilance, Int]` -> objet JSON indexé par nom.
   *
   * Les trois niveaux sont toujours présents, à `0` le cas échéant : le tableau
   * de bord n'a jamais de clé manquante à gérer.
   */
  private def objetDistributionVigilances(valeurs: Map[IndicateurVigilance, Int]): ujson.Value =
    ujson.Obj.from(
      vigilancesOrdonnees.map { niveau =>
        nomVigilance(niveau) -> (ujson.Num(valeurs.getOrElse(niveau, 0).toDouble): ujson.Value)
      }
    )

  /**
   * Seul `Writer` écrit à la main : `macroW` produirait ici les deux `Map` en
   * tableaux de paires. Les autres types de statistiques sont dérivés.
   */
  implicit val ecrivainResumeStatistique: Writer[ResumeStatistique] =
    writer[ujson.Value].comap[ResumeStatistique] { resume =>
      ujson.Obj(
        "nombre_mesures" -> ujson.Num(resume.nombreMesures.toDouble),
        "statistiques_mesure" -> objetStatistiquesMesure(resume.statistiquesMesure),
        "distribution_vigilances" -> objetDistributionVigilances(resume.distributionVigilances)
      )
    }

  implicit val ecrivainStatistiquePatient: Writer[StatistiquePatient] = macroW
  implicit val ecrivainStatistiqueService: Writer[StatistiqueService] = macroW
  implicit val ecrivainStatistique: Writer[Statistique] = macroW
}
