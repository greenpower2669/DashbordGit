# Rapport d'implémentation — 003

Date : 11 octobre 2026. Branche : `feature/dashgit-dom-scroll-consent-003`.

## Constat à reproduire

Le journal utilisateur du 11 octobre (six projets) montre un navigateur Chromium qui parcourt avec succès des pages GitHub Actions et révèle visuellement des icônes vertes, rouges ou orange. L'ancien moteur HTML déclarait pourtant « build non prouvé » pour tous ; l'interface effaçait les cartes pendant la lecture et bloquait l'utilisateur sur une demande REST.

## Corrections

1. Les cadres des dépôts restent sous un ScrollView, ne sont jamais vidés globalement lors d'un ACTUALISER normal. Chaque snapshot Web/Chromium/REST remplace seulement un holder du bon dépôt. Le cache déjà présent reste indiqué « DERNIER CONNU » jusqu'à une nouvelle preuve.
2. DOM de la **vraie page Github** : JavaScript partagé `app/src/main/assets/github_dom_evidence.js` utilisé par WebView Android ET par Selenium Chromium CI. Pas de fetch, xhr, iframe, contournement, ni couleur CSS seule ; seuls `aria-label` du lien direct vers `/greenpower2669/<repo>/actions/runs/<id>` et son icône dans ce même lien peuvent fournir l'état. URL + run ID validés par `GithubDomEvidence.kt`. Les messages, branches et dates sont complémentaires.
3. `MainActivity` reprend aussitôt un résultat DOM sémantique. Si un run plus récent est visible sans preuve d'état, il reste **gris** et cache un ancien succès. Si un run se termine spontanément et GitHub modifie effectivement le DOM observé, l'app peut relire la preuve sans refaire de requête ; cette transition complète est **non encore démontrée sur Android**.
4. Proposition API uniquement après fin du parcours WebView et du scan Web si des projets restent inconnus. Pas de modal bloquant : **CONTINUER SANS API** / **AUTORISER CETTE VÉRIFICATION**. Checkbox mémorisation décochée ; mode Web strict non contourné. Les autres demandes (quota/découverte) ont un bouton REFUSER explicite.

## Tests de vérité sur vraies pages

Dans Chrome CI, les liens de runs exposent des libellés réels avec preuves indépendantes REST CI :
- DashbordGit run #38136446069 : `currently running` ; API `in_progress` au moment du test.
- DashbordGit run #38132400440 : `completed successfully` ; API `success`.
- JTrex run #37941665158 : `completed successfully` ; API `success`.
- JTrex run #37941565126 : `failed` ; API `failure`.
- DashbordGit run #38136914792 : `failed` ; API `failure`.

Ces essais prouvent que les vraies pages HTML rendues contiennent des noms accessibles utiles. Ils ne prouvent pas le comportement de toutes les versions de GitHub ni les gestes Android.

## Risques et restrictions

- À vérifier physiquement : rafraîchissement des cartes et défilement pendant que la WebView miniature charge, choix refuser/autoriser, capture d'ARIA via WebView Samsung Android 16, popup login ou blocage HTTP.
- Pas de promesse que GitHub actualise spontanément un build : les mesures précédentes sur Chrome headless ont observé 0 mutation du DOM durant 8 secondes, pas une transition de workflow complète.
- Aucun token ni cookie sauvegardé dans le repo. WebView a son trafic interne GitHub ; quota/API REST du Dashboard ne doit pas être confondu.
- Aucune fusion `main`, release ou Play sans accord explicite.
