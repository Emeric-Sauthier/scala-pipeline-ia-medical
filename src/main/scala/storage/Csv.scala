package storage

import scala.annotation.tailrec

object Csv {

  def decouper(ligne: String, separateur: Char = ','): List[String] = {

    @tailrec
    def parcourir(
        reste: List[Char],
        champCourant: List[Char],     // accumulé à l'envers
        champsTermines: List[String], // accumulés à l'envers
        dansGuillemets: Boolean
    ): List[String] =
      reste match {
        case Nil =>
          (champCourant.reverse.mkString :: champsTermines).reverse

        // Guillemet doublé à l'intérieur d'un champ cité : un guillemet littéral.
        case '"' :: '"' :: suite if dansGuillemets =>
          parcourir(suite, '"' :: champCourant, champsTermines, dansGuillemets = true)

        case '"' :: suite =>
          parcourir(suite, champCourant, champsTermines, !dansGuillemets)

        case caractere :: suite if caractere == separateur && !dansGuillemets =>
          parcourir(suite, Nil, champCourant.reverse.mkString :: champsTermines, dansGuillemets = false)

        case caractere :: suite =>
          parcourir(suite, caractere :: champCourant, champsTermines, dansGuillemets)
      }

    parcourir(ligne.toList, Nil, Nil, dansGuillemets = false)
  }
}
