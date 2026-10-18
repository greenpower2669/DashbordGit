# DashbordGit — tâches actives après v0.5.2

## Livraison autorisée le 11/10/2026
- [x] Fab a donné son accord explicite pour fusionner et publier une GitHub Release.
- [x] Tests Android/Chrome de la branche #84 réussis ; journal Samsung confirme 3 vrais statuts success.
- [x] PR #1 fusionnée dans main, commit `241319d43fc965da6d688a4c7616a9de3a06ea27` vérifié.
- [x] CI main #89 (run `38140968511`) terminée `success`, tests Android + Chrome.
- [x] GitHub prerelease `v0.5.2` publiée, APK debug 183 337 153 octets et SHA256 vérifiés.
- [x] JSON public `dashboardgit-status` actif ; build #89 désormais `completed/success`.

## À améliorer / tester sur le téléphone
- [ ] **Défilement des cartes** pendant/à la fin du scan, mini-fenêtre non bloquante, agrandissement et appui long/double tap.
- [ ] Choix « CONTINUER SANS API » : refus réel et aucun REST Dashboard ajouté ; vérification après autorisation ; cases persistantes.
- [ ] Observer sans rechargement un workflow de « en cours » à « terminé » et comparer à Chrome externe.
- [ ] Tester accès privé/connexion, HTTP 403/404/429, pause, crash du renderer, orientation.
- [ ] Mesurer RAM/CPU/batterie/réseau sur Samsung en mini-vue et mosaïque à 2/3/4/6 fenêtres.
- [ ] Valider sauvegardes/restaurations, journal, TTS Supertonic 3, quota et stabilité de l'APK après fusion.
- [ ] Préparer ultérieurement **une signature pérenne** si l'objectif devient Google Play ; le debug APK ne suffit pas.
- [ ] Ne pas supprimer d'anciennes archives, ne pas déployer Play Store sans demande.

L'ancien TODO détaillé est archivé dans `docs/archive/2026-10-11-pre-v0.5.2/todo.md`.

## DJIPIE-AUDIO-004 — explorateur sonothèque (branche de travail)
- [x] Brancher un écran audio distinct sur l'accueil du Dashboard Android.
- [x] Parser le catalogue audio privé, familles, filtre/recherche et métadonnées.
- [x] Chiffrer localement un jeton de lecture du seul dépôt Djipie avec Android Keystore.
- [x] Télécharger un asset privé seulement au clic, vérifier SHA-256, lire via MediaPlayer et exporter via sélecteur Android.
- [x] Ajouter des tests unitaires de parsing, filtres et URLs valides.
- [x] **CI Android + tests JVM réussis** : run #39059365809 (17/10/2026), commit 22260c41a ; artifact APK debug 0.5.3-dev #12110692181 vérifié et SHA-256 local contrôlé. Tests sur Samsung et accès réellement privé restant à valider.
- [ ] Essai réel Samsung avec jeton fine-grained et catalogue 447 sons : API GitHub privée, redirections Release, accès 401/403/404, lecture WAV/OGG et export.
- [ ] Auditer le défilement de 60 cartes, gros WAV, cache/disque, annulation, appareil hors ligne, navigation et accessibilité.
- [ ] Si validation de Fab : prévoir une PR/merge distinct et éventuellement une future APK/AAB ; **ne pas publier ni fusionner sans accord**.

## DJIPIE-AUDIO-005 — mise à jour demandée d'après capture Samsung (18/10/2026)
- [x] Retrouver l'ancienne fiche cloches : ID `650663951` dans l'index, ID réel `652011955` dans la Release privée actuelle ; corriger l'accès par résolution du tag + nom + SHA-256.
- [x] Prendre en compte 553 références du catalogue Djipie de la branche `feature/musiques-cc0-directes-v1`, sans fusionner Djipie ; fallback de lecture vers `main` si branche absente.
- [x] Corriger la présentation des chaînes littérales `\\n`, les libellés de boutons et la surcharge des cartes sur petit écran.
- [x] Préparer `0.5.4-dev` et tests automatiques de résolution d'assets republiés.
- [x] CI Android + tests JVM **réussis** : [run #39084673802](https://github.com/greenpower2669/DashbordGit/actions/runs/39084673802), commit `31f850dd7e9b66366bc169172c21cfc01435fe8b` ; APK debug 0.5.4-dev extrait de l'artifact, testé comme ZIP valide et `Android package (APK)`, SHA-256 `fa488fdfa5596f9fb1a15fbc23baa182dee1bb55308d19b9aa0f5ef995d61bfa`, 183 369 961 octets, livré directement dans la conversation (sans ZIP).
- [ ] Tester sur Samsung la lecture du fichier de cloches, au moins un Kenney, un Meteor et un MP3 ; vérifier le jeton sans le recréer, les redirections et les permissions privées.
- [ ] Vérifier l'ergonomie réelle de l'écran, les erreurs 401/403/404, arrêt/annulation, tailles/cache et export dans le sélecteur Android.
- [ ] Ne pas fusionner vers main, ne pas publier de GitHub Release ni de Play/AAB sans accord explicite de Fab.

## DJIPIE-AUDIO-006 — nouveaux sons dans l'explorateur
- [x] Djipie 25 OGG + guitare, catalogue 579 : CI 39136622479 SUCCESS.
- [x] Source prioritaire vers Djipie/feature/fireworks-guitar-direct-v1, ancien 553 et main en secours 404 ; erreurs 401/403 intactes.
- [x] UI précise la source et propose recherches bang, cannon, fw_loop, guitare.
- [x] Tests Kotlin de lecture et filtre des nouvelles entrées.
- [x] Livraison APK simple sans ZIP via actions/upload-artifact@v7 archive:false, extraction et vérification RUNNER_TEMP.
- [ ] CI GitHub et APK v0.5.5 à vérifier puis essai Samsung, jeton Contents read et lecture réelle.
- [ ] Aucun merge main, Release, Play sans accord.
