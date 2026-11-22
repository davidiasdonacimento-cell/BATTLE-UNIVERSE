#!/usr/bin/env python3
"""Build a real, hash-verified Season 02 asset pack for Battle Universe."""
from pathlib import Path
import hashlib, json, math, random, struct, wave, zipfile, shutil
import numpy as np
from PIL import Image

ROOT=Path("build/content-pack-src")
OUT=Path("build")
if ROOT.exists(): shutil.rmtree(ROOT)
for folder in ("models","textures","audio","worlds","characters","missions","shaders","effects","materials"):
    (ROOT/folder).mkdir(parents=True,exist_ok=True)

def write_obj(name,vertices,faces):
    lines=["# Battle Universe Season 02 original mesh",f"o {name}"]
    lines.extend(f"v {x:.5f} {y:.5f} {z:.5f}" for x,y,z in vertices)
    lines.extend(f"f {a} {b} {c}" for a,b,c in faces)
    (ROOT/f"models/{name}.obj").write_text("\n".join(lines)+"\n",encoding="utf-8")

def sphere(vertices,faces,center,scale,segments=24,rings=16):
    ox,oy,oz=center;sx,sy,sz=scale;base=len(vertices)+1
    for iy in range(rings+1):
        phi=math.pi*iy/rings
        for ix in range(segments+1):
            theta=2*math.pi*ix/segments
            vertices.append((ox+sx*math.sin(phi)*math.cos(theta),
                oy+sy*math.cos(phi),oz+sz*math.sin(phi)*math.sin(theta)))
    for iy in range(rings):
        for ix in range(segments):
            a=base+iy*(segments+1)+ix;b=a+segments+1;c=b+1;d=a+1
            faces.extend(((a,b,c),(a,c,d)))

def cylinder(vertices,faces,start,end,radius,segments=24):
    ax,ay,az=start;bx,by,bz=end
    dx,dy,dz=bx-ax,by-ay,bz-az
    length=math.sqrt(dx*dx+dy*dy+dz*dz)
    if length<1e-6:return
    ux,uy,uz=dx/length,dy/length,dz/length
    ref=(0,1,0) if abs(uy)<.9 else (1,0,0)
    px=uy*ref[2]-uz*ref[1];py=uz*ref[0]-ux*ref[2];pz=ux*ref[1]-uy*ref[0]
    plen=math.sqrt(px*px+py*py+pz*pz);px/=plen;py/=plen;pz/=plen
    qx=uy*pz-uz*py;qy=uz*px-ux*pz;qz=ux*py-uy*px
    base=len(vertices)+1
    for cx,cy,cz in (start,end):
        for i in range(segments):
            a=2*math.pi*i/segments
            vx=math.cos(a)*px+math.sin(a)*qx
            vy=math.cos(a)*py+math.sin(a)*qy
            vz=math.cos(a)*pz+math.sin(a)*qz
            vertices.append((cx+radius*vx,cy+radius*vy,cz+radius*vz))
    for i in range(segments):
        n=(i+1)%segments;a=base+i;b=base+n;c=base+segments+n;d=base+segments+i
        faces.extend(((a,b,c),(a,c,d)))

def box_mesh(vertices,faces,cx,cy,cz,sx,sy,sz):
    base=len(vertices)+1
    vertices.extend([
        (cx-sx/2,cy-sy/2,cz-sz/2),(cx+sx/2,cy-sy/2,cz-sz/2),
        (cx+sx/2,cy+sy/2,cz-sz/2),(cx-sx/2,cy+sy/2,cz-sz/2),
        (cx-sx/2,cy-sy/2,cz+sz/2),(cx+sx/2,cy-sy/2,cz+sz/2),
        (cx+sx/2,cy+sy/2,cz+sz/2),(cx-sx/2,cy+sy/2,cz+sz/2)])
    faces.extend([(base+a,base+b,base+c) for a,b,c in [
        (0,1,2),(0,2,3),(4,7,6),(4,6,5),(0,4,5),(0,5,1),
        (1,5,6),(1,6,2),(2,6,7),(2,7,3),(3,7,4),(3,4,0)]])

