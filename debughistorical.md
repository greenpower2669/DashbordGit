# DashbordGit — historique des bugs / diagnostics

- Builds anciens : multiples requêtes REST aboutissant à HTTP 403 avec quota faible. Nouvelle architecture Web-first et autorisation API facultative.
- 10/10/2026 : 3 × 404 manifestes pour chacun de 4 projets : DashbordGit, JTrex, SpaceFortressVs, GeckoDoku. Cause : probing inconditionnel sur dashboardgit-status/main/master. Correction commit `2d9c7504` : manifeste seulement si une branche publique/lien l'annonce, plus cache d'URL connue; HTTP 404 facultatif silencieux. À valider sur appareil.
- Capture du 11/10/2026 : GitHub Actions listait des IDs mais affichait cinq cartes GRIS sans métadonnées. Cause : statut extrait par regex globale ou absent. Correction : `GithubActionHtmlParser.kt` parse run lié à ID; ne lit jamais une icône verte non liée. UI condense les gris, indique nombre vérifiés/inconnus et propose accord API en dernier recours. Statuts HTML réels non encore garantis.
- Premier build sur lecteur Web refactoré #24 : échec Kotlin « No value passed for parameter context » à l'instanciation. Corrigé ensuite; build #27 vert avec tests de parsing.
- Journal du 11/10 : Supertonic 3 prêt (44 100 Hz, 10 voix) en 932 ms ; génération de 6,565 s audio en 3,240 s, et 9,714 s audio en 4,851 s. Voix SID 1, seed 2026, morcellement OFF. Ne pas modifier inutilement le TTS.
- Kokoro natif : crash et lenteur; abandonné. Vieux APK trois moteurs >380 Mo remplacé par Supertonic seul.
- Anciennes erreurs de téléchargement ChatGPT : toujours vérifier le fichier réel, ne pas inventer de lien APK.
- Priorité sûreté : jamais de fausse réussite, quota fictif, merge ou release sans preuve.
