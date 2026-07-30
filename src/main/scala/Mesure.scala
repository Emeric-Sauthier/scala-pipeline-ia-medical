import java.time.LocalDateTime

case class Mesure(
    patientId: String,
    timestamp: LocalDateTime,
    service: String,
    age: Int,
    frequenceCardiaque: Option[Int],
    tensionSystolique: Option[Int],
    tensionDiastolique: Option[Int],
    temperature: Option[Double],
    spo2: Option[Int]
)
