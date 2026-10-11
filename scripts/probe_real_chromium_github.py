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
                assert url.split("/actions/runs/")[0] in later["url"], "Navigation left GitHub repository"
        # Additional real DOM check: filter public Actions by failed runs to
        # discover the exact accessible label used for a RED icon. This is not
        # inferred from its color, and remains separate from Android traffic.
        repo="DashbordGit"
        url="https://github.com/greenpower2669/DashbordGit/actions?query=conclusion%3Afailure"
        driver.get(url)
        time.sleep(3)
        js=(ROOT/"app"/"src"/"main"/"assets"/"github_dom_evidence.js").read_text(encoding="utf-8")
        rows=json.loads(driver.execute_script("return "+js.replace("__REPO__",json.dumps(repo))))
        observed=[]
        for row in rows[:4]:
            truth=api_truth(repo,int(row["id"]))
            observed.append({"id":row["id"],"label":row["label"][:140],"truth":truth})
        print("REAL_FAILED_FILTER "+json.dumps({
            "url":url,"items":observed
        },ensure_ascii=False))
        (OUT/"failed-list.png").write_bytes(driver.get_screenshot_as_png())
        assert len(report)>=2,"Must test real success and real failure pages"

        # Exercise the ACTUAL public Actions LIST pages used by the new
        # HOME mini explorer. These are different URLs from specific runs.
        # The scanner's fixed courtesy delay does not mimic or evade humans.
        for repo in ("DashbordGit","JTrex"):
            url=f"https://github.com/greenpower2669/{repo}/actions"
            driver.get(url)
            time.sleep(3)
            page=driver.execute_script(READ_SCRIPT)
            # Inspect REAL GitHub DOM semantics around each run link.
            # No fabricated fixture: the output tells Android extractor which
            # precise run-scoped accessibility metadata GitHub actually emits.
            semantic=driver.execute_script("""
              return Array.from(document.querySelectorAll('a[href*="/actions/runs/"]'))
               .slice(0,4).map(function(a){
                 var target=a.closest('li,article,[role="row"],.Box-row,.ActionList-item')||a.parentElement?.parentElement||a;
                 var icon=target.querySelector('svg');
                 return {
                   href:a.getAttribute('href'),
                   text:(target.innerText||'').slice(0,550),
                   aria:Array.from(target.querySelectorAll('[aria-label],[title]'))
                     .slice(0,20).map(x=>({
                         tag:x.tagName,aria:x.getAttribute('aria-label'),
                         title:x.getAttribute('title'),
                         className:typeof x.className==='string'?x.className:''
                       })),
                   svg:icon?{className:icon.getAttribute('class'),
                             aria:icon.getAttribute('aria-label'),
                             dataTestid:icon.getAttribute('data-testid')}:{},
                   sampleHTML:target.outerHTML.slice(0,2500)
                 };
               });
            """)
            print("REAL_ACTIONS_DOM "+repo+" "+json.dumps(semantic,ensure_ascii=False))
            js=(ROOT/"app"/"src"/"main"/"assets"/"github_dom_evidence.js").read_text(encoding="utf-8")
            typed=json.loads(driver.execute_script("return "+js.replace("__REPO__",json.dumps(repo))))
            assert typed, "Real Actions page has no accessible run labels"
            for item in typed[:3]:
                observed_id=int(item["id"])
                expected_url=f"https://github.com/greenpower2669/{repo}/actions/runs/{observed_id}"
                assert item["url"]==expected_url
                truth=api_truth(repo,observed_id)
                text=item.get("label","").lower()
                if text.startswith("completed successfully:"):
                    assert truth.get("conclusion")=="success", "Status label does not match official run"
                print("DOM_CHECK "+json.dumps({
                    "repo":repo,"runId":observed_id,"label":text[:90],"truth":truth
                },ensure_ascii=False))
            screenshot=OUT/(f"explore-{repo}.png")
            driver.save_screenshot(str(screenshot))
            item={
                "label":"home-explorer",
                "repo":repo,
                "url":url,
                "browserUrl":page["url"],
                "title":page["title"],
                "bodyLength":page["bodyLength"],
                "linksVisible":len(page["runLinks"]),
                "resourceEntries":page["browserResources"],
                "apiGithubResources":page["browserApiResources"],
                "png":screenshot.name,
                "evidence":"Actual public Actions listing, not an iframe or fabricated fixture."
            }
            print(json.dumps(item,ensure_ascii=False,sort_keys=True))
            report.append(item)
            assert page["url"].startswith(url), "Chromium redirected from real public Actions list"
            assert page["bodyLength"]>350, "Public Actions page failed to render in real Chrome"
            time.sleep(3) # courtesy gap; never stealth traffic shaping
    finally:
        driver.quit()
    (OUT/"report.json").write_text(
        json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8"
    )
    print("Done. Same-tab DOM observations captured; spontaneous status transition NOT automatically established.")

if __name__=="__main__":
    main()
