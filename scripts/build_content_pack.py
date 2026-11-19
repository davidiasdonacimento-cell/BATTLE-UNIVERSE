#!/usr/bin/env python3
"""Builds the downloadable, integrity-checked Battle Universe Season 01 pack."""
from pathlib import Path
import hashlib, json, math, random, struct, wave, zipfile
import numpy as np
from PIL import Image

ROOT=Path("build/content-pack-src")
OUT=Path("build")
if ROOT.exists():
    import shutil
    shutil.rmtree(ROOT)
for folder in ("models","textures","audio","worlds","characters","missions","shaders"):
    (ROOT/folder).mkdir(parents=True,exist_ok=True)

def sphere(vertices,faces,center,scale,segments=20,rings=12):
    ox,oy,oz=center; sx,sy,sz=scale; base=len(vertices)+1
    for iy in range(rings+1):
        phi=math.pi*iy/rings
        for ix in range(segments+1):
            theta=2*math.pi*ix/segments
            vertices.append((ox+sx*math.sin(phi)*math.cos(theta),
                oy+sy*math.cos(phi),oz+sz*math.sin(phi)*math.sin(theta)))
    for iy in range(rings):
        for ix in range(segments):
            a=base+iy*(segments+1)+ix; b=a+segments+1; c=b+1; d=a+1
            faces.extend(((a,b,c),(a,c,d)))

def cylinder(vertices,faces,start,end,radius,segments=16):
    ax,ay,az=start; bx,by,bz=end
    dx,dy,dz=bx-ax,by-ay,bz-az
    length=math.sqrt(dx*dx+dy*dy+dz*dz)
    if length<1e-6:return
    ux,uy,uz=dx/length,dy/length,dz/length
    ref=(0,1,0) if abs(uy)<.9 else (1,0,0)
    px=uy*ref[2]-uz*ref[1]; py=uz*ref[0]-ux*ref[2]; pz=ux*ref[1]-uy*ref[0]
    plen=math.sqrt(px*px+py*py+pz*pz); px/=plen; py/=plen; pz/=plen
    qx=uy*pz-uz*py; qy=uz*px-ux*pz; qz=ux*py-uy*px
    base=len(vertices)+1
    for cx,cy,cz in (start,end):
        for i in range(segments):
            a=2*math.pi*i/segments
            vx=math.cos(a)*px+math.sin(a)*qx
            vy=math.cos(a)*py+math.sin(a)*qy
            vz=math.cos(a)*pz+math.sin(a)*qz
            vertices.append((cx+radius*vx,cy+radius*vy,cz+radius*vz))
    for i in range(segments):
        n=(i+1)%segments
        a=base+i; b=base+n; c=base+segments+n; d=base+segments+i
        faces.extend(((a,b,c),(a,c,d)))

def write_obj(name,vertices,faces):
    lines=["# Battle Universe Season 01 - authored mesh",f"o {name}"]
    lines.extend(f"v {x:.5f} {y:.5f} {z:.5f}" for x,y,z in vertices)
    lines.extend(f"f {a} {b} {c}" for a,b,c in faces)
    (ROOT/f"models/{name}.obj").write_text("\n".join(lines)+"\n")

v=[];f=[]
sphere(v,f,(0,1.24,0),(.34,.48,.22),24,16)
sphere(v,f,(0,1.50,-.015),(.31,.25,.23),24,14)
sphere(v,f,(0,1.96,0),(.18,.23,.17),24,16)
sphere(v,f,(0,2.17,0),(.19,.11,.18),20,12)
sphere(v,f,(0,1.38,.25),(.25,.32,.13),20,12)
for side in (-1,1):
    cylinder(v,f,(side*.18,.82,0),(side*.20,.46,.015),.11)
    cylinder(v,f,(side*.20,.46,.015),(side*.20,.13,-.05),.085)
    sphere(v,f,(side*.20,.11,-.13),(.12,.07,.19),16,10)
    cylinder(v,f,(side*.38,1.56,0),(side*.46,1.13,-.04),.085)
    cylinder(v,f,(side*.46,1.13,-.04),(side*.43,.84,-.08),.07)
    sphere(v,f,(side*.43,.81,-.08),(.075,.075,.075),12,8)
write_obj("operator_field",v,f)