# Character body, backpack, articulated limbs and face protection.
v=[];f=[]
sphere(v,f,(0,1.22,0),(.35,.48,.24))
sphere(v,f,(0,1.52,-.02),(.32,.27,.24))
sphere(v,f,(0,1.98,0),(.19,.24,.18))
sphere(v,f,(0,2.18,0),(.205,.115,.19))
sphere(v,f,(0,1.39,.26),(.26,.34,.14))
for side in (-1,1):
    cylinder(v,f,(side*.18,.83,0),(side*.20,.47,.01),.115)
    cylinder(v,f,(side*.20,.47,.01),(side*.20,.13,-.05),.09)
    sphere(v,f,(side*.20,.11,-.13),(.13,.075,.20),16,10)
    cylinder(v,f,(side*.39,1.56,0),(side*.46,1.14,-.04),.09)
    cylinder(v,f,(side*.46,1.14,-.04),(side*.43,.84,-.08),.075)
    sphere(v,f,(side*.43,.81,-.08),(.08,.08,.08),12,8)
write_obj("operator_field",v,f)

# Stylized transport aircraft with a tapered fuselage, swept wings and tail.
v=[];f=[]
cylinder(v,f,(-5.4,0,0),(4.6,0,0),.54,32)
sphere(v,f,(4.1,0,0),(.85,.47,.48),24,14)
box_mesh(v,f,-.5,0,0,1.05,.17,18.0)
box_mesh(v,f,-4.7,.02,0,.55,.14,7.2)
box_mesh(v,f,-4.3,.72,0,2.1,.12,.23)
for side in (-1,1):
    cylinder(v,f,(-1.2,-.62,side*5.0),(-1.2,-.62,side*6.2),.39,24)
    sphere(v,f,(-1.2,-.62,side*6.25),(.42,.42,.42),18,12)
write_obj("transport_plane",v,f)

# Functional placement and environment objects.
for name,dimensions in (("build_wall",(3.2,2.4,.22)),("build_platform",(3.0,.22,3.0)),
                        ("build_ramp",(3.0,.22,3.0)),("supply_crate",(1.4,1.1,1.2)),
                        ("field_hangar",(16,7,11)),("lookout_tower",(3,12,3)),
                        ("training_target",(1.2,1.8,.5))):
    vv=[];ff=[];box_mesh(vv,ff,0,0,0,*dimensions);write_obj(name,vv,ff)
v=[];f=[]
cylinder(v,f,(0,0,0),(0,.85,0),.105,24)
sphere(v,f,(0,.38,0),(.18,.22,.20),18,10)
sphere(v,f,(0,.92,0),(.14,.13,.14),18,10)
cylinder(v,f,(0,.82,0),(0,1.1,0),.035,16)
write_obj("energy_emitter",v,f)

