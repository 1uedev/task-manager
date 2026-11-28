"""Synthetic real-HTTP probes. Run only against a disposable review database.

Usage: python http_probes.py PRIVATE_BOOTSTRAP_LOG PRIVATE_FIXTURE_JSON
Needs Pillow for independently generated/decoded image fixtures and PSQL/PG* for this database.
REVIEW_RESUME=1 reuses the synthetic bootstrap account after its first password change.
"""
import http.client
import http.cookies
import io
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import time
import uuid
from PIL import Image

PORT = int(os.environ.get("REVIEW_HTTP_PORT", "61556"))
PASSWORD = "Synthetic-Review-Password-2026!"
HERE = Path(__file__).resolve().parent
RESULTS = []


class Client:
    def __init__(self): self.cookies = {}
    def call(self, method, path, body=None, etag=None, content_type=None):
        data = body if isinstance(body, bytes) else json.dumps(body).encode() if body is not None else None
        headers = {"Cookie": "; ".join(f"{k}={v}" for k,v in self.cookies.items())}
        if data is not None: headers["Content-Type"] = content_type or "application/json"
        if etag: headers["If-Match"] = etag
        if "__Host-tm-csrf" in self.cookies: headers["X-CSRF-Token"] = self.cookies["__Host-tm-csrf"]
        c = http.client.HTTPConnection("127.0.0.1", PORT, timeout=30)
        c.request(method, "/api/v1" + path, body=data, headers=headers)
        r = c.getresponse(); raw = r.read(); h = {k.lower():v for k,v in r.getheaders()}
        for k,v in r.getheaders():
            if k.lower() == "set-cookie":
                for key,cookie in http.cookies.SimpleCookie(v).items():
                    if cookie["max-age"] == "0": self.cookies.pop(key, None)
                    else: self.cookies[key] = cookie.value
        result = r.status, json.loads(raw) if raw and "json" in h.get("content-type", "") else raw if raw else None, h
        c.close(); return result
    def ok(self, method, path, body=None, etag=None, status=(200,201,204), content_type=None):
        r = self.call(method,path,body,etag,content_type)
        assert r[0] in status, (method,path,r[0],r[1].get("code") if isinstance(r[1],dict) else "")
        return r
    def login(self, name, password=PASSWORD):
        self.ok("GET","/session"); self.ok("POST","/session",{"username":name,"password":password})


def sql(statement):
    return subprocess.check_output([os.environ["PSQL"],"-X","-At","-v","ON_ERROR_STOP=1","-c",statement],text=True).strip()
def record(case, **facts):
    entry = {"case":case, **facts}; RESULTS.append(entry); print(json.dumps(entry,ensure_ascii=False),flush=True)
def revision(): return int(sql("select revision from permissions_revision"))
def field(who, group, project, name, effect, operation="read"):
    return who.ok("PUT",f"/permissions/groups/{group}/fields",{"projectId":project,"resourceType":"ticket","field":name,"operation":operation,"effect":effect})
def action(who, group, project, name, enabled):
    return who.ok("PUT",f"/permissions/groups/{group}/actions",{"projectId":project,"action":name,"enabled":enabled})
def group(name):
    exists = next((g for g in admin.ok("GET","/permissions/groups")[1] if g["name"] == name),None)
    return exists["id"] if exists else admin.ok("POST","/permissions/groups",{"name":name})[1]["id"]
def account(name):
    exists = next((a for a in admin.ok("GET","/directory/accounts")[1] if a["username"] == name),None)
    c = Client()
    if exists: c.login(name); return exists["id"],c
    issued = admin.ok("POST","/accounts",{"username":name,"displayName":name})[1]
    c.login(name,issued["oneTimePassword"])
    c.ok("POST","/session/password",{"currentPassword":issued["oneTimePassword"],"newPassword":PASSWORD})
    return issued["account"]["id"],c
def project(key):
    return next((p for p in admin.ok("GET","/projects")[1] if p["key"] == key),None) or admin.ok("POST","/projects",{"key":key,"name":"Review "+key})[1]
def member(project, user):
    p = admin.ok("GET",f"/projects/{project}")
    admin.ok("PUT",f"/projects/{project}/members/{user}",etag=p[2]["etag"])


