# DashbordGit — architecture opérationnelle en branche de travail

- L'ancien Android Kotlin ARM64 demeure : ACTUALISER réseau uniquement manuel, découvertes Web/JSON, vérifications REST sur consentement, quota visible, sauvegardes, journal et Supertonic 3 SID 1/seed 2026/morcellement OFF.
- Nouvelle `MosaicActivity` accessible avec bouton dédié « 🧩 MOSAÏQUE CHROMIUM » : plusieurs WebView Android natives chargeant directement github.com. PAS iframe, PAS de Chrome embarqué en double ; ressources Chromium éventuellement mutualisées par Android, processus unique NON garanti.
- Cibles : liste Actions du dépôt par défaut (suivi de nouveaux builds), dernier run connu ou commit connu via sélection par carte. URL GitHub exacte vérifiée et conservée, persistance locale.
- Zéro chargement à l'ouverture ; individuel 🔄/appui long ou global explicitement manuel. Le global est séquencé avec délais de sécurité, sans polling.
- Double appui sur titre et tentative sur WebView ouvrent un choix de navigateur (Chrome externe possible). Appui simple dans WebView garde comportement natif ; le titre parle via TTS système léger dans cette activité. Supertonic 3 du Dashboard n'a pas été modifié.
- Zoom WebView texte 155 %, échelle 135 % et centrage ponctuel via sélecteur DOM (fallback page complète). Sur Android petit écran : colonne unique pour lisibilité ; 2 colonnes si largeur >=720dp.
- Budget de 2,3,4 ou 6 WebViews en mémoire (3 par défaut). Éviction : capture réellement dessinée puis étiquetée « FIGÉE — NON EN DIRECT » ; elle ne reçoit aucune évolution GitHub.
- `MutationObserver` sans réseau détecte des modifications DOM *possibles*. Aucun voyant « build terminé » n'est inféré d'un simple changement DOM ; GitHub affiché dans WebView demeure autorité visuelle.
- Compteurs indicatifs `shouldInterceptRequest` : ressources Chromium et requêtes vers `api.github.com` internes ; pas de REST explicite de la mosaïque. Pas de total réseau en octets ni RAM Android prouvée.
- Erreurs HTTP/chargement et renderer crash traités sans faux succès ; auth privée → ouverture navigateur externe.
- Chrome Headless CI réel : pages success/failure/in_progress vérifiées via API indépendamment. Échantillons #38090733129/#38090493336/#38127356104 chargés, 0 mutation du DOM observée pendant 8s chacun. Transition in_progress→completed NON observée.
- Aucune mise en réseau autonome de DashbordGit ; seule l'activité propre des pages GitHub peut continuer à solliciter Internet.

## DASHGIT-HOME-EXPLORER-002 — aperçu miniature sur l'accueil

- Un cadre **🔎 MINI-EXPLORATEUR** visible sur l'écran MainActivity, affichage WebView de hauteur 135dp, commande AGRANDIR (mosaïque) et pause/activation locale. La WebView est instanciée **uniquement** après ACTUALISER, jamais au démarrage.
- L'aperçu parcourt les vraies pages Actions des dépôts sélectionnés via `GithubExplorerPlan`, dans une unique WebView réutilisée. Maximum 12 pages par commande, une à la fois, 3 secondes minimum de pause fixe entre pages et timeout de 20 secondes. Aucun rafraîchissement périodique, aucune dissimulation de trafic ni timing destiné à échapper aux protections.
- Le scan structuré Web/JSON/API de MainActivity est **indépendant** de l'aperçu visuel Chromium. Ce parcours génère un trafic WebView supplémentaire ; ne jamais affirmer que le navigateur consomme 0 ressource ou quota. Le compteur des ressources HTTP et de api.github.com est indicatif, affiché et journalisé.
- Erreurs WebView, auth externe, HTTP 403/429 et perte du renderer : message explicite ; protections GitHub respectées et arrêt en cas de limitation. L'aperçu n'infère aucun état vert/rouge d'une simple mutation DOM.
- Départ uniquement sur une action utilisateur, bouton activation pour couper, libération des ressources au passage vers la mosaïque ou une autre application. MainActivity garde ses données locales, ses sauvegardes et Supertonic 3.
- Capture Chrome headless réelle en CI de `https://github.com/greenpower2669/DashbordGit/actions` et `.../JTrex/actions`, rendu confirmé. **Ce test Chrome ne valide pas encore Android WebView Samsung.**

## DASHGIT-DOM-SCROLL-CONSENT-003 — corrections Android

- Accueil : les cartes possèdent un emplacement stable (LinearLayout), initialisé avec le cache et conservé durant ACTUALISER ; chaque résultat Web/Chromium/API remplace seulement le contenu du dépôt concerné. L'interface de défilement ne doit plus se vider pendant la décision API.
- `HomeExplorerPreview.kt` lit depuis une vraie page WebView les attributs `aria-label` du lien exact `/greenpower2669/<repo>/actions/runs/<id>`, par le script partagé `app/src/main/assets/github_dom_evidence.js`. Aucun fetch/REST ajouté par ce script ; extraction du DOM local uniquement.
- `GithubDomEvidence.kt` vérifie URL, dépôt, ID, libellé explicite et date d'observation. Sens prouvés sur de vraies pages GitHub : `completed successfully:` => succès, `currently running:` => en cours, `failed:` => échec. Cas non prouvé => état gris. Un run récent inconnu masque un ancien succès ; une ancienne réussite ne peut jamais écraser un ID supérieur.
- Les preuves DOM alimentent MainActivity pendant l'exploration, avec séparation des états historiques (cache = DERNIER CONNU) et des états fraîchement observés. L'API n'est proposée qu'après la visite Chromium et la lecture Web, et uniquement pour les inconnus restants.
- Demande REST **non modale et explicite dans l'accueil** : `CONTINUER SANS API` ou `AUTORISER CETTE VÉRIFICATION`. Option de mémorisation décochée par défaut. Si mode WEB strict : choix `ACTIVER LE MODE DEMANDE`, jamais appel sans changement explicite. Tous les autres dialogues API ont un vrai bouton `REFUSER`.
- Le moteur vocal Supertonic 3 et la mosaïque en pleine page ne sont pas modifiés. Vérification physique de la ScrollView sur Samsung requise avant réussite fonctionnelle complète.
