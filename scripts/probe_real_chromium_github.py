#!/usr/bin/env python3
"""DASHGIT-CHROMIUM-001 — real GitHub pages in real headless Chromium.

This is a desktop Chromium smoke test, NOT an Android WebView test.
A ChromeDriver session remains on each page: the second observation does NOT reload.
We report DOM mutations and resource timings but never assume they prove a
workflow status changed. Live run numbers can be verified separately via API in CI.
"""
import json
import os
import pathlib
import shutil
import time
from urllib.request import Request, urlopen

from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service

ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/"build"/"chromium-live"
OUT.mkdir(parents=True,exist_ok=True)

CASES=[
    ("verified-success","DashbordGit",38090733129),
    ("verified-failure","DashbordGit",38090493336),
]
if os.getenv("GITHUB_RUN_ID"):
    CASES.append(("current-ci-run","DashbordGit",int(os.environ["GITHUB_RUN_ID"])))

def api_truth(repo,run_id):
    url=f"https://api.github.com/repos/greenpower2669/{repo}/actions/runs/{run_id}"
    headers={"User-Agent":"DashbordGit-Chromium-real-test/1","Accept":"application/vnd.github+json"}
    token=os.environ.get("GITHUB_TOKEN")
    if token: headers["Authorization"]="Bearer "+token
    try:
        with urlopen(Request(url,headers=headers),timeout=20) as response:
            item=json.loads(response.read(500_000))
        return {"status":item.get("status"),"conclusion":item.get("conclusion")}
    except Exception as exc:
        return {"error":exc.__class__.__name__}

def chromium_driver():
    options=Options()
    binary=next((shutil.which(x) for x in
                 ("google-chrome","google-chrome-stable","chromium","chromium-browser")
                 if shutil.which(x)),None)
    if binary is None:
        raise RuntimeError("No real Chromium/Google Chrome binary available")
    options.binary_location=binary
    options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--window-size=420,900")
    options.add_argument("--lang=fr-FR")
    options.add_argument("--disable-background-networking")
    options.add_argument("--remote-allow-origins=*")
    options.add_argument("--disable-extensions")
    driver=webdriver.Chrome(options=options)
    driver.set_page_load_timeout(45)
    driver.execute_cdp_cmd("Performance.enable",{})
    return driver

OBSERVE_SCRIPT="""
if(!window.__dashgitObservations) {
 window.__dashgitObservations={mutations:0,start:Date.now()};
 var target=document.querySelector('main')||document.body;
 if(target) {
  window.__dashgitObserver=new MutationObserver(function(items){
   window.__dashgitObservations.mutations+=items.length;
  });
  window.__dashgitObserver.observe(target,{subtree:true,childList:true,characterData:true});
 }
}
return window.__dashgitObservations.mutations;
"""

READ_SCRIPT="""
var root=window.__dashgitObservations||{mutations:-1};
var nav=performance.getEntriesByType('resource');
return {
  title:document.title,
  ready:document.readyState,
  url:location.href,
  bodyLength:(document.body&&document.body.innerText||'').length,
  visibleText:(document.body&&document.body.innerText||'').slice(0,550),
  runLinks:[...new Set(Array.from(document.querySelectorAll('a[href*="/actions/runs/"]'))
      .map(a=>a.getAttribute('href')))].slice(0,12),
  domMutations:root.mutations,
  browserResources:nav.length,
  browserApiResources:nav.filter(x=>x.name.startsWith('https://api.github.com/')).length
};
"""

def main():
    driver=chromium_driver()
    report=[]
    try:
        for label,repo,run_id in CASES:
            url=f"https://github.com/greenpower2669/{repo}/actions/runs/{run_id}"
            expected=api_truth(repo,run_id)
            driver.get(url)
            time.sleep(3)
            before=driver.execute_script(OBSERVE_SCRIPT)
            first=driver.execute_script(READ_SCRIPT)
            # Same tab, SAME document, NO refresh or polling from DashbordGit.
            time.sleep(8)
            later=driver.execute_script(READ_SCRIPT)
            screenshot=OUT/(label+".png")
            driver.save_screenshot(str(screenshot))
            metrics=driver.execute_cdp_cmd("Performance.getMetrics",{})
            values={x["name"]:x["value"] for x in metrics.get("metrics",[])}
            data={
                "label":label,"runId":run_id,"url":url,
                "independentApiTruth":expected,
                "browserTitle":later["title"],
                "browserUrl":later["url"],
                "pageReady":later["ready"],
                "initialDomMutations":before,
                "endDomMutations":later["domMutations"],
                "inPageUpdatesObserved":later["domMutations"]>before,
                "resourceEntries":later["browserResources"],
                "apiGithubResources":later["browserApiResources"],
                "jsHeapUsedBytes":values.get("JSHeapUsedSize"),
                "domNodes":values.get("Nodes"),
                "bodyLength":later["bodyLength"],
                "evidence":"DOM mutations do not prove run-status change; snapshots same Chrome session.",
                "png":screenshot.name
            }
            report.append(data)
            print(json.dumps(data,ensure_ascii=False,sort_keys=True))
            if label!="current-ci-run":
                assert later["bodyLength"]>500, "Real GitHub page did not render"
                assert url.split("/actions/runs/")[0] in later["browserUrl"], "Navigation left GitHub repository"
        assert len(report)>=2,"Must test real success and real failure pages"
    finally:
        driver.quit()
    (OUT/"report.json").write_text(
        json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8"
    )
    print("Done. Same-tab DOM observations captured; spontaneous status transition NOT automatically established.")

if __name__=="__main__":
    main()