v=[];f=[]
cylinder(v,f,(0,0,0),(0,.85,0),.105,20)
sphere(v,f,(0,.38,0),(.18,.22,.20),18,10)
sphere(v,f,(0,.92,0),(.14,.13,.14),18,10)
cylinder(v,f,(0,.82,0),(0,1.1,0),.035,16)
write_obj("energy_emitter",v,f)

def make_box(name,sx,sy,sz):
    vv=[(-sx/2,-sy/2,-sz/2),(sx/2,-sy/2,-sz/2),(sx/2,sy/2,-sz/2),(-sx/2,sy/2,-sz/2),
        (-sx/2,-sy/2,sz/2),(sx/2,-sy/2,sz/2),(sx/2,sy/2,sz/2),(-sx/2,sy/2,sz/2)]
    ff=[(1,2,3),(1,3,4),(5,8,7),(5,7,6),(1,5,6),(1,6,2),(2,6,7),(2,7,3),
        (3,7,8),(3,8,4),(4,8,5),(4,5,1)]
    write_obj(name,vv,ff)
make_box("build_wall",3.2,2.4,.22)
make_box("build_platform",3.0,.22,3.0)
make_box("build_ramp",3.0,.22,3.0)

rng=np.random.default_rng(20261119)
N=1024
xf=np.arange(N,dtype=np.float32)[None,:]/N
yf=np.arange(N,dtype=np.float32)[:,None]/N
def noise(seed):
    r=np.random.default_rng(seed)
    low=Image.fromarray(r.integers(0,256,(64,64),dtype=np.uint8)).resize((N,N),Image.Resampling.BICUBIC)
    high=Image.fromarray(r.integers(0,256,(256,256),dtype=np.uint8)).resize((N,N),Image.Resampling.BICUBIC)
    return np.asarray(low,dtype=np.float32)/255*.65+np.asarray(high,dtype=np.float32)/255*.35
def texture(name,base,seed,kind):
    n=noise(seed)
    fine=rng.normal(0,.05,(N,N)).astype(np.float32)
    wave=(np.sin(xf*70+np.sin(yf*17)*2)+np.cos(yf*64+np.sin(xf*13)))*.05
    if kind=="fabric": wave=np.sin(xf*500)*.025+np.cos(yf*420)*.025
    if kind=="concrete": wave=np.sin((xf+yf)*160)*.04
    height=n+wave
    lum=np.clip((n-.5)*.32+wave+fine,-.20,.20)
    rgb=np.stack([np.clip(base[i]+lum,0,1) for i in range(3)],axis=-1)
    Image.fromarray((rgb*255).astype(np.uint8),"RGB").save(ROOT/f"textures/{name}_albedo.png",optimize=True)
    gy,gx=np.gradient(height)
    nx=-gx*5; ny=-gy*5; nz=np.ones_like(nx); length=np.sqrt(nx*nx+ny*ny+nz*nz)
    normal=np.stack([nx/length*.5+.5,ny/length*.5+.5,nz/length*.5+.5],axis=-1)
    Image.fromarray((np.clip(normal,0,1)*255).astype(np.uint8),"RGB").save(ROOT/f"textures/{name}_normal.png",optimize=True)
texture("island_ground",(.30,.38,.22),101,"ground")
texture("tropical_rock",(.34,.32,.28),202,"rock")
texture("operator_fabric",(.12,.20,.23),303,"fabric")
texture("neon_concrete",(.18,.21,.25),404,"concrete")

worlds={
"island.json":{"id":"tropical_island","name":"Ilha Tropical","biome":"tropical","spawn":[0,0,0],
 "resource_nodes":[[8,-9],[-10,-6],[12,10],[-14,12],[0,19]],"build_limit":12,"npc_profile":"scavenger"},
"neon_city.json":{"id":"neon_city","name":"Neon City","biome":"urban_night","spawn":[0,0,0],
 "resource_nodes":[[9,-10],[-12,-8],[14,11],[-15,13],[0,20]],"build_limit":12,"npc_profile":"patrol"},
"desert_storm.json":{"id":"desert_storm","name":"Desert Storm","biome":"desert","spawn":[0,0,0],
 "resource_nodes":[[8,-9],[-10,-6],[12,10],[-14,12],[0,19]],"build_limit":12,"npc_profile":"scavenger"}}
for name,data in worlds.items():
    (ROOT/f"worlds/{name}").write_text(json.dumps(data,ensure_ascii=False,indent=2))
