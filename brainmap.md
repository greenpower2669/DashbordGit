# DashbordGit — Carte centrale

Branche de travail : `feature/web-first-supertonic3-v1`. Main conservée intacte.

Lire ensuite : `brain.md` pour l'architecture, `todo.md` pour les validations, `debughistorical.md` pour les régressions et `ordres-de-mission.md` pour les contraintes.

Modules : `MainActivity.kt` (interface, consentements API, jauge et paramètres), `GithubWebSource.kt` (pages publiques + JSON), `Supertonic3Speech.kt` (Sherpa-ONNX), `FrenchSpeechChunks.kt` (découpage optionnel), `scripts/build_dashboardgit_snapshot.py` (producteur JSON événementiel optionnel).

**Ne pas merger main, publier de release, ni faire de Play Store sans validation explicite.**
