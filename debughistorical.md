# DashbordGit — journal historique actif

Archives intégrales des incidents antérieurs : `docs/archive/2026-10-11-pre-v0.5.2/debughistorical.md`. Les détails de Chrome CI sont dans les trois rapports `docs/DASHGIT-*.md`.

## Régressions confirmées et corrigées
- HTML public simple : pages réelles montrent des builds mais l'extracteur d'origine ne reconnaissait pas leurs états. **Correction** : Chromium WebView + lecture d'`aria-label` attaché à l'ID exact, testé sur vraies pages; si absent : gris puis API consentie.
- Fenêtre API bloquante et absence de refus apparent : remplacée par **deux boutons visibles et non modaux** sur l'accueil, consentement persistant jamais coché par défaut.
- Liste de projets effacée pendant ACTUALISER : cartes stables, remplissage progressif. **Défilement réel Samsung encore à tester**, ne pas marquer résolu en ergonomie.
- 3 tentatives JSON 404/dépôt : JSON uniquement si annoncé; éviter les requêtes inutiles.
- 3 moteurs TTS dans l'APK volumineux : seul Supertonic 3 INT8 conservé.

## Dernier essai appareil (11/10/2026)
Journal fourni par Fab : 33 dépôts publics trouvés, 3 projets sélectionnés et explorés : SpaceFortressVs (run 38136673247 success), MassAI (run 35657752230 success), DashbordGit (run 38137528513 success), chacun via une preuve accessible exacte du WebView. **232 ressources WebView observées, dont 2 vers api.github.com** ; aucune preuve de zéro trafic réseau côté navigateur. TTS : 44,1 kHz, 10 voix, SID 1, seed 2026, 961 ms initialisation puis 3227 ms de génération pour 6444 ms d'audio. Sauvegarde exportée.
Tests CI branche #84 verts, Chrome réel status success/failure/in_progress testé contre API indépendante.

## Problèmes non prouvés / limites
- Scrolling et gestes (titre/WebView) selon appareil, performances mémoire/batterie ; transitions spontanées de GitHub à observer pendant vrai build.
- Build CI vert ≠ certification UX Android. Debug APK signé par clé de test CI : une installation par-dessus un ancien APK de signature différente peut échouer.

## Publication confirmée — 11/10/2026
- PR #1 fusionnée dans main à `241319d43fc965da6d688a4c7616a9de3a06ea27`. Branche CI #88, main CI #89 : succès.
- GitHub prerelease `v0.5.2` : https://github.com/greenpower2669/DashbordGit/releases/tag/v0.5.2 ; APK `DashbordGit-v0.5.2-debug.apk` (183 337 153 octets), `SHA256SUMS.txt` (100 octets).
- Le fichier JSON `dashboardgit-status/.dashboardgit.json` est réellement publié et est passé de `in_progress` à `completed/success` pour le build #89.
- APK debug uniquement, non signé par clé permanente Google Play. Le défilement et les gestes Samsung ne sont pas clos.

## DJIPIE-AUDIO-004 — points à surveiller
- Base canonique privée, Dashboard public : **un token GitHub propre à Djipie** est indispensable pour lire les ressources ; ne jamais confondre login Chrome et permission de l'API.
- Un SHA-256 déclaré n'est pas une validation d'écoute ; les sons Sonniss restent soumis à leur licence et aux restrictions de distribution.
- Les assets peuvent atteindre près de 400 Mio ; téléchargement explicite, SHA-256 strict, cache privé, arrêt utilisateur et erreurs visibles.
- Aucune preuve de lecture réelle sur Samsung tant que test appareil/tokens privés non exécuté ; la CI vérifie seulement syntaxe/tests accessibles publiquement.

## DJIPIE-AUDIO-005 — capture Samsung : lien 404 trompeur (18/10/2026)
- Reproduction par confrontation des données GitHub : pour `25 Church Bells, Far Distance, Rural Soundscape Distant, Hills Ridge, Spring 02.wav`, l'index conserve l'asset ID `650663951`, alors que la Release `djipie-audio-prive-non-classes` expose le même fichier à l'ID `652011955`. Le SHA-256 `49af5cedf7e5dea91efb4084f8d25cb01bbf4581bc1923e31bf5dc249a381b0f` demeure identique. Un 404 d'asset ne prouve **pas** un jeton invalide.
- Correction : résoudre le nouvel ID à partir des métadonnées de Release actuelles, contrôler le nom, la taille et le SHA GitHub, puis vérifier la somme du fichier ; réutiliser le cache seulement après SHA.
- La capture montre des caractères littéraux `\\n` dans les métadonnées et des libellés de boutons cassés sur petite largeur : correction dans l'écran Android.
- À confirmer sur téléphone : lecture réelle du son, robustesse de l'auth/du réseau, ergonomie, résultat des builds et jeton existant. Ne pas déclarer la correction testée Samsung sur la seule base d'une CI verte.


## Architecture catalogue — correctif de fond 18/10/2026
- Symptôme : les fichiers récents (guitare électrique, fireworks) étaient invisibles parce que l'APK v0.5.2 n'avait pas l'explorateur, puis les builds suivants ciblaient plusieurs branches 447/553/579. La cible changeait à chaque lot.
- Diagnostic contrôlé avant correction : Djipie `main` = 447, branche `feature/fireworks-guitar-direct-v1` = 579, 579/579 entrées acceptées par le parseur, Release privée `Fireworksetgitelec` avec guitare WAV et 25 OGG. Dash 0.5.5 CI #39137118368 verte mais test Samsung non effectué.
- Traitement : PR Djipie #10 fusionnée sur `main`, chemin unique `Djipie/main` dans Dashboard v0.6.0 ; supprimer les replis implicites vers les index historiques (ne pas confondre 404 privé et jeton invalide). Contrôle CI et lecture Samsung à confirmer sur la version finale.
- Attention : changement de signature debug entre APK de build peut bloquer l'installation incrémentale. Préserver les données/jetons avant désinstallation.
