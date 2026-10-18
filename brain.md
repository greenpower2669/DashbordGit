# DashbordGit — mémoire technique active v0.5.2

**Release exécutée le 11/10/2026** : PR #1 fusionnée dans `main` au commit `241319d43fc965da6d688a4c7616a9de3a06ea27`. CI main #89 (run `38140968511`) **verte** ; GitHub **prerelease `v0.5.2` publiée** avec APK de test debug de 183 337 153 octets et `SHA256SUMS.txt`. Fichier public `.dashboardgit.json` réel et mis à jour (`completed/success`). Google Play/AAB signé : **non autorisé**.

## Architecture actuelle
- Android Kotlin, application `fr.fab.dashboardgit`, arm64, Supertonic 3 INT8 FR (SID 1, seed 2026), découpage désactivé par défaut et paramétrable. Import/export et journal local préservés.
- Dashboard principal : démarrage hors ligne, cache horodaté, bouton ACTUALISER manuel, jauge du quota REST. Sources publiques Web/JSON, complément REST **uniquement sur consentement**, avec « CONTINUER SANS API » / « AUTORISER CETTE VÉRIFICATION » non bloquants.
- `HomeExplorerPreview.kt` : mini Chromium réel de 135dp, une WebView réutilisée, limite 12 pages/clic, pause fixe 3s, 20s par page, arrêt sur 403/429, sans polling ni simulation de trafic humain. Réseau interne à GitHub possible et distinct du REST Dashboard.
- `GithubDomEvidence.kt` + `github_dom_evidence.js` : statut issu du `aria-label` de la **même URL /actions/runs/ID**, pas d'une couleur ou d'un autre run. Succès, échec, en cours, attente ou gris si doute ; **run plus récent inconnu masque ancien vert**.
- Cartes dans une ScrollView à emplacements permanents, remplies progressivement. Conserver la source et la fraîcheur ; ancien cache « DERNIER CONNU ».
- `MosaicActivity.kt` : plusieurs WebView natives plafonnées (3 par défaut, choix 2/3/4/6). Autres vues = **captures figées**, non directes. Actions individuelles/globales sur commande, liens navigateur externe. Pas d'iframe.
- Le `MutationObserver` reflète les changements DOM, sans promettre qu'un workflow évolue spontanément jusqu'à sa fin.

## Vérifications
- Build de branche #84 : CI Android + tests Kotlin + Chrome réel réussis.
- Journal Samsung du 11/10 14:29–14:35 : 3 dépôts explorés, ID exact et statuts succès pour SpaceFortressVs, MassAI, DashbordGit. Mini WebView 232 ressources observées, dont 2 vers api.github.com ; **pas d'appel REST Dashboard pour ces vérifications**. Supertonic 3 OK.
- **À poursuivre :** scrolling/gestes Samsung, transition spontanée de run, conso mémoire/réseau, robustesse de l'auth et des pages GitHub. Voir `todo.md`.

Historique complet conservé dans `docs/archive/2026-10-11-pre-v0.5.2/` et rapports `docs/DASHGIT-*.md`.

## Mission en cours — DJIPIE-AUDIO-004 (branche, non fusionnée)
- Nouvel écran `DjipieAudioActivity.kt` depuis l'accueil ; aucune altération du scanner/mini-Chromium/Mosaïque.
- Index de la sonothèque privée Djipie lu seulement après 🔄 Charger, avec `DjipieAudioCatalog.kt`.
- `DjipiePrivateAudio.kt` : jeton read-only du dépôt privé chiffré via Android Keystore, fichier Release demandé individuellement par API, vérifié taille + SHA-256 et lu localement.
- Filtres, recherche, familles, écoute, fichier sauvegardé par SAF. Compil et tests CI, audit ergonomique Samsung et authentification réelle à confirmer.
- Numérotation de développement : **0.5.3-dev** ; main et release v0.5.2 inchangées. Voir `docs/DASHGIT-DJIPIE-AUDIO-004.md`.

