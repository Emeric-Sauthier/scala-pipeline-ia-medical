package domainTest

import org.scalatest.funsuite.AnyFunSuite
import domain.IndicateurVigilance._

class IndicateurVigilanceSpec extends AnyFunSuite:

    // Test - Severite & ordre
    test("Severite - Normal est moins severe que Surveillance") {
        assert(Normal.severite < Surveillance.severite)
    }

    test("Severite - Surveillance est moins severe que Alerte") {
        assert(Surveillance.severite < Alerte.severite)
    }

    test("Ordering - max sur une liste d'indicateurs") {
        val indicateurs: List[IndicateurVigilance] = List(Normal, Alerte, Surveillance)
        assert(indicateurs.max == Alerte)
    }

end IndicateurVigilanceSpec