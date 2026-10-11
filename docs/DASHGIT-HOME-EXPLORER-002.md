# DASHGIT-HOME-EXPLORER-002 — mini-explorateur sur l'accueil

Date : 11 octobre 2026 — développement sur `feature/dashgit-home-explorer-002`, depuis `feature/dashgit-chromium-mosaic-001`.

## Objectif utilisateur

Une toute petite fenêtre de navigation **réelle**, visible sur la première page de DashbordGit, montre les pages Actions explorées au cours du scan des projets. Le véritable Chromium Android WebView reste dans le programme, sans iframe ni image factice.

## Mise en œuvre

- Vue miniature d'environ 135 dp de hauteur, cadre et progression lisibles : `🔎 MINI-EXPLORATEUR`, projet actuellement visité, numéro de page, texte d'état et compteur de ressources navigateur.
- Boutons : **⤢** pour agrandir via `MosaicActivity`, **⏸/▶** pour désactiver/réactiver l'aperçu. Le bouton `ACTUALISER` historique démarre à la fois la lecture des données et le parcours visuel.
- Au lancement : simple bloc local, **aucune WebView instanciée, aucune page chargée**. Activation ON n'implique aucune activité Internet sans ACTUALISER.
- Parcours séquentiel des pages publiques `https://github.com/greenpower2669/<repo>/actions` (URL validée, nom de dépôt restreint) par **une WebView réutilisée**. Limite 12 pages par clic, 3 s fixe minimum entre pages, délai max 20 s par page ; pas de boucle continue/polling.
- Après la dernière page, elle reste affichée à l'écran, avec étiquette de statut **non certifié**, le DOM GitHub peut évoluer spontanément.
- Un DOM MutationObserver observe des changements **sans réseau supplémentaire** ; il ne prouve ni état de build ni transition et n'affiche jamais un faux vert.
- Le scanner Web/JSON/API historique reste indépendant : une visite visuelle ne prouve pas le statut. Le navigateur consomme des ressources/réseau propres, potentiellement des requêtes vers api.github.com. Compteurs partiels, pas une jauge de quota.
- Pause, déplacement vers Chrome/mosaïque ou fermeture : le navigateur miniature libère ses ressources et ne reprend jamais tout seul.
- HTTP403/429 : arrêt explicite du parcours, sans réessai automatique ni tentative de contourner les protections. Pas de simulation de comportement humain aléatoire.

## Validation réelle Chrome (non Android)

Dans les jobs de CI, le navigateur Google Chrome headless a affiché les **pages Actions réellement publiées** :
- DashbordGit : 4564 caractères de texte visible, 12 liens de runs relevés, 150 ressources HTTP vues dont 1 vers api.github.com.
- JTrex : 5023 caractères, 12 liens, 148 ressources HTTP dont 1 vers api.github.com.

Ce sont des mesures **sur ordinateur CI**. Elles ne démontrent ni la consommation Android, ni la transition en direct d'un workflow.

## Limites et essais restants

- Vérifier compile/test Kotlin sur le dernier commit : première tentative #58 échouée sur `loadsImages`, corrigé vers `loadsImagesAutomatically`.
- Tester sur Samsung Android 16 la lisibilité de la mini-vue, les chargements un par un, arrêt sur limitation GitHub, affichage réel du DOM et ouverture mosaïque.
- Chiffrer RAM totale et volume réseau : le navigateur ajoute du trafic à la lecture HTTP historique et ne doit pas être présenté comme « gratuit ».
- Ne jamais mélanger la jauge de quota REST principale et les requêtes WebView internes.
- Conserver `main` intacte, aucune release ni Play sans validation explicite.