## Mise à jour DJIPIE-AUDIO-005 (18/10/2026, branche non fusionnée)
- Le catalogue audio est **externe et privé** : `Djipie/feature/musiques-cc0-directes-v1` propose 553 références (dont 85 jingles Kenney et 4 WAV Meteor), tant que cette branche est ouverte ; secours sur `main` en cas de 404 de cette branche.
- Un ID d'asset GitHub Release est **éphémère** : le téléchargement recherche l'asset présent dans la Release courante à partir du tag et du nom stricts, contrôle taille et empreinte annoncée, puis vérifie intégralement le SHA-256 téléchargé avant écoute/export. Pas de réutilisation du cache sans SHA-256.
- Jeton conservé dans le Keystore ; aucune authentification n'est transmise aux redirections CDN ; accès uniquement après action explicite.
- Affichage révisé pour téléphone : retours à la ligne normaux, boutons lisibles, 20 cartes par lot. Aucun résultat de test appareil n'est présumé.

## DJIPIE-AUDIO-006 — fireworks et guitare (18/10/2026)
La v0.5.2 main n'a pas l'explorateur. La branche de travail v0.5.4 lisait 553 références sans les nouvelles. Branche v0.5.5-dev `feature/djipie-fireworks-audio-v055` : priorité au catalogue Djipie `feature/fireworks-guitar-direct-v1` (579 sons vérifiés : 25 OGG + guitare WAV), repli ancienne branche 553 puis main uniquement si 404 ; les 401/403 restent des échecs d'authentification. L'interface indique la source chargée, avec recherche guidée ; test Kotlin. L'APK doit être vérifié après extraction runner et livré unitaire directement avec actions/upload-artifact@v7 archive:false (rétention 30 jours), sans Release ni merge main.

### Résultat mission AUDIO-006 — Android compilé
Actions run 39137118368 SUCCESS, code dd368b1573652a8491566c66df02fc9d9ca27ce7, compilation Android et nouveaux tests Kotlin verts. Artefact **fichier APK brut** (actions/upload-artifact@v7 archive:false), `DashbordGit-0.5.5-audio-debug.apk`, ID 12140576101, taille 183369961 octets, lien https://github.com/greenpower2669/DashbordGit/actions/runs/39137118368/artifacts/12140576101 ; validité 30 jours. Pas de Release et main inchangée ; test Samsung de l'accès au dépôt privé requis.

## Mission DJIPIE-AUDIO-007 — catalogue permanent et Release 0.6.0 (18/10/2026)
- Accord explicite Fab : fusion Djipie puis DashboardGit dans `main`, GitHub Release APK. Djipie PR #10 fusionnée ; `Djipie/main` contient le catalogue privé de 579 références (sans fichiers audio intégrés à Git).
- `DjipiePrivateAudio.CATALOGUE_PATH` ne pointe plus sur des branches temporaires : lecture explicite et authentifiée de `Djipie/main/RESSOURCES_PARTAGEES/AUDIO/CATALOGUE/catalogue_direct.json`. La liste et les compteurs restent dynamiques : nouvelle entrée après fusion de Djipie = bouton Charger, **aucune nouvelle APK**.
- Conserver strictement la vérification SHA-256, le contrôle du contenu de Release, le jeton Keystore limité à Djipie et l'absence de sons privés dans l'APK publique.
- Version Android `0.6.0` (code 16), workflow de publication GitHub Release depuis un build `main` vert. Ne pas confondre cette APK debug avec AAB signé Play.
- Dernier test indispensable : validation Samsung avec jeton, affiche 579, recherche guitare, téléchargement et écoute.

### Résultat public v0.6.0 — 18/10/2026
- PR DashboardGit #3 fusionnée à `main` (28d86c71acdb8c23e2e35ad9de674fa6f7822fd2), après CI branche 39139377166 verte ; CI main 39139622395 complète **success**.
- GitHub Release `v0.6.0` publiée : https://github.com/greenpower2669/DashbordGit/releases/tag/v0.6.0 ; APK brute `DashbordGit-v0.6.0-debug.apk` (183369957 octets), SHA-256 `282fdf20702f5053d8a7774d058458daeee16d9aeb33fd42a0c223014bf6e922`.
- Dernière étape ouverte uniquement : test privé Samsung par Fab (jeton, compteur 579, guitare et OGG, lecture, export). Nouvelles entrées audio => mise à jour/fusion du catalogue Djipie/main, sans nouvelle compilation DashboardGit.
