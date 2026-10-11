# DashbordGit — suite des validations

- [x] CI réelle : trois pages GitHub Actions authentiques (vert/rouge/en cours) téléchargées et testées.
- [x] Prouver que le HTML actuel ne donne pas de couleurs fiables : 0/3, aucune fausse réussite.
- [x] Démontrer les mêmes statuts via API officielle en CI : 3/3 couleurs correctes.
- [x] Supprimer les lectures inutiles des pages de détail HTML et les recherches JSON 404.
- [x] Centraliser statut/couleur et politique de consentement API ; tests de refus mode WEB.
- [x] Regrouper la demande de consentement API par ACTUALISER, coût estimé et projets affichés.
- [x] Conserver Supertonic 3 SID 1, seed 2026, morcellement OFF.
- [ ] Tester la version 0.4.1 sur le téléphone Android : import sauvegarde, ACTUALISER et une seule demande API pour six projets.
- [ ] Vérifier six statuts réels et la jauge quota après consentement ; refus doit laisser les gris et consommer zéro API.
- [ ] Mesurer la fraîcheur des sources Web et contrôler les branches, commits, releases, artefacts sans présumer de données non prouvées.
- [ ] Vérifier persistance des permissions, révocation et reprise d'une ancienne sauvegarde.
- [ ] Tester l'arrêt et le redémarrage vocal ; sauvegardes et diagnostic.
- [ ] Publier les JSON événementiels seulement après décision explicite d'intégrer le producteur sur la branche par défaut ; aucun push aux autres projets sans accord.
- [ ] Garder `main`, les releases et Play inchangés jusqu'à validation.
