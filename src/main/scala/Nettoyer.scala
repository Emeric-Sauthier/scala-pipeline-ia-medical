import pipeline.Mesure
import java.time.LocalDateTime
import scala.util.Try

object Nettoyer:
    def lireMesure(ligneCSV: String): Either[String, Mesure] = {
        ligneCSV.split(",") match
            case Array(patientId, timestamp, service, age, frequenceCardiaque, tensionSystolique, tensionDiastolique, temperature, spo2) => {
                for {
                    _patientId <- lirePatientId(patientId)
                    _timestamp <- lireTimestamp(timestamp)
                } yield Mesure(_patientId, _timestamp, "", 0, Some(0), Some(0), Some(0), Some(0), Some(0))
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