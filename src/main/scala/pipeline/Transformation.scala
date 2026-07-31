package pipeline

import domain.IndicateurVigilance
import domain.Mesure
import domain.MesureTransformee

object Transformation {

    def pressionPulsee(mesure: Mesure): Option[Int] = {
        for {
            systolique  <- mesure.tensionSystolique
            diastolique <- mesure.tensionDiastolique
        } yield systolique - diastolique
    }

    def pressionArterielleMoyenne(mesure: Mesure): Option[Double] = {
        for {
            systolique  <- mesure.tensionSystolique
            diastolique <- mesure.tensionDiastolique
        } yield arrondir(diastolique + (systolique - diastolique) / 3.0, 1)
    }

    def arrondir(valeur: Double, precision: Int): Double = Math.round(valeur * Math.pow(10, precision)) / Math.pow(10, precision)

    def transformerMesure(mesure: Mesure): MesureTransformee = {
        val observation = Classification.observerMesure(mesure)

        MesureTransformee(
            mesure,
            pressionPulsee(mesure),
            pressionArterielleMoyenne(mesure),
            observation,
            observation.recupererObservationMax()
        )
    }

    def transformerMesures(mesures: List[Mesure]): List[MesureTransformee] = mesures.map(transformerMesure)
}