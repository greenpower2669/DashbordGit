# DashbordGit — Android

Tableau de bord Android accessible pour suivre les projets GitHub publics de `greenpower2669`.

## Dashboard principal — conservé

- Projets sélectionnés et sauvegardés ; bouton **ACTUALISER** exclusivement manuel.
- Sources Web + JSON public éventuel ; API REST seulement selon autorisation explicite et jauge de quota horodatée.
- Statuts vert/rouge/en cours/attente/inconnu sans fausse réussite ; cartes parlantes.
- Supertonic 3 INT8 seul : voix 1, seed 2026, morcellement désactivé par défaut et paramétrable dans ⚙.
- Import/export de sauvegardes et de journaux debug.

## Nouveau — 🧩 MOSAÏQUE CHROMIUM (développement 0.5.0)

L'écran principal ouvre une mosaïque WebView native (Android Chromium) : **les vraies pages GitHub**, pas des iframes, accessibles sans API REST explicite depuis le Dashboard. La mosaïque ne charge rien avant un clic utilisateur. Par carte : 🔄 individuel, choix de cible GitHub Actions / dernier run connu / commit, titre parlable, double tap vers « Ouvrir avec », appui long pour recharger. Bouton général pour recharger séquentiellement les cartes.

Défaut : trois fenêtres actives au plus (choix 2/3/4/6), les anciennes représentées par des **captures figées clairement identifiées**. Texte agrandi et cadrage sur la zone utile lorsque détectable. Les pages peuvent modifier leur DOM et émettre des requêtes réseau internes ; le Dashboard n'ajoute aucun polling.

**La mise à jour spontanée d'un workflow jusqu'au statut final n'est pas prouvée sur Android.** Tests navigateur Chrome réel en CI sur pages GitHub publiques, avec rapport de ressources et captures ; voir `docs/DASHGIT-CHROMIUM-001.md`. Les contenus HTML et comportements Chrome/WebView peuvent différer. Accès privé/login : fallback navigateur externe.

## Nouveau — mini-explorateur Chromium sur l'accueil (0.5.1-dev)

Une fenêtre **réelle et minuscule** (WebView de 135dp de haut) sur la première page montre les pages GitHub Actions pendant une actualisation **manuelle**. Compteur de visites et de ressources, action pause/activer, bouton agrandir vers la mosaïque. Un seul navigateur réutilisé, maximum 12 pages par clic, 3 secondes minimum entre pages, timeout 20s ; stop sur HTTP403/429, pas de polling ni déguisement pour contourner les protections. Le scan des données existant continue indépendamment et le navigateur ajoute du trafic Web : aucun statut de build n'est inventé.

Détails et tests : `docs/DASHGIT-HOME-EXPLORER-002.md`.

## Règles de développement

Branche : `feature/dashgit-home-explorer-002` (base `feature/dashgit-chromium-mosaic-001`). **Aucun merge main ni release** sans validation de Fab. Données d'authentification jamais versionnées ; journaux exempts de cookies/secrets. Les statuts inconnus restent gris.
