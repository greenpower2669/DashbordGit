# DashbordGit — historique des incidents et faits vérifiés

## 2026-10-11, contrôle robuste après la capture utilisateur
- L'APK précédent trouvait 5 run IDs dans chacun des six dépôts mais affichait des statuts gris.
- Journal : plus de recherches JSON inutiles (absence de manifestes annoncée). Supertonic 3 voix 1 / seed 2026 fonctionne ; ne pas modifier.
- Source réelle en CI : page succès du run 38090733129 (HTTP 200), page échec 38090493336 (HTTP 200), et page du run CI en cours (HTTP 200). Trois HTML authentiques, avec ID cible inclus.
- Résultat du **build #46** : 0/3 états confirmables avec le parseur HTML ; il a correctement refusé les fausses certitudes. Les réponses API indépendantes ont donné succès 🟢, échec 🔴, en cours 🟡 (3/3).
- Correction : arrêter les lectures de pages de détail HTML qui n'apportent pas de statuts ; utiliser le HTML pour découvrir les runs et l'API uniquement après consentement groupé.
- `GithubApiConsentPolicyTest` vérifie qu'aucun mode Web seul ou réglage corrompu n'autorise une requête API.
- Attention : CI verte = compilation/tests, **pas** garantie d'état de tous les repos en Web seul. Pour actualiser les six projets en couleurs, accorder la vérification API.
- Un contrôle réel d'interface Android avec le nouvel accord groupé reste à réaliser.

## Précédents
- Anciennes demandes REST sans contrôle de quota : HTTP 403.
- Kokoro : crash natif/latence, abandonné. Piper/MMS supprimés.
- APK trois moteurs >380 Mo : Supertonic seul depuis la branche Web-first.
- Liens ChatGPT auparavant défaillants : vérifier l'existence réelle du fichier avant tout lien sandbox.
- Aucune synthèse JSON publique n'est encore activée sur les autres repos.