admin = Client()
if os.environ.get("REVIEW_RESUME") == "1": admin.login("reviewadmin")
else:
    otp = re.search(r"One time password \(shown only now\): (.+)",Path(sys.argv[1]).read_text(encoding="utf-8-sig")).group(1).strip()
    admin.login("reviewadmin",otp)
    admin.ok("POST","/session/password",{"currentPassword":otp,"newPassword":PASSWORD})
admin_group = next(g["id"] for g in admin.ok("GET","/permissions/groups")[1] if g["name"] == "Unternehmensverwaltung")
admin_id = next(a["id"] for a in admin.ok("GET","/directory/accounts")[1] if a["username"] == "reviewadmin")
peer_id,peer = account("reviewpeer")
admin.ok("PUT",f"/permissions/groups/{admin_group}/members/{peer_id}")
delegate_id,delegate = account("reviewdelegate")
ordinary_id,ordinary = account("reviewordinary")
editor,delegates,target = group("Review editor 43"),group("Review delegate 43"),group("Review target 43")
for name in ["project.read","project.manage","ticket.read","ticket.create","ticket.edit","tag.create"]: action(admin,editor,None,name,True)
for name in ["title","description","type","status","assignee","tags"]:
    for operation in ["read","write"]: field(admin,editor,None,name,"allow",operation)
admin.ok("PUT",f"/permissions/groups/{editor}/members/{admin_id}")
action(admin,admin_group,None,"permissions.audit.read",True)
x,y = project("R43"),project("X43")
for who in [admin_id,peer_id,delegate_id,ordinary_id]: member(x["id"],who)
member(y["id"],admin_id)
for who in [delegate_id,admin_id]: admin.ok("PUT",f"/permissions/groups/{delegates}/members/{who}")
for name in ["project.read","ticket.read"]: action(admin,delegates,x["id"],name,True)
for operation in ["read","write"]: field(admin,delegates,x["id"],"title","allow",operation)
# The HTTP catalog currently offers permissions.manage on company scope only. Seed the intended project delegation as
# a synthetic fixture; this is not a claimed API grant or a way available to an unprivileged user.
sql(f"insert into permissions_action(company_id,group_id,project_id,action_key) select company_id,id,'{x['id']}'::uuid,'permissions.manage' from permissions_group where id='{delegates}'::uuid and not exists (select 1 from permissions_action where group_id='{delegates}'::uuid and project_id='{x['id']}'::uuid and action_key='permissions.manage'); update permissions_revision set revision=revision+1;")
admin.ok("PUT",f"/permissions/groups/{target}/members/{ordinary_id}")
action(admin,target,x["id"],"ticket.read",True)
action(admin,target,y["id"],"ticket.edit",True)
field(admin,target,None,"description","allow")
fixture = {"project":x["id"],"foreignProject":y["id"],"targetGroup":target,"delegateGroup":delegates,"targetName":"Review target 43"}
Path(sys.argv[2]).write_text(json.dumps(fixture),encoding="utf-8")

