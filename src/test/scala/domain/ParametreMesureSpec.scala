package domainTest

import org.scalatest.funsuite.AnyFunSuite
import java.time.LocalDateTime
import domain.Mesure
import domain.MesureTransformee
import domain.ParametreMesure
import pipeline.Transformation

class ParametreMesureSpec extends AnyFunSuite:

    val timestamp = LocalDateTime.parse("2026-07-28T08:00:00")

    def mesure(
        frequenceCardiaque: Option[Int] = None,
        tensionSystolique: Option[Int] = None,
        tensionDiastolique: Option[Int] = None,
        temperature: Option[Double] = None,
        spo2: Option[Int] = None
    ): MesureTransformee =
        Transformation.transformerMesure(
            Mesure(
                patientId          = "P001",
                timestamp          = timestamp,
                service            = "Chirurgie A",
                age                = 67,
                frequenceCardiaque = frequenceCardiaque,
                tensionSystolique  = tensionSystolique,
                tensionDiastolique = tensionDiastolique,
                temperature        = temperature,
                spo2               = spo2
            )
        )

    val mesureVide    = mesure()
    val mesureNormale = mesure(Some(78), Some(128), Some(82), Some(37.1), Some(97))

    test("Parametres - les sept parametres sont declares") {
        assert(ParametreMesure.parametres.size == 7)
    }

    test("Parametres - les noms sont uniques") {
        assert(ParametreMesure.parametres.map(_.nom).distinct.size == 7)
    }

    test("Parametres - les entiers sont convertis en Double") {
        // Sans le .toDouble, la somme resterait entière et la moyenne subirait une division entière
        assert(ParametreMesure.FrequenceCardiaque.extraireValeur(mesureNormale) == Some(78.0))
        assert(ParametreMesure.Spo2.extraireValeur(mesureNormale) == Some(97.0))
    }

    test("Parametres - les tensions sont extraites") {
        assert(ParametreMesure.TensionSystolique.extraireValeur(mesureNormale) == Some(128.0))
        assert(ParametreMesure.TensionDiastolique.extraireValeur(mesureNormale) == Some(82.0))
    }

    test("Parametres - la temperature est deja un Double") {
        assert(ParametreMesure.Temperature.extraireValeur(mesureNormale) == Some(37.1))
    }

    test("Parametres - les indicateurs derives sont extraits") {
        // Pression pulsée 128 - 82 = 46 ; PAM 82 + 46/3 = 97.333 arrondi à 97.3 par Transformation
        assert(ParametreMesure.PressionPulsee.extraireValeur(mesureNormale) == Some(46.0))
        assert(ParametreMesure.PressionArterielleMoyenne.extraireValeur(mesureNormale) == Some(97.3))
    }

    test("Parametres - capteur muet : aucune valeur extraite") {
        assert(ParametreMesure.parametres.forall(_.extraireValeur(mesureVide).isEmpty))
    }

    test("Parametres - tension incomplete : les derives sont absents") {
        val partielle = mesure(tensionSystolique = Some(120))
        assert(ParametreMesure.TensionSystolique.extraireValeur(partielle) == Some(120.0))
        assert(ParametreMesure.PressionPulsee.extraireValeur(partielle).isEmpty)
        assert(ParametreMesure.PressionArterielleMoyenne.extraireValeur(partielle).isEmpty)
    }

end ParametreMesureSpec
