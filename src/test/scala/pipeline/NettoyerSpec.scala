package pipelineTest

import org.scalatest.funsuite.AnyFunSuite
import java.time.LocalDateTime
import domain.ErreurParsing
import pipeline.Nettoyer

class NettoyerSpec extends AnyFunSuite:

    test("timestamp") {
        assert(Nettoyer.lireTimestamp(0, "abc") == Left(ErreurParsing.HorodatageInvalide(0, "abc")))
        assert(Nettoyer.lireTimestamp(0, "2026-07-28T08:00:00") == Right(LocalDateTime.parse("2026-07-28T08:00:00")))
    }

    test("id") {
        assert(Nettoyer.lirePatientId(0, "   ") == Left(ErreurParsing.ChampObligatoireVide(0, "   ")))
        assert(Nettoyer.lirePatientId(0, "abc") == Right("abc"))
    }

    test("service") {
        assert(Nettoyer.lireService(0, "   ") == Left(ErreurParsing.ChampObligatoireVide(0, "   ")))
        assert(Nettoyer.lireService(0, "abc") == Right("abc"))
    }

    test("age") {
        assert(Nettoyer.lireAge(0, "   ") == Left(ErreurParsing.ChampObligatoireVide(0, "   ")))
        assert(Nettoyer.lireAge(0, "abc") == Left(ErreurParsing.ChampNonNumerique(0, "abc")))
        assert(Nettoyer.lireAge(0, "14") == Right(14))
    }

    test("fréquence cardiaque") {
        assert(Nettoyer.lireFrequenceCardiaque(0, "   ") == Right(None))
        assert(Nettoyer.lireFrequenceCardiaque(0, "abc") == Left(ErreurParsing.ChampNonNumerique(0, "abc")))
        assert(Nettoyer.lireFrequenceCardiaque(0, "100") == Right(Some(100)))
        assert(Nettoyer.lireFrequenceCardiaque(0, "-1") match
            case Left(ErreurParsing.ValeurNumeriqueInvalide(0, "-1", _)) => true
            case _ => false
        )
        assert(Nettoyer.lireFrequenceCardiaque(0, "294") match
            case Left(ErreurParsing.ValeurNumeriqueInvalide(0, "294", _)) => true
            case _ => false
        )
    }

    test("tension systolique") {
        assert(Nettoyer.lireTensionSystolique(0, "   ") == Right(None))
        assert(Nettoyer.lireTensionSystolique(0, "abc") == Left(ErreurParsing.ChampNonNumerique(0, "abc")))
        assert(Nettoyer.lireTensionSystolique(0, "100") == Right(Some(100)))
        assert(Nettoyer.lireTensionSystolique(0, "-1") match
            case Left(ErreurParsing.ValeurNumeriqueInvalide(0, "-1", _)) => true
            case _ => false
        )
        assert(Nettoyer.lireTensionSystolique(0, "294") match
            case Left(ErreurParsing.ValeurNumeriqueInvalide(0, "294", _)) => true
            case _ => false
        )
    }

    test("tension diastolique") {
        assert(Nettoyer.lireTensionDiastolique(0, "   ") == Right(None))
        assert(Nettoyer.lireTensionDiastolique(0, "abc") == Left(ErreurParsing.ChampNonNumerique(0, "abc")))
        assert(Nettoyer.lireTensionDiastolique(0, "100") == Right(Some(100)))
        assert(Nettoyer.lireTensionDiastolique(0, "-1") match
            case Left(ErreurParsing.ValeurNumeriqueInvalide(0, "-1", _)) => true
            case _ => false
        )
        assert(Nettoyer.lireTensionDiastolique(0, "294") match
            case Left(ErreurParsing.ValeurNumeriqueInvalide(0, "294", _)) => true
            case _ => false
        )
    }

    test("température") {
        assert(Nettoyer.lireTemperature(0, "   ") == Right(None))
        assert(Nettoyer.lireTemperature(0, "abc") == Left(ErreurParsing.ChampNonNumerique(0, "abc")))
        assert(Nettoyer.lireTemperature(0, "38") == Right(Some(38)))
        assert(Nettoyer.lireTemperature(0, "-1") match
            case Left(ErreurParsing.ValeurNumeriqueInvalide(0, "-1", _)) => true
            case _ => false
        )
        assert(Nettoyer.lireTemperature(0, "294") match
            case Left(ErreurParsing.ValeurNumeriqueInvalide(0, "294", _)) => true
            case _ => false
        )
    }

    test("SpO2") {
        assert(Nettoyer.lireSpo2(0, "   ") == Right(None))
        assert(Nettoyer.lireSpo2(0, "abc") == Left(ErreurParsing.ChampNonNumerique(0, "abc")))
        assert(Nettoyer.lireSpo2(0, "98") == Right(Some(98)))
        assert(Nettoyer.lireSpo2(0, "-1") match
            case Left(ErreurParsing.ValeurNumeriqueInvalide(0, "-1", _)) => true
            case _ => false
        )
        assert(Nettoyer.lireSpo2(0, "294") match
            case Left(ErreurParsing.ValeurNumeriqueInvalide(0, "294", _)) => true
            case _ => false
        )
    }

end NettoyerSpec
