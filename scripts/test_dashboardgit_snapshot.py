#!/usr/bin/env python3
"""Offline tests for the public status JSON generator."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from build_dashboardgit_snapshot import snapshot
event={
    "action":"requested",
    "workflow_run":{"id":100,"name":"Android APK","head_branch":"main",
                    "html_url":"https://github.com/example/test/actions/runs/100",
                    "display_title":"fix build"}
}
queued=snapshot(event,"example/DashbordGit")
assert queued["schemaVersion"]==1 and queued["repo"]=="DashbordGit"
assert queued["runs"][0]["status"]=="queued"
event["action"]="in_progress"
progress=snapshot(event,"example/DashbordGit",queued)
assert progress["runs"][0]["status"]=="in_progress"
event["action"]="completed"
event["workflow_run"]["conclusion"]="success"
done=snapshot(event,"example/DashbordGit",progress)
assert done["runs"][0]["status"]=="completed" and done["runs"][0]["conclusion"]=="success"
event["action"]="in_progress"
assert snapshot(event,"example/DashbordGit",done)["runs"][0]["status"]=="completed"
event["workflow_run"]["id"]=99
assert snapshot(event,"example/DashbordGit",done)["runs"][0]["id"]==100
event["workflow_run"]["id"]=101
assert snapshot(event,"example/DashbordGit",done)["runs"][0]["id"]==101
print("PASS: queued/progress/completed and stale event guards")
