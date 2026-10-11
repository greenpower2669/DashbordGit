# DashbordGit — carte centrale

Branche active : `feature/web-first-supertonic3-v1`. Pas de merge `main`, pas de release automatique.

## Tiroirs
- `brain.md` : architecture Web/JSON/API et voix
- `debughistorical.md` : incidents et preuves
- `todo.md` : travaux encore à vérifier
- `ordres-de-mission.md` : critères de validation

## Code
- `GithubWebSource.kt` : sources publiques, JSON seulement annoncé, découverte des IDs de runs
- `GithubActionHtmlParser.kt` : extraction conservatrice si preuve liée au run
- `GithubWorkflowStatus.kt` : contrat central unique des statuts et couleurs
- `GithubApiConsentPolicy.kt` : politique API stricte, échec fermé
- `MainActivity.kt` : jauge API, consentement groupé, interface et sauvegarde
- `Supertonic3Speech.kt` : moteur vocal local inchangé
- `scripts/fetch_real_github_actions_fixtures.py` : captures HTML en CI et vérité indépendante via API

CI de référence : #46 vert, **vraies pages 0/3 états identifiables par HTML, API 3/3 correctes**, et refus API protégé. Vérification Android avec autorisation utilisateur encore requise.
