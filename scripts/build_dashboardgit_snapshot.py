#!/usr/bin/env python3
"""Create an optional public .dashboardgit.json from GitHub workflow_run.
No network calls. Ignore stale events so an old green cannot overwrite a new run.
"""
import argparse
import datetime
import json
from pathlib import Path

STATUSES = {"requested": "queued", "in_progress": "in_progress", "completed": "completed"}
ALLOWED_CONCLUSIONS = {"success", "failure", "cancelled", "timed_out", "action_required", "skipped", "neutral", "startup_failure"}

def snapshot(event, repository, previous=None):
    if event.get("action") not in STATUSES:
        raise ValueError("Only workflow_run requested/in_progress/completed events supported")
    run = event.get("workflow_run") or {}
    identifier = run.get("id")
    if not isinstance(identifier,int) or identifier <= 0:
        raise ValueError("Missing workflow run ID")
    repo_name = repository.split("/")[-1]
    status = STATUSES[event["action"]]
    conclusion = (run.get("conclusion") or "") if status == "completed" else ""
    if conclusion and conclusion not in ALLOWED_CONCLUSIONS:
        conclusion = ""
    current = {
        "id": identifier,
        "name": run.get("name") or "GitHub Actions",
        "status": status,
        "conclusion": conclusion,
        "head_branch": run.get("head_branch") or "",
        "html_url": run.get("html_url") or "",
        "head_commit": {"message": run.get("display_title") or ""},
    }
    if previous and isinstance(previous,dict) and previous.get("repo") == repo_name:
        old_runs = previous.get("runs") or []
        if old_runs:
            prior = old_runs[0]
            old_id = prior.get("id",-1)
            if isinstance(old_id,int):
                if old_id > identifier:
                    return previous
                if old_id == identifier and prior.get("status") == "completed" and status != "completed":
                    return previous
    return {
        "schemaVersion": 1,
        "repo": repo_name,
        "updatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "runs": [current],
    }

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--event",required=True)
    parser.add_argument("--repository",required=True)
    parser.add_argument("--previous")
    parser.add_argument("--output",required=True)
    args=parser.parse_args()
    event=json.loads(Path(args.event).read_text(encoding="utf-8"))
    prior=None
    if args.previous and Path(args.previous).is_file():
        try:
            prior=json.loads(Path(args.previous).read_text(encoding="utf-8"))
        except (ValueError,OSError):
            pass
    result=snapshot(event,args.repository,prior)
    Path(args.output).write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")

if __name__=="__main__":
    main()
