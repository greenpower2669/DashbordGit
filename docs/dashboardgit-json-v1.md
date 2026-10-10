# Contrat public DashbordGit JSON v1

Un dépôt public peut publier sur le Web un fichier /.dashboardgit.json
avec un résumé qui ne nécessite pas d'appel REST depuis Android.

Format :

{
  "schemaVersion": 1,
  "repo": "DashbordGit",
  "updatedAt": "2026-10-10T18:00:00Z",
  "runs": [{
    "id": 38079605085,
    "name": "Android APK",
    "status": "completed",
    "conclusion": "success",
    "head_branch": "main",
    "html_url": "https://github.com/greenpower2669/DashbordGit/actions/runs/38079605085",
    "head_commit": {"message": "exemple illustratif"}
  }]
}

Statuts : queued, in_progress, completed et unknown. La conclusion n'est
connue que si completed. Un manque d'information NE vaut JAMAIS success.

Champs facultatifs : branchCount, artifactName, latestCommit, latestRelease,
buildKnown/buildNeeded, mergeKnown/mergeNeeded, releaseKnown/releaseNeeded.
La paire "Known" doit rester false/absente sauf si la comparaison a été
réellement vérifiée. Ne pas inventer de besoin de merge, build ou release.

Ordre d'acquisition Android : lecture JSON public et page Actions publique ;
la page Web plus récente prime sur un JSON obsolète ; cache en dernier recours ;
API seulement sur autorisation. Les éléments absents restent GRIS.

Source préférée :
https://raw.githubusercontent.com/greenpower2669/DashbordGit/dashboardgit-status/.dashboardgit.json

Le producteur optionnel publish-dashboardgit-status.yml publie dans une branche
indépendante, sans déclencher de compilation (build.yml suit seulement main).
Il est déclenché par requested/in_progress/completed du workflow Android APK.
Le producteur évite de remplacer un run plus récent ou terminé par un ancien
événement. Toute publication peut prendre du retard ; ne pas traiter le JSON
comme une garantie d'état temps réel.

Le workflow_run producteur n'est activé qu'une fois son fichier présent sur
la branche par défaut, et nécessite contents:write autorisé. Ne pas
installer automatiquement dans les autres dépôts.