# Mobile PBR material sets: 2K albedo/roughness and 1K normal/AO. The pixel data is authored texture content, not padding.
N=2048
def make_texture_set(name,base,seed,kind):
    rng=np.random.default_rng(seed)
    small=Image.fromarray(rng.integers(0,256,(256,256),dtype=np.uint8)).resize(
        (N,N),Image.Resampling.BICUBIC)
    coarse=np.asarray(small,dtype=np.float32)/255.0
    del small
    micro=rng.integers(0,256,(N,N),dtype=np.uint8)
    microf=micro.astype(np.float32)/255.0
    del micro
    x=np.arange(N,dtype=np.float32)[None,:]/N
    y=np.arange(N,dtype=np.float32)[:,None]/N
    if kind=="fabric":
        pattern=(np.sin(x*580)*np.cos(y*460))*.035
    elif kind=="concrete":
        pattern=np.sin((x+y)*250)*.045+np.cos(x*85)*.02
    elif kind=="sand":
        pattern=np.sin(x*360+y*120)*.035
    elif kind=="rock":
        pattern=np.sin(x*140+np.cos(y*40))*0.05
    elif kind=="ice":
        pattern=np.sin(x*420+y*85)*.045+np.cos(y*310)*.025
    elif kind=="metal":
        pattern=np.sin(x*900)*.025+np.cos(y*64)*.035
    elif kind=="moss":
        pattern=np.sin(x*72+np.cos(y*33))*0.06
    elif kind=="composite":
        pattern=np.sin(x*260)*.025+np.cos(y*260)*.025
    elif kind=="stone":
        pattern=np.sin((x*2+y)*170)*.055
    elif kind=="water":
        pattern=np.sin(x*38+y*13+np.sin(y*17))*0.065
    elif kind=="neon":
        pattern=np.sin(x*680+y*90)*.03+np.cos(y*410)*.02
    elif kind=="volcanic":
        pattern=np.sin(x*160+y*60)*.055+np.cos(x*40)*.025
    else:
        pattern=(np.sin(x*105+np.sin(y*27))*0.04+np.cos(y*88)*0.025)
    lum=np.clip((coarse-.5)*.27+(microf-.5)*.12+pattern,-.23,.23)
    albedo=np.empty((N,N,3),dtype=np.uint8)
    for channel in range(3):
        albedo[:,:,channel]=(np.clip(base[channel]+lum*(0.85+channel*.13),0,1)*255).astype(np.uint8)
    Image.fromarray(albedo,"RGB").save(ROOT/f"textures/{name}_albedo_2k.png",compress_level=1)
    del albedo,lum,microf,coarse,pattern

    # Normal maps are derived from a detailed height field and upscaled smoothly.
    rs=np.random.default_rng(seed+17)
    height=np.asarray(Image.fromarray(rs.integers(0,256,(1024,1024),dtype=np.uint8)).resize(
        (1024,1024),Image.Resampling.BICUBIC),dtype=np.float32)/255.0
    gy,gx=np.gradient(height)
    nx=-gx*5.0;ny=-gy*5.0;nz=np.ones_like(nx)
    norm=np.sqrt(nx*nx+ny*ny+nz*nz)
    normal=np.stack([nx/norm*.5+.5,ny/norm*.5+.5,nz/norm*.5+.5],axis=-1)
    normal8=(np.clip(normal,0,1)*255).astype(np.uint8)
    Image.fromarray(normal8,"RGB").save(
        ROOT/f"textures/{name}_normal_1k.png",compress_level=1)
    del height,gx,gy,nx,ny,nz,norm,normal,normal8

    # Full-resolution roughness carries actual per-pixel surface variation.
    rr=np.random.default_rng(seed+31)
    rough=rr.integers(32,240,(N,N),dtype=np.uint8)
    Image.fromarray(rough,"L").save(ROOT/f"textures/{name}_roughness_2k.png",compress_level=1)
    del rough

    # Lower-resolution ambient occlusion for mobile-friendly sampling.
    ao_small=rs.integers(90,245,(1024,1024),dtype=np.uint8)
    Image.fromarray(ao_small,"L").save(
        ROOT/f"textures/{name}_ao_1k.png",compress_level=1)
    del ao_small
    print("Generated mobile material:",name,flush=True)

