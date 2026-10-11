# DASHGIT-CHROMIUM-001 — constat et procédure d'essai

Date : 11 octobre 2026. Branche dédiée : `feature/dashgit-chromium-mosaic-001`.

## Architecture réelle

- Android utilise **WebView système basé sur Chromium** (plusieurs vues visibles), sans iframe et sans empaqueter un Chrome indépendant.
- Android/Chromium **peut** mutualiser le moteur et les processus de rendu. Une unique instance de processus n'est **pas** garantie.
- Plafond mémorisé de **2, 3 (défaut), 4 ou 6 fenêtres WebView actives**. Quand la limite est atteinte, une ancienne fenêtre est désactivée et **sa capture devient explicitement figée**. Pour la revoir en direct, recharger/activer cette carte : cela peut désactiver une autre.
- Les cartes commencent SANS réseau à l'ouverture de l'activité. L'utilisateur choisit d'abord « 🔄 TOUT RECHARGER » ou un bouton 🔄 individuel.
- Bouton global : chargements séquencés, terminés/timeout avant le suivant, léger délai entre cartes. Ce n'est pas un polling continu ni un rechargement planifié.
- Chaque carte conserve URL et type (GitHub Actions récent par défaut, dernier run connu ou commit connu) ; sélection mémorisée.
- Double-tap sur le titre et sur la surface WebView : tentative d'ouverture via « Ouvrir avec ». Appui long/bouton 🔄 : rechargement individuel. Un appui simple dans WebView reste géré par la vraie page GitHub ; toucher le titre vocalise l'état antérieurement enregistré.
- Agrandissement du texte WebView à 155 %, échelle initiale 135 %, et cadrage d'un élément pertinent (build/commit) via `scrollIntoView` après chargement. Sélecteurs non garantis par GitHub : fallback page entière.
- Les sous-pages externes et navigations sortant de la cible GitHub ouvrent un navigateur externe ou sont bloquées. Pas de pont JavaScript Android exposé aux pages distantes. Fichiers locaux, contenu local et contenu mixte interdits.
- L'observateur DOM déclenché au chargement utilise **MutationObserver** ; il n'appelle ni `fetch` ni REST. Un changement du DOM est signalé comme activité, **pas comme modification de statut certifiée**.
- Les requêtes internes aux pages WebView ne sont pas des appels REST explicites de DashbordGit, mais peuvent atteindre `api.github.com` et consommer des ressources réseau. Le compteur d'interception HTTP du WebView est **indicatif** (certaines requêtes échappent à l'observation, pas de métrique en octets).
- Les statuts dans le bandeau de carte restent ceux de la dernière vérification mémorisée, clairement marqués **PAS EN DIRECT**. Le contenu visible dans WebView est la vraie page GitHub à l'instant où elle se rend (et seulement si GitHub actualise son DOM en interne). Ne pas afficher un ancien vert comme nouveau succès.

## Limitations / fallback

- Les protections GitHub contre iframes sont respectées : aucun iframe utilisé. WebView n'est pas forcément Chrome externe, notamment cookies, identifications, extensions et isolation.
- Connexion/authentification GitHub via navigateur externe recommandée ; l'application n'enregistre pas de token/cookie dans Git et ne synchronise pas les cookies Chrome.
- DOM mutable/non documenté : seul GitHub peut garantir les éléments qu'il choisit d'afficher. L'extraction du vrai statut via HTML a déjà échoué sur les tests réels précédents ; les cartes WebView doivent laisser voir la page, pas annoncer un vert inféré.
- L'ancien Dashboard, son mode WEB/JSON/REST autorisé, son quota, ses sauvegardes et son Supertonic 3 restent conservés. Mode mosaïque : **aucun REST Dashboard pour suivre les couleurs**.
- Limite mémoire : une capture figée n'est jamais une fenêtre live. Sélection de 6 active = coût potentiellement élevé ; à tester sur Samsung en conditions réelles.
- Les deux gestes sur la surface WebView peuvent entrer en concurrence avec les gestes/navigations natifs Chromium. La zone de titre reste la cible fiable pour double-tap et appui long. Valider réellement sur téléphone.

## Validation sur de VRAIES pages GitHub, build #53

L'outillage CI a ouvert les pages en **Chrome headless réel**, à l'aide de Selenium, sans iframe. État « vérité » indépendant par API CI (non utilisé par la mosaïque Android).

| Page réelle | État officiel CI | Requêtes ressources Chromium* | dont api.github.com* | Heap JS Chromium* | Mutations DOM en 8 s après chargement |
|---|---|---:|---:|---:|---:|
| DashbordGit run 38090733129 | Terminé, succès | 156 | 2 | 11 857 564 octets | 0 |
| DashbordGit run 38090493336 | Terminé, échec | 156 | 2 | 17 635 340 octets | 0 |
| DashbordGit run 38127356104 (pendant build #53) | En cours | 156 | 2 | 17 079 692 octets | 0 |

*Observations **ordinateur CI + navigateur Chrome**, pas des valeurs mesurées par Android WebView ; cela ne constitue pas une estimation de sa RAM totale. Les requêtes `api.github.com` sont internes à GitHub lors de sa propre navigation, et non lancées par DashbordGit.

**Résultat de l'expérience « évolution spontanée » : non démontré.** Aucun changement du DOM sur ces trois fenêtres de 8 s, et aucun des trois tests n'a couvert avec certitude la transition complète d'un build encore ouvert de « en cours » à « terminé ». Il serait inexact de promettre des indicateurs qui se mettent à jour spontanément. En cas d'absence de mise à jour, l'utilisateur devra cliquer 🔄 sur une carte.

GitHub peut changer sa structure. Le contrôle CI est refait contre de vraies URL publiques lors des builds de cette branche. Le test sur Chrome ne remplace **pas** les essais WebView Android.

## Protocole de validation Android restant

1. Ouvrir la mosaïque, **sans toucher aucun bouton** : aucun chargement ni requête manuelle.
2. ACTUALISER TOUT : vérifier chargement séquentiel, trois WebView au plus (défaut), anciennes captures figées.
3. Activer une carte d'un dépôt dont le workflow tourne, vérifier le zoom et le cadrage.
4. Comparer la même URL dans Chrome externe au même instant.
5. Sans geste pendant que le build se termine : relever le changement **ou non** dans le WebView et le journal `MOSAÏQUE`, ne pas déduire « changement de build » d'une simple mutation DOM.
6. Tester double-tap du **titre**, du WebView, appui long et bouton individuel 🔄 : autres cartes ne se rechargent pas.
7. Vérifier réponse à 403, 404, connexion privée, changement d'orientation, crash du renderer et fermeture.
8. Tester 2/3/4/6 fenêtres sur Samsung, mesurer la mémoire totale de l'application (y compris sous-processus WebView), consommation réseau et réactivité.
9. Vérifier que MainActivity et Supertonic 3, sauvegardes et autorisations API existants fonctionnent toujours.

**Aucune fusion vers main ni release sans approbation explicite de Fab.**
