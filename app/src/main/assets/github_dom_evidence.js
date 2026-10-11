(function() {
  "use strict";
  // Public GitHub DOM only. No fetch, iframe, requests or CSS-color guesses.
  var repository=__REPO__;
  var prefix="/greenpower2669/"+repository+"/actions/runs/";
  if(location.protocol!=="https:" || location.hostname!=="github.com" ||
     (location.pathname!=="/greenpower2669/"+repository+"/actions" &&
      location.pathname!=="/greenpower2669/"+repository+"/actions/")) {
    return JSON.stringify([]);
  }
  var rows=[],seen=Object.create(null);
  var links=document.querySelectorAll('main a[href*="/actions/runs/"]');
  for(var i=0;i<links.length && rows.length<12;i++) {
    var a=links[i],raw=a.getAttribute("href")||"",path;
    try { path=new URL(raw,location.origin).pathname; } catch(e) { continue; }
    if(path.indexOf(prefix)!==0) continue;
    var idText=path.slice(prefix.length);
    if(!/^[0-9]{5,20}$/.test(idText) || seen[idText]) continue;
    var label=a.getAttribute("aria-label")||"";
    var svg=a.querySelector("svg[aria-label]");
    var iconLabel=svg?svg.getAttribute("aria-label")||"":"";
    if(!label && !iconLabel) continue;
    var row=a.closest(".Box-row,[role='row'],li,article")||a.parentElement;
    var branchLink=row && row.querySelector("a.branch-name[title]");
    var branch=branchLink?branchLink.getAttribute("title")||"":"";
    var heading=a.querySelector(".markdown-title");
    var message=heading?(heading.textContent||"").trim():"";
    rows.push({
      id:idText,url:location.origin+prefix+idText,
      label:label.trim().slice(0,320),
      iconLabel:iconLabel.trim().slice(0,120),
      branch:branch.slice(0,160),
      message:message.slice(0,250)
    });
    seen[idText]=true;
  }
  rows.sort(function(a,b){
    var x=BigInt(a.id),y=BigInt(b.id);
    return x===y?0:x>y?-1:1;
  });
  return JSON.stringify(rows.slice(0,5));
})()