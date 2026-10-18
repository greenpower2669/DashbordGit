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

## Travail isolé DJIPIE-AUDIO-004
- Branche : `feature/djipie-audio-explorer-001` (non fusionnée).
- Architecture : `DjipieAudioActivity.kt` → `DjipieAudioCatalog.kt` + `DjipiePrivateAudio.kt`.
- Dossier sonore canonique externe **privé** : Djipie `RESSOURCES_PARTAGEES/AUDIO/CATALOGUE/catalogue_direct.json`, chargé sur demande uniquement.
- Tests : `DjipieAudioCatalogTest.kt` ; rapport : `docs/DASHGIT-DJIPIE-AUDIO-004.md`.

## DJIPIE-AUDIO-005 — catalogue et liens de Release corrigés (18/10/2026)
- Travail isolé : `feature/djipie-audio-explorer-001`, version `0.5.4-dev`, **sans fusion sur main**.
- `DjipiePrivateAudio.kt` : lecture du catalogue 553 références sur `Djipie/feature/musiques-cc0-directes-v1` (fallback `main` après 404 de branche), résolution d'asset par Release actuelle, nom + taille + SHA-256.
- `DjipieReleaseResolver.kt` et `DjipieReleaseResolverTest.kt` : résolution de l'ID devenu obsolète lors des republications GitHub.
- `DjipieAudioActivity.kt` : vrais sauts de ligne, boutons mobiles, affichage 20 sons par lot, MP3 à l'export.
- Mémoire et audit : `docs/DASHGIT-DJIPIE-AUDIO-005.md`; tests et essai Android à suivre dans `todo.md`.

- `feature/djipie-fireworks-audio-v055` : v0.5.5-dev, catalogue 579 (fireworks + guitare), source vérifiable, fallback privé sans masque auth.
