// Real production build + real backend. Run after http_probes.py: node ui_probes.mjs PRIVATE_FIXTURE_JSON
import assert from 'node:assert/strict';
import http from 'node:http';
import { readFile, writeFile } from 'node:fs/promises';
import { resolve, dirname, extname, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from '../../../frontend/node_modules/playwright-core/index.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const dist=resolve(here,'../../../frontend/dist');
const fixture=JSON.parse(await readFile(process.argv[2],'utf8'));
const backend=Number(process.env.REVIEW_HTTP_PORT??61556), port=61557, base=`http://localhost:${port}`;
const results=[], traffic=[];
const server=http.createServer(async(req,res)=>{
  if(req.url.startsWith('/api/')){
    const upstream=http.request({hostname:'127.0.0.1',port:backend,path:req.url,method:req.method,headers:req.headers},r=>{
      traffic.push({method:req.method,path:req.url,status:r.statusCode});res.writeHead(r.statusCode,r.headers);r.pipe(res);
    }); upstream.on('error',()=>res.writeHead(502).end());req.pipe(upstream);return;
  }
  const path=new URL(req.url,base).pathname;
  const target=resolve(dist,path==='/'?'index.html':path.slice(1));
  if(!target.startsWith(dist+sep)){res.writeHead(404).end();return;}
  try{const body=await readFile(target);res.writeHead(200,{'Content-Type':({'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml'})[extname(target)]??'application/octet-stream'});res.end(body);}
  catch{res.writeHead(404).end();}
});
await new Promise(r=>server.listen(port,'127.0.0.1',r));
const browser=await chromium.launch(process.env.TASKMANAGER_BROWSER?{executablePath:process.env.TASKMANAGER_BROWSER}:{channel:'chrome'});
async function open(user){
  const context=await browser.newContext({locale:'de-DE',viewport:{width:1100,height:850}});
  const page=await context.newPage();page.setDefaultTimeout(12000);
  await page.goto(base);await page.getByLabel(/^Anmeldename/).fill(user);await page.getByLabel(/^Passwort/).fill('Synthetic-Review-Password-2026!');
  await page.getByRole('button',{name:'Anmelden',exact:true}).click();await page.waitForSelector('.shell main h1');return{context,page};
}
async function check(name,run){try{const facts=await run();results.push({case:name,...facts});}catch(e){results.push({case:name,error:e.message});}console.log(JSON.stringify(results.at(-1)));}
try{
  if(!process.env.REVIEW_LEGACY_ONLY) await check('project-rights-real',async()=>{
    const {context,page}=await open('reviewdelegate');
    try{
      await page.goto(`${base}/#/projects/${fixture.project}/rights`);
      await page.getByRole('link',{name:'Rechte',exact:true}).waitFor();
      await page.getByLabel('Gruppe',{exact:true}).selectOption(fixture.targetGroup);
      const actions=await page.getByTestId('project-rights-actions').innerText();
      assert.ok(actions.includes('ticket.read')&&!actions.includes('ticket.edit')&&!actions.includes('permissions.manage'));
      assert.equal(await page.getByLabel('Lesen von Beschreibung',{exact:true}).count(),0);
      const box=page.getByRole('checkbox',{name:/Tickets lesen/});
      const initial=await box.isChecked();
      const [first]=await Promise.all([page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/actions')),box.click()]);
      assert.equal(first.status(),204);
      await page.getByText('Gespeichert. Die Änderung gilt sofort.',{exact:true}).waitFor();
      await page.waitForFunction(initial=>[...document.querySelectorAll('input[type=checkbox]')].some(e=>e.parentElement.textContent.includes('ticket.read')&&e.checked!==initial),initial);
      const [second]=await Promise.all([page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/actions')),box.click()]);
      assert.equal(second.status(),204);
      return{result:'PASS',onlyHeldActions:true,descriptionOffered:false,actualWriteStatuses:[204,204]};
    }finally{await context.close();}
  });
  if(!process.env.REVIEW_LEGACY_ONLY) await check('audit-real',async()=>{
    const {context,page}=await open('reviewadmin');
    try{
      await page.goto(`${base}/#/groups`);await page.getByTestId('audit-log').waitFor();
      const rows=page.getByTestId('audit-log').locator('tbody tr');
      await page.waitForFunction(()=>document.querySelectorAll('[data-testid=audit-log] tbody tr').length===50);
      const counts=[await rows.count()];
      for(let i=0;i<2;i++){const before=await rows.count();await page.getByRole('button',{name:'Ältere Einträge laden',exact:true}).click();await page.waitForFunction(n=>document.querySelectorAll('[data-testid=audit-log] tbody tr').length>n,before);counts.push(await rows.count());}
      assert.ok(counts.at(-1)>100);await page.getByRole('columnheader',{name:'Vorher',exact:true}).waitFor();
      return{result:'PASS',rowCounts:counts,previousValueColumn:true};
    }finally{await context.close();}
  });
  if(!process.env.REVIEW_LEGACY_ONLY) await check('appearance-real',async()=>{
    const {context,page}=await open('reviewadmin');
    try{
      await page.goto(`${base}/#/appearance`);
      const saved=await page.evaluate(()=>fetch('/api/v1/appearance').then(r=>r.json()));
      const nextColor=saved.accentColor.toUpperCase()==='#13A6B2'?'#2563EB':'#13A6B2';
      await page.getByLabel('Farbwert',{exact:true}).fill(nextColor);
      await page.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent.trim()==='Für alle speichern'&&!b.disabled));
      await page.getByTestId('logo-file').setInputFiles({name:'review.png',mimeType:'image/png',buffer:Buffer.from(fixture.pngHex,'hex')});
      await page.getByText('Das Logo ist für alle gespeichert.',{exact:true}).waitFor();
      const save=page.getByRole('button',{name:'Für alle speichern',exact:true});
      await page.getByLabel('Farbwert',{exact:true}).fill('#12345Z');
      assert.equal(await save.isDisabled(),true);
      const first=traffic.length;await page.getByLabel('Farbwert',{exact:true}).press('Enter');
      await page.waitForTimeout(100);assert.equal(traffic.slice(first).filter(r=>r.method==='PUT'&&r.path==='/api/v1/appearance').length,0);
      await page.getByLabel('Farbwert',{exact:true}).fill(nextColor);assert.equal(await save.isEnabled(),true);
      await save.click();await page.getByText('Die Akzentfarbe ist für alle gespeichert.',{exact:true}).waitFor();
      await page.setViewportSize({width:320,height:700});await page.waitForTimeout(100);
      const withLogo=await page.locator('.brand').ariaSnapshot();
      const display=await page.getByTestId('brand-name').evaluate(e=>getComputedStyle(e).display);
      const logoExists=await page.locator('.brand-logo').count();
      assert.equal(logoExists,1);
      const withLogoOverflow=await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth);
      await page.getByRole('button',{name:'Logo entfernen',exact:true}).click();await page.getByText('Das Logo wurde entfernt.',{exact:true}).waitFor();
      const withoutLogo=await page.locator('.brand').ariaSnapshot();
      return{invalidColorDisabled:true,invalidEnterWrites:0,validColorSaved:true,width:320,withLogoBrandSnapshot:withLogo,withLogoNameDisplay:display,withLogoNameAccessible:withLogo.includes('Task Manager'),withoutLogoBrandSnapshot:withoutLogo,withoutLogoNameAccessible:withoutLogo.includes('Task Manager'),horizontalOverflow:withLogoOverflow};
    }finally{await context.close();}
  });
  if(!process.env.REVIEW_LEGACY_ONLY) await check('webp-browser-decode',async()=>{
    const {context,page}=await open('reviewadmin');
    try{
      const cases=JSON.parse(await readFile(resolve(here,'http-results.json'),'utf8')).filter(r=>r.case==='malformed_webp');
      const decodes=[];
      for(const c of cases){
        const data='data:image/webp;base64,'+Buffer.from(c.hex,'hex').toString('base64');
        const decoded=await page.evaluate(async src=>{const img=new Image();img.src=src;try{await img.decode();return{decoded:true,width:img.naturalWidth};}catch{return{decoded:false,width:img.naturalWidth};}},data);
        decodes.push({fixture:c.fixture,serverAccepted:c.status===200,...decoded});
      }
      assert.ok(decodes.every(d=>d.serverAccepted&&!d.decoded&&d.width===0));return{cases:decodes};
    }finally{await context.close();}
  });
  if(process.env.REVIEW_LEGACY_ONLY) await check('legacy-audit-real',async()=>{
    const {context,page}=await open('reviewadmin');
    try{
      await page.goto(`${base}/#/groups`);await page.getByTestId('audit-log').waitFor();
      const more=page.getByRole('button',{name:'Ältere Einträge laden',exact:true});
      for(let i=0;i<10&&await more.count();i++){
        const before=await page.getByTestId('audit-log').locator('tbody tr').count();await more.click();
        await page.waitForFunction(n=>document.querySelectorAll('[data-testid=audit-log] tbody tr').length>n,before);
      }
      const row=page.getByTestId('audit-log').locator('tbody tr').filter({hasText:'01.01.2020'});
      assert.equal(await row.count(),1);
      const text=await row.innerText();const previous=await row.locator('td').last().innerText();
      return{previousShown:previous,rowText:text};
    }finally{await context.close();}
  });
}finally{
  await browser.close();server.closeAllConnections();await new Promise(r=>server.close(r));
  await writeFile(resolve(here,process.env.REVIEW_LEGACY_ONLY?'legacy-ui-results.json':'ui-results.json'),JSON.stringify(results,null,2)+'\n');
}
if(results.some(r=>r.error))process.exitCode=1;