try:
    rights = delegate.ok("GET",f"/permissions/projects/{x['id']}/groups")[1]
    assert "ticket.edit" not in rights["actions"] and "permissions.manage" not in rights["actions"]
    assert rights["fields"] == [{"resourceType":"ticket","fields":["title"]}]
    assert all(set(g)=={"id","name","actions","fields"} for g in rights["groups"])
    assert all(r["projectId"]==x["id"] for g in rights["groups"] for r in g["actions"]+g["fields"])
    refused = []
    for method,path,body in [
        ("GET","/permissions/groups",None),("GET",f"/permissions/groups/{target}",None),
        ("GET","/permissions/audit",None),("PUT",f"/permissions/groups/{target}/members/{delegate_id}",None),
        ("PUT",f"/permissions/groups/{target}/actions",{"projectId":x["id"],"action":"ticket.edit","enabled":True}),
        ("PUT",f"/permissions/groups/{target}/actions",{"projectId":y["id"],"action":"ticket.read","enabled":True}),
        ("PUT",f"/permissions/groups/{target}/actions",{"projectId":None,"action":"accounts.manage","enabled":True}),
        ("PUT",f"/permissions/groups/{target}/fields",{"projectId":x["id"],"resourceType":"ticket","field":"description","operation":"read","effect":"allow"})]:
        response=delegate.call(method,path,body); assert response[0]==403,(method,path,response[0]); refused.append(response[0])
    assert action(delegate,target,x["id"],"ticket.read",True)[0]==204
    field(admin,target,x["id"],"title","deny")
    denied=delegate.call("PUT",f"/permissions/groups/{target}/fields",{"projectId":x["id"],"resourceType":"ticket","field":"title","operation":"read","effect":"remove"})
    assert denied[0]==403
    field(admin,target,x["id"],"title","allow")
    record("delegation",onlyOwnProject=True,noMembers=True,onlyHeldActions=True,onlyHeldFields=True,negativeStatuses=refused,allowedChange=204,companyDenyRemoval=denied[0])

    known=ordinary.call("GET",f"/permissions/projects/{y['id']}/groups")
    unknown=ordinary.call("GET",f"/permissions/projects/{uuid.uuid4()}/groups")
    normal=ordinary.call("GET",f"/projects/{y['id']}")
    record("project_existence",knownStatus=known[0],knownCode=known[1].get("code"),unknownStatus=unknown[0],unknownCode=unknown[1].get("code"),normalProjectStatus=normal[0])

    duplicate=admin.call("POST","/permissions/groups",{"name":"rEvIeW TaRgEt 43"})
    assert duplicate[0]==409 and duplicate[1]["code"]=="permissions.group_name_taken"
    record("duplicate_group_name",status=duplicate[0],code=duplicate[1]["code"])

    before=revision()
    admin.ok("PUT",f"/permissions/groups/{target}/members/{ordinary_id}")
    admin.ok("DELETE",f"/permissions/groups/{target}/members/{delegate_id}")
    action(admin,target,x["id"],"ticket.read",True)
    field(admin,target,x["id"],"status","remove")
    assert revision()==before
    field(admin,target,x["id"],"title","allow")
    v=revision(); field(admin,target,x["id"],"title","allow"); assert revision()==v
    field(peer,target,x["id"],"title","allow"); assert revision()==v+1
    assert admin.ok("GET","/permissions/audit?limit=1")[1][0]["previousEffect"]=="allow"
    v=revision(); field(peer,target,x["id"],"title","allow"); assert revision()==v
    field(admin,target,x["id"],"title","allow")
    peer.ok("DELETE",f"/permissions/groups/{admin_group}/members/{admin_id}")
    try:
        v=revision(); assert field(admin,target,x["id"],"title","allow")[0]==204; assert revision()==v+1
        v=revision(); field(admin,target,x["id"],"title","allow"); assert revision()==v
    finally: peer.ok("PUT",f"/permissions/groups/{admin_group}/members/{admin_id}")
    record("noops_and_provenance",noOpsUnchanged=True,otherAuthorCounted=True,roleChangeCounted=True,previousEffect="allow")

    for i in range(125): action(admin,target,x["id"],"ticket.create",i%2==0)
    entries=[]; cursor=None
    while True:
        page=admin.ok("GET","/permissions/audit?limit=17"+("&before="+str(cursor) if cursor else ""))[1]
        if not page: break
        entries.extend(page); cursor=min(e["rightsVersion"] for e in page)
    versions=[e["rightsVersion"] for e in entries]
    assert len(entries)>100 and len(versions)==len(set(versions)) and versions==sorted(versions,reverse=True)
    assert len(entries)==int(sql("select count(*) from permissions_audit"))
    record("audit_paging",entries=len(entries),pageLimit=17,duplicates=0,complete=True)

    expiry_id,expiry = account("reviewexpiry")
    admin.ok("PUT",f"/permissions/groups/{editor}/members/{expiry_id}")
    member(x["id"],expiry_id)
    exp_ticket=admin.ok("POST",f"/projects/{x['id']}/tickets",{"title":"Expiry remains unchanged","type":"task"})[1]
    exp_path=f"/projects/{x['id']}/tickets/{exp_ticket['number']}"
    exp_before=expiry.ok("GET",exp_path)
    ticket_id=exp_ticket["id"]
    holder=subprocess.Popen([os.environ["PSQL"],"-X","-At","-v","ON_ERROR_STOP=1","-c",
        f"begin; select id from ticket where id='{ticket_id}'::uuid for update; select pg_sleep(3); commit;"],
        env={**os.environ,"PGAPPNAME":"task043-expiry-ticket-holder"},stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    for attempt in range(100):
        if sql("select count(*) from pg_stat_activity where application_name='task043-expiry-ticket-holder' and wait_event='PgSleep'")=="1": break
        time.sleep(.02)
    else: raise AssertionError("actual ticket lock holder not observed")
    sql(f"update identity_session set expires_at=clock_timestamp()+interval '2 seconds' where account_id='{expiry_id}'::uuid and revoked_at is null;")
    started=time.monotonic()
    exp_result=expiry.call("PATCH",exp_path,{"title":"Must roll back after expiry"},exp_before[2]["etag"])
    elapsed=time.monotonic()-started
    holder.communicate(timeout=10)
    assert exp_result[0]==403 and exp_result[1]["code"]=="auth.forbidden",(exp_result[0],exp_result[1])
    exp_after=admin.ok("GET",exp_path)[1]
    assert exp_after["fields"]["title"]=="Expiry remains unchanged" and exp_after["version"]==exp_before[1]["version"]
    record("commit_http",status=exp_result[0],code=exp_result[1]["code"],elapsedSeconds=round(elapsed,2),unchangedTitle=True,unchangedVersion=True,correlation=bool(exp_result[2].get("x-correlation-id")))

    for label,image,options in [
        ("png-rgb",Image.new("RGB",(8,5),"orange"),{}),("png-alpha",Image.new("RGBA",(8,5),(255,0,0,100)),{}),
        ("jpeg-baseline",Image.new("RGB",(8,5),"orange"),{}),("jpeg-progressive",Image.new("RGB",(8,5),"orange"),{"progressive":True}),
        ("jpeg-cmyk",Image.new("CMYK",(8,5),(10,20,30,0)),{}),("webp-lossy",Image.new("RGB",(8,5),"orange"),{}),
        ("webp-lossless",Image.new("RGBA",(8,5),(255,0,0,100)),{"lossless":True}),
        ("webp-animated",Image.new("RGB",(8,5),"orange"),{"save_all":True,"append_images":[Image.new("RGB",(8,5),"blue")],"duration":80,"loop":0})]:
        fmt=label.split("-")[0]; b=io.BytesIO(); image.save(b,format=fmt,**options); content=b.getvalue()
        decoded=Image.open(io.BytesIO(content)); decoded.load()
        tag=admin.ok("GET","/appearance")[2]["etag"]
        response=admin.call("PUT","/appearance/logo",content,tag,"image/"+fmt)
        record("logo",fixture=label,independentDecode=True,bytes=len(content),status=response[0]); assert response[0]==200
    def chunk(kind,payload): return kind+struct.pack("<I",len(payload))+payload+(b"\0" if len(payload)%2 else b"")
    def riff(*chunks):
        data=b"WEBP"+b"".join(chunks); return b"RIFF"+struct.pack("<I",len(data))+data
    vp8x=b"\x02\0\0\0"+b"\x01\0\0"*2
    bad_cases={"anmf-only-no-image":riff(chunk(b"ANMF",bytes(16))),
        "animated-no-frame-image":riff(chunk(b"VP8X",vp8x),chunk(b"ANIM",bytes(6)),chunk(b"ANMF",bytes(16)))}
    for label,content in bad_cases.items():
        try: Image.open(io.BytesIO(content)).load(); decoded=True
        except (OSError,ValueError): decoded=False
        assert not decoded
        before=admin.ok("GET","/appearance")
        response=admin.call("PUT","/appearance/logo",content,before[2]["etag"],"image/webp")
        after=admin.ok("GET","/appearance")
        record("malformed_webp",fixture=label,hex=content.hex(),independentDecode=False,status=response[0],versionBefore=before[1]["version"],versionAfter=after[1]["version"])
    # Leave a valid small logo for the independent UI pass.
    image=Image.new("RGB",(3,2),"orange"); b=io.BytesIO(); image.save(b,format="PNG")
    admin.ok("PUT","/appearance/logo",b.getvalue(),admin.ok("GET","/appearance")[2]["etag"],content_type="image/png")
    fixture["pngHex"]=b.getvalue().hex()
    Path(sys.argv[2]).write_text(json.dumps(fixture),encoding="utf-8")

    self_id,self_client=account("reviewselfreset"+uuid.uuid4().hex[:8])
    admin.ok("PUT",f"/permissions/groups/{admin_group}/members/{self_id}")
    response=self_client.ok("POST",f"/accounts/{self_id}/password-reset")
    record("self_reset",status=response[0],selfRevocationDidNotRollback=True)
finally:
    (HERE/"http-results.json").write_text(json.dumps(RESULTS,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
