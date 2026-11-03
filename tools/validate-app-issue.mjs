// Issue content is data, never shell code. Only GitHub's API is contacted.
import fs from 'node:fs';
const event=JSON.parse(fs.readFileSync(process.env.GITHUB_EVENT_PATH,'utf8'));
const issue=event.issue;
const repository=process.env.GITHUB_REPOSITORY;
const headers={Authorization:`Bearer ${process.env.GITHUB_TOKEN}`,Accept:'application/vnd.github+json','Content-Type':'application/json'};
async function api(path,method='GET',body){const r=await fetch(`https://api.github.com/${path}`,{method,headers,...(body?{body:JSON.stringify(body)}:{})});if(!r.ok)throw Error(`GitHub request returned ${r.status}`);return r.status===204?null:r.json()}
const match=String(issue.body||'').match(/### Upstream repository\s+https:\/\/github\.com\/([A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+)\/?(?:\s|$)/);
if(!match){await api(`repos/${repository}/issues/${issue.number}/comments`,'POST',{body:'Please use the app template and provide the original public GitHub repository. No APK files or code were executed.'});process.exit(0)}
const upstream=match[1];
const [meta,release]=await Promise.all([api(`repos/${upstream}`),api(`repos/${upstream}/releases/latest`)]);
if(!release.assets.some(a=>a.name.endsWith('.apk'))){await api(`repos/${repository}/issues/${issue.number}/comments`,'POST',{body:'No APK release was found upstream. This request needs a source review before it can be listed.'});process.exit(0)}
const record={issue:issue.number,repository:meta.html_url,license:meta.license?.spdx_id||'NOASSERTION',release:release.html_url,tag:release.tag_name,mirrorApproved:false,status:'needs-maintainer-review'};
const main=await api(`repos/${repository}/git/ref/heads/main`);const commit=await api(`repos/${repository}/git/commits/${main.object.sha}`);
const branch=`app-request-${issue.number}`;try{await api(`repos/${repository}/git/refs`,'POST',{ref:`refs/heads/${branch}`,sha:main.object.sha})}catch{await api(`repos/${repository}/issues/${issue.number}/comments`,'POST',{body:'A review branch already exists for this issue. Please update that pull request rather than creating duplicate requests.'});process.exit(0)}
async function readJson(path){const file=await api(`repos/${repository}/contents/${path}?ref=main`);return JSON.parse(Buffer.from(file.content,'base64').toString('utf8'))}
const [catalog,sources]=await Promise.all([readJson('data/catalog.json'),readJson('data/app-sources.json')]);
const id=upstream.toLowerCase().replace(/[^a-z0-9]+/g,'-');
const asset=release.assets.find(a=>/stable.*\.apk$/.test(a.name))||release.assets.find(a=>a.name.endsWith('.apk'));
const existing=catalog.apps.find(a=>a.repository.toLowerCase()===meta.html_url.toLowerCase());
const app={...(existing||{}),id:existing?.id||id,name:existing?.name||meta.name,description:existing?.description||'A community-requested Android app. Review features and permissions before installing.',category:existing?.category||'Community',repository:meta.html_url,license:record.license,version:release.tag_name,downloadUrl:asset.browser_download_url,sourceArchive:`https://github.com/${upstream}/archive/refs/tags/${release.tag_name}.tar.gz`,sha256:String(asset.digest||'').replace(/^sha256:/,''),bytes:asset.size,status:'official-release',notes:'Proposed through GitHub. Inclusion is not kosher certification. No binary mirroring is authorized by this request.'};
if(existing)catalog.apps=catalog.apps.map(a=>a.id===existing.id?app:a);else catalog.apps.push(app);
if(!sources.some(a=>a.repository.toLowerCase()===upstream.toLowerCase()))sources.push({id:app.id,repository:upstream,license:record.license,assetPattern:asset.name.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'),assetName:asset.name,mirrorApproved:false,attribution:'Maintainer review required before mirroring.'});
const changes=[{path:`data/requests/issue-${issue.number}.json`,data:record},{path:'data/catalog.json',data:catalog},{path:'data/app-sources.json',data:sources}].map(x=>({path:x.path,mode:'100644',type:'blob',content:JSON.stringify(x.data,null,2)+'\n'}));
const tree=await api(`repos/${repository}/git/trees`,'POST',{base_tree:commit.tree.sha,tree:changes});
const next=await api(`repos/${repository}/git/commits`,'POST',{message:`Validate app source from issue #${issue.number}`,tree:tree.sha,parents:[main.object.sha],author:{name:'kosherhub-bot',email:'41898282+github-actions[bot]@users.noreply.github.com'}});
await api(`repos/${repository}/git/refs/heads/${branch}`,'PATCH',{sha:next.sha,force:false});
let pr;try{pr=await api(`repos/${repository}/pulls`,'POST',{title:`Review app source from issue #${issue.number}`,head:branch,base:'main',body:`Automated metadata check for #${issue.number}.\n\nUpstream: ${meta.html_url}\nLicense: ${record.license}\nRelease: ${release.html_url}\n\nNo APK has been imported or approved. A maintainer must verify suitability, redistribution rights, upstream identity, and the mirror allowlist. Merging this PR publishes the proposed catalog entry; mirroring remains disabled for new apps.`})}catch{await api(`repos/${repository}/issues/${issue.number}/comments`,'POST',{body:`Metadata validated and review branch created: https://github.com/${repository}/compare/main...${branch}. Repository policy prevented automatic pull-request creation. No binary was downloaded or approved.`});process.exit(0)}
await api(`repos/${repository}/issues/${issue.number}/comments`,'POST',{body:`Upstream metadata checked. Review prepared: ${pr.html_url}. No automatic approval or certification is implied.`});
