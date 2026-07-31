import clean.Nettoyer

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

import pipeline.{Aggregation, Transformation}
import storage.{EcrivainJson, LecteurCsv}

/**
 * Point d'entrée du pipeline.
 * Enchaîne les cinq étapes : lecture du CSV, nettoyage, transformation,
 * agrégation, puis export JSON. Elle ne contient aucune règle métier : elle ne
 * fait qu'ordonnancer des effets autour d'appels à des fonctions pures.
 */
object Main {

  private val CheminEntree = "data/dataset_patients_exemple.csv"
  private val CheminSortie = "out/statistiques.json"

  def main(arguments: Array[String]): Unit =
    LecteurCsv.lire(CheminEntree) match {
      case Left(erreur) =>
        System.err.println(s"Échec : ${erreur.message}")
        sys.exit(1)

      case Right(lignes) =>
        val (erreurs, mesures) = Nettoyer.lireMesures(lignes)
        val statistiques = Aggregation.aggreger(Transformation.transformerMesures(mesures))

        println(s"Source : $CheminEntree")
        println(s"Lignes : ${lignes.size}")
        println(s"${erreurs.size} lignes invalides")
        println(s"${mesures.size} lignes valides")
        erreurs.foreach(e => println(s"[ERREUR]: ${e.message}"))
        println(s"Patients : ${statistiques.statistiquesPatients.size}")
        println(s"Services : ${statistiques.statistiquesServices.size}")

        EcrivainJson.exporter(CheminSortie, statistiques) match {
          case Right(fichier) =>
            println(s"Export : $fichier")

          case Left(erreur) =>
            System.err.println(s"Échec de l'export : ${erreur.message}")
            sys.exit(1)
        }
    }
}