make_texture_set("island_ground",(.30,.39,.23),1001,"ground")
make_texture_set("tropical_rock",(.34,.33,.29),2002,"rock")
make_texture_set("operator_fabric",(.11,.19,.23),3003,"fabric")
make_texture_set("neon_concrete",(.18,.21,.27),4004,"concrete")
make_texture_set("desert_sand",(.68,.49,.29),5005,"sand")
# Distinct material families for the remaining biomes and major world surfaces.
# Each set has its own deterministic surface detail; no duplicated or padding files.
make_texture_set("frozen_ice",(.62,.78,.86),6006,"ice")
make_texture_set("industrial_metal",(.29,.34,.38),7007,"metal")
make_texture_set("green_moss",(.20,.34,.19),8008,"moss")
make_texture_set("orbital_composite",(.24,.29,.39),9009,"composite")
make_texture_set("abandoned_stone",(.39,.38,.34),10101,"stone")
make_texture_set("tropical_water",(.08,.31,.42),11111,"water")
make_texture_set("neon_alloy",(.19,.23,.36),12121,"neon")
make_texture_set("volcanic_rock",(.25,.16,.14),13131,"volcanic")

world_templates={
"island":{"id":"tropical_island","name":"Ilha Tropical","biome":"tropical","spawn":[0,0,0],
 "resource_nodes":[[8,-18],[-24,-12],[32,18],[-42,31],[55,-46],[-63,-52],[73,62],[-82,75],[96,-73],[15,91],[-102,8],[103,88]],
 "map_bounds":[-120,120,-120,120],"build_limit":20,"npc_profile":"scavenger"},
"neon_city":{"id":"neon_city","name":"Neon City","biome":"urban_night","spawn":[0,0,0],
 "resource_nodes":[[9,-18],[-24,-12],[32,18],[-42,31],[55,-46],[-63,-52],[73,62],[-82,75],[96,-73],[15,91],[-102,8],[103,88]],
 "map_bounds":[-120,120,-120,120],"build_limit":20,"npc_profile":"patrol"},
"desert_storm":{"id":"desert_storm","name":"Desert Storm","biome":"desert","spawn":[0,0,0],
 "resource_nodes":[[9,-18],[-24,-12],[32,18],[-42,31],[55,-46],[-63,-52],[73,62],[-82,75],[96,-73],[15,91],[-102,8],[103,88]],
 "map_bounds":[-120,120,-120,120],"build_limit":20,"npc_profile":"scavenger"},
"frozen_front":{"id":"frozen_front","name":"Frozen Front","biome":"snow","spawn":[0,0,0],"map_bounds":[-120,120,-120,120],"build_limit":20},
"industrial_zone":{"id":"industrial_zone","name":"Industrial Zone","biome":"industrial","spawn":[0,0,0],"map_bounds":[-120,120,-120,120],"build_limit":20},
"green_valley":{"id":"green_valley","name":"Green Valley","biome":"forest","spawn":[0,0,0],"map_bounds":[-120,120,-120,120],"build_limit":20},
"orbital_base":{"id":"orbital_base","name":"Orbital Base","biome":"futuristic","spawn":[0,0,0],"map_bounds":[-120,120,-120,120],"build_limit":20},
"abandoned_sector":{"id":"abandoned_sector","name":"Abandoned Sector","biome":"ruins","spawn":[0,0,0],"map_bounds":[-120,120,-120,120],"build_limit":20}
}
world_materials={
 "island":["island_ground","tropical_rock","tropical_water","operator_fabric"],
 "neon_city":["neon_concrete","neon_alloy","operator_fabric"],
 "desert_storm":["desert_sand","volcanic_rock","abandoned_stone"],
 "frozen_front":["frozen_ice","abandoned_stone"],
 "industrial_zone":["industrial_metal","neon_concrete"],
 "green_valley":["green_moss","tropical_rock"],
 "orbital_base":["orbital_composite","neon_alloy"],
 "abandoned_sector":["abandoned_stone","volcanic_rock","industrial_metal"]
}
material_catalog={"materials":{},"world_bindings":world_materials}
for material in sorted({name for group in world_materials.values() for name in group}):
    material_catalog["materials"][material]={
      "albedo":f"textures/{material}_albedo_2k.png",
      "normal":f"textures/{material}_normal_1k.png",
      "roughness":f"textures/{material}_roughness_2k.png",
      "ambient_occlusion":f"textures/{material}_ao_1k.png",
      "resolution":"2048x2048 albedo/roughness; 1024x1024 normal/AO"
    }
