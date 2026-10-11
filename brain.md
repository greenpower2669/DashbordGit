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
