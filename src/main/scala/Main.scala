import storage.{EcrivainJson, LecteurCsv}

object Main {

  private val CheminEntree = "data/dataset_patients_exemple.csv"
  private val CheminSortie = "out/rapport.json"

  def main(arguments: Array[String]): Unit = {
    val resultat = for {
      lecture <- LecteurCsv.lire(LecteurCsv.chemin(CheminEntree))

      ecrit <- EcrivainJson.exporter(EcrivainJson.chemin(CheminSortie), lecture.mesures)
    } yield (lecture, ecrit)

    resultat match {
      case Right((lecture, fichier)) =>
        println(s"Lecture   : ${lecture.mesures.size} mesure(s) sur ${lecture.lignesLues} ligne(s)")
        println(s"Parsing   : ${lecture.erreurs.size} ligne(s) illisible(s)")
        lecture.erreurs.foreach(erreur => println(s"            ligne ${erreur.numeroLigne} : ${erreur.message}"))
        println(s"Export    : $fichier")

      case Left(erreur) =>
        System.err.println(s"Échec : ${erreur.message}")
        sys.exit(1)
    }
  }
}
