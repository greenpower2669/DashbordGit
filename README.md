# DashbordGit — tableau de bord Android accessible

## Version de test 0.4.1 : sources réelles et vérification rigoureuse

Architecture **Web d'abord, API seulement sur consentement** :
- Découvrir les projets GitHub publics par le Web.
- Compléter commits, branches visibles et releases par leurs pages publiques.
- Lire un manifeste `.dashboardgit.json` seulement s'il est effectivement annoncé, sans recherches systématiques qui provoquent des erreurs HTTP 404.
- Découvrir les identifiants des builds depuis la page Actions.
- **Ne jamais inventer une couleur** : les vraies pages GitHub 2026 ont été testées en CI et n'exposent pas les statuts vert/rouge/en cours dans une structure exploitable par notre lecteur.
- Pour les projets dont le statut est inconnu : demander **une seule autorisation groupée**, indiquant le nombre de projets et le coût API estimé, puis lire au maximum une page REST `actions/runs` par projet.
- Mode initial **ASK** (question préalable, aucune API avant accord) ; mode `WEB` strict et blocage si l'utilisateur refuse ; catégories autorisées persistantes et révocables.
- Jauge quota API toujours visible ; valeurs et horodatages exclusivement issus des réponses réelles.
- Aucun accès réseau automatique au lancement ; `ACTUALISER` reste manuel.
- Affichage condensé des statuts inconnus ; lecture au toucher ; accès à l'URL Actions par appui long.
- Supertonic 3 INT8 seul, SID 1, seed 2026, morcellement désactivé par défaut mais réglable dans ⚙.
- Sauvegarde/import, cache et journal debug conservés.

## Tests réels obligatoires

La CI exécute `scripts/fetch_real_github_actions_fixtures.py` : elle télécharge **pendant le build** trois pages publiques GitHub (un run réussi, un run échoué, le run actuellement en cours), et vérifie leurs statuts indépendamment via l'API officielle dans la CI. Les fichiers HTML sont temporaires, non versionnés.

Résultat mesuré dans le build #46 : 0/3 couleurs déterminables avec le HTML seul, **3/3 correctement déterminées depuis le JSON API**, et tests de consentement réussis. Une compilation verte signifie que la politique de non-invention et le secours API sont testés ; **elle ne signifie pas que le HTML public fournit toutes les informations**, ni que les essais sur téléphone sont terminés.

Le producteur JSON optionnel est décrit dans `docs/dashboardgit-json-v1.md`; il nécessite d'être installé sur la branche par défaut des projets et n'a **pas** été activé sur les autres projets.

Branche : `feature/web-first-supertonic3-v1`. Aucun merge vers `main`, aucune release / publication Play sans accord.
