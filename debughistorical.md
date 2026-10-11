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
