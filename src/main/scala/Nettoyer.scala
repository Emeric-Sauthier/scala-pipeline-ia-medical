package clean

import domain.ErreurParsing
import domain.Mesure
import java.time.LocalDateTime
import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.Try

object Nettoyer:
    def lireMesures(lignesCSV: List[String]): (List[ErreurParsing], List[Mesure]) = {
        lignesCSV.zipWithIndex.map((i, a) => lireMesure(i, a)).partitionMap(identity) match
            case (bad, good) => (bad, removeDuplicates(good))
    }

    def removeDuplicates(vals: List[Mesure]): List[Mesure] = {
        vals.foldLeft(List.empty) {
            (acc, item) => if (acc.exists(a => a.patientId == item.patientId && a.timestamp == item.timestamp)) {
                acc
            } else {
                acc.appended(item)
            }
        }
    }

    def lireMesure(ligneCSV: String, i: Int): Either[ErreurParsing, Mesure] = {
        ligneCSV.split(",") match
            case Array(patientId, timestamp, service, age, frequenceCardiaque, tensionSystolique, tensionDiastolique, temperature, spo2) => {
                for {
                    _patientId <- lirePatientId(i, patientId)
                    _timestamp <- lireTimestamp(i, timestamp)
                    _service <- lireService(i, service)
                    _age <- lireAge(i, age)
                    _frequenceCardiaque <- lireFrequenceCardiaque(i, frequenceCardiaque)
                    _tensionSystolique <- lireTensionSystolique(i, tensionSystolique)
                    _tensionDiastolique <- lireTensionDiastolique(i, tensionDiastolique)
                    _temperature <- lireTemperature(i, temperature)
                    _spo2 <- lireSpo2(i, spo2)
                } yield Mesure(_patientId, _timestamp, _service, _age, _frequenceCardiaque, _tensionSystolique, _tensionDiastolique, _temperature, _spo2)
            }
            case err => Left(ErreurParsing.NombreDeChampsInvalide(i, 9, err.size))
    }

    def lirePatientId(i: Int, id: String): Either[ErreurParsing, String] = id.trim match
        case "" => Left(ErreurParsing.ChampObligatoireVide(i, id))
        case i => Right(i)

    def lireTimestamp(i: Int, timestamp: String): Either[ErreurParsing, LocalDateTime] = Try(LocalDateTime.parse(timestamp.trim)).fold(
        err => Left(ErreurParsing.HorodatageInvalide(i, timestamp)),
        Right.apply
    )

    def lireService(i: Int, service: String): Either[ErreurParsing, String] = service.trim match
        case "" => Left(ErreurParsing.ChampObligatoireVide(i, service))
        case s => Right(s)

    def lireAge(i: Int, age: String): Either[ErreurParsing, Int] = age.trim match
        case "" => Left(ErreurParsing.ChampObligatoireVide(i, age))
        case a => a.toIntOption match
            case None => Left(ErreurParsing.ChampNonNumerique(i, age))
            case Some(_age) if _age < 0 => Left(ErreurParsing.ValeurNumeriqueInvalide(i, age, "Âge négatif !"))
            case Some(_age) => Right(_age)

    def lireCapteur[T](i: Int, valeur: String, nom: String, min: T, max: T, convertisseur: String => Option[T])(using Ordering[T]): Either[ErreurParsing, Option[T]] = valeur.trim match
        case "" => Right(None)
        case n => convertisseur(n) match
            case None => Left(ErreurParsing.ChampNonNumerique(i, valeur))
            case Some(x) if (x < min || x > max) => Left(ErreurParsing.ValeurNumeriqueInvalide(i, valeur, s"doit être compris entre $min et $max"))
            case Some(x) => Right(Some(x))

    def lireCapteurInt(i: Int, valeur: String, nom: String, min: Int, max: Int) = lireCapteur[Int](i, valeur, nom, min, max, _.toIntOption)
    def lireCapteurDouble(i: Int, valeur: String, nom: String, min: Double, max: Double) = lireCapteur[Double](i, valeur, nom, min, max, _.toDoubleOption)

    def lireFrequenceCardiaque(i: Int, frequenceCardiaque: String) = lireCapteurInt(i, frequenceCardiaque, "Fréquence cardiaque", 30, 220)
    def lireTensionSystolique(i: Int, tensionSystolique: String) = lireCapteurInt(i, tensionSystolique, "Tension systolique", 40, 250)
    def lireTensionDiastolique(i: Int, tensionDiastolique: String) = lireCapteurInt(i, tensionDiastolique, "Tension diastolique", 20, 150)
    def lireTemperature(i: Int, temperature: String) = lireCapteurDouble(i, temperature, "Température", 30, 42)
    def lireSpo2(i: Int, spo2: String) = lireCapteurInt(i, spo2, "SpO2", 0, 100)
