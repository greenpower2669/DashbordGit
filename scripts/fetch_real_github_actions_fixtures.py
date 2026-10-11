#!/usr/bin/env python3
"""Integration input: REAL public GitHub HTML and GitHub API truth.

Downloads a known successful and a known failed public workflow run.
The API is used ONLY in CI as an independent verification reference;
the Android app NEVER calls it without user consent.
The HTML files are temporary test inputs and are NOT committed.
"""
import hashlib
import json
import os
from pathlib import Path
import time
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/"app"/"src"/"test"/"resources"/"github_live"
CASES=[
    ("success","greenpower2669/DashbordGit",38090733129),
    ("failure","greenpower2669/DashbordGit",38090493336),
]
USER_AGENT="DashbordGit-ci-real-source-validation/1.0"

def download(url, token=None, html=False):
    headers={
        "User-Agent": USER_AGENT if not html else "Mozilla/5.0 (compatible; DashbordGit-ci-real-source-validation/1.0)",
        "Accept": "text/html" if html else "application/vnd.github+json",
    }
    if token and not html:
        headers["Authorization"]="Bearer "+token
        headers["X-GitHub-Api-Version"]="2022-11-28"
    last=None
    for attempt in range(3):
        try:
            with urlopen(Request(url,headers=headers),timeout=35) as r:
                data=r.read(3_000_000)
                return r.status, r.geturl(), data
        except (HTTPError,URLError,TimeoutError) as exc:
            last=exc
            if attempt!=2: time.sleep(2*(attempt+1))
    raise RuntimeError("GitHub source unavailable: "+url+" / "+str(last))

def main():
    DEST.mkdir(parents=True,exist_ok=True)
    token=os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    reports=[]
    for expected,repo,run_id in CASES:
        api=f"https://api.github.com/repos/{repo}/actions/runs/{run_id}"
        web=f"https://github.com/{repo}/actions/runs/{run_id}"
        api_code,_,api_raw=download(api,token)
        api_json=json.loads(api_raw)
        status=api_json.get("status")
        conclusion=api_json.get("conclusion")
        assert api_json.get("id")==run_id, f"API run mismatch: {run_id}"
        assert status=="completed" and conclusion==expected, (
            f"Reference status changed unexpectedly for run {run_id}: {status}/{conclusion}"
        )
        clean_api={key:api_json.get(key) for key in (
            "id","status","conclusion","name","head_branch","display_title","html_url"
        )}
        (DEST/(f"api-{expected}-{run_id}.json")).write_text(
            json.dumps(clean_api,ensure_ascii=False,indent=2)+"\n",encoding="utf-8"
        )
        html_code,html_final,html_raw=download(web,html=True)
        assert html_code==200 and len(html_raw)>10_000, (
            f"Real HTML unavailable for {run_id}: HTTP {html_code}, {len(html_raw)} bytes"
        )
        assert str(run_id) in html_raw.decode("utf-8","replace"), (
            f"GitHub HTML does not contain target run ID {run_id}; cannot test a real run page"
        )
        # We never infer status from a generic success word inside the page.
        # Kotlin tests decide whether evidence is bound to this exact run ID.
        basename=f"{expected}-{run_id}"
        (DEST/(basename+".html")).write_bytes(html_raw)
        reports.append({
            "case":expected,"repo":repo,"runId":run_id,
            "expectedStatus":status,"expectedConclusion":conclusion,
            "url":web,"httpStatus":html_code,"bytes":len(html_raw),
            "sha256":hashlib.sha256(html_raw).hexdigest(),
            "redirected":html_final!=web,
        })
        print(f"REAL HTML captured {basename}: HTTP {html_code}, "
              f"{len(html_raw)} bytes, SHA256 {reports[-1]['sha256'][:16]}")
    (DEST/"manifest.json").write_text(
        json.dumps(reports,ensure_ascii=False,indent=2)+"\n",encoding="utf-8"
    )
    print("REAL LIVE FIXTURES READY: "+str(len(reports))+
          " (success and failure independently verified by REST in CI)")

if __name__=="__main__":
    main()
