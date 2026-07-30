package storage

class CsvSpec extends munit.FunSuite {

  test("découpe une ligne simple") {
    assertEquals(Csv.decouper("a,b,c"), List("a", "b", "c"))
  }

  test("conserve les champs vides, y compris en fin de ligne") {
    assertEquals(Csv.decouper("a,,c,"), List("a", "", "c", ""))
  }

  test("conserve un champ vide en début de ligne") {
    assertEquals(Csv.decouper(",b"), List("", "b"))
  }

  test("une ligne vide donne un unique champ vide") {
    assertEquals(Csv.decouper(""), List(""))
  }

  test("un séparateur entre guillemets ne découpe pas le champ") {
    assertEquals(Csv.decouper("""a,"Chirurgie A, lit 3",c"""), List("a", "Chirurgie A, lit 3", "c"))
  }

  test("un guillemet doublé est un guillemet littéral") {
    // Ligne CSV : a,"dit ""oui""",c   ->   champs : a | dit "oui" | c
    assertEquals(Csv.decouper("a,\"dit \"\"oui\"\"\",c"), List("a", "dit \"oui\"", "c"))
  }

  test("supporte un autre séparateur") {
    assertEquals(Csv.decouper("a;b;c", separateur = ';'), List("a", "b", "c"))
  }

  test("reste en récursion terminale sur une ligne très longue") {
    // Sans @tailrec, ce test provoquerait un StackOverflowError.
    val ligne = List.fill(50000)("x").mkString(",")
    assertEquals(Csv.decouper(ligne).size, 50000)
  }
}
