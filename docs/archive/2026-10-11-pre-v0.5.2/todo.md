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

## HOME-EXPLORER-002 — contrôle qualité à poursuivre

- [x] Fenêtre mini WebView sur accueil, chargement uniquement par ACTUALISER, possibilité de désactiver/agrandir.
- [x] Parcours GitHub Actions public sur une seule WebView, maximum 12 pages, délai fixe, timeout, arrêt sur 403/429.
- [x] Capturer/contrôler dans Chrome réel les pages Actions de DashbordGit et JTrex, sans HTML fictif.
- [x] Tests Kotlin des URL publiques et de la limite de navigation.
- [ ] Confirmer le **dernier** build CI Android et vérifier l'artefact APK (pas juste un ancien vert).
- [ ] Essai sur Samsung : aperçu 135dp, zoom/taille, boutons activer/pause/agrandir, parcours 6 projets, temps perçu, scrolling.
- [ ] Vérifier compteurs WebView et logs, aucune requête WebView au lancement, aucune navigation après sortie de l'application.
- [ ] Observer limitation GitHub 403/429 sans réessai automatique.
- [ ] Mesurer RAM et trafic réellement consommés par l'aperçu vs ancien Dashboard, réduire volume si trop coûteux.
- [ ] Préserver compatibilité sauvegardes, API sur consentement, barre quota, Supertonic 3, mosaïque pleine page.
- [ ] Pas de merge main, release ni publication Play sans autorisation.

## Correctifs 003 — tests et tâches

- [x] Brancher le vrai DOM Chromium en lecture seule sur le Dashboard avec contrôle strict ID+URL et aria-label.
- [x] Ajouter des cas de test sur vrai succès / en cours / échec, anti-faux-vert et run plus récent inconnu.
- [x] Conserver les cartes lors d'ACTUALISER ; actualiser chaque projet indépendamment sans vider la ScrollView.
- [x] Ajouter le choix non modal **CONTINUER SANS API** / **AUTORISER CETTE VÉRIFICATION** après les preuves Chromium.
- [x] Ajouter bouton **REFUSER** explicite dans les autres consentements API ; persistance seulement sur consentement volontaire.
- [ ] Dernier job CI Android/JVM et inspection de la correspondance DOM des vraies pages, puis vérification du bon APK du même commit.
- [ ] **Test matériel Samsung** : six projets visibles et défilables pendant le scan ; glissement du bas indépendant du petit navigateur, cartes lisibles avant décision API.
- [ ] Cliquer CONTINUER SANS API : zéro REST direct supplémentaire, aucun popup bloquant, couleurs déjà vérifiées toujours affichées.
- [ ] Tester autorisation API : une vérification par projet encore inconnu, refus possible à chaque nouvelle demande, barres API correctes.
- [ ] Observer build en cours devenant terminé dans WebView : enregistrer le statut seulement si GitHub modifie réellement son DOM ; sinon recharger manuellement.
- [ ] Tester changement de page, authentification, 403/429, reprise après pause, sérialisation de plusieurs ACTUALISER, consommation RAM et réseau.
- [ ] Conserver Supertonic 3, import/export, mosaïque et journaux identiques ; ne pas merger `main`.
