# DashbordGit — Historique des incidents

- Anciennes requêtes REST publiques multiples : quota GitHub HTTP 403. Correction à valider : lecture Web et JSON prioritaire, API après consentement, cache sûr.
- Kokoro français int8 : crash natif en callback et latence importante ; retiré de cette mission.
- Ancien APK à trois modèles (Piper / Supertonic / MMS) > 380 Mo. Correction attendue : Supertonic 3 seul dans APK.
- Anciens liens APK `Interpreter file not found` : toujours vérifier matériellement le fichier livré avant le lien.
- Attention à l'ancien indicateur 'tout à jour' sur simple succès CI : ne jamais conclure sans preuve sur merge, build et release.
- JSON public : une compilation ancienne verte ne doit pas masquer un build plus récent en cours ; vérifier identifiants et statut.
- Build, test téléphone, valeur API réelles non encore validés pour la branche de travail.
