# Projet Scala - Pipeline IA médicale

Contributeurs : Alexis GODARD, Emeric SAUTHIER, Thomas SAYEN

## Analyse du sujet

Des données sont récupérées à partir de capteurs intégrés à des lits d'hopital. Les capteurs peuvent subir des perturbations et envoyer des valeur aberrantes, en double ou même ne rien envoyer (si débranché par exemple).
L'équipe soignante souhaite extraire des statistiques par patient et par service, afin d'exporter un rapport au format JSON exploitable sur un tableau de bord.

Il sera donc important de nettoyer les données avant de les traiter.
Voici une simplification du flux de données :
Collecte -> Nettoyage -> Transformation -> Aggrégation -> Export

## Cadrage

Voici les différentes tâches identifiées pour la réalisation de ce projet :
- Couche I/O => Lecture du fichier csv & export au format JSON
- Nettoyage des données => Filtrer les données pour garder uniquement celles exploitables & elaboration d'un rapport de nettoyage
- Transformation des données => Définition des indicateurs de vigilance et classification des données (Normal, Surveillance, Alerte)
- Aggrégation => Calcule de statistiques
- Tests => Ecriture de tests pour valider le bon fonctionnement du pipeline

Répartition des rôles :
- Alexis =>
- Emeric =>
- Thomas =>

## Modélisation

### Mesures

Les mesures seront modélisées à l'aide d'une `case class`, comportant les informations suivantes :
- l'ID du patient
- le timestamp de la mesure
- le nom du service
- l'âge du patient
- la fréquence cardiaque du patient (entre 30 et 220 bpm)
- la tension systolique du patient (entre 40 et 250 mmHg)
- la tension diastolique du patient (entre 20 et 150 mmHg)
- la température corporelle du patient (entre 30 et 42 °C)
- la saturation en oxygène du patient (entre 0 et 100 %)

L'ensemble des champs relatifs à une mesure de capteur sera modélisé par une `Option`. La valeur sera ainsi à `None` si une valeur est manquante.

Exemple :
```scala
case class Mesure(
    ...
)
```

### Indicateurs de vigilance

Les indicateurs de vigilance seront modélisés à l'aide d'un `sealed trait`, ils classifieront les mesures ainsi :
- Normal
- Surveillance
- Alerte

Le mot-clé `sealed` permet de ne définir toutes les variantes possibles dans le code.
La classification se basera sur les seuils cliniques référencés dans le sujet.

Exemple :
```scala
sealed trait IndicateurVigilance
object IndicateurVigilance {
    case object Normal extends IndicateurVigilance
    case object Surveillance extends IndicateurVigilance
    case object Alerte extends IndicateurVigilance
} 
```