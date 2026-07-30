package domain

import domain.IndicateurVigilance

final case class ObservationMesure(
    frequenceCardiaque: Option[IndicateurVigilance],
    tensionSystolique: Option[IndicateurVigilance],
    tensionDiastolique: Option[IndicateurVigilance],
    temperature: Option[IndicateurVigilance],
    spo2: Option[IndicateurVigilance]
) {
    def observations: List[IndicateurVigilance] = List(
        frequenceCardiaque,
        tensionSystolique,
        tensionDiastolique,
        temperature,
        spo2
    ).flatten

    def recupererObservationMax(): Option[IndicateurVigilance] = observations.maxOption
}
