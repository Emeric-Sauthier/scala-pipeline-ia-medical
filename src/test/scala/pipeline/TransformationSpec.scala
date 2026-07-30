package pipelineTest

import org.scalatest.funsuite.AnyFunSuite
import java.time.LocalDateTime
import pipeline.Classification
import pipeline.Transformation
import domain.IndicateurVigilance
import domain.IndicateurVigilance._
import domain.Mesure

class TransformationSpec extends AnyFunSuite:

    val timestamp = LocalDateTime.parse("2026-07-28T08:00:00")
    val mesureVide = mesure()
    val mesureNormale = mesure(
        Some(78),
        Some(128),
        Some(82),
        Some(37.1),
        Some(97)
    )
    val mesureAlerte = mesureNormale.copy(spo2 = Some(89))
    val mesureSurveillance = mesureNormale.copy(spo2 = Some(94))
    
    def mesure(
        frequenceCardiaque: Option[Int] = None,
        tensionSystolique: Option[Int] = None,
        tensionDiastolique: Option[Int] = None,
        temperature: Option[Double] = None,
        spo2: Option[Int] = None
    ): Mesure =
        Mesure(
            patientId          = "001",
            timestamp          = timestamp,
            service            = "Chirurgie A",
            age                = 67,
            frequenceCardiaque = frequenceCardiaque,
            tensionSystolique  = tensionSystolique,
            tensionDiastolique = tensionDiastolique,
            temperature        = temperature,
            spo2               = spo2
        )

    // Test - Pression pulsee

    test("Pression pulsee - tension complete") {
        assert(Transformation.pressionPulsee(mesureNormale) == Some(46))
    }

    test("Pression pulsee - systolique manquante") {
        val m = mesure(tensionDiastolique = Some(82))
        assert(Transformation.pressionPulsee(m).isEmpty)
    }

    test("Pression pulsee - diastolique manquante") {
        val m = mesure(tensionSystolique = Some(128))
        assert(Transformation.pressionPulsee(m).isEmpty)
    }

    test("Pression pulsee - mesure vide") {
        assert(Transformation.pressionPulsee(mesureVide).isEmpty)
    }

    // Test - Pression arterielle moyenne

    test("Pression arterielle moyenne - tension complete") {
        assert(Transformation.pressionArterielleMoyenne(mesureNormale) == Some(97.3)) // 97.333... arrondi a 97.3
    }

    test("Pression arterielle moyenne - pas de division entiere") {
        val m = mesure(tensionSystolique = Some(120), tensionDiastolique = Some(80)) // 93.333... arrondi a 93.3
        assert(Transformation.pressionArterielleMoyenne(m) == Some(93.3))
    }

    test("Pression arterielle moyenne - tension incomplete") {
        val m = mesure(tensionSystolique = Some(120))
        assert(Transformation.pressionArterielleMoyenne(m).isEmpty)
    }

    // Test - Classification d'une mesure complete

    test("Classification mesure - tous les parametres normaux") {
        val c = Classification.observerMesure(mesureNormale)
        assert(c.frequenceCardiaque == Some(Normal))
        assert(c.tensionSystolique == Some(Normal))
        assert(c.tensionDiastolique == Some(Normal))
        assert(c.temperature == Some(Normal))
        assert(c.spo2 == Some(Normal))
    }

    test("Classification mesure - un parametre absent reste a None") {
        val c = Classification.observerMesure(mesureNormale.copy(frequenceCardiaque = None))
        assert(c.frequenceCardiaque.isEmpty)
        assert(c.tensionSystolique == Some(Normal))
        assert(c.tensionDiastolique == Some(Normal))
        assert(c.temperature == Some(Normal))
        assert(c.spo2 == Some(Normal))
    }

    test("Classification mesure - mesure vide ne produit aucun niveau") {
        assert(Classification.observerMesure(mesureVide).observations.isEmpty)
    }

    test("Classification mesure - niveaux ignore les parametres absents") {
        val m = mesure(frequenceCardiaque = Some(78), spo2 = Some(97))
        assert(Classification.observerMesure(m).observations.size == 2)
    }

    // Test - Vigilance globale

    test("Vigilance globale - tous les parametres normaux") {
        assert(Transformation.transformerMesure(mesureNormale).vigilanceGlobale == Some(Normal))
    }

    test("Vigilance globale - un seul parametre en surveillance") {
        assert(Transformation.transformerMesure(mesureSurveillance).vigilanceGlobale == Some(Surveillance))
    }

    test("Vigilance globale - un seul parametre en alerte") {
        assert(Transformation.transformerMesure(mesureAlerte).vigilanceGlobale == Some(Alerte))
    }

    test("Vigilance globale - Alerte l'emporte sur Surveillance") {
        // SpO2 94 => Surveillance, frequence cardiaque 35 => Alerte
        val m = mesureSurveillance.copy(frequenceCardiaque = Some(35))
        assert(Transformation.transformerMesure(m).vigilanceGlobale == Some(Alerte))
    }

    test("Vigilance globale - un seul parametre renseigne") {
        val m = mesure(spo2 = Some(97))
        assert(Transformation.transformerMesure(m).vigilanceGlobale == Some(Normal))
    }

    test("Vigilance globale - mesure vide") {
        assert(Transformation.transformerMesure(mesureVide).vigilanceGlobale.isEmpty)
    }

    // Test - Transformation d'une mesure

    test("Transformer - mesure d'origine conservee") {
        assert(Transformation.transformerMesure(mesureNormale).mesure == mesureNormale)
    }

    test("Transformer - indicateurs derives calcules") {
        val mesureTransformee = Transformation.transformerMesure(mesureNormale)
        assert(mesureTransformee.pressionPulsee == Some(46))
        assert(mesureTransformee.pressionArterielleMoyenne == Some(97.3))
    }

    test("Transformer - mesure vide : indicateurs derives absents") {
        val mesureTransformee = Transformation.transformerMesure(mesureVide)
        assert(mesureTransformee.pressionPulsee.isEmpty)
        assert(mesureTransformee.pressionArterielleMoyenne.isEmpty)
    }

    // Test - Transformation d'une liste de mesures

    test("Transformer toutes - liste vide") {
        assert(Transformation.transformerMesures(Nil).isEmpty)
    }

    test("Transformer toutes - la taille est preservee") {
        val mesures = List(mesureNormale, mesureSurveillance, mesureAlerte)
        assert(Transformation.transformerMesures(mesures).size == 3)
    }

    test("Transformer toutes - l'ordre est preserve") {
        val mesures = List(mesureNormale, mesureAlerte)
        val severites = Transformation.transformerMesures(mesures).map(_.vigilanceGlobale)
        assert(severites == List(Some(Normal), Some(Alerte)))
    }

    test("Transformer toutes - une mesure vide n'interrompt pas le lot") {
        val mesures = List(mesureNormale, mesureVide, mesureAlerte)
        val severites = Transformation.transformerMesures(mesures).map(_.vigilanceGlobale)
        assert(severites == List(Some(Normal), None, Some(Alerte)))
    }

end TransformationSpec
