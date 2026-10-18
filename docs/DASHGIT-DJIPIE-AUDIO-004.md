# DASHGIT-AUDIO-004 — explorateur sonore Djipie (17/10/2026)

## Périmètre
Nouvel écran Android indépendant du scanner de workflows et de la mosaïque Chromium, accessible depuis le bouton **🎧 EXPLORATEUR DE SONS DJIPIE**.

- Index canonique privé : `greenpower2669/Djipie/RESSOURCES_PARTAGEES/AUDIO/CATALOGUE/catalogue_direct.json` (branche main).
- 447 références déclarées au 17 octobre 2026 ; 14 familles, 14 Releases privées, 347 WAV Sonniss et 100 OGG Kenney.
- Aucune copie du catalogue ou d'un fichier audio dans le code de DashbordGit.
- Aucun chargement automatique à l'ouverture du Dashboard ni de cet écran : les boutons déclenchent explicitement les actions.
- Navigation par famille, compteur, recherche insensible aux accents, affichage nom, source, format, taille, archive, chemin et statut descriptif.
- Résultats paginés (60 à la fois) ; lecture d'un fichier audio individuel, arrêt, accès à l'URL GitHub, enregistrement via sélecteur Android.
- Les sons >64 Mio demandent une confirmation avant téléchargement.
- Un seul téléchargement à la fois, cache audio privé local limité à un fichier à la fois (plusieurs temporaires possibles lors de transition), rejet >450 Mio.
- Vérification de la taille exacte et de la somme SHA-256 avant écoute/export ; les fichiers partiels sont supprimés.

## Authentification privée
Le Dashboard est **public**, la sonothèque Djipie est **privée**.
Le jeton n'est jamais compilé dans l'APK, envoyé dans une URL, inscrit en journal, ni copié dans les sauvegardes applicatives : seul un texte chiffré par clé AES-GCM Android Keystore est mémorisé dans SharedPreferences.

Configuration manuelle :
1. GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens.
2. Repository access : **Only select repositories → Djipie**.
3. Repository permissions : **Contents: Read-only**, **Metadata: Read-only** (implicite).
4. Dans DashbordGit → 🎧 Explorateur → 🔐 Connexion → coller le jeton → ENREGISTRER.
5. Toucher 🔄 Charger : une requête privée GitHub API à la demande (contents API, index JSON codé base64).
6. Toucher ▶ Écouter ou 💾 Enregistrer pour récupérer **un seul asset** privé par son ID GitHub Release ; authentification transmise uniquement à `api.github.com`, jamais au CDN de redirection.

**Important** : ne pas donner les permissions d'écriture, ne pas utiliser le jeton du Dashboard pour les dépôts publics et ne pas publier le catalogue dans une GitHub Release publique. Ne pas ajouter de permissions audio Android superflues : les fichiers sont sélectionnés avec le sélecteur système ou lus depuis le cache privé.

La connexion du navigateur externe GitHub peut être distincte du jeton enregistré dans l'application. Le bouton 🔗 GitHub ouvre le lien de Release : il peut demander une connexion.

## Limites réelles
- Le parser prend pour fiables uniquement les entrées valides `disponible_prive` avec SHA-256, taille, ID d'asset, chemin de Release et extension audio cohérents ; l'index n'est pas une validation à l'oreille.
- La compatibilité de décodage WAV/OGG dépend des codecs Android. Les longs fichiers consomment espace/cache, données réseau et batterie.
- Si la redirection GitHub change d'hôte, le téléchargement est bloqué volontairement : adapter uniquement après audit de sécurité.
- Les URLs GitHub signées ne sont pas enregistrées ou exposées par l'application.
- Les 14 familles renvoient aux index de la source canonique ; elles ne représentent pas 14 copies locales.
- Tester sur le Samsung réel : scrolling, lecture, sélection du jeton, export, son grand format, annulation et reconnexion.

## Vérifications
- Tests JVM `DjipieAudioCatalogTest` : lecture, recherche accentuée, filtres, URLs hors dépôt refusées, doublons, métadonnées sans empreinte rejetées.
- Compilation Android/Kotlin et APK **debug 0.5.3-dev** depuis GitHub Actions sur la branche de mission ; une compilation CI verte ne garantit pas la réussite de la connexion privée à l'exécution.
- **Pas de merge dans main, pas de release et pas de Play/AAB** sans validation explicite de Fab.
