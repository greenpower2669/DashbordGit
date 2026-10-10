# DashbordGit — TODO vivant

- [x] Première compilation Android Web-first #21 passée.
- [x] CI Android + tests unitaires parseur #27 verts sur `feature/web-first-supertonic3-v1`.
- [x] Supprimer la boucle aveugle 3 × 404 sur les manifestes JSON non annoncés.
- [x] Éviter la fausse classification verte depuis un indicateur HTML d'un autre build.
- [x] Ajouter un bilan « builds identifiés / inconnus / via API » et condenser les cartes grises.
- [ ] Tester sur l'appareil les nouveaux 404 (attendu : aucun pour JSON non annoncés).
- [ ] Tester parsing des pages Actions LIVE pour builds en cours, échoués, réussis. Si insuffisant, activer « demander l'API » et confirmer les statuts.
- [ ] Contrôler quota mesuré, autorisation API une fois/permanente/révocable et erreur 403.
- [ ] Vérifier les branches repérées (partielles), commits, releases et artefacts sur 4 dépôts réels.
- [ ] Reconfirmer absence de réseau au lancement et persistance des sauvegardes/paramètres.
- [ ] Réinstaller APK récent, voix SID 1 / seed 2026 / OFF inchangés, vérifier lecture normale.
- [ ] Après validation explicite seulement : installer le workflow JSON sur la branche par défaut, puis valider la branche publique dashboardgit-status.
- [ ] Aucun merge main, release, Play sans feu vert.
