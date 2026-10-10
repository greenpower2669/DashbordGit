# DashbordGit

Dashboard Android accessible pour suivre les projets GitHub de greenpower2669.

## Objectif V1
- scan des dépôts publics ;
- choix dans l'engrenage des projets à suivre ;
- affichage des branches récemment actives ;
- rafraîchissement **uniquement manuel** ;
- lecture des derniers GitHub Actions : vert, en cours, rouge ;
- affichage du message du commit à côté de chaque résultat ;
- artefacts APK/AAB lorsqu'ils sont disponibles ;
- lecture vocale au toucher d'une carte ;
- voix féminine locale, avec architecture prévue pour Sherpa-ONNX / Kokoro ;
- token GitHub facultatif saisi localement, jamais versionné.

## Contrat facultatif
Un projet pourra fournir `.dashboardgit.json`. S'il est absent, l'application fonctionne en mode découverte et ne suppose jamais qu'un rouge est volontaire.

## Sécurité
Aucun token, mot de passe ou secret dans le dépôt.
