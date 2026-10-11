# DashbordGit — carte centrale vivante

Branche active : `feature/dashgit-chromium-mosaic-001`. Base vérifiée : `feature/web-first-supertonic3-v1`. Branche `main` non modifiée, aucune release ni Play Store.

## Tiroirs

- `brain.md` : architecture Chromium/WebView, réseau/API, TTS et conservation des données
- `debughistorical.md` : incidents prouvés, CI browser, limites et mesures
- `todo.md` : validations restantes sur appareil
- `ordres-de-mission.md` : mission DASHGIT-CHROMIUM-001 et interdictions

## Modules

- `MainActivity.kt` : dashboard original, API sur consentement, jauge et bouton d'accès MOSAÏQUE
- `MosaicActivity.kt` : vrai WebView Chromium Android, gestes, mosaïque, captures figées, observateur DOM, instrumentation réseau indicative
- `GithubMosaicTargets.kt` : URL exacte run/actions/commit GitHub validée et type mémorisé
- `GithubWebSource.kt` / `GithubWorkflowStatus.kt` / `GithubApiConsentPolicy.kt` : ancienne architecture conservée
- `Supertonic3Speech.kt` : moteur existant préservé sur le dashboard principal
- `scripts/probe_real_chromium_github.py` : navigateur Chrome réel en CI sans rechargement, capture screenshot + métriques
- `docs/DASHGIT-CHROMIUM-001.md` : rapport expérimental complet

Ne jamais confondre CI Android compilée, navigateur Chrome réel, et vraie validation WebView Samsung. Aucune observation ne prouve actuellement une transition de statut spontanée jusqu'à fin de run.

## Nouvelle extension HOME-EXPLORER-002

Branche `feature/dashgit-home-explorer-002`, créée à partir de `feature/dashgit-chromium-mosaic-001` ; aucun merge de `main`.

- `HomeExplorerPreview.kt` : cadre miniature de la première page, une WebView réelle lazy, parcours séquentiel visibles, pause, bouton agrandir, instrumentation des requêtes.
- `GithubExplorerPlan.kt` + test JVM : liste d'URL publiques strictes, 12 pages max, délai fixe de 3000 ms.
- `MainActivity.kt` : insertion de la mini-fenêtre et démarrage exclusivement sur ACTUALISER, anti-doublon des actualisations.
- `scripts/probe_real_chromium_github.py` : vérification additionnelle dans Chrome réel des pages Actions DashbordGit et JTrex.
- `docs/DASHGIT-HOME-EXPLORER-002.md` : contrat, résultats, contraintes et tests restant à faire.

## Correction suivante — DOM/SCROLL/CONSENT-003

Branche `feature/dashgit-dom-scroll-consent-003`, issue de `feature/dashgit-home-explorer-002`. Ne pas merger `main`.

- `app/src/main/assets/github_dom_evidence.js` : extraction accessible réelle des liens directs vers les builds.
- `GithubDomEvidence.kt` et `GithubDomEvidenceTest.kt` : preuve sémantique liée au run et fusion conservatrice, protection contre ancien vert.
- `HomeExplorerPreview.kt` : callback à chaque observation DOM vérifiée et signal de fin de visite, sans polling.
- `MainActivity.kt` : cartes permanentes et défilables pendant les requêtes, vrais boutons refus/autorisation REST et merge DOM.
- `scripts/probe_real_chromium_github.py` : exécution du même JavaScript sur des pages publiques actuelles dans Chrome CI ; comparaison au statut GitHub officiel.
- `docs/DASHGIT-DOM-SCROLL-CONSENT-003.md` : rapport, critères et tests restants.