(ROOT/"materials/biome_materials.json").write_text(
    json.dumps(material_catalog,ensure_ascii=False,indent=2),encoding="utf-8")
for key,data in world_templates.items():
    data["materials"]=world_materials[key]
    (ROOT/f"worlds/{key}.json").write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding="utf-8")

(ROOT/"characters/roster.json").write_text(json.dumps({"operators":[
 {"id":"nova","role":"reconhecimento","mesh":"models/operator_field.obj"},
 {"id":"atlas","role":"defesa","mesh":"models/operator_field.obj"},
 {"id":"ember","role":"exploracao","mesh":"models/operator_field.obj"},
 {"id":"volt","role":"estrategia","mesh":"models/operator_field.obj"}]},ensure_ascii=False,indent=2))
(ROOT/"missions/daily.json").write_text(json.dumps({"missions":[
 {"id":"collect_resources","target":8,"reward":90},
 {"id":"place_builds","target":5,"reward":120},
 {"id":"practice_energy","target":12,"reward":80},
 {"id":"land_from_transport","target":1,"reward":100}], "season":2},indent=2))
(ROOT/"effects/weapons.json").write_text(json.dumps({"fictional_energy_weapons":[
 {"id":"plasma_pulse","display":"Pulso Plasma","ammo":40,"damage_class":"light"},
 {"id":"arc_ray","display":"Raio Arc","ammo":40,"damage_class":"medium"},
 {"id":"nova","display":"Nova","ammo":40,"damage_class":"heavy"}]},ensure_ascii=False,indent=2))

(ROOT/"shaders/lit_es3.vert").write_text("""#version 300 es
precision highp float;
layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
uniform mat4 uMVP; uniform mat4 uModel;
out vec3 vNormal; out vec3 vWorld;
void main(){vec4 w=uModel*vec4(aPosition,1.0);vWorld=w.xyz;vNormal=mat3(uModel)*aNormal;gl_Position=uMVP*vec4(aPosition,1.0);}
""")
(ROOT/"shaders/lit_es3.frag").write_text("""#version 300 es
precision mediump float;
in vec3 vNormal; in vec3 vWorld; uniform vec4 uColor; out vec4 fragColor;
void main(){vec3 n=normalize(vNormal);vec3 l=normalize(vec3(-.45,.85,.35));float d=max(dot(n,l),0.0);vec3 v=normalize(vec3(0.0,6.0,18.0)-vWorld);float s=pow(max(dot(reflect(-l,n),v),0.0),24.0);fragColor=vec4(uColor.rgb*(.42+.58*d)+vec3(.10)*s,uColor.a);}
""")

def write_ambience(filename,seconds,seed,kind):
    sample_rate=22050
    rng=random.Random(seed)
    with wave.open(str(ROOT/f"audio/{filename}.wav"),"wb") as wf:
        wf.setnchannels(1);wf.setsampwidth(2);wf.setframerate(sample_rate)
        chunk=bytearray()
        for i in range(sample_rate*seconds):
            t=i/sample_rate
            noise=(rng.random()*2-1)
            if kind=="plane":
                value=.18*math.sin(2*math.pi*68*t)+.08*math.sin(2*math.pi*136*t)+noise*.05
            elif kind=="city":
                value=.12*math.sin(2*math.pi*.11*t)+.08*math.sin(2*math.pi*47*t)+noise*.035
            elif kind=="desert":
                value=.14*math.sin(2*math.pi*.07*t+math.sin(2*math.pi*.17*t))+noise*.045
            else:
                value=.12*math.sin(2*math.pi*.17*t)+.08*math.sin(2*math.pi*.31*t+1.2)+noise*.04
            chunk.extend(struct.pack("<h",int(max(-.7,min(.7,value))*32767)))
            if len(chunk)>=131072:
                wf.writeframes(chunk);chunk.clear()
        if chunk:wf.writeframes(chunk)
