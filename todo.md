# DashbordGit — tâches actives après v0.5.2

## Livraison autorisée le 11/10/2026
- [x] Fab a donné son accord explicite pour fusionner et publier une GitHub Release.
- [x] Tests Android/Chrome de la branche #84 réussis ; journal Samsung confirme 3 vrais statuts success.
- [ ] Vérifier PR/merge main et dernier commit intégré (ne jamais présumer la fusion).
- [ ] Vérifier CI **main**, sa compilation et les tests réels.
- [ ] Vérifier création **GitHub prerelease v0.5.2**, APK `DashbordGit-v0.5.2-debug.apk`, fichier SHA256, taille et lien.
- [ ] Vérifier que le producteur JSON `dashboardgit-status` est réellement actif avant de dire qu'il publie.

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
