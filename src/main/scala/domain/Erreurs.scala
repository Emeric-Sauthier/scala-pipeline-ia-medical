package domain

sealed trait ErreurParsing {
  def numeroLigne: Int
  def message: String
}

object ErreurParsing {

  case class NombreDeChampsInvalide(numeroLigne: Int, attendu: Int, obtenu: Int) extends ErreurParsing {
    val message: String = s"$obtenu champ(s) au lieu de $attendu"
  }

  case class ChampNonNumerique(numeroLigne: Int, valeur: String) extends ErreurParsing {
    val message: String = s"champ non numérique : « $valeur »"
  }

  case class HorodatageInvalide(numeroLigne: Int, valeur: String) extends ErreurParsing {
    val message: String = s"horodatage illisible : « $valeur »"
  }

  case class ChampObligatoireVide(numeroLigne: Int, champ: String) extends ErreurParsing {
    val message: String = s"champ obligatoire vide : '$champ'"
  }

  case class ValeurNumeriqueInvalide(numeroLigne: Int, valeur: String, raison: String) extends ErreurParsing {
    val message: String = s"valeur '$valeur' invalide : '$raison'"
  }
}

sealed trait ErreurPipeline {
  def message: String
}

object ErreurPipeline {

  case class LectureImpossible(chemin: String, cause: String) extends ErreurPipeline {
    val message: String = s"lecture impossible du fichier « $chemin » : $cause"
  }

  case class EcritureImpossible(chemin: String, cause: String) extends ErreurPipeline {
    val message: String = s"écriture impossible du fichier « $chemin » : $cause"
  }

  case class FichierVide(chemin: String) extends ErreurPipeline {
    val message: String = s"le fichier « $chemin » ne contient aucune ligne"
  }

  case class EnTeteInvalide(attendues: List[String], obtenues: List[String]) extends ErreurPipeline {
    val message: String =
      s"en-tête CSV inattendu : attendu [${attendues.mkString(", ")}], obtenu [${obtenues.mkString(", ")}]"
  }

  case class AucuneLigneExploitable(erreurs: List[ErreurParsing]) extends ErreurPipeline {
    val message: String = s"aucune ligne exploitable après parsing (${erreurs.size} erreur(s))"
  }

  case class JsonInvalide(cause: String) extends ErreurPipeline {
    val message: String = s"le JSON produit n'est pas relisible : $cause"
  }
}
