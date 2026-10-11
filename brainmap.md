# DashbordGit — brainmap active

**Version visée : v0.5.2**, merge + GitHub prerelease **autorisés le 11/10/2026**. Branche source : `feature/dashgit-dom-scroll-consent-003`; `main` cible. La publication Play Store n'est pas incluse.

- `brain.md` : modèle d'architecture et invariants fonctionnels.
- `todo.md` : points non validés et étapes après release.
- `debughistorical.md` : régressions, preuves et vérifications.
- `ordres-de-mission.md` : autorisations, périmètre et interdictions.
- `docs/archive/2026-10-11-pre-v0.5.2/` : **copies intégrales pré-synthèse** des 5 mémoires vivantes (aucune donnée perdue).
- `docs/DASHGIT-CHROMIUM-001.md`, `docs/DASHGIT-HOME-EXPLORER-002.md`, `docs/DASHGIT-DOM-SCROLL-CONSENT-003.md` : audits/essais détaillés.
- `docs/releases/v0.5.2.md` : notes de GitHub préversion.

## Points d'entrée code
`MainActivity.kt` → `HomeExplorerPreview.kt` / `GithubDomEvidence.kt` / `GithubWebSource.kt` / `GithubApiConsentPolicy.kt` ; `MosaicActivity.kt` / `GithubMosaicTargets.kt` ; `Supertonic3Speech.kt` ; `github_dom_evidence.js`.

Chaîne GitHub : tests + APK sur la branche → PR vers main → CI main → job GitHub Release avec APK debug et SHA256. **Ne pas déclarer la release publiée avant sa vérification réelle.**
