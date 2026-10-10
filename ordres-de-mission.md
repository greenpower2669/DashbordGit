# Mission active — Web-first + corrections de diagnostic 11/10/2026

Fab a autorisé le CODE mais pas de merge main, release ou publication. Travailler sur `feature/web-first-supertonic3-v1`.

Dernières corrections : JSON public **uniquement si annoncé** sur une branche/lien, Web de complément pour données manquantes ; identité exacte de run nécessaire avant toute classification HTML. API GitHub accessible seulement selon paramètres de consentement, jauge toujours visible. Affichage d'états inconnus explicite et historique compact. Conserver sans modification le Supertonic 3 INT8, SID 1 et seed 2026, morcellement par défaut désactivé.

Tests préalables : compilation Android + tests unitaires status parser sur CI (build #27 vert). Tests sur téléphone encore requis pour vérifier les pages GitHub réelles et voir si accord API nécessaire. Ne pas déclarer 100 % Web réussi sans preuve utilisateur.

Méthode : mémoire vivante synchronisée, conservation des erreurs historiques et contraintes, traçabilité SHA/build, éviter de coder depuis la seule mémoire.
