# DashbordGit — suivi opérationnel DASHGIT-CHROMIUM-001

- [x] Lire 5 mémoires vivantes avant codage et auditer MainActivity existante.
- [x] Branche dédiée `feature/dashgit-chromium-mosaic-001`, sans toucher main ni release.
- [x] Activité mosaïque WebView Chromium native, sans iframe et sans API REST explicite pour les statuts.
- [x] Cibles Actions (défaut) / run / commit par dépôt ; sauvegarde du choix.
- [x] Boutons individuel, global séquencé, ouverture externe « Ouvrir avec », appui long/double tap (code ajouté).
- [x] Afficher page réelle + grossissement + cadrage, et images figées explicitement signalées sous limite de fenêtres (3 par défaut, choix 2/3/4/6).
- [x] Observer DOM via MutationObserver uniquement, sans polling, distinguer requêtes internes Chromium du REST Dashboard.
- [x] Tester vraies pages GitHub succès/échec/en cours dans Chrome headless (pas sur Android), conserver rapport et captures en artifact CI.
- [x] Documenter observations négatives et impossibilités / incertitudes.
- [ ] **TEST ANDROID RÉEL PRIORITAIRE** : WebView charge les pages GitHub sur Samsung, taille/position du dernier build lisibles, appui long et double tap différenciés, ouvrir avec Chrome.
- [ ] **EXPERIENCE SPONTANÉE** : garder une page build en cours ouverte sans action jusqu'au résultat, comparer avec Chrome externe et noter horodatages/statut/changements DOM sans rechargement.
- [ ] Mesurer RAM totale application + renderer, réseau et batterie en mode 2/3/4/6 WebViews, ajuster les limites si nécessaire.
- [ ] Tester auth privée, HTTP403/404, crash renderer, navigation externe, orientation et pauses.
- [ ] Vérifier MainActivity, sauvegardes, consentements API, jauge et Supertonic 3 inchangés.
- [ ] Vérifier CI de tout dernier commit, livrer lien GitHub Actions/artefacts.
- [ ] Aucun merge main ni publication/release avant accord explicite.
