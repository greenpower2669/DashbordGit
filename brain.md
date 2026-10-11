# DashbordGit — architecture active

- Android Kotlin ARM64, ACTUALISER manuel, aucune requête réseau au lancement.
- Projets/branches/releases : pages Web publiques. Manifestes `.dashboardgit.json` seulement si publiquement annoncés ; pas de 404 systématiques.
- Builds : identifier les runs dans la page Actions ; tenter une preuve ciblée, ne jamais déduire un succès d'une icône non liée.
- **Constat CI réel du 11/10/2026 :** trois pages HTML Github.com authentiques avec identifiants valides (success, failure, running) ont donné `UNKNOWN` (0/3). L'API GitHub officielle fournit pour ces mêmes runs les statuts sûrs (3/3).
- Pour les états inconnus, une seule boîte de dialogue demande si l'utilisateur autorise une requête API par projet concerné, avec noms et coût prévisionnel. Réponse négative → zéro appel et voyants gris.
- Politique centralisée `GithubApiConsentPolicy` : `WEB` interdit tout appel ; `ASK` exige consentement si catégorie non autorisée ; `ALLOWED` n'utilise que les catégories explicitement autorisées ; réglages inconnus bloqués.
- Mode par défaut ASK sur nouvelle installation / anciennes sauvegardes sans réglage explicite ; choix WEB explicite conservé.
- Jauge API fixée, uniquement valeurs reçues de GitHub, datées. Cache avec horodatage.
- Contrat unique `GithubWorkflowStatus` : réussite verte, échec rouge, en cours jaune, attente orange, et inconnu gris. Un résultat completed sans conclusion reste inconnu.
- Supertonic 3 INT8 seul ; SID 1, seed 2026, morcellement OFF par défaut ; sauvegardes, journaux et TTS système de secours.
- CI : sources GitHub LIVE capturées temporairement, tests Kotlin sur HTML et REST indépendants, rapport publié comme artefact. Aucun HTML fictif ne peut à lui seul valider cette fonctionnalité.
