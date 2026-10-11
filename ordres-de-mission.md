# MISSION SOL — DASHGIT-CHROMIUM-001

**Date 11/10/2026. Statut : implémentation sur branche dédiée, validation Android finale non obtenue.**

Objectif : surveillance visuelle autonome des pages GitHub via WebView Android Chromium, sans API REST explicite et sans intervention de ChatGPT. Conserver Dashboard Git existant, Supertonic 3, sélections, sauvegardes et mode API sur consentement.

Règles : pas d'iframe bloquée GitHub, pas de bypass origine/protection, pas de multiplication de processus forcée ; ressources bornées. Pages chargées à la demande, non à l'ouverture ; boutons individuels et global, appui long (recharge individuelle), double tap (navigateur externe), choix de cible actions/run/commit, zoom et cadrage. Les pages GitHub peuvent faire leurs propres requêtes/DOM changements indépendamment.

Vérification robuste : vrais statuts GitHub testés dans navigateur Chromium CI ; vraie mutation/transition spontanée de build à observer sur téléphone avant toute garantie de rafraîchissement autonome. Afficher les erreurs, données périmées et captures figées. Pas de faux vert. Historique dans debughistorical, suite dans todo.

**Interdictions** : pas de merge main, pas de release, pas de publication Play, pas de cookie/secret GitHub versionné, pas de modifications d'autres dépôts sans autorisation. Les capacités impossibles ou non prouvées doivent rester documentées.

## Extension autorisée — DASHGIT-HOME-EXPLORER-002

Ajouter une fenêtre Chromium *réelle et miniature* sur la première page du logiciel pour afficher visuellement la visite des pages GitHub Actions. Préserver Dashboard et mosaïque complète. Déclenchement uniquement manuel, un seul navigateur miniature, limite de pages et temporisation fixe raisonnable, stop/pause et bouton de zoom vers mosaïque. Protéger contre les limitations 403/429 et éviter une charge excessive. Toute idée de « comportement humain aléatoire pour ne pas être repéré » doit être remplacée par des limites explicites : **aucun contournement de la détection/protection de GitHub**.

La lecture HTTP/JSON/API et le navigateur visible sont indépendants ; annoncer les requêtes supplémentaires du navigateur. Pas de faux statut, pas de promesse de rafraîchissement spontané non prouvée. Valider sur pages réelles en Chrome CI puis sur Android appareil.

Développer/pousser seulement sur `feature/dashgit-home-explorer-002` ; garder `main`, releases et Play inchangés.
