import storage.LecteurCsv

/**
 * Point d'entrée du pipeline — coquille impérative.
 *
 * En l'état, seule la lecture du fichier est branchée. Le parsing, le nettoyage,
 * la transformation, l'agrégation puis l'export viendront consommer les lignes
 * renvoyées ici.
 */
object Main {

  private val CheminEntree = "data/dataset_patients_exemple.csv"

  def main(arguments: Array[String]): Unit =
    LecteurCsv.lire(CheminEntree) match {
      case Right(lignes) =>
        println(s"Source : $CheminEntree")
        println(s"Lignes : ${lignes.size}")

      // Les étapes suivantes du pipeline s'inséreront ici, en consommant `lignes`.

      case Left(erreur) =>
        System.err.println(s"Échec : ${erreur.message}")
        sys.exit(1)
    }
}
