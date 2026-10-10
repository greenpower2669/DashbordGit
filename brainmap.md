# DashbordGit — carte centrale

Branche d'étude et développement : `feature/web-first-supertonic3-v1`. **Main intacte**.

Tiroirs essentiels : `brain.md` (architecture), `debughistorical.md` (régressions), `todo.md` (validation), `ordres-de-mission.md` (instructions).

Code : `MainActivity.kt` (UI, consentements API, quota et TTS), `GithubWebSource.kt` (lecture Web et découverte JSON), `GithubActionHtmlParser.kt` (statuts conservateurs par ID de build), `Supertonic3Speech.kt` (seule voix), `FrenchSpeechChunks.kt` (morcellement optionnel), `scripts/build_dashboardgit_snapshot.py` (production JSON si installé sur la branche principale).

CI #27 vert le 11/10/2026 : compilation APK et tests Kotlin du parseur passés. **Le statut Web en conditions réelles reste à vérifier sur téléphone** ; un résultat ambigu doit rester GRIS et l'API reste facultative avec consentement.

Ne pas merger `main`, créer de release, ni publier sur Play sans validation explicite.
