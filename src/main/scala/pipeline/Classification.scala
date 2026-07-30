package pipeline

import domain.ObservationMesure
import domain.IndicateurVigilance
import domain.IndicateurVigilance._
import domain.SeuilsVigilance._
import domain.Mesure

object Classification {
    def classerFrequenceCardiaque(freq: Int): IndicateurVigilance = freq match {
        case v if v < FrequenceCardiaque.alerteBasse => Alerte
        case v if v < FrequenceCardiaque.normalMin => Surveillance
        case v if v <= FrequenceCardiaque.normalMax => Normal
        case v if v <= FrequenceCardiaque.surveillanceMax => Surveillance
        case _ => Alerte
    }

    def classerTensionSystolique(sys: Int): IndicateurVigilance = sys match {
        case v if v <  TensionSystolique.alerteBasse => Alerte
        case v if v <  TensionSystolique.normalMin => Surveillance
        case v if v <= TensionSystolique.normalMax => Normal
        case v if v <= TensionSystolique.surveillanceMax => Surveillance
        case _ => Alerte
    }

    def classerTensionDiastolique(dia: Int): IndicateurVigilance = dia match {
        case v if v <  TensionDiastolique.alerteBasse => Alerte
        case v if v <  TensionDiastolique.normalMin => Surveillance
        case v if v <= TensionDiastolique.normalMax => Normal
        case v if v <= TensionDiastolique.surveillanceMax => Surveillance
        case _ => Alerte
    }

    def classerTemperature(temp: Double): IndicateurVigilance = temp match {
        case v if v <  Temperature.alerteBasse => Alerte
        case v if v <  Temperature.normalMin => Surveillance
        case v if v <= Temperature.normalMax => Normal
        case v if v <= Temperature.surveillanceMax => Surveillance
        case _ => Alerte
    }

    def classerSpo2(spo2: Int): IndicateurVigilance = spo2 match {
        case v if v < Spo2.alerteBasse => Alerte
        case v if v < Spo2.normalMin => Surveillance
        case _ => Normal
    }

    def observerMesure(mesure: Mesure): ObservationMesure =
        ObservationMesure(
            frequenceCardiaque = mesure.frequenceCardiaque.map(classerFrequenceCardiaque),
            tensionSystolique = mesure.tensionSystolique.map(classerTensionSystolique),
            tensionDiastolique = mesure.tensionDiastolique.map(classerTensionDiastolique),
            temperature = mesure.temperature.map(classerTemperature),
            spo2 = mesure.spo2.map(classerSpo2)
        )
}