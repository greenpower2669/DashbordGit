# Mission active — validation Web réelle et secours API robuste

Autorisation utilisateur : coder l'évolution de validation sur pages réelles de GitHub Actions et éviter toute reprise de tests fictifs.

## Critères
- Tests CI sur HTML authentiques des statuts réussite, échec, build en cours ; source de vérité indépendante API dans la CI.
- Interdiction de présenter une compilation verte comme validation du scraping HTML si l'état réel demeure inconnu.
- En production Android : le Web fournit la découverte et les statuts seulement si preuve vérifiée, sinon l'API sur autorisation explicite. Une seule demande groupée par actualisation (noms projets + nombre d'appels estimé).
- Mode WEB strict, refus → zéro REST. Tous paramètres persistants, quota honnête et visible.
- Supertonic 3 inchangé : voix SID 1, seed 2026, morcellement OFF par défaut. Aucune action réseau au démarrage.
- Branche de travail : `feature/web-first-supertonic3-v1`. Aucun merge main ni release sans validation explicite.
- Test Android utilisateur toujours requis après CI.
