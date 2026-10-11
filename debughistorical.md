# DashbordGit — historique incidents et preuves

## DASHGIT-CHROMIUM-001 — 11/10/2026

- GitHub refuse généralement d'être encadré en iframe ; décision : WebView Android top-level par carte, pas iframe. Chromium peut mutualiser son renderer mais un seul processus n'est jamais garanti.
- Test CI navigateur réel Chrome : pages success #38090733129, failure #38090493336, in progress #38127356104. Indicateurs de vérité REST (uniquement dans test CI) respectivement success/failure/in_progress. Chrome a affiché les véritables pages.
- Chaque page : 156 ressources de navigateur signalées, dont 2 vers `api.github.com`. Mémoire JavaScript JSHeapUsedSize : 11 857 564 / 17 635 340 / 17 079 692 octets respectivement. **Ce ne sont PAS les mesures WebView Android**.
- Aucun changement DOM constaté pendant 8 s, sur les trois échantillons. Cela **ne prouve pas** l'absence de mise à jour spontanée lorsque l'état du build change ; transition complète restant à tester en situation.
- Build #48 (intégration initiale) CI verte. Build #50 navigateur CI échoué à cause d'une erreur de clé Python `browserUrl` dans l'assertion, corrigée `380c2cae`. Build #52 CI verte ; build #53 avec centrage DOM CI verte. Mises à jour ultérieures à vérifier.
- Aucune garantie que les gestes WebView et double tap Chromium seront sans conflit ; titre natif constitue zone de geste fiable. Mesures de mémoire totale appareil et batterie à faire.
- Anciennes erreurs Web-only : parser HTML de statuts sur vraies pages 0/3, API indépendante 3/3 ; conservées. Ne jamais transformer gris en vert sans preuve.
- Ancien site / app : 12 erreurs 404 JSON corrigées ; Supertonic 3 testé sur Samsung, SID1 seed2026 ; APK triplement vocal >380Mo remplacé par un seul modèle.
- Liens sandbox auparavant indisponibles : préférer CI GitHub et vérifier physiquement toute pièce jointe.

## HOME-EXPLORER-002 — contrôles du 11/10/2026

- Requête utilisateur : voir en direct dans une toute petite fenêtre, sur la première page, le navigateur qui explore les dépôts. L'idée de timings « humains et aléatoires pour ne pas être détecté » n'est pas implémentée : aucun déguisement ou contournement de protections. Parcours borné, délai fixe et respect des 403/429.
- Vrai Chrome en CI : liste Actions DashbordGit rendue (4564 caractères, 12 liens visibles, 150 ressources observées dont 1 vers api.github.com) ; JTrex Actions rendue (5023 caractères, 12 liens visibles, 148 ressources dont 1 vers api.github.com). Ce ne sont pas des mesures WebView Android.
- Première compilation #58 échouée sur une faute Kotlin `loadsImages` non supportée ; correction `loadsImagesAutomatically` commit `4281bc9c`. Les builds intermédiaires ont pu reprendre la même erreur.
- Vérifier le dernier build après mise à jour des mémoires et conserver la preuve CI ; ne jamais annoncer une capacité Android réelle seulement sur une compilation verte.

## Diagnostic et corrections 003 — 11/10/2026

- Capture/journal Fab 13:19–13:20 : six projets visités, codes couleur visibles dans la mini-WebView ; ancienne lecture HTML non capable de confirmer les statuts. 347 ressources navigateur observées, 5 vers api.github.com dans ce parcours, 0 API Dashboard explicitement autorisée.
- Problème liste blanche : MainActivity vidait `list` puis n'affichait les cartes qu'après la proposition REST, rendant le défilement inutilisable durant le scan. Correction : holders persistants + mise à jour progressive, dates d'anciennes données distinguées.
- Problème consentement : ancien `setItems` dans une popup, options et REFUSER peu visibles ; remplacement par choix REST non modal dans la première page, boutons explicites refus/accord, option persistante volontaire. Autres demandes API ont boutons positifs/négatifs explicites.
- **VRAIE PAGE** GitHub Actions DashbordGit : aria sur ancre de build `currently running: Run 65...`, `completed successfully: Run 64...`; JTrex : `completed successfully: Run 115...`, `failed: Run 114...`. Les libellés et URLs exactes ont été observés par Chrome CI sur vraies pages ; comparaison faite avec REST CI, jamais avec un HTML inventé.
- Un build plus récent sans aria exploitable passe gris et masque l'ancien vert. Extraction locale du DOM WebView sans requête REST ajoutée ; statut à confirmer sur téléphone Samsung.
- Les couleurs visuelles seules ne constituent pas une preuve. GitHub peut modifier ses aria-label : garder le gris et le fallback consentement API si la structure change.
- La compilation verte n'atteste pas du toucher/défilement ni du résultat spontané Android : test réel requis.
