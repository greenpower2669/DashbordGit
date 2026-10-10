# DashbordGit — Android

Tableau de bord accessible pour surveiller les dépôts GitHub publics de `greenpower2669`.

## Version de travail 0.4.0 — Web d'abord

- Bouton **ACTUALISER** : unique déclencheur d'interrogation réseau des projets. Aucun appel automatique au lancement.
- Lecture prioritaire des pages GitHub publiques et de fichiers `.dashboardgit.json` facultatifs ; le fichier incomplet est complété par le Web.
- Statuts GitHub Actions : 🟢 succès vérifié, 🔴 échec vérifié, 🟡 en cours, 🟠 en attente, ⚪ inconnu. Le dernier vert ne masque jamais une exécution récente indéterminée.
- Les branches, releases, artefacts et merges non vérifiables ne sont **jamais** annoncés comme à jour.
- Jauge du quota API épinglée en haut, avec dernier horodatage connu ; aucune valeur inventée.
- Modes API : Web seul (défaut), demander l'accord, ou catégories déjà autorisées. Permissions révocables et demandes « une fois ».
- Réglages dans ⚙ : projets, sources Web/API, voix et morcellement, sauvegardes, diagnostic et mesure du quota.
- Supertonic 3 INT8 **uniquement**, SID 1, seed 2026, français ; morcellement désactivé par défaut (case à cocher pour expérimenter).
- Lecture au toucher d'une carte, appui long pour la page GitHub si disponible.
- Import/export JSON local et journal debug persistant.

## JSON événementiel facultatif

Voir `docs/dashboardgit-json-v1.md`. Le producteur `publish-dashboardgit-status.yml` s'activera quand le workflow sera sur la branche principale et que les écritures GITHUB_TOKEN seront permises. Sur la branche de travail, il ne faut pas présenter ce fichier comme déjà publié.

## Sécurité / honnêteté des données

Ne pas stocker de secret dans GitHub ou les exports. Pas d'API REST sans autorisation. Les pages HTML publiques peuvent être dynamiques ou partiellement indisponibles ; dans ce cas l'application affiche explicitement **NON VÉRIFIÉ**.

Développement sur `feature/web-first-supertonic3-v1` ; aucun merge main ni release sans validation.
