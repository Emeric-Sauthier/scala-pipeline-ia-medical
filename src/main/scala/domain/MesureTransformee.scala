package domain

final case class MesureTransformee(
    mesure: Mesure,
    pressionPulsee: Option[Int],
    pressionArterielleMoyenne: Option[Double],
    observation: ObservationMesure,
    vigilanceGlobale: Option[IndicateurVigilance]
)
