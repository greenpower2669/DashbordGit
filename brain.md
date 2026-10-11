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
