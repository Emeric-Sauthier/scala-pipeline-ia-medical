package clean

import domain.Mesure
import java.time.LocalDateTime
import scala.math.Ordering.Implicits.infixOrderingOps
import scala.util.Try

object Nettoyer:
    def lireMesures(lignesCSV: List[String]): List[Mesure] = {
        removeDuplicates(lignesCSV.map(lireMesure).collect {
            case Right(value) => value
        })
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

    def lireMesure(ligneCSV: String): Either[String, Mesure] = {
        ligneCSV.split(",") match
            case Array(patientId, timestamp, service, age, frequenceCardiaque, tensionSystolique, tensionDiastolique, temperature, spo2) => {
                for {
                    _patientId <- lirePatientId(patientId)
                    _timestamp <- lireTimestamp(timestamp)
                    _service <- lireService(service)
                    _age <- lireAge(age)
                    _frequenceCardiaque <- lireFrequenceCardiaque(frequenceCardiaque)
                    _tensionSystolique <- lireTensionSystolique(tensionSystolique)
                    _tensionDiastolique <- lireTensionDiastolique(tensionDiastolique)
                    _temperature <- lireTemprature(temperature)
                    _spo2 <- lireSpo2(spo2)
                } yield Mesure(_patientId, _timestamp, _service, _age, _frequenceCardiaque, _tensionSystolique, _tensionDiastolique, _temperature, _spo2)
            }
            case _ => Left("Pas assez ou trop de champs dans le CSV !")
    }

    def lirePatientId(id: String): Either[String, String] = id.trim match
        case "" => Left("ID non spécifié !")
        case i => Right(i)

    def lireTimestamp(timestamp: String): Either[String, LocalDateTime] = Try(LocalDateTime.parse(timestamp.trim)).fold(
        err => Left(s"Timestamp '$timestamp' invalide !"),
        Right.apply
    )

    def lireService(service: String): Either[String, String] = service.trim match
        case "" => Left("Service vide !")
        case s => Right(s)

    def lireAge(age: String): Either[String, Int] = age.trim.toIntOption match
        case None => Left("Âge non spécifié ou invalide !")
        case Some(age) if age < 0 => Left("Âge négatif !")
        case Some(age) => Right(age)

    def lireCapteur[T](valeur: String, nom: String, min: T, max: T, convertisseur: String => Option[T])(using Ordering[T]): Either[String, Option[T]] = valeur.trim match
        case "" => Right(None)
        case n => convertisseur(n) match
            case None => Left(s"$nom invalide !")
            case Some(x) if (x < min || x > max) => Left(s"$nom invalide, valeur aberrante !")
            case Some(x) => Right(Some(x))

    def lireCapteurInt(valeur: String, nom: String, min: Int, max: Int) = lireCapteur[Int](valeur, nom, min, max, _.toIntOption)
    def lireCapteurDouble(valeur: String, nom: String, min: Double, max: Double) = lireCapteur[Double](valeur, nom, min, max, _.toDoubleOption)

    def lireFrequenceCardiaque(frequenceCardiaque: String) = lireCapteurInt(frequenceCardiaque, "Fréquence cardiaque", 30, 220)
    def lireTensionSystolique(tensionSystolique: String) = lireCapteurInt(tensionSystolique, "Tension systolique", 40, 250)
    def lireTensionDiastolique(tensionDiastolique: String) = lireCapteurInt(tensionDiastolique, "Tension diastolique", 20, 150)
    def lireTemprature(temperature: String) = lireCapteurDouble(temperature, "Température", 30, 42)
    def lireSpo2(spo2: String) = lireCapteurInt(spo2, "SpO2", 0, 100)
