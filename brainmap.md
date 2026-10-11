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
