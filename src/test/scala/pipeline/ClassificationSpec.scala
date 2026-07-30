package pipelineTest

import org.scalatest.funsuite.AnyFunSuite
import pipeline.Classification
import domain.IndicateurVigilance._

class ClassificationSpec extends AnyFunSuite:

    // Test - Classification frequence cardiaque

    test("Alerte Basse - Frequence cardiaque") { 
        assert(Classification.classerFrequenceCardiaque(39) == Alerte)
    }

    test("Surveillance Basse - Frequence cardiaque") { 
        assert(Classification.classerFrequenceCardiaque(49) == Surveillance)
    }

    test("Normal - Frequence cardiaque") { 
        assert(Classification.classerFrequenceCardiaque(60) == Normal)
    }

    test("Surveillance Haute - Frequence cardiaque") { 
        assert(Classification.classerFrequenceCardiaque(109) == Surveillance)
    }

    test("Alerte Haute - Frequence cardiaque") { 
        assert(Classification.classerFrequenceCardiaque(160) == Alerte)
    }

    // Test - Classification tension systolique

    test("Alerte Basse - Tension systolique") { 
        assert(Classification.classerTensionSystolique(79) == Alerte)
    }

    test("Surveillance Basse - Tension systolique") { 
        assert(Classification.classerTensionSystolique(89) == Surveillance)
    }

    test("Normal - Tension systolique") { 
        assert(Classification.classerTensionSystolique(120) == Normal)
    }

    test("Surveillance Haute - Tension systolique") { 
        assert(Classification.classerTensionSystolique(141) == Surveillance)
    }

    test("Alerte Haute - Tension systolique") { 
        assert(Classification.classerTensionSystolique(181) == Alerte)
    }

    // Test - Classification tension diastolique

    test("Alerte Basse - Tension diastolique") { 
        assert(Classification.classerTensionDiastolique(49) == Alerte)
    }

    test("Surveillance Basse - Tension diastolique") { 
        assert(Classification.classerTensionDiastolique(59) == Surveillance)
    }

    test("Normal - Tension diastolique") { 
        assert(Classification.classerTensionDiastolique(80) == Normal)
    }

    test("Surveillance Haute - Tension diastolique") { 
        assert(Classification.classerTensionDiastolique(91) == Surveillance)
    }

    test("Alerte Haute - Tension diastolique") { 
        assert(Classification.classerTensionDiastolique(111) == Alerte)
    }

    // Test - Classification temperature

    test("Alerte Basse - Temperature") { 
        assert(Classification.classerTemperature(34.9) == Alerte)
    }

    test("Surveillance Basse - Temperature") { 
        assert(Classification.classerTemperature(35.9) == Surveillance)
    }

    test("Normal - Temperature") { 
        assert(Classification.classerTemperature(37) == Normal)
    }

    test("Surveillance Haute - Temperature") { 
        assert(Classification.classerTemperature(38.4) == Surveillance)
    }

    test("Alerte Haute - Temperature") { 
        assert(Classification.classerTemperature(38.6) == Alerte)
    }

    // Test - Classification saturation oxygène

    test("Alerte Basse - Spo2") { 
        assert(Classification.classerSpo2(89) == Alerte)
    }

    test("Surveillance Basse - Spo2") { 
        assert(Classification.classerSpo2(94) == Surveillance)
    }

    test("Normal - Spo2") { 
        assert(Classification.classerSpo2(95) == Normal)
    }

end ClassificationSpec