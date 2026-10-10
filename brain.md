# DashbordGit — architecture courante

- Android Kotlin ARM64 accessible, boutons ACTUALISER et ⚙. Aucune connexion automatique au démarrage.
- Mode **Web d'abord** : pages publiques GitHub et fichier `.dashboardgit.json` publié s'il est annoncé par une branche/lien visible. Aucun balayage spéculatif main/master/dashboardgit-status provoquant 3 HTTP 404 par projet.
- Compléments Web : dernier commit visible, branches visibles (liste partielle signalée), dernières releases, liens et identifiants GitHub Actions.
- Détection statut : `GithubActionHtmlParser` ne classe VERT/ROUGE/JAUNE/ORANGE que si l'état est lié à l'identifiant du run examiné dans une donnée structurée ou une ligne HTML non ambiguë. Sinon GRIS et dernier résultat connu clairement daté.
- Règle de priorité : Web plus récent prime sur JSON ancien ; Web ambigu pour **le même run** ne doit pas écraser un statut complété vérifié du JSON. Ancien build vert ne cache jamais build plus récent inconnu.
- API GitHub REST : seulement via catégorie et accord explicite (une fois ou permanent, révocable), et valeur de quota mesurée par headers. Défault « Web uniquement ». La jauge reste fixe, indique « inconnu » si non mesuré.
- TTS : Supertonic 3 INT8 seul, SID=1, seed=2026, morcellement OFF par défaut; paramétrage dans ⚙, TTS système en secours.
- Cache et sauvegardes horodatés, jamais de secret exporté; journal debug exportable.
- CI branch #27 : `gradle :app:testDebugUnitTest :app:assembleDebug` réussi. Test Android réel requis après nouvelle livraison.
