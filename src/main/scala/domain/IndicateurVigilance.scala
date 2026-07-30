package domain

sealed trait IndicateurVigilance

object IndicateurVigilance {
    case object Normal extends IndicateurVigilance
    case object Surveillance extends IndicateurVigilance
    case object Alerte extends IndicateurVigilance
}

object SeuilsVigilance {
    // Seuils pour la fréquence cardiaque
    object FrequenceCardiaque {
        val alerteBasse       = 40 
        val normalMin         = 50
        val normalMax         = 100
        val surveillanceMax   = 130
    }

    // Seuils pour la tension systolique
    object TensionSystolique {
        val alerteBasse     = 80
        val normalMin       = 90
        val normalMax       = 140
        val surveillanceMax = 180
    }
    
    // Seuils pour la tension diastolique
    object TensionDiastolique {
        val alerteBasse     = 50
        val normalMin       = 60
        val normalMax       = 90
        val surveillanceMax = 110
    }

    // Seuils pour la température corporelle
    object Temperature {
        val alerteBasse     = 35.0
        val normalMin       = 36.0
        val normalMax       = 37.5
        val surveillanceMax = 38.5
    }

    // Seuils pour la saturation en oxygène
    object Spo2 {
        val alerteBasse = 90
        val normalMin   = 95
    }
}