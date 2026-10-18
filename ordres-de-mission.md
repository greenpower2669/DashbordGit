# DashbordGit — mission active et autorisations

**Décision de Fab le 11 octobre 2026 : « merge release là et met à jour les mémoires ; si une est trop grande synthétise et archive un bout ».**

Autorisé pour **ce projet** :
1. Fusionner `feature/dashgit-dom-scroll-consent-003` dans `main` après vérification CI et absence de divergence.
2. Créer une **GitHub Release v0.5.2**, avec l'APK de test issu d'un build vert de main, notes de release et SHA256.
3. Consolider les cinq fichiers vivants, archiver les versions longues **sans supprimer l'historique**.

**Non autorisé** : publication Google Play/AAB, modification d'autres dépôts, nouveaux déploiements sans accord. Les futurs merges/releases restent soumis à nouvelle validation.

**Règles de qualité** : pas de faux succès, ne pas confondre WebView/REST, préserver refus API explicite, Supertonic 3, choix de projets, sauvegardes et date de cache. Indiquer les limites connues de défilement/gestes et les tests réels nécessaires. Le canal GitHub peut rester en préversion tant que scrolling et signature de distribution ne sont pas validés.

Les instructions d'évolution historiques complètes figurent dans `docs/archive/2026-10-11-pre-v0.5.2/ordres-de-mission.md`.

## Autorisation exécutée — 11/10/2026
- PR #1 fusionnée vers `main` au commit `241319d43`.
- GitHub Actions main #89 réussie, GitHub prerelease `v0.5.2` créée avec APK de test debug et SHA256.
- Les cinq mémoires précédentes restent archivées intégralement dans `docs/archive/2026-10-11-pre-v0.5.2/`.
- Encore à tester/améliorer : scrolling et gestes Samsung, transition spontanée d'un workflow, mémoire/réseau et signature pérenne.
- Aucun déploiement Google Play ni future release autorisés sans nouvel accord.

## DJIPIE-AUDIO-004 — nouvelle demande Fab du 17 octobre 2026
**Demande explicite :** « Dans git dashboard créé un explorer de fichier son dans nos bibliothèques djipie ».

**Autorisé :** coder l'explorateur dans le dépôt **DashbordGit** sur branche isolée ; consulter Djipie en lecture seule pour son index de médias ; compiler et tester via CI de la branche.
**Non autorisé :** modifier Djipie ou ses médias, aspirer les archives, diffuser les sons en public, intégrer un jeton dans l'APK, fusionner automatiquement dans main, créer une Release, publier sur Google Play.
Protéger le contrat v0.5.2 des pages Web GitHub, des permissions API, du cache de statuts et de Supertonic 3.
Voir `docs/DASHGIT-DJIPIE-AUDIO-004.md`.

## DJIPIE-AUDIO-005 — demande Fab du 18/10/2026
**Autorisé :** mettre à jour l'explorateur de sons de **DashbordGit** sur la branche `feature/djipie-audio-explorer-001` (v0.5.4-dev), corriger les liens GitHub 404 liés aux IDs republiés, consommer en lecture seule le nouveau catalogue Djipie disponible, corriger l'affichage mobile, exécuter tests et build CI, documenter les résultats dans les cinq fichiers vivants du dépôt.
**Interdictions maintenues :** aucune modification automatique de Djipie, aucune fusion de branche `main`, aucune Release/Play Store/AAB sans nouveau feu vert, aucun jeton intégré dans un APK ou log, aucune extraction en masse de sons, aucune substitution aux vérifications SHA-256 ou permissions GitHub.
