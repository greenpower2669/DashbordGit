# DASHGIT-DJIPIE-AUDIO-005 — mise à jour catalogue et lecture après republication (18/10/2026)

## Incident visible sur la capture Samsung

L'explorateur affiche `25 Church Bells, Far Distance, Rural Soundscape Distant, Hills Ridge, Spring 02.wav` en famille NON_CLASSES, puis `GitHub 404 : vérifie le jeton`. Il affiche aussi des séquences `\\n` textuelles et des boutons trop serrés.

**Cause vérifiée par consultation des métadonnées GitHub privées :**
- Enregistrement historique Djipie : `asset_id=650663951` dans `catalogue_direct.json`.
- Release actuelle `djipie-audio-prive-non-classes` : même nom de fichier, **asset_id=652011955** ; le SHA-256 est resté `49af5cedf7e5dea91efb4084f8d25cb01bbf4581bc1923e31bf5dc249a381b0f`.
- Conclusion : l'identifiant GitHub des assets change lors de leur republication. L'ancienne application utilisait directement l'ID périmé. L'erreur 404 n'est pas une preuve de jeton expiré.

## Corrections techniques

1. **Catalogue** : requête manuelle vers `Djipie/feature/musiques-cc0-directes-v1` pour les **553 références** (447 historiques + 17 musiques CC0 + 85 jingles Kenney + 4 Meteor). Si cette branche a été supprimée et répond 404, essai du catalogue `main`. Les erreurs 401/403 restent des erreurs d'autorisation réelles, sans contournement.
2. **Téléchargement** : recherche l'asset dans `GET /repos/greenpower2669/Djipie/releases/tags/{releaseTag}` via le jeton autorisé sur `api.github.com`. Comparaison stricte du tag, nom exact, taille et digest SHA-256, puis utilisation de l'identifiant courant. Le téléchargement retourne toujours un SHA-256 exact. Aucun token dans les redirections CDN.
3. **Cache** : SHA-256 vérifié avant réutilisation, même si la taille est égale. Fichiers partiels supprimés.
4. **UX** : suppression des sauts de ligne littéraux, deux boutons maximum par ligne, commandes simples, 20 résultats par page (au lieu de 60), export MP3 avec `audio/mpeg`.
5. **Tests JVM** : résoudre l'ID republié, rejeter tag erroné, fichier manquant, taille ou SHA différente.

## Limites et autorisations

- Le catalogue de Djipie reste **privé**, ses médias ne sont ni compilés ni publiés dans DashboardGit, lui-même public. La branche source reste indépendante : aucune fusion/écriture dans Djipie.
- Les 553 références décrivent les fichiers actuellement indexés. Les liens et droits restent vérifiés à l'utilisation ; si un asset a disparu, l'utilisateur voit une erreur précise.
- Le nouveau DashboardGit est `0.5.4-dev` sur `feature/djipie-audio-explorer-001`, pas une Release stable. Les tests CI Android/JVM et le test d'écoute sur Samsung sont distincts.
- Jamais de merge `main`, de publication Play/AAB ou de GitHub Release sans accord de Fab. L'APK debug peut être construit par le workflow déjà présent ; son artifact GitHub Actions est un ZIP et ne constitue pas un lien APK direct.

## DJIPIE-AUDIO-006 — catalogue 579 (18 octobre 2026)
Nouvelle Release privée `Fireworksetgitelec` : 25 OGG désormais disponibles en fichiers individuels, guitare électrique WAV, SHA GitHub vérifiés. Catalogue Djipie nouveau `feature/fireworks-guitar-direct-v1` = 579 références ; ancienne branche 553 ne les connaît pas. DashboardGit v0.5.5-dev sur branche `feature/djipie-fireworks-audio-v055` lit nouveau catalogue d'abord ; 404 seulement -> ancien -> main, 401/403 non contournés. Interface indique si le catalogue est ancien, recherche guidée. CI v7 archive:false fournit un seul .apk après décompression/contrôle sur RUNNER_TEMP ; pas de fusion main ni Release. Une mention CC0 dans un nom ZIP n'est pas à elle seule une preuve de licence pour une redistribution externe.
