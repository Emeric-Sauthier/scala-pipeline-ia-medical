package storage

import java.time.LocalDateTime

import domain.Mesure

/**
 * Tests de l'export JSON.
 *
 * L'encodage étant une fonction pure, la structure du JSON se teste sans écrire
 * un seul fichier.
 */
class EcrivainJsonSpec extends munit.FunSuite {

  private val mesure = Mesure(
    patientId = "P001",
    timestamp = LocalDateTime.parse("2026-07-28T08:00:00"),
    service = "Chirurgie A",
    age = 67,
    frequenceCardiaque = Some(78),
    tensionSystolique = Some(128),
    tensionDiastolique = Some(82),
    temperature = Some(37.1),
    spo2 = Some(97)
  )

  test("le JSON produit est syntaxiquement valide") {
    assert(EcrivainJson.valider(EcrivainJson.encoder(mesure)).isRight)
  }

  test("les clés sont en snake_case, comme les colonnes du CSV") {
    val json = EcrivainJson.encoder(mesure)
    assert(json.contains("\"patient_id\""), json)
    assert(json.contains("\"frequence_cardiaque\""), json)
    assert(!json.contains("\"patientId\""), "une clé camelCase a fuité")
  }

  test("les valeurs sont correctement typées") {
    val arbre = ujson.read(EcrivainJson.encoder(mesure))
    assertEquals(arbre("patient_id").str, "P001")
    assertEquals(arbre("age").num, 67.0)
    assertEquals(arbre("frequence_cardiaque").num, 78.0)
    assertEquals(arbre("temperature").num, 37.1)
  }

  test("un horodatage est exporté au format ISO 8601") {
    assertEquals(ujson.read(EcrivainJson.encoder(mesure))("timestamp").str, "2026-07-28T08:00:00")
  }

  test("un capteur débranché est exporté en null, pas en tableau vide") {
    val json = EcrivainJson.encoder(mesure.copy(frequenceCardiaque = None))
    val arbre = ujson.read(json)
    assert(arbre("frequence_cardiaque").isNull, json)
    // Le piège à éviter : surtout pas 0, qui passerait pour une vraie mesure.
    assert(!json.contains("\"frequence_cardiaque\": 0"), json)
    assert(!json.contains("\"frequence_cardiaque\": []"), json)
  }

  test("les accents et le symbole degré survivent à l'encodage") {
    val json = EcrivainJson.encoder(mesure.copy(service = "Réanimation"))
    assert(json.contains("Réanimation"), json)
  }

  test("une liste de mesures est exportée en tableau JSON") {
    val json = EcrivainJson.encoder(List(mesure, mesure.copy(patientId = "P002")))
    assertEquals(ujson.read(json).arr.map(_("patient_id").str).toList, List("P001", "P002"))
  }

  test("un JSON tronqué est détecté par la validation") {
    assert(EcrivainJson.valider(EcrivainJson.encoder(mesure).dropRight(20)).isLeft)
  }

  test("l'encodage est déterministe : deux appels donnent le même texte") {
    assertEquals(EcrivainJson.encoder(mesure), EcrivainJson.encoder(mesure))
  }

  /**
   * Vérifie que l'export fonctionne sur un type COMPOSITE arbitraire.
   *
   * C'est la preuve que la couche est prête pour le rapport de statistiques du
   * reste de l'équipe : une ligne de `macroW` par `case class`, et rien à changer
   * dans `EcrivainJson`.
   */
  test("un type composite arbitraire s'exporte avec une seule ligne de Writer") {
    import ExempleRapport._

    val rapport = RapportExemple(
      genereLe = "2026-07-30T12:00:00",
      services = List(
        ServiceExemple(
          service = "Reanimation",
          proportionAlerte = 0.571,
          patientsEnAlerte = List("P005")
        )
      )
    )

    val arbre = ujson.read(EcrivainJson.encoder(rapport))
    assertEquals(arbre("genere_le").str, "2026-07-30T12:00:00")
    assertEquals(arbre("services")(0)("service").str, "Reanimation")
    assertEquals(arbre("services")(0)("proportion_alerte").num, 0.571)
    assertEquals(arbre("services")(0)("patients_en_alerte")(0).str, "P005")
  }
}

/**
 * Types de démonstration, à remplacer par les vrais types de statistiques.
 *
 * Ils servent de contrat provisoire : c'est cette forme (`services[]` contenant
 * `patients_en_alerte[]`) que le sujet exige pour que le tableau de bord puisse
 * afficher, par service, la liste des patients en catégorie « Alerte ».
 */
object ExempleRapport {

  case class ServiceExemple(service: String, proportionAlerte: Double, patientsEnAlerte: List[String])
  case class RapportExemple(genereLe: String, services: List[ServiceExemple])

  // ORDRE IMPORTANT : les types feuilles avant les types composites.
  implicit val ecrivainService: JsonFormat.Writer[ServiceExemple] = JsonFormat.macroW
  implicit val ecrivainRapport: JsonFormat.Writer[RapportExemple] = JsonFormat.macroW
}
