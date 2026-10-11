# DashbordGit — brainmap active

**v0.5.2 publiée le 11/10/2026** : PR #1 fusionnée sur `main` (`241319d43`), CI #89 verte, prerelease GitHub vérifiée. Branche de travail `feature/dashgit-dom-scroll-consent-003` conservée ; pas de publication Play.

- `brain.md` : modèle d'architecture et invariants fonctionnels.
- `todo.md` : points non validés et étapes après release.
- `debughistorical.md` : régressions, preuves et vérifications.
- `ordres-de-mission.md` : autorisations, périmètre et interdictions.
- `docs/archive/2026-10-11-pre-v0.5.2/` : **copies intégrales pré-synthèse** des 5 mémoires vivantes (aucune donnée perdue).
- `docs/DASHGIT-CHROMIUM-001.md`, `docs/DASHGIT-HOME-EXPLORER-002.md`, `docs/DASHGIT-DOM-SCROLL-CONSENT-003.md` : audits/essais détaillés.
- `docs/releases/v0.5.2.md` : notes de GitHub préversion.

## Points d'entrée code
`MainActivity.kt` → `HomeExplorerPreview.kt` / `GithubDomEvidence.kt` / `GithubWebSource.kt` / `GithubApiConsentPolicy.kt` ; `MosaicActivity.kt` / `GithubMosaicTargets.kt` ; `Supertonic3Speech.kt` ; `github_dom_evidence.js`.

**Chaîne validée** : branche CI #88 verte → PR #1 fusionnée (`241319d43`) → CI main #89 verte → release `v0.5.2` publiée avec APK debug/SHA256. Workflow `dashboardgit-status` actif, JSON final `completed/success`.