(ROOT/"characters/roster.json").write_text(json.dumps({"operators":[
 {"id":"nova","role":"reconhecimento"},{"id":"atlas","role":"defesa"},
 {"id":"ember","role":"exploracao"},{"id":"volt","role":"estrategia"}]},ensure_ascii=False,indent=2))
(ROOT/"missions/daily.json").write_text(json.dumps({"missions":[
 {"id":"collect_resources","target":5,"reward":75},{"id":"place_builds","target":3,"reward":90},
 {"id":"practice_pulses","target":10,"reward":60}]},indent=2))
(ROOT/"shaders/lit_es3.vert").write_text("""#version 300 es
precision highp float;
layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
uniform mat4 uMVP; uniform mat4 uModel;
out vec3 vNormal; out vec3 vWorld;
void main(){vec4 w=uModel*vec4(aPosition,1.0);vWorld=w.xyz;vNormal=mat3(uModel)*aNormal;gl_Position=uMVP*vec4(aPosition,1.0);}
""")
(ROOT/"shaders/lit_es3.frag").write_text("""#version 300 es
precision highp float;
in vec3 vNormal; in vec3 vWorld; uniform vec4 uColor; out vec4 fragColor;
void main(){vec3 n=normalize(vNormal);vec3 l=normalize(vec3(-.45,.85,.35));float d=max(dot(n,l),0.0);vec3 v=normalize(vec3(0.0,6.0,18.0)-vWorld);float s=pow(max(dot(reflect(-l,n),v),0.0),24.0);fragColor=vec4(uColor.rgb*(.42+.58*d)+vec3(.10)*s,uColor.a);}
""")

sr=22050; seconds=12; random.seed(5150)
with wave.open(str(ROOT/"audio/island_ambience.wav"),"wb") as wf:
    wf.setnchannels(1);wf.setsampwidth(2);wf.setframerate(sr)
    frames=[]
    for i in range(sr*seconds):
        t=i/sr
        wind=(math.sin(2*math.pi*.17*t)+.45*math.sin(2*math.pi*.31*t+1.2))*.10
        surf=math.sin(2*math.pi*.055*t+math.sin(2*math.pi*.11*t))*.12
        noise=(random.random()*2-1)*(.035+.02*math.sin(2*math.pi*.08*t))
        frames.append(struct.pack("<h",int(max(-.5,min(.5,wind+surf+noise))*32767)))
    wf.writeframes(b"".join(frames))

files=[]
for item in sorted(ROOT.rglob("*")):
    if item.is_file():
        data=item.read_bytes()
        files.append({"path":item.relative_to(ROOT).as_posix(),"size":len(data),
            "sha256":hashlib.sha256(data).hexdigest()})
pack_manifest={"pack_id":"battle_universe_season_01","version":1,
 "title":"Battle Universe — Dados da Temporada 01","engine":"Android OpenGL ES 3.0",
 "files":files,"required":["worlds/island.json","characters/roster.json",
 "models/operator_field.obj","models/energy_emitter.obj",
 "textures/island_ground_albedo.png","audio/island_ambience.wav"]}
(ROOT/"manifest.json").write_text(json.dumps(pack_manifest,ensure_ascii=False,indent=2))
(ROOT/"PACK_README.txt").write_text("BATTLE UNIVERSE SEASON 01 CONTENT PACK\nIncludes smooth OBJ meshes, eight 1024px PBR-style textures, three map configs, roster, daily missions, ES 3.0 shaders and a 12-second island ambience loop.\n")

OUT.mkdir(parents=True,exist_ok=True)
archive=OUT/"battle-universe-season-01-pack.zip"
with zipfile.ZipFile(archive,"w",zipfile.ZIP_DEFLATED,compresslevel=7) as z:
    for item in sorted(ROOT.rglob("*")):
        if item.is_file(): z.write(item,item.relative_to(ROOT).as_posix())
raw=archive.read_bytes()
manifest={"version":1,"title":"Battle Universe Season 01","url":
 "https://github.com/davidiasdonacimento-cell/BATTLE-UNIVERSE/releases/download/data-pack-v1/battle-universe-season-01-pack.zip",
 "size_bytes":len(raw),"sha256":hashlib.sha256(raw).hexdigest()}
(OUT/"content-pack-manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
print(f"Content pack ready: {len(raw)} bytes; SHA-256 {manifest['sha256']}; {len(files)} checked resources")