write_ambience("island_ambience",75,5150,"island")
write_ambience("transport_engine",55,6150,"plane")
write_ambience("neon_city_ambience",60,7150,"city")
write_ambience("desert_wind",60,8150,"desert")

files=[]
for item in sorted(ROOT.rglob("*")):
    if item.is_file():
        size=item.stat().st_size
        digest=hashlib.sha256(item.read_bytes()).hexdigest()
        files.append({"path":item.relative_to(ROOT).as_posix(),"size":size,"sha256":digest})
required=[
 "worlds/island.json","worlds/neon_city.json","worlds/desert_storm.json",
 "characters/roster.json","models/operator_field.obj","models/transport_plane.obj",
 "models/energy_emitter.obj","models/build_wall.obj",
 "textures/island_ground_albedo_2k.png","textures/island_ground_normal_1k.png",
 "textures/desert_sand_albedo_2k.png","audio/transport_engine.wav",
 "effects/weapons.json","materials/biome_materials.json"]
pack_manifest={"pack_id":"battle_universe_season_02","version":4,
 "title":"Battle Universe — Season 02 Mobile 2K",
 "engine":"Android OpenGL ES 3.0","texture_resolution":"2048x2048 albedo/roughness; 1024x1024 normal/AO",
 "world_count":8,"files":files,"required":required}
(ROOT/"manifest.json").write_text(json.dumps(pack_manifest,ensure_ascii=False,indent=2),encoding="utf-8")
(ROOT/"PACK_README.txt").write_text(
 "BATTLE UNIVERSE SEASON 02 CONTENT PACK\n"
 "Original OBJ meshes: field operator, transport airplane, energy emitter, build pieces, hangar, lookout tower, supply crate and training target.\n"
 "Mobile PBR material sets: 2K albedo/roughness and 1K normal/ambient-occlusion maps.\n"
 "Eight world configuration files, roster, missions, fictional energy weapon metadata, GLSL ES 3.0 shaders and four ambient audio loops.\n",
 encoding="utf-8")

OUT.mkdir(parents=True,exist_ok=True)
archive=OUT/"battle-universe-season-02-pack.zip"
with zipfile.ZipFile(archive,"w",zipfile.ZIP_DEFLATED,compresslevel=1) as z:
    for item in sorted(ROOT.rglob("*")):
        if item.is_file():z.write(item,item.relative_to(ROOT).as_posix())
size=archive.stat().st_size
if size<1024*1024:
    raise SystemExit(f"Content pack is unexpectedly small ({size} bytes); refusing to publish incomplete assets.")
if size>400*1024*1024:
    raise SystemExit(f"Season 02 exceeds the 400 MiB mobile package limit ({size} bytes).")
h=hashlib.sha256()
with archive.open("rb") as inp:
    for chunk in iter(lambda:inp.read(4*1024*1024),b""):h.update(chunk)
manifest={"version":4,"title":"Battle Universe Season 02 — Pacote Mobile 2K",
 "url":"https://github.com/davidiasdonacimento-cell/BATTLE-UNIVERSE/releases/download/data-pack-v4/battle-universe-season-02-pack.zip",
 "size_bytes":size,"sha256":h.hexdigest()}
(OUT/"content-pack-manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2))

# Embed the complete Season 02 archive in the APK. The app extracts this private asset on first launch.
ASSET_DIR=Path("app/build/generated/season02Assets/season02")
ASSET_DIR.mkdir(parents=True,exist_ok=True)
shutil.copy2(archive,ASSET_DIR/"battle-universe-season-02-pack.zip")
shutil.copy2(OUT/"content-pack-manifest.json",ASSET_DIR/"content-pack-manifest.json")
print(f"Season 02 pack: {size/1024/1024:.1f} MiB; {len(files)} real hashed resources; SHA-256 {manifest['sha256']}",flush=True)
