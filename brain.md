# DashbordGit — Architecture active

- Android Kotlin, ARM64, affichage lisible et cartes parlantes.
- Réseau manuel : uniquement sur ACTUALISER / action de l'utilisateur, jamais au démarrage ni en tâche de fond planifiée.
- Sources : fichier `.dashboardgit.json` public facultatif puis pages GitHub publiques pour compléter et comparer les dernières exécutions ; API REST par catégorie autorisée, cache horodaté et statut inconnu si insuffisant.
- Jauge API fixe en haut : valeur réelle provenant des headers API, date de mesure et heure de reset ; inconnue si non mesurée.
- TTS Supertonic 3 INT8 seul, voix 1 et seed 2026, morcellement désactivé par défaut, case d'activation et longueur réglable dans ⚙.
- Sauvegarde import/export des projets, états et réglages sans token. Journal debug persistant dans filesDir et exportable.
- Livrer via GitHub Actions Android APK sur branche de travail; validation manuelle de l'APK sur téléphone requise.
