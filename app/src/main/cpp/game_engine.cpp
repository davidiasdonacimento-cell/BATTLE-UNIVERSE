#include <jni.h>
#include <GLES3/gl3.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cstdio>
#include <array>
#include <cmath>
#include <mutex>
#include <sstream>
#include <iomanip>
#include <limits>
#include <string>
#include <vector>

namespace {
constexpr float PI = 3.14159265358979323846f;
constexpr int BOT_COUNT = 29;
constexpr int BOT_MAX_HEALTH = 12;
constexpr bool FRIENDLY_FIRE_ENABLED = false;
constexpr int CRYSTAL_COUNT = 12;
constexpr int BUTTON_JUMP = 1;
constexpr int BUTTON_INTERACT = 2;
constexpr int BUTTON_BUILD = 4;
constexpr int BUTTON_RUN = 8;
constexpr int BUTTON_PULSE = 16;
constexpr int BUTTON_SWAP = 32;
constexpr int BUTTON_AIM = 64;
constexpr int BUTTON_CROUCH = 128;
constexpr int BUILD_CAPACITY = 24;
constexpr int TARGET_COUNT = 3;

struct Vec3 { float x, y, z; };
struct Mat4 { float m[16]{}; };

Vec3 sub(Vec3 a, Vec3 b) { return {a.x-b.x, a.y-b.y, a.z-b.z}; }
Vec3 muls(Vec3 a, float s) { return {a.x*s, a.y*s, a.z*s}; }
float dot(Vec3 a, Vec3 b) { return a.x*b.x + a.y*b.y + a.z*b.z; }
Vec3 cross(Vec3 a, Vec3 b) {
    return {a.y*b.z-a.z*b.y, a.z*b.x-a.x*b.z, a.x*b.y-a.y*b.x};
}
float length(Vec3 v) { return std::sqrt(dot(v,v)); }
Vec3 normalize(Vec3 v) { return muls(v, 1.0f/std::max(0.0001f,length(v))); }

Mat4 identity() {
    Mat4 r{};
    r.m[0]=r.m[5]=r.m[10]=r.m[15]=1.0f;
    return r;
}
Mat4 multiply(const Mat4& a, const Mat4& b) {
    Mat4 r{};
    for (int c=0;c<4;++c) for (int row=0;row<4;++row)
        for (int k=0;k<4;++k) r.m[c*4+row] += a.m[k*4+row]*b.m[c*4+k];
    return r;
}
Mat4 translation(float x,float y,float z) {
    Mat4 r=identity(); r.m[12]=x; r.m[13]=y; r.m[14]=z; return r;
}
Mat4 scaling(float x,float y,float z) {
    Mat4 r{}; r.m[0]=x; r.m[5]=y; r.m[10]=z; r.m[15]=1; return r;
}
Mat4 rotationY(float a) {
    Mat4 r=identity(); const float c=std::cos(a),s=std::sin(a);
    r.m[0]=c; r.m[2]=-s; r.m[8]=s; r.m[10]=c; return r;
}
Mat4 rotationZ(float a) {
    Mat4 r=identity(); const float c=std::cos(a),s=std::sin(a);
    r.m[0]=c; r.m[1]=s; r.m[4]=-s; r.m[5]=c; return r;
}
Mat4 rotationX(float a) {
    Mat4 r=identity(); const float c=std::cos(a),s=std::sin(a);
    r.m[5]=c; r.m[6]=s; r.m[9]=-s; r.m[10]=c; return r;
}
Mat4 perspective(float fov,float aspect,float nearP,float farP) {
    Mat4 r{}; const float f=1.0f/std::tan(fov*0.5f);
    r.m[0]=f/std::max(0.1f,aspect); r.m[5]=f;
    r.m[10]=(farP+nearP)/(nearP-farP); r.m[11]=-1;
    r.m[14]=(2*farP*nearP)/(nearP-farP); return r;
}
Mat4 lookAt(Vec3 eye,Vec3 center,Vec3 up) {
    const Vec3 f=normalize(sub(center,eye));
    const Vec3 s=normalize(cross(f,up));
    const Vec3 u=cross(s,f);
    Mat4 r=identity();
    r.m[0]=s.x; r.m[4]=s.y; r.m[8]=s.z;
    r.m[1]=u.x; r.m[5]=u.y; r.m[9]=u.z;
    r.m[2]=-f.x; r.m[6]=-f.y; r.m[10]=-f.z;
    r.m[12]=-dot(s,eye); r.m[13]=-dot(u,eye); r.m[14]=dot(f,eye);
    return r;
}

std::atomic<int> gCrashTraceStage{0};
std::atomic<int> gLastEliminationStage{0};
GLuint gProgram=0, gCubeVbo=0, gSphereVbo=0, gCylinderVbo=0, gConeVbo=0;
GLsizei gSphereCount=0, gCylinderCount=0, gConeCount=0;
GLint gMvpLocation=-1, gModelLocation=-1, gColorLocation=-1;
int gScreenMode=0, gSelectedCharacter=0, gSelectedMap=0, gSelectedMode=0, gQuality=1;
int gSelectedShirtStyle=0,gSelectedBootStyle=0,gSelectedSetStyle=0;
Mat4 gProjection=identity();
float gCharacterPoseScale=1.0f;
int gWidth=1, gHeight=1;
Vec3 gPlayer{0,0.55f,0};
std::atomic<float> gPlayerXSnapshot{0.0f},gPlayerZSnapshot{0.0f};
std::atomic<int> gDropStateSnapshot{0};
std::atomic<float> gPlaneXSnapshot{-110.0f},gPlaneZSnapshot{-32.0f};
std::atomic<float> gDropTimerSnapshot{0.0f},gZoneRadiusSnapshot{112.0f};
std::atomic<float> gWaypointXSnapshot{0.0f},gWaypointZSnapshot{0.0f};
std::atomic<bool> gWaypointActiveSnapshot{false};
float gPlayerYaw=0, gCameraYaw=0, gCameraPitch=0.36f;
float gJumpHeight=0, gJumpVelocity=0, gMatchTime=0, gZoneRadius=30;
float gInteractCooldown=0;
int gCollected=0, gPreviousButtons=0;
std::atomic<int> gCollectedSnapshot{0},gEliminationsSnapshot{0};
std::atomic<int> gDamageDealtSnapshot{0},gDamageTakenSnapshot{0};
std::atomic<int> gAiEliminationsSnapshot{0},gPlayerHealthSnapshot{100};
std::atomic<int> gAliveEnemiesSnapshot{0};
std::array<bool,CRYSTAL_COUNT> gCrystalTaken{};
struct BotState { Vec3 pos; float yaw=0; float speed=0; float phase=0; float fireFlash=0; bool aiming=false; int targetIndex=-1; bool targetPlayer=false; };
std::array<int,BOT_COUNT> gBotHealth{};
std::array<int,BOT_COUNT> gBotDamageAmount{};
std::array<float,BOT_COUNT> gBotDamageTimer{};
std::array<Vec3,BOT_COUNT> gBotDamagePosition{};
std::array<bool,BOT_COUNT> gBotActive{};
std::array<bool,BOT_COUNT> gBotDowned{};
std::array<float,BOT_COUNT> gBotDownedTimer{};
std::array<float,BOT_COUNT> gBotReviveProgress{};
std::array<float,BOT_COUNT> gBotRespawn{};
std::array<float,BOT_COUNT> gBotAttackCooldown{};
bool isAllyBot(int index) {
    return (gSelectedMode==1&&index==0)||(gSelectedMode==2&&index<3);
}
int botTeamId(int index) {
    if(isAllyBot(index)) return 0;
    if(gSelectedMode==1) return 1+(index-1)/2;
    if(gSelectedMode==2) return 1+(index-3)/4;
    return index+1;
}
bool sameBotTeam(int a,int b) {
    if(a==b) return true;
    if(gSelectedMode!=1&&gSelectedMode!=2) return false;
    return botTeamId(a)==botTeamId(b);
}
std::array<BotState,BOT_COUNT> gBots{};
std::array<float,BOT_COUNT> gBotDropHeight{},gBotDropSpeed{};
std::array<bool,BOT_COUNT> gBotDropCanopy{};
std::array<Vec3,BOT_COUNT> gBotHomes{{
    {-92,0.55f,-88},{-53,0.55f,-94},{-10,0.55f,-99},{38,0.55f,-92},
    {87,0.55f,-71},{98,0.55f,-18},{88,0.55f,48},{48,0.55f,94},
    {2,0.55f,91},{-47,0.55f,83},{-96,0.55f,42},{-101,0.55f,-22}
}};
struct BuildPiece { Vec3 pos; float yaw=0; int type=0; int health=100; };
constexpr int ICE_WALL_MAX_HEALTH=100;
std::array<BuildPiece,BUILD_CAPACITY> gBuildPieces{};
int gBuildCount=0, gBuildType=0, gWood=120, gBuildLimit=12, gResourceCount=5;
constexpr std::array<Vec3,32> gBuildingPositions{{
    {-76,0,-70},{-38,0,-76},{12,0,-78},{57,0,-73},
    {79,0,-22},{-81,0,-18},{-72,0,35},{-34,0,72},
    {15,0,76},{64,0,72},{32,0,-30},{-38,0,-24},
    {-7,0,37},{78,0,35},{-91,0,82},{91,0,-86},
    {-128,0,-118},{-86,0,-128},{-34,0,-130},{20,0,-130},
    {74,0,-126},{126,0,-108},{132,0,-54},{132,0,4},
    {130,0,62},{116,0,116},{62,0,130},{8,0,132},
    {-48,0,130},{-104,0,120},{-132,0,66},{-132,0,2}
}};
bool gContentPackReady=false;
float gBuildCooldown=0;
struct TrainingTarget { Vec3 pos; int energy=3; bool active=true; };
std::array<TrainingTarget,TARGET_COUNT> gTargets{{
    {{-12,1.25f,-13},3,true}, {{13,1.25f,-12},3,true}, {{2,1.25f,18},3,true}
}};
std::array<int,4> gWeaponAmmo{{0,0,0,0}};
std::array<bool,4> gWeaponOwned{{false,false,false,false}};
int gTrainingScore=0, gWeaponType=-1;
struct GroundLoot { Vec3 pos; int weaponType=0; int ammo=0; bool active=true; bool ammoOnly=false; };
std::array<GroundLoot,18> gGroundLoot{};
int gPlayerHealth=100,gEliminations=0;
int gDamageDealt=0,gDamageTaken=0,gAiEliminations=0;
bool gMatchFinished=false,gSpectatorMode=false;
void publishMatchStats() {
    int aliveEnemies=0;
    for(int i=0;i<BOT_COUNT;++i) {
        if(gBotActive[static_cast<size_t>(i)]&&!isAllyBot(i)) ++aliveEnemies;
    }
    gCollectedSnapshot.store(gCollected,std::memory_order_relaxed);
    gEliminationsSnapshot.store(gEliminations,std::memory_order_relaxed);
    gDamageDealtSnapshot.store(gDamageDealt,std::memory_order_relaxed);
    gDamageTakenSnapshot.store(gDamageTaken,std::memory_order_relaxed);
    gAiEliminationsSnapshot.store(gAiEliminations,std::memory_order_relaxed);
    gPlayerHealthSnapshot.store(gPlayerHealth,std::memory_order_relaxed);
    gAliveEnemiesSnapshot.store(aliveEnemies,std::memory_order_relaxed);
}
int gSpectatorTarget=-1;
bool gPlayerDowned=false,gCrouched=false,gBuildPreview=false;
float gDownedTimer=0.0f,gReviveProgress=0.0f;
int gPlayerReviveTarget=-1;
float gPlayerReviveProgress=0.0f;
float gPulseCooldown=0, gPulseFlash=0, gDropTimer=0;
float gZoneDamageTimer=0,gLookSensitivity=1.0f;
constexpr float PLANE_COUNTDOWN_SECONDS=7.0f;
constexpr float WORLD_LIMIT=148.0f;
int gDropState=0; // 0 lobby, 1 inside transport plane, 2 parachute descent, 3 ground
Vec3 gPlanePosition{-110,24,-32};
float gPlaneWalkX=1.0f,gPlaneWalkZ=0.0f;
Vec3 gPulseStartPosition{0,1.5f,0},gPulseHitPosition{0,1.4f,-8};
bool gParachuteOpen=false;
bool gPlaneExteriorView=false;
Vec3 gWaypoint{0.0f,0.55f,0.0f};
bool gWaypointActive=false;
std::mutex gStatusMutex;
std::string gHudStatus="CARREGANDO CENÁRIO 3D...";

const std::array<Vec3,CRYSTAL_COUNT> gCrystals{{
    {8,0.55f,-18},{-24,0.55f,-12},{32,0.55f,18},{-42,0.55f,31},
    {55,0.55f,-46},{-63,0.55f,-52},{73,0.55f,62},{-82,0.55f,75},
    {96,0.55f,-73},{15,0.55f,91},{-102,0.55f,8},{103,0.55f,88}
}};

void setStatus(const std::string& value) {
    std::lock_guard<std::mutex> lock(gStatusMutex); gHudStatus=value;
}
void publishFlightInfo() {
    gDropStateSnapshot.store(gDropState,std::memory_order_relaxed);
    gPlaneXSnapshot.store(gPlanePosition.x,std::memory_order_relaxed);
    gPlaneZSnapshot.store(gPlanePosition.z,std::memory_order_relaxed);
    gDropTimerSnapshot.store(gDropTimer,std::memory_order_relaxed);
    gZoneRadiusSnapshot.store(gZoneRadius,std::memory_order_relaxed);
    gWaypointXSnapshot.store(gWaypoint.x,std::memory_order_relaxed);
    gWaypointZSnapshot.store(gWaypoint.z,std::memory_order_relaxed);
    gWaypointActiveSnapshot.store(gWaypointActive,std::memory_order_relaxed);
}
GLuint compileShader(GLenum type,const char* source) {
    GLuint shader=glCreateShader(type); glShaderSource(shader,1,&source,nullptr);
    glCompileShader(shader); GLint ok=GL_FALSE; glGetShaderiv(shader,GL_COMPILE_STATUS,&ok);
    if (ok!=GL_TRUE) {
        char log[1024]{}; glGetShaderInfoLog(shader,sizeof(log),nullptr,log);
        __android_log_print(ANDROID_LOG_ERROR,"BattleUniverse","Shader: %s",log);
        glDeleteShader(shader); return 0;
    }
    return shader;
}
void addFace(std::vector<float>& out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 n) {
    const std::array<Vec3,6> points{{a,b,c,a,c,d}};
    for (const Vec3& p:points) {
        out.push_back(p.x); out.push_back(p.y); out.push_back(p.z);
        out.push_back(n.x); out.push_back(n.y); out.push_back(n.z);
    }
}
void appendVertex(std::vector<float>& out, Vec3 p, Vec3 n) {
    out.insert(out.end(), {p.x,p.y,p.z,n.x,n.y,n.z});
}
void appendTriangle(std::vector<float>& out, Vec3 a, Vec3 b, Vec3 c, Vec3 na, Vec3 nb, Vec3 nc) {
    appendVertex(out,a,na); appendVertex(out,b,nb); appendVertex(out,c,nc);
}
struct MeshBuffer { GLuint vbo=0; GLsizei count=0; };
MeshBuffer makeMesh(const std::vector<float>& vertices) {
    MeshBuffer mesh;
    glGenBuffers(1,&mesh.vbo);
    glBindBuffer(GL_ARRAY_BUFFER,mesh.vbo);
    glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(vertices.size()*sizeof(float)),vertices.data(),GL_STATIC_DRAW);
    mesh.count=static_cast<GLsizei>(vertices.size()/6);
    return mesh;
}
MeshBuffer buildSphere() {
    std::vector<float> v;
    constexpr int rings=18, slices=28;
    for(int y=0;y<rings;++y) {
        float v0=static_cast<float>(y)/rings, v1=static_cast<float>(y+1)/rings;
        float p0=PI*v0, p1=PI*v1;
        for(int x=0;x<slices;++x) {
            float u0=2*PI*static_cast<float>(x)/slices, u1=2*PI*static_cast<float>(x+1)/slices;
            auto point=[](float a,float b){return Vec3{std::sin(a)*std::cos(b),std::cos(a),std::sin(a)*std::sin(b)};};
            Vec3 a=point(p0,u0),b=point(p1,u0),c=point(p1,u1),d=point(p0,u1);
            appendTriangle(v,a,b,c,a,b,c); appendTriangle(v,a,c,d,a,c,d);
        }
    }
    return makeMesh(v);
}
MeshBuffer buildCylinder() {
    std::vector<float> v;
    constexpr int n=24;
    for(int i=0;i<n;++i) {
        float a=2*PI*i/n,b=2*PI*(i+1)/n;
        Vec3 p0{0.5f*std::cos(a),-0.5f,0.5f*std::sin(a)};
        Vec3 p1{0.5f*std::cos(b),-0.5f,0.5f*std::sin(b)};
        Vec3 p2{0.5f*std::cos(b),0.5f,0.5f*std::sin(b)};
        Vec3 p3{0.5f*std::cos(a),0.5f,0.5f*std::sin(a)};
        Vec3 n0{std::cos(a),0,std::sin(a)},n1{std::cos(b),0,std::sin(b)};
        appendTriangle(v,p0,p1,p2,n0,n1,n1); appendTriangle(v,p0,p2,p3,n0,n1,n0);
        appendTriangle(v,{0,-0.5f,0},p1,p0,{0,-1,0},{0,-1,0},{0,-1,0});
        appendTriangle(v,{0,0.5f,0},p3,p2,{0,1,0},{0,1,0},{0,1,0});
    }
    return makeMesh(v);
}
MeshBuffer buildCone() {
    std::vector<float> v;
    constexpr int n=24;
    Vec3 tip{0,0.5f,0};
    for(int i=0;i<n;++i) {
        float a=2*PI*i/n,b=2*PI*(i+1)/n;
        Vec3 p0{0.5f*std::cos(a),-0.5f,0.5f*std::sin(a)};
        Vec3 p1{0.5f*std::cos(b),-0.5f,0.5f*std::sin(b)};
        Vec3 na=normalize({std::cos(a),0.55f,std::sin(a)});
        Vec3 nb=normalize({std::cos(b),0.55f,std::sin(b)});
        appendTriangle(v,p0,p1,tip,na,nb,normalize({std::cos(a)+std::cos(b),1.0f,std::sin(a)+std::sin(b)}));
        appendTriangle(v,{0,-0.5f,0},p1,p0,{0,-1,0},{0,-1,0},{0,-1,0});
    }
    return makeMesh(v);
}
MeshBuffer gSphereMesh, gCylinderMesh, gConeMesh;
void createCube() {
    std::vector<float> v; v.reserve(36*6);
    constexpr float n=-0.5f,p=0.5f;
    addFace(v,{p,n,n},{p,p,n},{p,p,p},{p,n,p},{1,0,0});
    addFace(v,{n,n,p},{n,p,p},{n,p,n},{n,n,n},{-1,0,0});
    addFace(v,{n,p,n},{n,p,p},{p,p,p},{p,p,n},{0,1,0});
    addFace(v,{n,n,p},{n,n,n},{p,n,n},{p,n,p},{0,-1,0});
    addFace(v,{p,n,p},{p,p,p},{n,p,p},{n,n,p},{0,0,1});
    addFace(v,{n,n,n},{n,p,n},{p,p,n},{p,n,n},{0,0,-1});
    glGenBuffers(1,&gCubeVbo); glBindBuffer(GL_ARRAY_BUFFER,gCubeVbo);
    glBufferData(GL_ARRAY_BUFFER,static_cast<GLsizeiptr>(v.size()*sizeof(float)),v.data(),GL_STATIC_DRAW);
}
bool initializeGraphics() {
    const char* vsSource=
        "#version 300 es\nprecision highp float;\n"
        "layout(location=0) in vec3 aPosition; layout(location=1) in vec3 aNormal;\n"
        "uniform mat4 uMVP; uniform mat4 uModel; out vec3 vNormal; out vec3 vWorld;\n"
        "void main(){ vec4 world=uModel*vec4(aPosition,1.0);"
        "vNormal=mat3(uModel)*aNormal; vWorld=world.xyz;"
        "gl_Position=uMVP*vec4(aPosition,1.0); }\n";
    const char* fsSource=
        "#version 300 es\nprecision mediump float;\n"
        "in vec3 vNormal; in vec3 vWorld; uniform vec4 uColor; out vec4 fragColor;\n"
        "void main(){ vec3 n=normalize(vNormal); vec3 l=normalize(vec3(-0.45,0.85,0.35));"
        "float d=max(dot(n,l),0.0); vec3 v=normalize(vec3(0.0,6.0,18.0)-vWorld);"
        "float sp=pow(max(dot(reflect(-l,n),v),0.0),24.0);"
        "vec3 c=uColor.rgb*(0.42+0.58*d)+vec3(0.10)*sp;"
        "fragColor=vec4(c,uColor.a); }\n";
    GLuint vs=compileShader(GL_VERTEX_SHADER,vsSource);
    GLuint fs=compileShader(GL_FRAGMENT_SHADER,fsSource);
    if (!vs||!fs) return false;
    gProgram=glCreateProgram(); glAttachShader(gProgram,vs); glAttachShader(gProgram,fs);
    glLinkProgram(gProgram); glDeleteShader(vs); glDeleteShader(fs);
    GLint linked=GL_FALSE; glGetProgramiv(gProgram,GL_LINK_STATUS,&linked);
    if (linked!=GL_TRUE) {
        char log[1024]{}; glGetProgramInfoLog(gProgram,sizeof(log),nullptr,log);
        __android_log_print(ANDROID_LOG_ERROR,"BattleUniverse","Link: %s",log);
        glDeleteProgram(gProgram); gProgram=0; return false;
    }
    gMvpLocation=glGetUniformLocation(gProgram,"uMVP");
    gModelLocation=glGetUniformLocation(gProgram,"uModel");
    gColorLocation=glGetUniformLocation(gProgram,"uColor");
    createCube();
    gSphereMesh=buildSphere(); gCylinderMesh=buildCylinder(); gConeMesh=buildCone();
    gSphereVbo=gSphereMesh.vbo; gSphereCount=gSphereMesh.count;
    gCylinderVbo=gCylinderMesh.vbo; gCylinderCount=gCylinderMesh.count;
    gConeVbo=gConeMesh.vbo; gConeCount=gConeMesh.count;
    return gCubeVbo!=0 && gSphereVbo!=0 && gCylinderVbo!=0 && gConeVbo!=0;
}
void drawMesh(GLuint vbo,GLsizei count,Vec3 p,Vec3 size,Vec3 color,float yaw=0,float tilt=0,float tiltX=0,float alpha=1.0f) {
    if(!gProgram||!vbo||count<=0)return;
    size.y*=gCharacterPoseScale;
    Mat4 model=multiply(translation(p.x,p.y,p.z),
        multiply(rotationY(yaw),multiply(rotationZ(tilt),multiply(rotationX(tiltX),scaling(size.x,size.y,size.z)))));
    Mat4 mvp=multiply(gProjection,model);
    glUniformMatrix4fv(gMvpLocation,1,GL_FALSE,mvp.m);
    glUniformMatrix4fv(gModelLocation,1,GL_FALSE,model.m);
    glUniform4f(gColorLocation,color.x,color.y,color.z,alpha);
    glBindBuffer(GL_ARRAY_BUFFER,vbo);
    glEnableVertexAttribArray(0); glEnableVertexAttribArray(1);
    glVertexAttribPointer(0,3,GL_FLOAT,GL_FALSE,6*sizeof(float),reinterpret_cast<void*>(0));
    glVertexAttribPointer(1,3,GL_FLOAT,GL_FALSE,6*sizeof(float),reinterpret_cast<void*>(3*sizeof(float)));
    glDrawArrays(GL_TRIANGLES,0,count);
}
void drawCube(Vec3 p,Vec3 size,Vec3 color,float yaw=0,float tilt=0,float tiltX=0,float alpha=1.0f) {
    drawMesh(gCubeVbo,36,p,size,color,yaw,tilt,tiltX,alpha);
}
void drawDamagePopup(Vec3 origin,int damage,float yaw,float rise) {
    // Números 3D em sete segmentos, sempre voltados para a câmera.
    const Vec3 color{1.0f,0.78f,0.16f};
    const float scale=1.0f;
    const float rightX=std::cos(yaw), rightZ=-std::sin(yaw);
    auto segment=[&](float x,float y,Vec3 size) {
        Vec3 p{origin.x+rightX*x,origin.y+3.35f+rise+y,origin.z+rightZ*x};
        drawCube(p,size,color,yaw);
    };
    // Sinal de menos.
    segment(-0.30f,0.0f,{0.19f*scale,0.055f*scale,0.07f});
    const int digit=std::clamp(damage,0,9);
    static const bool digits[10][7]={
        {1,1,1,0,1,1,1},{0,0,1,0,0,1,0},{1,0,1,1,1,0,1},
        {1,0,1,1,0,1,1},{0,1,1,1,0,1,0},{1,1,0,1,0,1,1},
        {1,1,0,1,1,1,1},{1,0,1,0,0,1,0},{1,1,1,1,1,1,1},
        {1,1,1,1,0,1,1}
    };
    const float x=0.02f;
    if(digits[digit][0]) segment(x,0.25f,{0.30f*scale,0.055f*scale,0.07f});
    if(digits[digit][1]) segment(x-0.15f,0.13f,{0.055f*scale,0.22f*scale,0.07f});
    if(digits[digit][2]) segment(x+0.15f,0.13f,{0.055f*scale,0.22f*scale,0.07f});
    if(digits[digit][3]) segment(x,0.0f,{0.30f*scale,0.055f*scale,0.07f});
    if(digits[digit][4]) segment(x-0.15f,-0.13f,{0.055f*scale,0.22f*scale,0.07f});
    if(digits[digit][5]) segment(x+0.15f,-0.13f,{0.055f*scale,0.22f*scale,0.07f});
    if(digits[digit][6]) segment(x,-0.25f,{0.30f*scale,0.055f*scale,0.07f});
}
void drawSphere(Vec3 p,Vec3 size,Vec3 color,float yaw=0,float tilt=0,float tiltX=0) {
    drawMesh(gSphereVbo,gSphereCount,p,size,color,yaw,tilt,tiltX);
}
void drawCylinder(Vec3 p,Vec3 size,Vec3 color,float yaw=0,float tilt=0,float tiltX=0) {
    drawMesh(gCylinderVbo,gCylinderCount,p,size,color,yaw,tilt,tiltX);
}
void drawCone(Vec3 p,Vec3 size,Vec3 color,float yaw=0,float tilt=0,float tiltX=0) {
    drawMesh(gConeVbo,gConeCount,p,size,color,yaw,tilt,tiltX);
}
void drawPalm(float x,float z,float scale) {
    drawCylinder({x,1.45f*scale,z},{0.24f*scale,2.65f*scale,0.24f*scale},{0.35f,0.20f,0.10f},0,-0.035f);
    drawSphere({x,2.85f*scale,z},{0.42f*scale,0.34f*scale,0.42f*scale},{0.18f,0.48f,0.23f});
    for(int i=0;i<8;++i){
        float a=(2*PI*i)/8.0f;
        drawSphere({x+std::cos(a)*0.92f*scale,2.95f*scale,z+std::sin(a)*0.92f*scale},
            {1.20f*scale,0.10f*scale,0.30f*scale},{0.10f,0.42f+0.025f*(i%3),0.20f},-a,0.12f);
    }
}
float buildingWidth(size_t i) { return 7.0f+static_cast<float>(i%3)*2.1f; }
float buildingDepth(size_t i) { return 6.0f+static_cast<float>(i%2)*2.0f; }
float buildingHeight(size_t i) { return 4.0f+static_cast<float>(i%4)*1.5f; }
bool worldPositionBlocked(float x,float z,float radius) {
    if(x<-WORLD_LIMIT+radius||x>WORLD_LIMIT-radius||z<-WORLD_LIMIT+radius||z>WORLD_LIMIT-radius)return true;
    for(size_t i=0;i<gBuildingPositions.size();++i) {
        const Vec3 h=gBuildingPositions[i];
        const float halfW=buildingWidth(i)*0.5f,halfD=buildingDepth(i)*0.5f;
        const float left=h.x-halfW,right=h.x+halfW,front=h.z-halfD,back=h.z+halfD;
        if(z>=front-radius&&z<=back+radius &&
                (std::fabs(x-left)<radius+0.16f||std::fabs(x-right)<radius+0.16f))return true;
        if(std::fabs(z-back)<radius+0.16f&&x>=left-radius&&x<=right+radius)return true;
        if(std::fabs(z-front)<radius+0.16f&&x>=left-radius&&x<=right+radius) {
            const bool doorway=std::fabs(x-h.x)<1.12f && z<front+1.35f;
            if(!doorway)return true;
        }
    }
    for(int i=0;i<gBuildCount;++i) {
        const BuildPiece& b=gBuildPieces[static_cast<size_t>(i)];
        if(b.type!=0||b.health<=0)continue;
        const float rx=x-b.pos.x,rz=z-b.pos.z,co=std::cos(b.yaw),sn=std::sin(b.yaw);
        const float localX=co*rx-sn*rz,localZ=sn*rx+co*rz;
        if(std::fabs(localX)<1.60f+radius&&std::fabs(localZ)<0.12f+radius)return true;
    }
    return false;
}
bool segmentHitsIceWall(Vec3 from,Vec3 to,const BuildPiece& wall,float& hitT) {
    const float co=std::cos(wall.yaw),sn=std::sin(wall.yaw);
    auto local=[&](Vec3 p) {
        const float rx=p.x-wall.pos.x,rz=p.z-wall.pos.z;
        return Vec3{co*rx-sn*rz,p.y,sn*rx+co*rz};
    };
    const Vec3 a=local(from),b=local(to);
    const float dx=b.x-a.x,dz=b.z-a.z;
    float enter=0.0f,leave=1.0f;
    auto clip=[&](float origin,float delta,float low,float high) {
        if(std::fabs(delta)<0.00001f)return origin>=low&&origin<=high;
        float t0=(low-origin)/delta,t1=(high-origin)/delta;
        if(t0>t1)std::swap(t0,t1);
        enter=std::max(enter,t0);leave=std::min(leave,t1);
        return enter<=leave;
    };
    if(!clip(a.x,dx,-1.60f,1.60f)||!clip(a.z,dz,-0.20f,0.20f))return false;
    if(leave<0.0f||enter>1.0f)return false;
    hitT=std::clamp(enter,0.0f,1.0f);
    return true;
}
bool damageIceWallOnPath(Vec3 from,Vec3 to,int damage,Vec3* hitPosition=nullptr) {
    int nearest=-1;float nearestT=1.01f;
    for(int i=0;i<gBuildCount;++i) {
        const BuildPiece& wall=gBuildPieces[static_cast<size_t>(i)];
        if(wall.type!=0||wall.health<=0)continue;
        float t=0.0f;
        if(segmentHitsIceWall(from,to,wall,t)&&t<nearestT){nearest=i;nearestT=t;}
    }
    if(nearest<0)return false;
    BuildPiece& wall=gBuildPieces[static_cast<size_t>(nearest)];
    wall.health=std::max(0,wall.health-std::max(1,damage));
    if(hitPosition) {
        hitPosition->x=from.x+(to.x-from.x)*nearestT;
        hitPosition->y=from.y+(to.y-from.y)*nearestT;
        hitPosition->z=from.z+(to.z-from.z)*nearestT;
    }
    if(wall.health<=0)setStatus("GELO QUEBRADO • BARREIRA DESTRUÍDA");
    else setStatus("GELO BLOQUEOU O DISPARO • RESISTÊNCIA "+std::to_string(wall.health)+"%");
    return true;
}
void moveWithCollision(Vec3& actor,float nextX,float nextZ,float radius) {
    if(!worldPositionBlocked(nextX,actor.z,radius))actor.x=nextX;
    if(!worldPositionBlocked(actor.x,nextZ,radius))actor.z=nextZ;
}
void drawBuilding(float x,float z,float w,float d,float h,Vec3 wall,Vec3 roof) {
    const float wallY=0.55f+h*0.5f,front=z-d*0.5f,back=z+d*0.5f;
    // Open-front room with four real walls: the entrance remains clear.
    drawCube({x,0.57f,z},{w-0.24f,0.16f,d-0.24f},{0.37f,0.31f,0.24f});
    drawCube({x,wallY,back},{w,h,0.20f},wall);
    drawCube({x-w*0.5f,wallY,z},{0.20f,h,d},wall);
    drawCube({x+w*0.5f,wallY,z},{0.20f,h,d},wall);
    const float sideWidth=(w-2.25f)*0.5f;
    drawCube({x-(1.125f+sideWidth*0.5f),wallY,front},{sideWidth,h,0.20f},wall);
    drawCube({x+(1.125f+sideWidth*0.5f),wallY,front},{sideWidth,h,0.20f},wall);
    drawCube({x,0.55f+2.35f,front},{2.25f,0.35f,0.22f},roof);
    const float roofY=0.55f+h+0.16f;
    // Roof beams frame the room without hiding its interior from the follow camera.
    drawCube({x,roofY,z-d*0.5f},{w+0.35f,0.22f,0.20f},roof);
    drawCube({x,roofY,z+d*0.5f},{w+0.35f,0.22f,0.20f},roof);
    drawCube({x-w*0.5f,roofY,z},{0.20f,0.22f,d+0.35f},roof);
    drawCube({x+w*0.5f,roofY,z},{0.20f,0.22f,d+0.35f},roof);
    drawCube({x-w*0.28f,2.7f,front-0.04f},{0.85f,0.72f,0.07f},{0.12f,0.66f,0.79f});
    drawCube({x+w*0.28f,2.7f,front-0.04f},{0.85f,0.72f,0.07f},{0.12f,0.66f,0.79f});
    drawCube({x+1.35f,0.92f,z+0.75f},{1.45f,0.55f,0.85f},{0.24f,0.34f,0.40f});
    drawCube({x-1.35f,0.72f,z+1.05f},{0.85f,0.25f,0.70f},{0.46f,0.35f,0.23f});
}
void drawWorld(float time) {
    // Expanded island arena: broad coast, inland districts, roads and forest belts.
    drawSphere({0,-1.05f,0},{520,0.55f,520},{0.025f,0.22f,0.36f});
    const std::array<Vec3,8> beachColors{{
        {0.78f,0.68f,0.44f},{0.24f,0.27f,0.38f},{0.68f,0.47f,0.29f},
        {0.73f,0.84f,0.91f},{0.34f,0.39f,0.40f},{0.57f,0.70f,0.39f},{0.30f,0.40f,0.58f},{0.50f,0.43f,0.34f}
    }};
    const std::array<Vec3,8> landColors{{
        {0.17f,0.43f,0.23f},{0.19f,0.24f,0.31f},{0.49f,0.37f,0.24f},
        {0.65f,0.78f,0.87f},{0.28f,0.34f,0.35f},{0.20f,0.48f,0.25f},{0.12f,0.20f,0.37f},{0.31f,0.27f,0.21f}
    }};
    const std::array<Vec3,8> roadColors{{
        {0.34f,0.37f,0.39f},{0.12f,0.16f,0.22f},{0.40f,0.37f,0.32f},
        {0.48f,0.57f,0.64f},{0.22f,0.27f,0.29f},{0.30f,0.39f,0.29f},{0.18f,0.26f,0.42f},{0.31f,0.29f,0.25f}
    }};
    const size_t mapStyle=static_cast<size_t>(std::clamp(gSelectedMap,0,7));
    const Vec3 beach=beachColors[mapStyle],land=landColors[mapStyle],road=roadColors[mapStyle];
    drawSphere({0,-0.02f,0},{174,0.62f,174},beach);
    drawSphere({0,0.31f,0},{158,0.31f,158},land);

    // Long roads create readable navigation across the larger play space.
    drawCube({0,0.535f,0},{8.0f,0.06f,310.0f},road);
    drawCube({0,0.536f,0},{310.0f,0.06f,8.0f},road);
    drawCube({-48,0.536f,-48},{5.0f,0.06f,180.0f},road,0.60f);
    drawCube({51,0.536f,48},{5.0f,0.06f,180.0f},road,-0.60f);
    drawCube({0,0.537f,-112},{150.0f,0.06f,6.0f},road);
    drawCube({0,0.537f,112},{150.0f,0.06f,6.0f},road);

    for(size_t i=0;i<gBuildingPositions.size();++i) {
        const std::array<Vec3,8> buildingTints{{
            {0.66f,0.61f,0.48f},{0.27f,0.30f,0.38f},{0.64f,0.48f,0.31f},
            {0.66f,0.77f,0.84f},{0.38f,0.43f,0.44f},{0.55f,0.65f,0.43f},{0.25f,0.35f,0.54f},{0.43f,0.37f,0.29f}
        }};
        const std::array<Vec3,8> roofTints{{
            {0.27f,0.20f,0.16f},{0.11f,0.18f,0.28f},{0.35f,0.23f,0.16f},
            {0.38f,0.52f,0.62f},{0.16f,0.20f,0.22f},{0.23f,0.34f,0.22f},{0.08f,0.15f,0.30f},{0.22f,0.18f,0.14f}
        }};
        Vec3 tint=buildingTints[mapStyle],roof=roofTints[mapStyle];
        const float width=buildingWidth(i),depth=buildingDepth(i),height=buildingHeight(i);
        const Vec3 pos=gBuildingPositions[i];
        drawBuilding(pos.x,pos.z,width,depth,height,tint,roof);
        drawCube({pos.x,0.72f+height+0.30f,pos.z},
            {width*0.52f,0.22f,depth*0.55f},
            gSelectedMap==1?Vec3{0.14f,0.74f,0.83f}:
            (gSelectedMap==3?Vec3{0.82f,0.91f,0.98f}:
            (gSelectedMap==4?Vec3{0.88f,0.49f,0.20f}:
            (gSelectedMap==6?Vec3{0.18f,0.83f,0.96f}:Vec3{0.35f,0.40f,0.43f}))));
    }

    // A forest ring and smaller groves break up sightlines and make the island feel broad.
    const std::array<Vec3,32> trees{{
        {-112,0,-88},{-105,0,-52},{-112,0,-12},{-110,0,36},{-104,0,88},
        {-82,0,109},{-45,0,111},{-4,0,111},{39,0,108},{82,0,105},
        {111,0,78},{112,0,38},{110,0,-6},{112,0,-49},{91,0,-111},
        {49,0,-113},{8,0,-112},{-35,0,-111},{-77,0,-108},{-93,0,12},
        {-91,0,56},{-61,0,42},{-55,0,-45},{-4,0,-51},{45,0,-43},
        {56,0,20},{39,0,54},{-20,0,58},{-6,0,12},{24,0,13},
        {5,0,-20},{-28,0,18}
    }};
    size_t treeLimit=gQuality==0?8:(gQuality==1?14:24);
    for(size_t i=0;i<treeLimit;++i) {
        float sway=std::sin(time*0.8f+static_cast<float>(i))*0.035f;
        float scale=0.80f+0.08f*static_cast<float>(i%4)+sway;
        if(gSelectedMap==0) drawPalm(trees[i].x,trees[i].z,scale);
        else if(gSelectedMap==1||gSelectedMap==6) {
            drawCylinder({trees[i].x,1.2f,trees[i].z},{0.16f,2.3f,0.16f},{0.20f,0.25f,0.26f});
            drawSphere({trees[i].x,2.8f,trees[i].z},{1.2f,1.4f,1.0f},
                gSelectedMap==6?Vec3{0.12f,0.62f,0.78f}:Vec3{0.12f,0.24f,0.30f});
        } else if(gSelectedMap==3) {
            drawCylinder({trees[i].x,1.0f,trees[i].z},{0.12f,2.0f,0.12f},{0.58f,0.70f,0.78f});
            drawCone({trees[i].x,2.3f,trees[i].z},{1.45f,2.5f,1.45f},{0.78f,0.88f,0.94f});
        } else if(gSelectedMap==4) {
            drawCylinder({trees[i].x,2.2f,trees[i].z},{0.32f,4.4f,0.32f},{0.27f,0.31f,0.33f});
            drawCylinder({trees[i].x,4.7f,trees[i].z},{0.42f,0.28f,0.42f},{0.90f,0.39f,0.14f});
        } else if(gSelectedMap==5) {
            drawCylinder({trees[i].x,1.0f,trees[i].z},{0.14f,2.0f,0.14f},{0.31f,0.24f,0.15f});
            drawSphere({trees[i].x,2.7f,trees[i].z},{1.5f,1.7f,1.5f},{0.13f,0.42f,0.20f});
        } else {
            drawCylinder({trees[i].x,0.9f,trees[i].z},{0.14f,1.8f,0.14f},{0.31f,0.24f,0.15f});
            drawCone({trees[i].x,2.0f,trees[i].z},{1.25f,2.0f,1.25f},{0.22f,0.39f,0.24f});
        }
    }

    // Distinct points of interest: pier, supply yard, lookout and water.
    drawCube({-14,0.64f,-112},{12.0f,0.22f,12.0f},{0.38f,0.24f,0.14f});
    for(int i=0;i<7;++i)
        drawCube({-18.5f+i*1.5f,0.96f,-117.0f},{0.10f,0.55f,0.10f},{0.55f,0.40f,0.22f});
    drawCube({85,0.75f,-102},{18,0.42f,11},{0.22f,0.28f,0.32f});
    drawCube({-91,0.72f,99},{18,0.36f,16},{0.40f,0.34f,0.27f});
    drawSphere({-58,0.02f,32},{18,0.22f,27},
        gSelectedMap==3?Vec3{0.72f,0.87f,0.94f}:
        (gSelectedMap==5?Vec3{0.08f,0.42f,0.30f}:Vec3{0.08f,0.36f,0.48f}));
    drawCylinder({53,4.0f,9},{0.28f,7.0f,0.28f},{0.42f,0.43f,0.42f});
    drawSphere({53,7.6f,9},{1.6f,0.5f,1.6f},{0.90f,0.58f,0.22f});
    // Map-specific landmarks make each destination visually readable in-world.
    if(gSelectedMap==3) {
        for(int i=0;i<5;++i)drawCone({-72.0f+i*12.0f,2.2f,58.0f},{4.0f,4.4f,4.0f},{0.78f,0.88f,0.96f});
        drawCube({74,1.2f,-64},{22,0.7f,14},{0.56f,0.70f,0.80f});
    } else if(gSelectedMap==4) {
        for(int i=0;i<4;++i) {
            float x=-78.0f+i*18.0f;
            drawCylinder({x,7.0f,-72},{1.2f,14.0f,1.2f},{0.23f,0.28f,0.30f});
            drawCylinder({x,14.3f,-72},{1.8f,0.45f,1.8f},{0.91f,0.38f,0.13f});
        }
        drawCube({78,1.1f,72},{24,0.6f,18},{0.25f,0.32f,0.35f});
    } else if(gSelectedMap==5) {
        for(int i=0;i<6;++i)drawSphere({-70.0f+i*25.0f,0.6f,72},{5,0.5f,7},{0.12f,0.45f,0.27f});
        drawCube({72,0.9f,-70},{20,0.35f,12},{0.20f,0.48f,0.30f});
    } else if(gSelectedMap==6) {
        for(int i=0;i<5;++i) {
            float x=-76.0f+i*36.0f;
            drawCylinder({x,5.0f,-76},{1.1f,10.0f,1.1f},{0.28f,0.43f,0.62f});
            drawSphere({x,10.8f,-76},{2.1f,0.6f,2.1f},{0.18f,0.78f,0.92f});
        }
        drawCube({76,1.1f,76},{22,0.6f,22},{0.16f,0.28f,0.48f});
    } else if(gSelectedMap==7) {
        for(int i=0;i<5;++i) {
            float x=-78.0f+i*38.0f;
            drawCube({x,1.1f,66},{12.0f,2.2f,3.0f},{0.32f,0.29f,0.24f});
            drawCube({x+3.0f,3.0f,66},{3.0f,1.6f,3.0f},{0.48f,0.39f,0.28f});
        }
        drawCube({72,0.9f,-72},{24.0f,1.8f,16.0f},{0.27f,0.24f,0.20f});
    }

    // Landmark sets give every destination a distinct silhouette and readable identity.
    // These are native low-poly meshes, not color-only variants or downloaded placeholders.
    if(gSelectedMap==0) { // Ilha Tropical: marina, stilt huts and coastal cliffs.
        drawCube({-112,0.62f,-24},{28,0.22f,9},{0.39f,0.25f,0.13f});
        for(int i=0;i<5;++i) {
            float x=-120.0f+i*4.0f;
            drawCylinder({x,1.8f,-24},{0.22f,2.5f,0.22f},{0.34f,0.22f,0.12f});
            drawCone({x,3.2f,-24},{3.6f,1.7f,3.2f},{0.43f,0.20f,0.12f});
        }
        for(int i=0;i<4;++i) {
            float z=-68.0f+i*38.0f;
            drawSphere({-103,1.0f,z},{8,2.0f,5},{0.34f,0.39f,0.25f});
        }
        drawCube({-112,0.65f,48},{18,0.24f,12},{0.48f,0.34f,0.18f});
    } else if(gSelectedMap==1) { // Neon City: skyline, elevated transit and luminous facades.
        for(int i=0;i<7;++i) {
            float x=-92.0f+i*29.0f, h=13.0f+static_cast<float>((i*7)%5)*2.2f;
            drawCube({x,h*0.5f,-104},{13,h,12},{0.10f,0.13f,0.25f});
            for(int band=0;band<4;++band)
                drawCube({x,h*(0.22f+band*0.18f),-97.8f},{10,0.22f,0.10f},
                    (i+band)%2?Vec3{0.08f,0.78f,0.91f}:Vec3{0.72f,0.20f,0.88f});
        }
        drawCube({0,5.2f,38},{150,0.65f,5},{0.16f,0.20f,0.30f});
        for(int i=0;i<8;++i)drawCylinder({-65.0f+i*18.5f,4.9f,38},{0.42f,7.5f,0.42f},{0.12f,0.65f,0.78f});
        drawCube({-92,1.1f,88},{26,2.2f,18},{0.15f,0.18f,0.29f});
    } else if(gSelectedMap==2) { // Desert Storm: mesas, sandstone arches and dry ravines.
        for(int i=0;i<6;++i) {
            float x=-112.0f+i*43.0f;
            drawSphere({x,2.2f,-106},{17,4.4f,12},{0.62f,0.37f,0.19f});
            drawCube({x,4.0f,-106},{8,4.0f,5},{0.69f,0.43f,0.23f});
        }
        for(int i=0;i<4;++i) {
            float x=-84.0f+i*54.0f;
            drawCube({x,3.0f,82},{3,6,3},{0.52f,0.31f,0.18f});
            drawCube({x+5,3.0f,82},{3,6,3},{0.52f,0.31f,0.18f});
            drawCube({x+2.5f,6.0f,82},{8,1.2f,3},{0.66f,0.40f,0.22f});
        }
        drawSphere({82,0.9f,-42},{28,1.8f,18},{0.74f,0.48f,0.27f});
    } else if(gSelectedMap==3) { // Frozen Front: ice shelves, glacier spires and polar station.
        for(int i=0;i<8;++i) {
            float x=-112.0f+i*31.0f;
            drawCone({x,5.0f,-104.0f+(i%2)*13.0f},{8,10,7},{0.72f,0.88f,0.96f});
        }
        drawSphere({-84,1.0f,70},{38,2.0f,23},{0.48f,0.70f,0.82f});
        drawCube({76,2.2f,74},{28,4.4f,20},{0.54f,0.67f,0.75f});
        drawSphere({76,5.1f,74},{13,5.8f,13},{0.75f,0.88f,0.94f});
        for(int i=0;i<3;++i)drawCylinder({64+i*12.0f,1.2f,86},{0.5f,2.4f,0.5f},{0.24f,0.42f,0.53f});
    } else if(gSelectedMap==4) { // Industrial Zone: storage tanks, pipe racks and gantry.
        for(int i=0;i<5;++i) {
            float x=-104.0f+i*43.0f;
            drawCylinder({x,4.0f,88},{10,8,10},{0.38f,0.43f,0.44f});
            drawCylinder({x,8.1f,88},{10.2f,0.35f,10.2f},{0.72f,0.38f,0.17f});
        }
        for(int i=0;i<6;++i) {
            float x=-90.0f+i*34.0f;
            drawCylinder({x,3.0f,-98},{0.65f,6.0f,0.65f},{0.31f,0.37f,0.39f});
            drawCube({x,6.2f,-98},{2.0f,0.35f,2.0f},{0.85f,0.42f,0.17f});
        }
        drawCube({0,5.5f,0},{58,0.8f,4},{0.30f,0.35f,0.37f});
        for(int i=0;i<5;++i)drawCube({-25+i*12.5f,3.0f,0},{0.7f,6,0.7f},{0.85f,0.40f,0.15f});
    } else if(gSelectedMap==5) { // Green Valley: river, bridges and rolling hill groups.
        drawSphere({-70,0.22f,-12},{16,0.18f,115},{0.08f,0.37f,0.43f});
        drawSphere({-70,0.28f,-12},{12,0.10f,110},{0.18f,0.56f,0.57f});
        for(int i=0;i<4;++i) {
            float z=-82.0f+i*52.0f;
            drawCube({-70,1.0f,z},{24,0.55f,7},{0.42f,0.34f,0.22f});
        }
        for(int i=0;i<7;++i) {
            float x=-112.0f+i*36.0f;
            drawSphere({x,1.2f,104},{22,2.4f,15},{0.16f,0.39f,0.20f});
        }
        drawCube({78,0.85f,-74},{34,0.35f,18},{0.24f,0.48f,0.27f});
    } else if(gSelectedMap==6) { // Orbital Base: launch pads, radar dishes and modular hangars.
        for(int i=0;i<4;++i) {
            float x=-96.0f+i*62.0f;
            drawCylinder({x,0.85f,82},{22,0.45f,22},{0.20f,0.29f,0.43f});
            drawCylinder({x,1.25f,82},{15,0.35f,15},{0.36f,0.49f,0.62f});
            drawCylinder({x,5.0f,82},{1.0f,7.5f,1.0f},{0.25f,0.63f,0.78f});
            drawSphere({x,9.0f,82},{4,1.0f,4},{0.16f,0.78f,0.90f});
        }
        for(int i=0;i<3;++i) {
            float x=-72.0f+i*72.0f;
            drawCube({x,2.5f,-88},{24,5,18},{0.15f,0.24f,0.39f});
            drawCube({x,5.2f,-78.8f},{16,0.25f,0.15f},{0.10f,0.82f,0.94f});
        }
        drawSphere({76,1.1f,-22},{18,2.2f,18},{0.18f,0.35f,0.52f});
    } else { // Abandoned Sector: collapsed walls, broken towers and debris fields.
        for(int i=0;i<7;++i) {
            float x=-112.0f+i*36.0f;
            drawCube({x,1.5f,94},{19,3.0f,2.0f},{0.34f,0.30f,0.25f},0.08f*i);
            drawCube({x+4.0f,4.2f,94},{2.0f,5.4f,2.0f},{0.43f,0.36f,0.27f});
            drawCube({x-5.0f,0.9f,88},{7,1.8f,7},{0.27f,0.25f,0.22f},0.3f*i);
        }
        for(int i=0;i<5;++i) {
            float x=-96.0f+i*47.0f;
            drawCylinder({x,4.5f,-98},{2.0f,9.0f,2.0f},{0.31f,0.29f,0.25f});
            drawCube({x+2.0f,8.2f,-98},{5.0f,1.0f,5.0f},{0.42f,0.35f,0.27f},0.2f*i);
        }
        drawSphere({76,0.8f,-28},{26,1.6f,22},{0.30f,0.27f,0.22f});
    }
}
Vec3 localPoint(Vec3 base,float lx,float ly,float lz,float yaw) {
    float c=std::cos(yaw), sn=std::sin(yaw);
    return {base.x+c*lx+sn*lz,base.y+ly*gCharacterPoseScale,base.z-sn*lx+c*lz};
}
void drawSegment(Vec3 base,float yaw,Vec3 a,Vec3 b,float radius,Vec3 color) {
    float dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z;
    float len=std::sqrt(dx*dx+dy*dy+dz*dz);
    if(len<0.001f)return;
    Vec3 mid{(a.x+b.x)*0.5f,(a.y+b.y)*0.5f,(a.z+b.z)*0.5f};
    float tiltZ=std::atan2(-dx,std::max(0.001f,dy));
    float tiltX=std::atan2(dz,std::max(0.001f,std::sqrt(dx*dx+dy*dy)));
    drawSphere(localPoint(base,mid.x,mid.y,mid.z,yaw),
        {radius*2.0f,len+radius*0.8f,radius*2.0f},color,yaw,tiltZ,tiltX);
}
void drawCharacter(Vec3 p,float yaw,Vec3 color,bool bot,float time,float movement=0.0f,
                   bool aiming=false,bool firing=false,bool sprinting=false) {
    if(!bot&&gWeaponType==2)gCrashTraceStage.store(80,std::memory_order_relaxed);
    gCharacterPoseScale=(!bot&&(gCrouched||gPlayerDowned))?0.62f:1.0f;
    if(!bot&&gPlayerDowned) {
        drawCube({p.x,p.y+0.30f,p.z},{0.92f,0.34f,0.62f},color,yaw,0.0f,0.0f);
        drawSphere({p.x,p.y+0.43f,p.z-0.43f},{0.28f,0.28f,0.28f},{0.88f,0.70f,0.55f});
        drawCube({p.x+0.40f,p.y+0.16f,p.z+0.12f},{0.48f,0.18f,0.20f},color,yaw);
        gCharacterPoseScale=1.0f;
        return;
    }
    // Operador humanoide procedural: proporções de cabeça, pescoço, tórax,
    // cintura, membros articulados e botas. A malha ainda é geométrica;
    // modelos escaneados/GLB e rig profissional continuam sendo uma etapa de assets.
    const bool female=!bot&&(gSelectedCharacter==1||gSelectedCharacter==3);
    const float gait=std::clamp(movement,0.0f,1.0f);
    const float phase=time*(sprinting?10.8f:8.2f)+(bot?0.7f:0.0f);
    const float swing=std::sin(phase)*(sprinting?0.46f:0.34f)*gait;
    const float bob=std::sin(phase*2.0f)*0.035f*gait+std::sin(time*1.5f)*0.014f;
    const float recoil=firing?0.105f:0.0f;
    Vec3 skin{0.72f,0.49f,0.35f};
    if(!bot) {
        static const std::array<Vec3,4> skinTones{{
            {0.72f,0.49f,0.35f},{0.82f,0.62f,0.48f},
            {0.48f,0.30f,0.21f},{0.90f,0.70f,0.55f}
        }};
        skin=skinTones[static_cast<size_t>(std::clamp(gSelectedCharacter,0,3))];
    } else {
        skin={0.70f,0.48f,0.34f};
    }
    Vec3 shirt=color;
    Vec3 pants{0.075f,0.105f,0.13f};
    Vec3 boots{0.055f,0.065f,0.075f};
    int shirtIndex=0,bootIndex=0,setIndex=0;
    if(!bot) {
        static const std::array<Vec3,5> shirts{{
            {0.035f,0.34f,0.43f},{0.10f,0.20f,0.28f},{0.35f,0.12f,0.48f},
            {0.70f,0.74f,0.78f},{0.62f,0.30f,0.12f}
        }};
        static const std::array<Vec3,5> trousers{{
            {0.055f,0.095f,0.13f},{0.16f,0.20f,0.22f},{0.18f,0.12f,0.28f},
            {0.30f,0.36f,0.40f},{0.30f,0.20f,0.10f}
        }};
        static const std::array<Vec3,5> bootStyles{{
            {0.16f,0.19f,0.21f},{0.12f,0.16f,0.20f},{0.18f,0.52f,0.58f},
            {0.72f,0.76f,0.80f},{0.72f,0.48f,0.16f}
        }};
        shirtIndex=std::clamp(gSelectedShirtStyle,0,4);
        bootIndex=std::clamp(gSelectedBootStyle,0,4);
        setIndex=std::clamp(gSelectedSetStyle,0,4);
        if(setIndex>0) { shirtIndex=setIndex; bootIndex=setIndex; }
        shirt=shirts[static_cast<size_t>(shirtIndex)];
        pants=trousers[static_cast<size_t>(shirtIndex)];
        boots=bootStyles[static_cast<size_t>(bootIndex)];
    }
    const Vec3 dark{0.035f,0.045f,0.055f};
    const Vec3 hair=female?Vec3{0.075f,0.045f,0.035f}:Vec3{0.045f,0.055f,0.065f};
    const float legWidth=female?0.105f:0.12f;
    const float shoulder=female?0.34f:0.40f;
    const float torsoWidth=female?0.31f:0.36f;

    // Pernas com coxas, joelhos, canelas e botas.
    Vec3 hipL{-legWidth,0.88f+bob,swing*0.30f};
    Vec3 kneeL{-legWidth,0.48f+bob,swing*0.70f};
    Vec3 footL{-legWidth,0.17f+bob,swing*0.88f};
    Vec3 hipR{legWidth,0.88f+bob,-swing*0.30f};
    Vec3 kneeR{legWidth,0.48f+bob,-swing*0.70f};
    Vec3 footR{legWidth,0.17f+bob,-swing*0.88f};
    drawSegment(p,yaw,hipL,kneeL,0.115f,pants);
    drawSegment(p,yaw,kneeL,footL,0.088f,pants);
    drawSegment(p,yaw,hipR,kneeR,0.115f,pants);
    drawSegment(p,yaw,kneeR,footR,0.088f,pants);
    drawSphere(localPoint(p,-legWidth,0.12f+bob,-0.15f+swing*0.88f,yaw),
        {0.17f,0.11f,0.29f},boots,yaw);
    drawSphere(localPoint(p,legWidth,0.12f+bob,-0.15f-swing*0.88f,yaw),
        {0.17f,0.11f,0.29f},boots,yaw);
    if(!bot&&shirtIndex>0) {
        static const std::array<Vec3,5> accents{{
            {0.10f,0.72f,0.82f},{0.28f,0.92f,0.98f},{0.90f,0.35f,0.92f},
            {0.32f,0.70f,0.98f},{0.95f,0.67f,0.28f}
        }};
        const Vec3 trim=accents[static_cast<size_t>(shirtIndex)];
        drawCube(localPoint(p,-legWidth,0.49f+bob,-0.105f,yaw),{0.105f,0.12f,0.045f},trim,yaw);
        drawCube(localPoint(p,legWidth,0.49f+bob,-0.105f,yaw),{0.105f,0.12f,0.045f},trim,yaw);
        drawCube(localPoint(p,-legWidth,0.25f+bob,-0.09f,yaw),{0.085f,0.045f,0.035f},trim,yaw);
        drawCube(localPoint(p,legWidth,0.25f+bob,-0.09f,yaw),{0.085f,0.045f,0.035f},trim,yaw);
    }

    // Pelve, abdômen, tórax e colete em camadas.
    drawSphere(localPoint(p,0,0.91f+bob,0,yaw),{0.31f,0.22f,0.20f},pants,yaw);
    drawSphere(localPoint(p,0,1.13f+bob,0,yaw),{torsoWidth*0.82f,0.27f,0.19f},shirt,yaw);
    drawSphere(localPoint(p,0,1.39f+bob,0,yaw),{shoulder,0.34f,0.22f},shirt,yaw);
    drawSphere(localPoint(p,0,1.40f+bob,-0.035f,yaw),{torsoWidth,0.28f,0.225f},
        {std::min(1.0f,shirt.x*1.12f),std::min(1.0f,shirt.y*1.12f),std::min(1.0f,shirt.z*1.12f)},yaw);
    drawCube(localPoint(p,0,1.36f+bob,-0.222f,yaw),{female?0.20f:0.23f,0.22f,0.025f},
        {0.12f,0.17f,0.20f},yaw);
    drawCube(localPoint(p,0,1.13f+bob,-0.195f,yaw),{0.16f,0.10f,0.025f},
        {0.16f,0.22f,0.24f},yaw);
    if(!bot&&shirtIndex>0) {
        static const std::array<Vec3,5> accents{{
            {0.10f,0.72f,0.82f},{0.28f,0.92f,0.98f},{0.90f,0.35f,0.92f},
            {0.32f,0.70f,0.98f},{0.95f,0.67f,0.28f}
        }};
        const Vec3 trim=accents[static_cast<size_t>(shirtIndex)];
        const Vec3 darkTrim{0.035f,0.055f,0.075f};
        drawCube(localPoint(p,0,1.43f+bob,-0.247f,yaw),{0.19f,0.19f,0.035f},trim,yaw);
        drawCube(localPoint(p,0,1.12f+bob,-0.222f,yaw),{0.28f,0.075f,0.035f},darkTrim,yaw);
        drawCube(localPoint(p,-0.275f,1.43f+bob,-0.065f,yaw),{0.12f,0.14f,0.13f},trim,yaw);
        drawCube(localPoint(p,0.275f,1.43f+bob,-0.065f,yaw),{0.12f,0.14f,0.13f},trim,yaw);
        drawCube(localPoint(p,-0.36f,1.18f+bob,-0.045f,yaw),{0.09f,0.16f,0.10f},darkTrim,yaw);
        drawCube(localPoint(p,0.36f,1.18f+bob,-0.045f,yaw),{0.09f,0.16f,0.10f},darkTrim,yaw);
        drawCube(localPoint(p,0,1.19f+bob,0.235f,yaw),{0.25f,0.32f,0.15f},darkTrim,yaw);
        drawCube(localPoint(p,-0.22f,1.38f+bob,0.10f,yaw),{0.055f,0.24f,0.055f},trim,yaw);
        drawCube(localPoint(p,0.22f,1.38f+bob,0.10f,yaw),{0.055f,0.24f,0.055f},trim,yaw);
        drawCube(localPoint(p,-legWidth,0.12f+bob,-0.34f+swing*0.88f,yaw),{0.13f,0.055f,0.045f},trim,yaw);
        drawCube(localPoint(p,legWidth,0.12f+bob,-0.34f-swing*0.88f,yaw),{0.13f,0.055f,0.045f},trim,yaw);
    }

    // Pescoço, cabeça, orelhas, nariz, olhos, sobrancelhas e cabelo.
    drawCylinder(localPoint(p,0,1.68f+bob,0,yaw),{0.12f,0.16f,0.12f},skin,yaw);
    drawSphere(localPoint(p,0,1.91f+bob,0,yaw),
        {female?0.225f:0.235f,0.285f,0.205f},skin,yaw);
    drawSphere(localPoint(p,0,2.075f+bob,0.005f,yaw),
        {0.235f,0.145f,0.215f},hair,yaw);
    drawSphere(localPoint(p,0,1.99f+bob,0.115f,yaw),
        {0.235f,0.16f,0.12f},hair,yaw);
    drawSphere(localPoint(p,-0.235f,1.91f+bob,0,yaw),{0.045f,0.075f,0.055f},skin,yaw);
    drawSphere(localPoint(p,0.235f,1.91f+bob,0,yaw),{0.045f,0.075f,0.055f},skin,yaw);
    if(female) {
        drawSphere(localPoint(p,-0.18f,1.82f+bob,0.04f,yaw),{0.09f,0.22f,0.10f},hair,yaw);
        drawSphere(localPoint(p,0.18f,1.82f+bob,0.04f,yaw),{0.09f,0.22f,0.10f},hair,yaw);
    }
    if(!bot&&setIndex>0) {
        static const std::array<Vec3,5> accents{{
            {0.10f,0.72f,0.82f},{0.28f,0.92f,0.98f},{0.90f,0.35f,0.92f},
            {0.32f,0.70f,0.98f},{0.95f,0.67f,0.28f}
        }};
        const Vec3 trim=accents[static_cast<size_t>(setIndex)];
        drawSphere(localPoint(p,0,2.16f+bob,0.01f,yaw),{0.255f,0.075f,0.23f},trim,yaw);
        drawCube(localPoint(p,0,2.11f+bob,-0.17f,yaw),{0.23f,0.035f,0.12f},trim,yaw);
    }
    drawSphere(localPoint(p,-0.082f,1.94f+bob,-0.184f,yaw),{0.038f,0.027f,0.025f},{0.95f,0.96f,0.92f},yaw);
    drawSphere(localPoint(p,0.082f,1.94f+bob,-0.184f,yaw),{0.038f,0.027f,0.025f},{0.95f,0.96f,0.92f},yaw);
    drawSphere(localPoint(p,-0.082f,1.94f+bob,-0.207f,yaw),{0.017f,0.020f,0.014f},dark,yaw);
    drawSphere(localPoint(p,0.082f,1.94f+bob,-0.207f,yaw),{0.017f,0.020f,0.014f},dark,yaw);
    drawSphere(localPoint(p,0,1.88f+bob,-0.205f,yaw),{0.035f,0.045f,0.035f},skin,yaw);
    drawCube(localPoint(p,0,1.835f+bob,-0.19f,yaw),{0.045f,0.009f,0.012f},{0.30f,0.10f,0.10f},yaw);

    // Braços articulados e mãos.
    Vec3 shoulderL{-shoulder,1.48f+bob,0.0f}, shoulderR{shoulder,1.48f+bob,0.0f};
    Vec3 elbowL,handL,elbowR,handR;
    if(aiming) {
        elbowL={-0.30f,1.34f+bob,-0.25f}; handL={-0.15f,1.31f+bob,-0.57f};
        elbowR={0.30f,1.36f+bob,-0.24f}; handR={0.15f,1.31f+bob,-0.58f};
    } else {
        elbowL={-shoulder-0.08f,1.14f+bob,-swing*0.55f};
        handL={-shoulder-0.05f,0.91f+bob,-swing*0.78f};
        elbowR={shoulder+0.08f,1.14f+bob,swing*0.55f};
        handR={shoulder+0.05f,0.91f+bob,swing*0.78f};
    }
    drawSegment(p,yaw,shoulderL,elbowL,0.095f,shirt);
    drawSegment(p,yaw,elbowL,handL,0.075f,shirt);
    drawSegment(p,yaw,shoulderR,elbowR,0.095f,shirt);
    drawSegment(p,yaw,elbowR,handR,0.075f,shirt);
    drawSphere(localPoint(p,handL.x,handL.y,handL.z,yaw),{0.075f,0.09f,0.07f},skin,yaw);
    drawSphere(localPoint(p,handR.x,handR.y,handR.z,yaw),{0.075f,0.09f,0.07f},skin,yaw);

    if(bot) {
        drawCube(localPoint(p,0,1.31f+bob,-0.79f+recoil,yaw),
            {0.16f,0.13f,0.42f},{0.045f,0.075f,0.085f},yaw);
        drawCube(localPoint(p,0,1.40f+bob,-0.77f+recoil,yaw),
            {0.10f,0.045f,0.23f},{0.18f,0.30f,0.30f},yaw);
        drawCube(localPoint(p,0,1.31f+bob,-1.03f+recoil,yaw),
            {0.07f,0.075f,0.22f},{0.12f,0.20f,0.22f},yaw);
    } else if(gWeaponType>=0&&gWeaponType<4) {
        const Vec3 receiver{0.075f,0.075f,0.28f};
        const Vec3 darkMetal{0.055f,0.075f,0.085f};
        const Vec3 accent=gWeaponType==2?Vec3{0.70f,0.72f,0.74f}:
            (gWeaponType==3?Vec3{0.16f,0.18f,0.20f}:Vec3{0.12f,0.22f,0.25f});
        drawCube(localPoint(p,0,1.31f+bob,-0.79f+recoil,yaw),receiver,darkMetal,yaw);
        if(gWeaponType==0) { // XM8: corpo e coronha alongados, trilho superior.
            drawCube(localPoint(p,0,1.31f+bob,-0.53f+recoil,yaw),{0.12f,0.09f,0.34f},accent,yaw);
            drawCube(localPoint(p,0,1.40f+bob,-0.79f+recoil,yaw),{0.08f,0.035f,0.34f},{0.24f,0.31f,0.33f},yaw);
            drawCube(localPoint(p,0,1.28f+bob,-1.13f+recoil,yaw),{0.055f,0.055f,0.48f},darkMetal,yaw);
        } else if(gWeaponType==1) { // UMP: corpo compacto e carregador curvo estilizado.
            drawCube(localPoint(p,0,1.31f+bob,-0.98f+recoil,yaw),{0.10f,0.08f,0.32f},accent,yaw);
            drawCube(localPoint(p,0,1.17f+bob,-0.78f+recoil,yaw),{0.075f,0.22f,0.13f},darkMetal,yaw);
            drawCube(localPoint(p,0,1.38f+bob,-0.78f+recoil,yaw),{0.075f,0.035f,0.26f},{0.30f,0.34f,0.36f},yaw);
        } else if(gWeaponType==2) { // Carapina: cano longo e mira óptica.
            gCrashTraceStage.store(81,std::memory_order_relaxed);
            drawCube(localPoint(p,0,1.30f+bob,-1.16f+recoil,yaw),{0.055f,0.055f,0.72f},accent,yaw);
            drawCube(localPoint(p,0,1.43f+bob,-0.77f+recoil,yaw),{0.09f,0.10f,0.22f},darkMetal,yaw);
            drawCylinder(localPoint(p,0,1.49f+bob,-0.83f+recoil,yaw),{0.10f,0.12f,0.22f},{0.16f,0.22f,0.27f},yaw);
            gCrashTraceStage.store(82,std::memory_order_relaxed);
        } else { // Mini Uzi: formato curto e carregador frontal.
            drawCube(localPoint(p,0,1.31f+bob,-0.99f+recoil,yaw),{0.12f,0.09f,0.24f},accent,yaw);
            drawCube(localPoint(p,0,1.18f+bob,-0.91f+recoil,yaw),{0.075f,0.22f,0.10f},darkMetal,yaw);
            drawCube(localPoint(p,0,1.31f+bob,-1.17f+recoil,yaw),{0.045f,0.045f,0.25f},darkMetal,yaw);
        }
        if(firing) drawSphere(localPoint(p,0,1.31f+bob,-1.34f+recoil,yaw),
            {0.16f,0.12f,0.16f},{1.0f,0.72f,0.28f},yaw);
    }
    gCharacterPoseScale=1.0f;
    if(!bot&&gWeaponType==2)gCrashTraceStage.store(83,std::memory_order_relaxed);
}
void drawCrystal(Vec3 p,float time,int index) {
    float bob=std::sin(time*2.2f+index)*0.16f, spin=time*0.55f+index*0.7f;
    drawCone({p.x,p.y+0.58f+bob,p.z},{0.42f,0.86f,0.42f},{0.12f,0.82f,0.96f},spin);
    drawCone({p.x,p.y+0.96f+bob,p.z},{0.24f,0.48f,0.24f},{0.45f,0.98f,1.0f},spin+PI);
    drawSphere({p.x,p.y+0.58f+bob,p.z},{0.48f,0.08f,0.48f},{0.08f,0.55f,0.68f});
}
void resetGroundLoot() {
    gGroundLoot={{
        {{-3.0f,0.55f,-2.0f},0,30,true,false},
        {{3.0f,0.55f,-2.0f},1,24,true,false},
        {{-3.0f,0.55f,3.0f},2,18,true,false},
        {{3.0f,0.55f,3.0f},3,240,true,false},
        {{64.0f,0.55f,69.0f},1,24,true,false},
        {{-91.0f,0.55f,79.0f},2,18,true,false},
        {{-72.0f,0.55f,32.0f},0,20,true,false},
        {{91.0f,0.55f,-82.0f},1,20,true,false},
        {{-128.0f,0.55f,-116.0f},2,22,true,false},
        {{-84.0f,0.55f,-126.0f},0,24,true,false},
        {{126.0f,0.55f,-106.0f},1,22,true,false},
        {{130.0f,0.55f,61.0f},2,24,true,false},
        {{-104.0f,0.55f,118.0f},0,36,true,true},
        {{30.0f,0.55f,-116.0f},1,36,true,true},
        {{112.0f,0.55f,110.0f},2,36,true,true},
        {{-126.0f,0.55f,62.0f},0,36,true,true},
        {{6.0f,0.55f,132.0f},1,30,true,true},
        {{-44.0f,0.55f,128.0f},2,30,true,true}
    }};
}
void drawGroundLoot(float time) {
    for(size_t i=0;i<gGroundLoot.size();++i) {
        const GroundLoot& loot=gGroundLoot[i];if(!loot.active)continue;
        const float bob=std::sin(time*2.6f+static_cast<float>(i))*0.12f;
        const Vec3 color=loot.ammoOnly?Vec3{0.92f,0.68f,0.20f}:
            (loot.weaponType==0?Vec3{0.12f,0.86f,0.94f}:
             (loot.weaponType==1?Vec3{0.28f,0.58f,1.0f}:(loot.weaponType==2?Vec3{0.94f,0.48f,0.20f}:Vec3{0.20f,0.92f,0.56f})));
        drawCube({loot.pos.x,loot.pos.y+0.34f+bob,loot.pos.z},{0.82f,0.52f,0.62f},color);
        drawCube({loot.pos.x,loot.pos.y+0.63f+bob,loot.pos.z},{0.54f,0.12f,0.66f},{0.12f,0.17f,0.22f});
        if(!loot.ammoOnly) {
            drawCube({loot.pos.x,loot.pos.y+0.76f+bob,loot.pos.z-0.36f},{0.16f,0.13f,0.56f},{0.17f,0.20f,0.24f});
            drawCube({loot.pos.x,loot.pos.y+0.48f+bob,loot.pos.z+0.25f},{0.20f,0.25f,0.18f},{0.78f,0.83f,0.86f});
        } else {
            drawCube({loot.pos.x,loot.pos.y+0.72f+bob,loot.pos.z},{0.20f,0.12f,0.40f},{0.96f,0.88f,0.62f});
        }
    }
}
void drawSafeZone() {
    constexpr int segments=52;
    for (int i=0;i<segments;++i) {
        float a=(2*PI*i)/segments;
        drawCube({std::cos(a)*gZoneRadius,0.78f,std::sin(a)*gZoneRadius},
                 {0.62f,0.18f,0.18f},{0.13f,0.77f,0.92f},-a);
    }
}
const char* weaponName() {
    static const char* names[]={"XM8","UMP","CARAPINA","MINI UZI"};
    if(gWeaponType<0||gWeaponType>3)return "SEM ARMA";
    return names[gWeaponType];
}
void updateStatus() {
    std::ostringstream out;
    if(gSpectatorMode) {
        out<<"MODO ESPECTADOR  •  ACOMPANHANDO SOBREVIVENTES";
        setStatus(out.str());return;
    }
    if(gPlayerDowned) {
        out<<"DERRUBADO • AGUARDANDO REVIVER • "<<static_cast<int>(gDownedTimer)<<"s";
        setStatus(out.str());return;
    }
    if(gBuildPreview) {
        const char* buildNames[]={"GELO","PLATAFORMA","RAMPA"};
        out<<"PRÉVIA DE "<<buildNames[std::clamp(gBuildType,0,2)]<<" • TOQUE CONSTRUIR NOVAMENTE PARA CONFIRMAR";
        setStatus(out.str());return;
    }
    if(gDropState==1) {
        if(gDropTimer<PLANE_COUNTDOWN_SECONDS) {
            int remaining=static_cast<int>(std::ceil(PLANE_COUNTDOWN_SECONDS-gDropTimer));
            out<<"EMBARQUE NA AERONAVE  •  RAMPA FECHADA  •  "<<remaining<<"s";
        } else if(gPlaneWalkX<=-12.8f && std::fabs(gPlaneWalkZ)<=4.2f) {
            out<<"AVIÃO EM ROTA  •  RAMPA PRONTA  •  TOQUE SALTAR";
        } else {
            out<<"AVIÃO EM ROTA  •  CORREDOR LIVRE  •  ANDE ATÉ A RAMPA";
        }
    } else if(gDropState==2) {
        out<<(gParachuteOpen?"PARAQUEDAS ABERTO":"QUEDA LIVRE")
           <<"  •  MIRA E CÂMERA LIVRES  •  "<<weaponName();
    } else if(gSelectedMode==3) {
        out<<"TREINAMENTO  •  "<<weaponName()<<"  CARGA "
           <<(gSelectedMode==3?"INFINITA":(gWeaponType<0?"0":std::to_string(gWeaponAmmo[static_cast<size_t>(gWeaponType)])))
           <<"  •  MADEIRA "<<gWood<<"  •  PONTOS "<<gTrainingScore;
    } else {
        int remaining=0;
        for(int i=0;i<BOT_COUNT;++i) if(!isAllyBot(i)&&gBotActive[static_cast<size_t>(i)]) ++remaining;
        out<<"VIDA "<<gPlayerHealth<<"%  •  ELIMINAÇÕES "<<gEliminations
           <<"  •  OPONENTES "<<remaining<<"  •  "<<weaponName()<<" "
           <<(gWeaponType<0?0:gWeaponAmmo[static_cast<size_t>(gWeaponType)]);
        if(gMatchFinished) out<<(gPlayerHealth<=0?"  •  ELIMINADO":"  •  VITÓRIA");
        else out<<"  •  MADEIRA "<<gWood;
    }
    setStatus(out.str());
}
void resetBots() {
    // 17 posições extras completam os 29 bots espalhados pelo mapa.
    for(int i=12;i<BOT_COUNT;++i) {
        const int slot=i-12;
        const float angle=2.0f*PI*static_cast<float>(slot)/17.0f;
        const float radius=68.0f+static_cast<float>(slot%4)*16.0f;
        gBotHomes[static_cast<size_t>(i)]={std::cos(angle)*radius,0.55f,
            std::sin(angle)*radius};
    }
    const std::array<Vec3,3> allyOffsets{{{-8,0.55f,-7},{8,0.55f,-8},{0,0.55f,9}}};
    gBotDropHeight.fill(0.0f);gBotDropSpeed.fill(0.0f);gBotDropCanopy.fill(false);
    gBotHealth.fill(BOT_MAX_HEALTH);
    gBotDamageAmount.fill(0);
    gBotDamageTimer.fill(0.0f);
    gBotActive.fill(true);
    gBotDowned.fill(false);gBotDownedTimer.fill(0.0f);gBotReviveProgress.fill(0.0f);
    gBotRespawn.fill(0.0f);
    for(int i=0;i<BOT_COUNT;++i) {
        if(isAllyBot(i)) {
            const Vec3 offset=allyOffsets[static_cast<size_t>(i%3)];
            gBotHomes[static_cast<size_t>(i)]={
                std::clamp(gPlayer.x+offset.x,-112.0f,112.0f),0.55f,
                std::clamp(gPlayer.z+offset.z,-112.0f,112.0f)};
        }
        gBots[static_cast<size_t>(i)]={gBotHomes[static_cast<size_t>(i)],0,0,static_cast<float>(i)*1.3f};
        gBotAttackCooldown[static_cast<size_t>(i)]=0.45f+static_cast<float>(i%5)*0.16f;
    }
}
void applyPlayerDamage(int amount,const char* reason);
void updateBots(float dt) {
    if(gMatchFinished) return;
    // Limita a pressão simultânea no jogador: o restante da IA prioriza combates entre bots.
    int botsPressuringPlayer=0;
    for(int i=0;i<BOT_COUNT;++i) {
        BotState& bot=gBots[static_cast<size_t>(i)];
        if(gBotDropHeight[static_cast<size_t>(i)]>0.0f) {
            const float phase=bot.phase+gMatchTime*0.28f;
            bot.pos.x=std::clamp(gBotHomes[static_cast<size_t>(i)].x+std::sin(phase)*2.8f,-WORLD_LIMIT+3.0f,WORLD_LIMIT-3.0f);
            bot.pos.z=std::clamp(gBotHomes[static_cast<size_t>(i)].z+std::cos(phase)*2.8f,-WORLD_LIMIT+3.0f,WORLD_LIMIT-3.0f);
            gBotDropHeight[static_cast<size_t>(i)]=std::max(0.0f,
                gBotDropHeight[static_cast<size_t>(i)]-gBotDropSpeed[static_cast<size_t>(i)]*dt);
            if(gBotDropHeight[static_cast<size_t>(i)]<=0.0f)gBotDropCanopy[static_cast<size_t>(i)]=false;
            continue;
        }
        bot.fireFlash=std::max(0.0f,bot.fireFlash-dt);
        bot.aiming=false;
        bot.targetIndex=-1;
        bot.targetPlayer=false;

        if(!gBotActive[static_cast<size_t>(i)]) {
            if(gSelectedMode==3) {
                gBotRespawn[static_cast<size_t>(i)]=std::max(0.0f,gBotRespawn[static_cast<size_t>(i)]-dt);
                if(gBotRespawn[static_cast<size_t>(i)]<=0.0f) {
                    bot.pos=gBotHomes[static_cast<size_t>(i)];
                    bot.speed=0;gBotHealth[static_cast<size_t>(i)]=BOT_MAX_HEALTH;
                    gBotActive[static_cast<size_t>(i)]=true;
                } else continue;
            } else continue;
        }

        const bool ally=isAllyBot(i);
        if(gBotDowned[static_cast<size_t>(i)]) {
            float nearest=std::numeric_limits<float>::max();
            Vec3 rescuePoint=bot.pos;
            // Um aliado derrubado também rasteja na direção do jogador vivo.
            if(isAllyBot(i)&&!gPlayerDowned) {
                const float px=gPlayer.x-bot.pos.x,pz=gPlayer.z-bot.pos.z;
                nearest=std::sqrt(px*px+pz*pz);
                rescuePoint=gPlayer;
            }
            for(int mate=0;mate<BOT_COUNT;++mate) {
                if(mate==i||!gBotActive[static_cast<size_t>(mate)]||gBotDowned[static_cast<size_t>(mate)]
                        ||!sameBotTeam(i,mate)) continue;
                const Vec3 p=gBots[static_cast<size_t>(mate)].pos;
                const float dx=p.x-bot.pos.x,dz=p.z-bot.pos.z;
                const float d=std::sqrt(dx*dx+dz*dz);
                if(d<nearest){nearest=d;rescuePoint=p;}
            }
            bot.speed=0.0f;
            if(nearest>=70.0f) {
                const float drift=std::sin(gMatchTime*0.8f+bot.phase)*0.35f;
                moveWithCollision(bot.pos,bot.pos.x+drift*dt,bot.pos.z+0.18f*dt,0.45f);
            }
            if(nearest>2.4f&&nearest<140.0f) {
                const float dx=rescuePoint.x-bot.pos.x,dz=rescuePoint.z-bot.pos.z;
                const float inv=1.0f/std::max(0.001f,nearest);
                bot.yaw=std::atan2(-dx,-dz);
                bot.speed=0.85f;
                const float step=std::min(nearest-2.0f,bot.speed*dt);
                moveWithCollision(bot.pos,bot.pos.x+dx*inv*step,bot.pos.z+dz*inv*step,0.45f);
            }
            continue;
        }
        const float toPlayerX=gPlayer.x-bot.pos.x;
        const float toPlayerZ=gPlayer.z-bot.pos.z;
        const float playerDistance=std::sqrt(toPlayerX*toPlayerX+toPlayerZ*toPlayerZ);
        Vec3 target=bot.pos;
        bool engaging=false;
        bool targetPlayer=false;
        bool rescuing=false;
        int targetBot=-1;
        float targetDistance=std::numeric_limits<float>::max();
        if(gDropState==3&&(gSelectedMode==1||gSelectedMode==2)) {
            float rescueDistance=70.0f;
            for(int mate=0;mate<BOT_COUNT;++mate) {
                if(mate==i||!gBotActive[static_cast<size_t>(mate)]||!gBotDowned[static_cast<size_t>(mate)]
                        ||!sameBotTeam(i,mate)) continue;
                const Vec3 p=gBots[static_cast<size_t>(mate)].pos;
                const float dx=p.x-bot.pos.x,dz=p.z-bot.pos.z;
                const float d=std::sqrt(dx*dx+dz*dz);
                if(d<rescueDistance){rescueDistance=d;target=p;rescuing=true;}
            }
        }

        // Os bots procuram primeiro outros bots hostis para manter batalhas pelo mapa.
        // O jogador só entra na seleção a curta distância e no máximo dois bots o pressionam
        // ao mesmo tempo. Em equipes, os aliados nunca escolhem o próprio jogador como alvo.
        if(!rescuing && gDropState==3 && gSelectedMode!=3 && !(ally&&gPlayerDowned)) {
            for(int other=0;other<BOT_COUNT;++other) {
                if(other==i||!gBotActive[static_cast<size_t>(other)]||gBotDowned[static_cast<size_t>(other)])continue;
                if(!FRIENDLY_FIRE_ENABLED&&sameBotTeam(i,other))continue;
                if(gSelectedMode==0 && isAllyBot(other)==ally)continue;
                const Vec3 op=gBots[static_cast<size_t>(other)].pos;
                const float ox=op.x-bot.pos.x,oz=op.z-bot.pos.z;
                const float d=std::sqrt(ox*ox+oz*oz);
                if(d<targetDistance && d<92.0f) {
                    targetDistance=d;
                    targetBot=other;
                }
            }
            if(!ally && !gPlayerDowned && playerDistance<20.0f && playerDistance<targetDistance
                    && botsPressuringPlayer<2) {
                targetDistance=playerDistance;
                targetPlayer=true;
                targetBot=-1;
                ++botsPressuringPlayer;
            }
            if(targetPlayer||targetBot>=0) {
                engaging=true;
                Vec3 enemy=targetPlayer?gPlayer:gBots[static_cast<size_t>(targetBot)].pos;
                const float radial=std::atan2(bot.pos.z-enemy.z,bot.pos.x-enemy.x);
                const float strafe=std::sin(gMatchTime*0.52f+bot.phase)*0.48f;
                const float desiredRadius=targetPlayer?12.0f+static_cast<float>(i%5)*1.3f:
                    9.0f+static_cast<float>(i%4)*1.2f;
                const float angle=radial+strafe;
                target={enemy.x+std::cos(angle)*desiredRadius,0.55f,
                        enemy.z+std::sin(angle)*desiredRadius};
            }
        }

        if(rescuing) {
            // Segue o destino do companheiro derrubado.
        } else if(ally && gDropState==3 && !engaging) {
            const float formationAngle=bot.phase+static_cast<float>(i)*0.75f;
            const float followRadius=gPlayerDowned?2.0f:(7.5f+static_cast<float>(i%2)*2.0f);
            target={gPlayer.x+std::cos(formationAngle)*followRadius,0.55f,
                    gPlayer.z+std::sin(formationAngle)*followRadius};
        } else if(!engaging) {
            // Patrol independent map sectors; pickups never become a bot's combat objective.
            const float phase=gMatchTime*(ally?0.24f:0.17f)+bot.phase;
            const Vec3 home=gBotHomes[static_cast<size_t>(i)];
            const float patrolRadius=ally?4.5f:7.0f;
            target={home.x+std::cos(phase)*patrolRadius,0.55f,
                    home.z+std::sin(phase)*patrolRadius};
        }

        float dx=target.x-bot.pos.x,dz=target.z-bot.pos.z;
        float distance=std::sqrt(dx*dx+dz*dz);
        bot.speed=0.0f;
        if(distance>0.55f) {
            const float inv=1.0f/std::max(0.001f,distance);
            const float dirX=dx*inv,dirZ=dz*inv;
            bot.yaw=std::atan2(-dirX,-dirZ);
            bot.speed=rescuing?3.4f:(engaging?(targetDistance>24.0f?5.2f:2.8f):(ally?3.0f:2.2f));
            const float step=std::min(distance,bot.speed*dt);
            moveWithCollision(bot.pos,bot.pos.x+dirX*step,bot.pos.z+dirZ*step,0.55f);
        }

        if(engaging) {
            const Vec3 enemy=targetPlayer?gPlayer:gBots[static_cast<size_t>(targetBot)].pos;
            const float attackDx=enemy.x-bot.pos.x;
            const float attackDz=enemy.z-bot.pos.z;
            const float attackDistance=std::sqrt(attackDx*attackDx+attackDz*attackDz);
            bot.yaw=std::atan2(-attackDx,-attackDz);
            bot.aiming=attackDistance<58.0f;
            gBotAttackCooldown[static_cast<size_t>(i)]=
                std::max(0.0f,gBotAttackCooldown[static_cast<size_t>(i)]-dt);
            if(attackDistance>4.0f && attackDistance<58.0f
                    && gBotAttackCooldown[static_cast<size_t>(i)]<=0.0f) {
                bot.fireFlash=0.32f;
                Vec3 shotFrom=bot.pos,shotTo=enemy;
                shotFrom.y+=1.28f;shotTo.y+=1.28f;
                if(!damageIceWallOnPath(shotFrom,shotTo,8)) {
                if(targetPlayer) {
                    applyPlayerDamage(1,"ELIMINADO • FIM DA PARTIDA");
                } else if(targetBot>=0 && gBotActive[static_cast<size_t>(targetBot)]) {
                    const size_t victim=static_cast<size_t>(targetBot);
                    gBotHealth[victim]=std::max(0,gBotHealth[victim]-1);
                    if(gBotHealth[victim]<=0) {
                        // Aliados do jogador sempre entram em estado derrubado,
                        // mesmo quando o atacante pertence a outro esquadrão.
                        if((isAllyBot(targetBot)||sameBotTeam(i,targetBot))
                                &&(gSelectedMode==1||gSelectedMode==2)) {
                            gBotDowned[victim]=true;gBotDownedTimer[victim]=90.0f;gBotReviveProgress[victim]=0.0f;
                            gBotHealth[victim]=0;
                        } else { gBotActive[victim]=false;++gAiEliminations;
                            gBotRespawn[victim]=gSelectedMode==3?3.0f:0.0f; }
                    }
                }
                }
                gBotAttackCooldown[static_cast<size_t>(i)]=0.95f+static_cast<float>(i%5)*0.15f;
            }
            bot.targetPlayer=targetPlayer;
            bot.targetIndex=targetBot;
        } else {
            gBotAttackCooldown[static_cast<size_t>(i)]=
                std::max(0.0f,gBotAttackCooldown[static_cast<size_t>(i)]-dt);
        }

        bot.pos.x=std::clamp(bot.pos.x,-WORLD_LIMIT+1.0f,WORLD_LIMIT-1.0f);
        bot.pos.z=std::clamp(bot.pos.z,-WORLD_LIMIT+1.0f,WORLD_LIMIT-1.0f);
        for(int other=0;other<i;++other) {
            if(!gBotActive[static_cast<size_t>(other)])continue;
            const Vec3 op=gBots[static_cast<size_t>(other)].pos;
            const float sx=bot.pos.x-op.x,sz=bot.pos.z-op.z;
            const float d2=sx*sx+sz*sz;
            if(d2<1.8f*1.8f&&d2>0.001f) {
                const float push=0.28f/std::sqrt(d2);
                bot.pos.x+=sx*push;bot.pos.z+=sz*push;
            }
        }
    }
}
Vec3 buildPreviewPosition() {
    const Vec3 forward{-std::sin(gPlayerYaw),0,-std::cos(gPlayerYaw)};
    const Vec3 right{std::cos(gPlayerYaw),0,-std::sin(gPlayerYaw)};
    const int sequence=gBuildCount%6;
    const float forwardStep=3.3f+static_cast<float>(sequence/2)*2.65f;
    const float sideStep=(sequence%2==0?-1.0f:1.0f)*1.65f;
    return {gPlayer.x+forward.x*forwardStep+right.x*sideStep,0.68f,gPlayer.z+forward.z*forwardStep+right.z*sideStep};
}
void updateDownedState(float dt) {
    if(!gPlayerDowned)return;
    gDownedTimer=std::max(0.0f,gDownedTimer-dt);float nearest=999.0f;
    for(int i=0;i<BOT_COUNT;++i)if(isAllyBot(i)&&gBotActive[static_cast<size_t>(i)]){float dx=gBots[static_cast<size_t>(i)].pos.x-gPlayer.x,dz=gBots[static_cast<size_t>(i)].pos.z-gPlayer.z;nearest=std::min(nearest,std::sqrt(dx*dx+dz*dz));}
    if(nearest<=5.0f){gReviveProgress+=dt;std::ostringstream st;st<<"DERRUBADO • ALIADO REVIVENDO "<<static_cast<int>(std::min(100.0f,gReviveProgress*25.0f))<<"%";setStatus(st.str());
        if(gReviveProgress>=4.0f){gPlayerDowned=false;gPlayerHealth=45;gDownedTimer=0;gReviveProgress=0;setStatus("REVIVIDO PELO ESQUADRÃO • VIDA 45%");}}
    else {gReviveProgress=0;if(gDownedTimer<=0){gDownedTimer=15.0f;setStatus("DERRUBADO • ESQUADRÃO AINDA PODE SOCORRER");}else setStatus("DERRUBADO • AGUARDANDO ALIADO • "+std::to_string(static_cast<int>(gDownedTimer))+"s");}
}
void applyPlayerDamage(int amount,const char* reason) {
    if(gPlayerDowned||gMatchFinished)return;gPlayerHealth=std::max(0,gPlayerHealth-amount);gDamageTaken+=amount;
    if(gPlayerHealth<=0){bool ally=false;if(gSelectedMode==1||gSelectedMode==2)for(int i=0;i<BOT_COUNT;++i)if(isAllyBot(i)&&gBotActive[static_cast<size_t>(i)])ally=true;
        if(ally){gPlayerDowned=true;gDownedTimer=90;gReviveProgress=0;setStatus("DERRUBADO • RASTEJE ATÉ SEU ESQUADRÃO • AGUARDANDO REVIVER");}else{gMatchFinished=true;setStatus(reason);}}
    else setStatus("DANO RECEBIDO • VIDA "+std::to_string(gPlayerHealth)+"%");
}
void placeBuildPiece() {
    if(gBuildCount>=gBuildLimit||gBuildCount>=BUILD_CAPACITY) {
        setStatus("LIMITE DE CONSTRUÇÕES ATINGIDO");return;
    }
    if(gWood<10) {setStatus("MADEIRA INSUFICIENTE • COLETE RECURSOS");return;}
    Vec3 pos=buildPreviewPosition();
    for(int i=0;i<gBuildCount;++i) {
        float dx=pos.x-gBuildPieces[static_cast<size_t>(i)].pos.x;
        float dz=pos.z-gBuildPieces[static_cast<size_t>(i)].pos.z;
        if(gBuildPieces[static_cast<size_t>(i)].health>0&&dx*dx+dz*dz<2.0f) {setStatus("LOCAL DE CONSTRUÇÃO BLOQUEADO");return;}
    }
    if(worldPositionBlocked(pos.x,pos.z,1.15f)) {
        setStatus("CONSTRUÇÃO BLOQUEADA • NÃO COLOQUE DENTRO DE CASAS OU BARREIRAS");return;
    }
    const int placedType=gBuildType;
    gBuildPieces[static_cast<size_t>(gBuildCount)]={pos,gPlayerYaw,placedType,ICE_WALL_MAX_HEALTH};
    ++gBuildCount;gWood-=10;gBuildType=(gBuildType+1)%3;
    const char* names[]={"GELO","PLATAFORMA","RAMPA"};
    std::ostringstream out;out<<names[placedType]<<" CONSTRUÍDA  |  "<<gWood<<" MADEIRA RESTANTE";
    setStatus(out.str());
}
void drawBuildPreview() {
    if(!gBuildPreview||gDropState!=3)return;
    const Vec3 pos=buildPreviewPosition();
    bool valid=gWood>=10&&gBuildCount<gBuildLimit&&gBuildCount<BUILD_CAPACITY&&!worldPositionBlocked(pos.x,pos.z,1.15f);
    for(int i=0;i<gBuildCount;++i){const BuildPiece& existing=gBuildPieces[static_cast<size_t>(i)];const float dx=pos.x-existing.pos.x;const float dz=pos.z-existing.pos.z;if(existing.health>0&&dx*dx+dz*dz<2.0f)valid=false;}
    glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
    const Vec3 tint=valid?Vec3{0.30f,0.88f,1.0f}:Vec3{1.0f,0.22f,0.18f};
    if(gBuildType==0)drawCube({pos.x,1.85f,pos.z},{3.2f,2.25f,0.24f},tint,gPlayerYaw,0,0,0.38f);
    else if(gBuildType==1)drawCube({pos.x,0.78f,pos.z},{3.0f,0.22f,3.0f},tint,gPlayerYaw,0,0,0.38f);
    else drawCube({pos.x,0.98f,pos.z},{3.0f,0.20f,3.0f},tint,gPlayerYaw,0.0f,0.26f,0.38f);
    glDisable(GL_BLEND);
}
void drawBuildPieces() {
    for(int i=0;i<gBuildCount;++i) {
        const BuildPiece& b=gBuildPieces[static_cast<size_t>(i)];
        if(b.type==0) {
            if(b.health<=0)continue;
            const float strength=static_cast<float>(b.health)/static_cast<float>(ICE_WALL_MAX_HEALTH);
            const Vec3 iceColor{0.12f+0.08f*strength,0.42f+0.25f*strength,0.62f+0.30f*strength};
            drawCube({b.pos.x,1.85f,b.pos.z},{3.2f,2.25f,0.24f},iceColor,b.yaw);
            if(b.health<75)drawSegment(b.pos,b.yaw,{-0.65f,1.95f,0.15f},{-0.10f,1.35f,0.15f},0.035f,{0.035f,0.20f,0.31f});
            if(b.health<45)drawSegment(b.pos,b.yaw,{0.55f,2.35f,0.15f},{0.02f,1.82f,0.15f},0.040f,{0.035f,0.20f,0.31f});
            if(b.health<20)drawSegment(b.pos,b.yaw,{-0.15f,1.35f,0.15f},{0.42f,0.85f,0.15f},0.045f,{0.035f,0.20f,0.31f});
            drawCube({b.pos.x-0.9f,1.85f,b.pos.z},{0.12f,2.30f,0.32f},{0.48f,0.88f,0.96f},b.yaw);
            drawCube({b.pos.x+0.9f,1.85f,b.pos.z},{0.12f,2.30f,0.32f},{0.48f,0.88f,0.96f},b.yaw);
        } else if(b.type==1) {
            drawCube({b.pos.x,0.78f,b.pos.z},{3.0f,0.22f,3.0f},{0.52f,0.37f,0.23f},b.yaw);
            drawCube({b.pos.x,0.62f,b.pos.z-1.28f},{3.0f,0.10f,0.16f},{0.72f,0.51f,0.30f},b.yaw);
        } else {
            drawCube({b.pos.x,0.98f,b.pos.z},{3.0f,0.20f,3.0f},{0.58f,0.41f,0.24f},b.yaw,0.0f,0.26f);
            drawCube({b.pos.x,1.32f,b.pos.z-0.8f},{3.0f,0.12f,0.20f},{0.72f,0.53f,0.32f},b.yaw,0.0f,0.26f);
        }
    }
}
void firePulse() {
    gCrashTraceStage.store(gWeaponType==2?50:10,std::memory_order_relaxed);
    if(gWeaponType<0||gWeaponType>3||!gWeaponOwned[static_cast<size_t>(gWeaponType)]) {
        setStatus("SEM ARMA • PROCURE UM EQUIPAMENTO NO CHÃO");return;
    }
    int& ammo=gWeaponAmmo[static_cast<size_t>(gWeaponType)];
    const bool infiniteTrainingAmmo=(gSelectedMode==3);
    if(!infiniteTrainingAmmo&&ammo<=0){setStatus("SEM MUNIÇÃO • PEGUE CAIXAS DE CARGA");return;}
    if(!infiniteTrainingAmmo)--ammo;
    gPulseFlash=0.18f;
    if(gWeaponType==2)gCrashTraceStage.store(51,std::memory_order_relaxed);
    gPlayerYaw=gCameraYaw;
    gPulseStartPosition=localPoint(gPlayer,0.49f,1.22f,-0.42f,gCameraYaw);
    const float fx=-std::sin(gCameraYaw),fz=-std::cos(gCameraYaw);
    const float range=gWeaponType==2?88.0f:56.0f;
    const float tolerance=gWeaponType==2?1.25f:(gWeaponType==1?2.35f:1.85f);
    const int damage=gWeaponType==2?4:(gWeaponType==0?2:1);
    float best=range;
    int hitTarget=-1,hitBot=-1;
    if(gSelectedMode==3) {
        for(int i=0;i<TARGET_COUNT;++i) {
            TrainingTarget& target=gTargets[static_cast<size_t>(i)];
            if(!target.active)continue;
            float dx=target.pos.x-gPlayer.x,dz=target.pos.z-gPlayer.z;
            float along=dx*fx+dz*fz,side=std::fabs(dx*fz-dz*fx);
            if(along>0.8f&&along<best&&side<tolerance) {
                best=along;hitTarget=i;hitBot=-1;
            }
        }
    }
    for(int i=0;i<BOT_COUNT;++i) {
        if(!gBotActive[static_cast<size_t>(i)]||gBotDowned[static_cast<size_t>(i)]
                ||(!FRIENDLY_FIRE_ENABLED&&isAllyBot(i)))continue;
        const Vec3 pos=gBots[static_cast<size_t>(i)].pos;
        float dx=pos.x-gPlayer.x,dz=pos.z-gPlayer.z;
        float along=dx*fx+dz*fz,side=std::fabs(dx*fz-dz*fx);
        if(along>0.8f&&along<best&&side<tolerance*1.5f) {
            best=along;hitTarget=-1;hitBot=i;
        }
    }
    gPulseHitPosition={gPlayer.x+fx*best,gPlayer.y+1.45f+gJumpHeight,gPlayer.z+fz*best};
    Vec3 iceImpact{};
    const bool hitIceWall=damageIceWallOnPath(
        {gPlayer.x,gPlayer.y+1.30f+gJumpHeight,gPlayer.z},
        {gPlayer.x+fx*best,gPlayer.y+1.30f+gJumpHeight,gPlayer.z+fz*best},
        damage*5,&iceImpact);
    if(hitIceWall) {
        gPulseHitPosition=iceImpact;
        hitTarget=-1;hitBot=-1;
    }
    if(hitTarget>=0) {
        TrainingTarget& target=gTargets[static_cast<size_t>(hitTarget)];
        target.energy=std::max(0,target.energy-damage);
        gPulseHitPosition=target.pos;
        if(target.energy==0) {
            target.active=false;gTrainingScore+=100;gWood=std::min(250,gWood+5);
            setStatus("ALVO DE TREINO DESATIVADO  •  +100 PONTOS");
        }
    } else if(hitBot>=0) {
        // Faixas de diagnóstico independentes por arma:
        // Carapina mantém 100-105; XM8/UMP/Uzi usam 200-237.
        const int traceBase=(gWeaponType==2)?100:(200+gWeaponType*10);
        gCrashTraceStage.store(traceBase,std::memory_order_relaxed);
        gLastEliminationStage.store(traceBase,std::memory_order_relaxed);
        gBotHealth[static_cast<size_t>(hitBot)]=std::max(0,gBotHealth[static_cast<size_t>(hitBot)]-damage);
        gDamageDealt+=damage;
        const int healthAfterHit=gBotHealth[static_cast<size_t>(hitBot)];
        gBotDamageAmount[static_cast<size_t>(hitBot)]=damage;
        gBotDamageTimer[static_cast<size_t>(hitBot)]=1.20f;
        gBotDamagePosition[static_cast<size_t>(hitBot)]=gBots[static_cast<size_t>(hitBot)].pos;
        gCrashTraceStage.store(traceBase+1,std::memory_order_relaxed);gLastEliminationStage.store(traceBase+1,std::memory_order_relaxed);
        { std::ostringstream hit; hit<<"ACERTOU IA  •  -"<<damage<<" DANO  •  VIDA "<<healthAfterHit<<"/"<<BOT_MAX_HEALTH; setStatus(hit.str()); }
        gPulseHitPosition=gBots[static_cast<size_t>(hitBot)].pos;
        if(gBotHealth[static_cast<size_t>(hitBot)]==0) {
            gCrashTraceStage.store(traceBase+2,std::memory_order_relaxed);gLastEliminationStage.store(traceBase+2,std::memory_order_relaxed);
            if((gSelectedMode==1||gSelectedMode==2)&&!gBotDowned[static_cast<size_t>(hitBot)]) {
                gBotDowned[static_cast<size_t>(hitBot)]=true;gBotDownedTimer[static_cast<size_t>(hitBot)]=25.0f;
                gBotReviveProgress[static_cast<size_t>(hitBot)]=0.0f;
                setStatus("ALIADO DERRUBADO • APROXIME-SE E TOQUE USAR PARA REVIVER");
            } else {
            gCrashTraceStage.store(traceBase+3,std::memory_order_relaxed);gLastEliminationStage.store(traceBase+3,std::memory_order_relaxed);
            gBotActive[static_cast<size_t>(hitBot)]=false;
            gBotDamageTimer[static_cast<size_t>(hitBot)]=0.0f;
            gBotDamageAmount[static_cast<size_t>(hitBot)]=0;
            if(gSelectedMode==3) {
                gBotRespawn[static_cast<size_t>(hitBot)]=12.0f;
                gTrainingScore+=150;
            } else {
                gBotRespawn[static_cast<size_t>(hitBot)]=0.0f;
                ++gEliminations;
            }
            gWood=std::min(250,gWood+10);
            gCrashTraceStage.store(traceBase+4,std::memory_order_relaxed);gLastEliminationStage.store(traceBase+4,std::memory_order_relaxed);
            int remaining=0;
            for(int i=0;i<BOT_COUNT;++i)
                if(!isAllyBot(i)&&gBotActive[static_cast<size_t>(i)])++remaining;
            if(gSelectedMode!=3&&remaining==0) {
                gMatchFinished=true;
                setStatus("VITÓRIA • TODOS OS OPONENTES ELIMINADOS");
            } else if(gSelectedMode==3) setStatus("ALVO BOT DESATIVADO • +150 PONTOS");
            else setStatus("OPONENTE ELIMINADO • RESTAM "+std::to_string(remaining));
            gCrashTraceStage.store(traceBase+5,std::memory_order_relaxed);gLastEliminationStage.store(traceBase+5,std::memory_order_relaxed); }
        }
    }
    if(hitTarget<0&&hitBot<0&&!hitIceWall) {
        const char* names[]={"PULSO PLASMA","RAIO ARC","NOVA","MINI UZI"};
        std::ostringstream out;out<<names[std::clamp(gWeaponType,0,3)]<<" • MIRA DA CÂMERA • SEM ALVO NO TRAJETO";
        setStatus(out.str());
    }
}
void drawTargets(float time) {
    if(gSelectedMode==3) {
        for(int i=0;i<TARGET_COUNT;++i) {
            const TrainingTarget& target=gTargets[static_cast<size_t>(i)];
            if(!target.active)continue;
            float bob=std::sin(time*2.0f+i)*0.12f;
            drawSphere({target.pos.x,target.pos.y+bob,target.pos.z},{0.60f,0.60f,0.60f},{0.12f,0.69f,0.86f});
            drawSphere({target.pos.x,target.pos.y+bob,target.pos.z},{0.72f,0.10f,0.72f},{0.80f,0.94f,1.0f});
            drawSphere({target.pos.x,target.pos.y+bob,target.pos.z},{0.24f,0.24f,0.24f},{0.55f,0.98f,1.0f});
            for(int ring=0;ring<target.energy;++ring)
                drawCube({target.pos.x-0.42f+ring*0.42f,target.pos.y+0.70f,target.pos.z},
                    {0.28f,0.07f,0.08f},{0.24f,0.95f,0.92f});
        }
    }
    if(gPulseFlash>0.0f) {
        const Vec3 boltColor=gWeaponType==2?Vec3{1.0f,0.66f,0.24f}:
            (gWeaponType==1?Vec3{0.34f,0.78f,1.0f}:
             (gWeaponType==3?Vec3{1.0f,0.78f,0.30f}:Vec3{0.35f,0.96f,0.88f}));
        const float progress=std::clamp(1.0f-gPulseFlash/0.18f,0.0f,1.0f);
        const Vec3 projectile{
            gPulseStartPosition.x+(gPulseHitPosition.x-gPulseStartPosition.x)*progress,
            gPulseStartPosition.y+(gPulseHitPosition.y-gPulseStartPosition.y)*progress,
            gPulseStartPosition.z+(gPulseHitPosition.z-gPulseStartPosition.z)*progress
        };
        // Efeito curto de projétil, clarão no cano e impacto; sem feixe/linha atravessando a tela.
        drawSphere(gPulseStartPosition,{0.22f,0.22f,0.22f},boltColor);
        drawSphere(projectile,{0.13f,0.13f,0.13f},boltColor);
        drawSphere(gPulseHitPosition,{0.24f,0.24f,0.24f},boltColor);
    }
}
void drawTransportPlaneCabin(Vec3 pos,float time) {
    const Vec3 frame{0.39f,0.47f,0.54f};
    drawCube({pos.x,pos.y-0.68f,pos.z},{32.0f,0.22f,12.0f},{0.23f,0.29f,0.35f});
    drawCube({pos.x,pos.y+2.43f,pos.z},{32.0f,0.20f,12.0f},{0.28f,0.35f,0.42f});
    // Structural ribs, lower wall panels and open side windows.
    for(int side=-1;side<=1;side+=2) {
        const float z=pos.z+static_cast<float>(side)*6.0f;
        drawCube({pos.x,pos.y-0.20f,z},{32.0f,0.78f,0.28f},frame);
        drawCube({pos.x,pos.y+2.03f,z},{32.0f,0.30f,0.30f},frame);
        for(int rib=0;rib<=8;++rib) {
            float x=pos.x-15.0f+static_cast<float>(rib)*3.75f;
            drawCube({x,pos.y+0.91f,z},{0.28f,2.35f,0.32f},frame);
            drawCube({x,pos.y+2.30f,pos.z},{0.22f,0.24f,12.0f},frame);
        }
        for(int window=0;window<7;++window) {
            float x=pos.x-13.1f+static_cast<float>(window)*3.75f;
            drawCube({x,pos.y+1.12f,z-static_cast<float>(side)*0.18f},
                {2.55f,1.18f,0.045f},{0.13f,0.43f,0.57f});
        }
    }
    // Rear cargo ramp opens after the flight countdown.
    drawCube({pos.x+15.85f,pos.y+0.55f,pos.z},{0.30f,2.45f,12.0f},{0.31f,0.38f,0.45f});
    if(gDropTimer>=PLANE_COUNTDOWN_SECONDS) {
        drawCube({pos.x-15.8f,pos.y-0.46f,pos.z},{4.6f,0.18f,8.0f},{0.39f,0.46f,0.53f},0.0f,0.12f);
        for(int side=-1;side<=1;side+=2)
            drawCube({pos.x-15.8f,pos.y+0.18f,pos.z+static_cast<float>(side)*3.85f},
                {4.6f,0.48f,0.14f},{0.52f,0.61f,0.68f},0.0f,0.12f);
        for(int stripe=0;stripe<6;++stripe)
            drawCube({pos.x-15.8f+stripe*0.72f,pos.y-0.34f,pos.z},
                {0.12f,0.04f,7.5f},{0.93f,0.66f,0.20f});
    } else {
        drawCube({pos.x-16.0f,pos.y+0.76f,pos.z},{0.24f,2.0f,11.8f},{0.30f,0.37f,0.44f});
    }
    // Walkway and tie-down rails.
    drawCube({pos.x,pos.y-0.49f,pos.z},{30.0f,0.05f,1.35f},{0.48f,0.55f,0.60f});
    for(int rail=0;rail<2;++rail)
        drawCube({pos.x,pos.y-0.43f,pos.z+(rail==0?-5.0f:5.0f)},
            {30.0f,0.12f,0.14f},{0.62f,0.67f,0.70f});
    float pulse=0.13f+0.06f*std::sin(time*7.0f);
    for(int light=0;light<8;++light) {
        float x=pos.x-13.0f+static_cast<float>(light)*3.7f;
        drawCube({x,pos.y+2.28f,pos.z},{0.70f,0.06f,0.55f},{0.54f,0.79f,0.84f});
        drawSphere({x,pos.y+2.13f,pos.z},{pulse,pulse,pulse},{0.30f,0.85f,1.0f});
    }
    // Fifteen rows of two seats keep the center aisle clear.
    const std::array<Vec3,4> outfits{{
        {0.035f,0.66f,0.88f},{0.10f,0.18f,0.23f},
        {0.52f,0.12f,0.88f},{0.82f,0.55f,0.08f}
    }};
    for(int row=0;row<15;++row) {
        const float x=pos.x-13.1f+static_cast<float>(row)*1.86f;
        for(int side=-1;side<=1;side+=2) {
            const float z=pos.z+static_cast<float>(side)*3.8f;
            drawCube({x,pos.y-0.36f,z},{0.92f,0.18f,1.05f},{0.08f,0.22f,0.31f});
            drawCube({x-0.38f,pos.y-0.02f,z},{0.14f,0.68f,1.05f},{0.12f,0.38f,0.49f});
        }
    }
    // Twenty-nine AI passengers occupy distinct seats; no more overlapping columns.
    for(int i=0;i<BOT_COUNT;++i) {
        int row=i/2, side=(i%2==0?-1:1);
        float xOffset=-13.1f+static_cast<float>(row)*1.86f;
        float zOffset=static_cast<float>(side)*3.8f;
        const float phase=time*0.78f+static_cast<float>(i)*1.31f;
        const float walk=0.18f+0.42f*std::fabs(std::sin(phase));
        const float hop=std::pow(std::max(0.0f,std::sin(phase*0.83f+0.7f)),6.0f)*0.34f;
        const float sway=std::sin(phase*0.62f)*0.12f;
        Vec3 passenger{pos.x+xOffset+std::sin(phase*0.47f)*0.14f,
                       pos.y-0.54f+hop,pos.z+zOffset+sway};
        drawCharacter(passenger,PI*0.5f,outfits[static_cast<size_t>(i%4)],true,
            time+static_cast<float>(i)*0.19f,walk,false,false,false);
    }
    // Instrument console and front cockpit partition.
    drawCube({pos.x+14.8f,pos.y+0.95f,pos.z},{0.55f,1.8f,8.0f},{0.17f,0.24f,0.31f});
    for(int side=-1;side<=1;side+=2)
        drawCube({pos.x+14.5f,pos.y+1.12f,pos.z+static_cast<float>(side)*2.2f},
            {0.22f,0.55f,1.3f},{0.12f,0.68f,0.79f});
}
void drawTransportPlaneExterior(Vec3 pos,float time) {
    drawCube({pos.x,pos.y,pos.z},{34.0f,3.2f,4.0f},{0.42f,0.49f,0.56f});
    drawCone({pos.x+18.2f,pos.y,pos.z},{4.2f,3.0f,3.4f},{0.76f,0.81f,0.86f},0.0f,-PI*0.5f);
    drawCube({pos.x-17.0f,pos.y+0.1f,pos.z},{2.4f,2.7f,3.8f},{0.34f,0.41f,0.48f});
    drawCube({pos.x-0.8f,pos.y+0.05f,pos.z},{5.0f,0.28f,34.0f},{0.53f,0.60f,0.67f});
    drawCube({pos.x-13.0f,pos.y+0.45f,pos.z},{5.0f,0.22f,12.0f},{0.48f,0.55f,0.62f});
    drawCube({pos.x-15.4f,pos.y+1.55f,pos.z},{2.8f,2.6f,0.35f},{0.38f,0.46f,0.53f});
    for(int side=-1;side<=1;side+=2) {
        const float z=pos.z+static_cast<float>(side)*10.5f;
        drawCylinder({pos.x+1.5f,pos.y-0.92f,z},{1.2f,3.2f,1.2f},{0.22f,0.29f,0.36f},0.0f,-PI*0.5f);
        drawSphere({pos.x+1.5f,pos.y-0.92f,z},{0.62f,0.62f,0.62f},{0.18f,0.75f,0.90f});
        drawCylinder({pos.x-6.5f,pos.y-0.92f,pos.z+static_cast<float>(side)*10.5f},
            {1.2f,3.2f,1.2f},{0.22f,0.29f,0.36f},0.0f,-PI*0.5f);
    }
    const float blink=0.12f+0.06f*std::sin(time*8.0f);
    drawSphere({pos.x-16.0f,pos.y+1.55f,pos.z-3.8f},{blink,blink,blink},{0.25f,0.90f,1.0f});
    drawSphere({pos.x-16.0f,pos.y+1.55f,pos.z+3.8f},{blink,blink,blink},{1.0f,0.25f,0.18f});
}
void drawParachute(Vec3 player) {
    if(gDropState!=2||!gParachuteOpen)return;
    Vec3 canopy{player.x,player.y+gJumpHeight+7.2f,player.z};
    drawSphere(canopy,{3.3f,1.15f,3.3f},{0.83f,0.24f,0.19f});
    drawSegment({0,0,0},0.0f,{player.x-0.42f,player.y+gJumpHeight+1.5f,player.z},
        {canopy.x-2.1f,canopy.y-0.3f,canopy.z},0.025f,{0.88f,0.88f,0.84f});
    drawSegment({0,0,0},0.0f,{player.x+0.42f,player.y+gJumpHeight+1.5f,player.z},
        {canopy.x+2.1f,canopy.y-0.3f,canopy.z},0.025f,{0.88f,0.88f,0.84f});
    drawSegment({0,0,0},0.0f,{player.x,player.y+gJumpHeight+1.6f,player.z-0.25f},
        {canopy.x,canopy.y-0.2f,canopy.z-2.0f},0.025f,{0.88f,0.88f,0.84f});
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetScreen(JNIEnv*,jclass,jint mode) {
    gScreenMode=std::clamp(static_cast<int>(mode),0,1);
    if(gScreenMode==1) {
        gLastEliminationStage.store(0,std::memory_order_relaxed);
        gPlayer={-110,0.55f,-32};
        gPlayerXSnapshot.store(gPlayer.x);gPlayerZSnapshot.store(gPlayer.z);
        gPlayerYaw=-PI*0.5f;gCameraYaw=-PI*0.5f;gCameraPitch=0.36f;
        gMatchTime=0;gZoneRadius=112;gCollected=0;gCrystalTaken.fill(false);
        gBuildCount=0;gWood=180;gBuildType=0;gBuildCooldown=0;
        gWeaponAmmo.fill(0);gWeaponOwned.fill(false);gTrainingScore=0;gWeaponType=-1;gPulseCooldown=0;gPulseFlash=0;
        gPlayerHealth=100;gEliminations=0;gDamageDealt=0;gDamageTaken=0;gAiEliminations=0;gMatchFinished=false;gSpectatorMode=false;gSpectatorTarget=-1;gZoneDamageTimer=0;
        gPlayerDowned=false;gCrouched=false;gBuildPreview=false;gDownedTimer=0;gReviveProgress=0;
    gPlayerReviveTarget=-1;gPlayerReviveProgress=0;
        resetGroundLoot();
        if(gSelectedMode==3) {
            gPlayer={0,0.55f,0};gPlayerXSnapshot.store(0.0f);gPlayerZSnapshot.store(0.0f);
            gDropState=3;gDropTimer=0;gPlaneExteriorView=false;gParachuteOpen=false;
            gWeaponOwned.fill(true);gWeaponAmmo.fill(999);gWeaponType=3;
            gGroundLoot[0]={{-3.0f,0.55f,-2.0f},0,240,true,false};
            gGroundLoot[1]={{3.0f,0.55f,-2.0f},1,240,true,false};
            gGroundLoot[2]={{-3.0f,0.55f,3.0f},2,240,true,false};
            gGroundLoot[3]={{3.0f,0.55f,3.0f},3,240,true,false};
        } else {
            gDropState=1;gDropTimer=0;gPlaneExteriorView=false;gPlanePosition={-110,24,-32};gPlaneWalkX=1.0f;gPlaneWalkZ=0.0f;
        }
        gJumpHeight=0;gJumpVelocity=0;gParachuteOpen=false;gPreviousButtons=0;
        gTargets[0]={{0,1.25f,-17},3,true};
        gTargets[1]={{-18,1.25f,-28},3,true};
        gTargets[2]={{20,1.25f,-31},3,true};
        resetBots();updateStatus();
    } else {
        gDropState=0;gJumpHeight=0;gJumpVelocity=0;gParachuteOpen=false;
        gSpectatorMode=false;gSpectatorTarget=-1;gMatchFinished=false;
        gPlayerDowned=false;gPlayerHealth=100;gPreviousButtons=0;
        gPlayer={32.0f,0.55f,-8.0f};
        gPlayerXSnapshot.store(gPlayer.x);gPlayerZSnapshot.store(gPlayer.z);
        gWaypointActive=false;
        setStatus("LOBBY • PRONTO PARA NOVA PARTIDA");
    }
    publishMatchStats();
    publishFlightInfo();
    // Não sobrescrever o último estágio: ele precisa sobreviver entre quadros
    // para que o diagnóstico indique a operação imediatamente anterior ao crash.
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetCharacter(JNIEnv*,jclass,jint id) {
    gSelectedCharacter=std::clamp(static_cast<int>(id),0,3);
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetMap(JNIEnv*,jclass,jint id) {
    gSelectedMap=std::clamp(static_cast<int>(id),0,7);
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetMode(JNIEnv*,jclass,jint id) {
    gSelectedMode=std::clamp(static_cast<int>(id),0,3);
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetQuality(JNIEnv*,jclass,jint value) {
    gQuality=std::clamp(static_cast<int>(value),0,2);
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetWardrobe(JNIEnv*,jclass,jint shirt,jint boots,jint set) {
    gSelectedShirtStyle=std::clamp(static_cast<int>(shirt),0,4);
    gSelectedBootStyle=std::clamp(static_cast<int>(boots),0,4);
    gSelectedSetStyle=std::clamp(static_cast<int>(set),0,4);
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetLookSensitivity(JNIEnv*,jclass,jint value) {
    const int level=std::clamp(static_cast<int>(value),0,2);
    gLookSensitivity=level==0?0.65f:(level==1?1.0f:1.45f);
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetContentData(JNIEnv*,jclass,jint buildLimit,jint resourceCount) {
    gBuildLimit=std::clamp(static_cast<int>(buildLimit),4,BUILD_CAPACITY);
    gResourceCount=std::clamp(static_cast<int>(resourceCount),1,CRYSTAL_COUNT);
    gContentPackReady=true;
    updateStatus();
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeInit(JNIEnv*,jclass) {
    gPlayer={0,0.55f,0};gPlayerXSnapshot.store(0.0f);gPlayerZSnapshot.store(0.0f);gPlayerYaw=0;gCameraYaw=0;gCameraPitch=0.36f;
    gScreenMode=0;gSelectedCharacter=0;gSelectedMap=0;gSelectedMode=0;gQuality=1;
    gJumpHeight=0;gJumpVelocity=0;gMatchTime=0;gZoneRadius=112;
    gCollected=0;gPreviousButtons=0;gInteractCooldown=0;gCrystalTaken.fill(false);
    gBuildCount=0;gBuildType=0;gWood=180;gBuildCooldown=0;gBuildLimit=12;gResourceCount=5;gContentPackReady=false;
    gWeaponAmmo.fill(0);gWeaponOwned.fill(false);gTrainingScore=0;gWeaponType=-1;gPulseCooldown=0;gPulseFlash=0;
    gPlayerHealth=100;gEliminations=0;gDamageDealt=0;gDamageTaken=0;gAiEliminations=0;gMatchFinished=false;gSpectatorMode=false;gSpectatorTarget=-1;gZoneDamageTimer=0;
    gPlayerDowned=false;gCrouched=false;gBuildPreview=false;gDownedTimer=0;gReviveProgress=0;
    gBotAttackCooldown.fill(0.0f);gLookSensitivity=1.0f;
    gDropState=0;gDropTimer=0;gPlaneExteriorView=false;gPlanePosition={-110,24,-32};gPlaneWalkX=1.0f;gPlaneWalkZ=0.0f;gParachuteOpen=false;
    gWaypoint={0.0f,0.55f,0.0f};gWaypointActive=false;publishFlightInfo();
    gTargets[0]={{0,1.25f,-10},3,true};
    gTargets[1]={{-10,1.25f,-14},3,true};
    gTargets[2]={{10,1.25f,-14},3,true};
    resetBots();
    glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LEQUAL); glDisable(GL_CULL_FACE);
    glClearColor(0.39f,0.69f,0.85f,1.0f);
    if (!initializeGraphics()) { setStatus("ERRO AO INICIAR OPENGL ES 3.0"); return; }
    updateStatus();
    publishMatchStats();
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeResize(JNIEnv*,jclass,jint width,jint height) {
    gWidth=std::max(1,static_cast<int>(width)); gHeight=std::max(1,static_cast<int>(height));
    glViewport(0,0,gWidth,gHeight);
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeRender(JNIEnv*,jclass,
    jfloat deltaSeconds,jfloat moveX,jfloat moveY,jfloat lookDX,jfloat lookDY,jint buttons) {
    if(!gProgram||!gCubeVbo){glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);return;}
    const float dt=std::clamp(static_cast<float>(deltaSeconds),0.001f,0.05f);
    gMatchTime+=dt;
    for(float& timer:gBotDamageTimer) timer=std::max(0.0f,timer-dt);
    gZoneRadius=std::max(38.0f,112.0f-gMatchTime*0.12f);
    if(gPulseCooldown>0)gPulseCooldown=std::max(0.0f,gPulseCooldown-dt);
    if(gBuildCooldown>0)gBuildCooldown=std::max(0.0f,gBuildCooldown-dt);
    if(gPulseFlash>0)gPulseFlash=std::max(0.0f,gPulseFlash-dt);
    gCameraYaw+=static_cast<float>(lookDX)*4.8f*gLookSensitivity;
    gCameraPitch=std::clamp(gCameraPitch+static_cast<float>(lookDY)*2.2f*gLookSensitivity,0.12f,0.85f);

    if(gSpectatorMode){moveX=0.0f;moveY=0.0f;buttons=0;}
    const bool jumpPressed=(buttons&BUTTON_JUMP)&&!(gPreviousButtons&BUTTON_JUMP);
    const bool buildPressed=(buttons&BUTTON_BUILD)&&!(gPreviousButtons&BUTTON_BUILD);
    gCrouched=(buttons&BUTTON_CROUCH)!=0&&!gPlayerDowned;
    const bool interactPressed=(buttons&BUTTON_INTERACT)&&!(gPreviousButtons&BUTTON_INTERACT);
    const bool interactHeld=(buttons&BUTTON_INTERACT)!=0;
    const bool pulseHeld=(buttons&BUTTON_PULSE)!=0;
    const bool swapPressed=(buttons&BUTTON_SWAP)&&!(gPreviousButtons&BUTTON_SWAP);
    const bool aimHeld=(buttons&BUTTON_AIM)!=0;
    gPreviousButtons=buttons;

    if(gScreenMode==0) {
        gDropState=0;gZoneRadius=112.0f;
        gPlayer={32.0f,0.55f,-8.0f};gPlayerYaw=PI;
        gPlayerXSnapshot.store(gPlayer.x,std::memory_order_relaxed);
        gPlayerZSnapshot.store(gPlayer.z,std::memory_order_relaxed);
        const float lobbyOrbit=std::sin(gMatchTime*0.16f)*0.10f;
        Vec3 eye{gPlayer.x+lobbyOrbit*8.0f,gPlayer.y+4.25f,gPlayer.z+11.5f};
        Vec3 target{gPlayer.x,gPlayer.y+1.18f,gPlayer.z};
        Mat4 view=lookAt(eye,target,{0,1,0});
        gProjection=multiply(perspective(0.86f,static_cast<float>(gWidth)/gHeight,0.1f,260.0f),view);
        glClearColor(0.24f,0.47f,0.58f,1.0f);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(gProgram);
        drawWorld(gMatchTime);
        const std::array<Vec3,4> lobbyOutfits{{
            {0.035f,0.66f,0.88f},{0.10f,0.18f,0.23f},
            {0.52f,0.12f,0.88f},{0.82f,0.55f,0.08f}
        }};
        drawCharacter(gPlayer,gPlayerYaw,lobbyOutfits[static_cast<size_t>(gSelectedCharacter)],
            false,gMatchTime,0.0f,false,false,false);
        drawCharacter({gPlayer.x-4.1f,0.55f,gPlayer.z-1.8f},PI+0.18f,
            {0.10f,0.18f,0.23f},true,gMatchTime+0.8f,0.0f,false,false,false);
        drawCharacter({gPlayer.x+4.0f,0.55f,gPlayer.z-2.2f},PI-0.18f,
            {0.52f,0.12f,0.88f},true,gMatchTime+1.6f,0.0f,false,false,false);
        publishFlightInfo();
        glDisableVertexAttribArray(0);glDisableVertexAttribArray(1);glBindBuffer(GL_ARRAY_BUFFER,0);
        return;
    }

    // Grande aeronave: movimento livre durante a rota, câmera acompanhando o operador.
    if(gDropState==1) {
        gDropTimer+=dt;
        gPlanePosition.x=std::min(112.0f,-110.0f+6.8f*gDropTimer);
        gPlanePosition.y=24.0f+std::sin(gMatchTime*0.72f)*0.16f;
        const float forward=static_cast<float>(moveY),strafe=static_cast<float>(moveX);
        float planeDx=std::cos(gCameraYaw)*strafe-std::sin(gCameraYaw)*forward;
        float planeDz=std::sin(gCameraYaw)*strafe-std::cos(gCameraYaw)*forward;
        float movement=std::sqrt(planeDx*planeDx+planeDz*planeDz);
        if(movement>0.08f) {
            planeDx/=std::max(1.0f,movement);planeDz/=std::max(1.0f,movement);
            gPlaneWalkX=std::clamp(gPlaneWalkX+planeDx*5.0f*dt,-14.6f,14.6f);
            gPlaneWalkZ=std::clamp(gPlaneWalkZ+planeDz*5.0f*dt,-4.55f,4.55f);
            gPlayerYaw=std::atan2(-planeDx,-planeDz);
        }
        gPlayer.x=gPlanePosition.x+gPlaneWalkX;
        gPlayer.z=gPlanePosition.z+gPlaneWalkZ;
        gPlayer.y=0.55f;
        gPlayerXSnapshot.store(gPlayer.x,std::memory_order_relaxed);
        gPlayerZSnapshot.store(gPlayer.z,std::memory_order_relaxed);
        if(gDropTimer>=PLANE_COUNTDOWN_SECONDS)gPlaneExteriorView=true;
        const bool atRamp=gPlaneWalkX<=-12.8f && std::fabs(gPlaneWalkZ)<=4.2f;
        const bool playerJump=jumpPressed&&gDropTimer>=PLANE_COUNTDOWN_SECONDS&&atRamp;
        const bool forcedJump=gDropTimer>=34.0f;
        if(jumpPressed&&gDropTimer>=PLANE_COUNTDOWN_SECONDS&&!atRamp)
            setStatus("RAMPA NA TRASEIRA • ANDE ATÉ A SAÍDA PARA SALTAR");
        if(playerJump||forcedJump) {
            if(forcedJump&&!atRamp) {
                gPlaneWalkX=-14.4f;gPlaneWalkZ=0.0f;
                gPlayer.x=gPlanePosition.x+gPlaneWalkX;gPlayer.z=gPlanePosition.z;
            }
            gDropState=2;gJumpHeight=22.0f;gJumpVelocity=0.0f;gParachuteOpen=false;
            gTargets[0]={{gPlayer.x,1.25f,gPlayer.z-18.0f},3,true};
            gTargets[1]={{gPlayer.x-16.0f,1.25f,gPlayer.z-27.0f},3,true};
            gTargets[2]={{gPlayer.x+17.0f,1.25f,gPlayer.z-28.0f},3,true};
            resetBots();
            for(int i=0;i<BOT_COUNT;++i) {
                const size_t n=static_cast<size_t>(i);
                gBotDropHeight[n]=27.0f+static_cast<float>((i*7)%8)*4.0f;
                gBotDropSpeed[n]=2.8f+static_cast<float>(i%4)*0.22f;
                gBotDropCanopy[n]=true;
            }
            updateStatus();
            glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(gProgram);
            return;
        }
        glClearColor(0.34f,0.66f,0.88f,1.0f);
        glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(gProgram);
        Vec3 cabinPlayer{gPlayer.x,gPlanePosition.y-0.54f,gPlayer.z};
        Vec3 eye{cabinPlayer.x+std::sin(gCameraYaw)*5.2f,
                 cabinPlayer.y+2.55f+gCameraPitch*0.65f,
                 cabinPlayer.z+std::cos(gCameraYaw)*5.2f};
        Vec3 target{cabinPlayer.x-std::sin(gCameraYaw)*4.8f,
                    cabinPlayer.y+1.25f,
                    cabinPlayer.z-std::cos(gCameraYaw)*4.8f};
        Mat4 view=lookAt(eye,target,{0,1,0});
        gProjection=multiply(perspective(1.05f,static_cast<float>(gWidth)/gHeight,0.1f,360.0f),view);
        drawWorld(gMatchTime);
        drawTransportPlaneCabin(gPlanePosition,gMatchTime);
        const std::array<Vec3,4> cabinOutfits{{
            {0.035f,0.66f,0.88f},{0.10f,0.18f,0.23f},
            {0.52f,0.12f,0.88f},{0.82f,0.55f,0.08f}
        }};
        drawCharacter(cabinPlayer,gPlayerYaw,cabinOutfits[static_cast<size_t>(gSelectedCharacter)],
            false,gMatchTime,movement>0.08f?std::min(1.0f,movement):0.0f,false,false,false);
        static float planeStatusTimer=0.0f;planeStatusTimer+=dt;
        if(planeStatusTimer>0.25f){planeStatusTimer=0.0f;updateStatus();}
        publishFlightInfo();
        glDisableVertexAttribArray(0);glDisableVertexAttribArray(1);glBindBuffer(GL_ARRAY_BUFFER,0);
        return;
    }

    float dx=0.0f,dz=0.0f;
    float moveLength=0.0f;
    bool moving=false;
    if(gDropState==2) {
        for(int i=0;i<BOT_COUNT;++i) {
            const size_t n=static_cast<size_t>(i);
            if(gBotDropHeight[n]<=0.0f)continue;
            const float phase=gBots[n].phase+gMatchTime*0.28f;
            gBots[n].pos.x=gBotHomes[n].x+std::sin(phase)*2.8f;
            gBots[n].pos.z=gBotHomes[n].z+std::cos(phase)*2.8f;
            gBotDropHeight[n]=std::max(0.0f,gBotDropHeight[n]-gBotDropSpeed[n]*dt);
            if(gBotDropHeight[n]<=0.0f)gBotDropCanopy[n]=false;
        }
        if(!gParachuteOpen) {
            gJumpVelocity-=9.8f*dt;
            if(gJumpHeight<12.0f) {
                gParachuteOpen=true;
                gJumpVelocity=std::max(gJumpVelocity,-2.8f);
            }
        } else {
            gJumpVelocity=std::max(-3.0f,gJumpVelocity-0.8f*dt);
        }
        gJumpHeight+=gJumpVelocity*dt;
        // Small air steering keeps the player responsive while descending.
        const float forward=static_cast<float>(moveY),strafe=static_cast<float>(moveX);
        dx=std::cos(gCameraYaw)*strafe-std::sin(gCameraYaw)*forward;
        dz=std::sin(gCameraYaw)*strafe-std::cos(gCameraYaw)*forward;
        moveLength=std::sqrt(dx*dx+dz*dz);
        if(moveLength>0.08f) {
            dx/=std::max(1.0f,moveLength);dz/=std::max(1.0f,moveLength);
            const float nx=std::clamp(gPlayer.x+dx*2.4f*dt,-WORLD_LIMIT+1.0f,WORLD_LIMIT-1.0f);
            const float nz=std::clamp(gPlayer.z+dz*2.4f*dt,-WORLD_LIMIT+1.0f,WORLD_LIMIT-1.0f);
            moveWithCollision(gPlayer,nx,nz,0.42f);
            gPlayerYaw=std::atan2(-dx,-dz);
        }
        if(gJumpHeight<=0.0f) {
            gJumpHeight=0.0f;gJumpVelocity=0.0f;gParachuteOpen=false;gDropState=3;
            gPlayerXSnapshot.store(gPlayer.x);gPlayerZSnapshot.store(gPlayer.z);
            updateStatus();
        }
    } else {
        const float forward=static_cast<float>(moveY),strafe=static_cast<float>(moveX);
        dx=std::cos(gCameraYaw)*strafe-std::sin(gCameraYaw)*forward;
        dz=std::sin(gCameraYaw)*strafe-std::cos(gCameraYaw)*forward;
        moveLength=std::sqrt(dx*dx+dz*dz);
        moving=moveLength>0.08f;
        if(moving) {
            dx/=std::max(1.0f,moveLength);dz/=std::max(1.0f,moveLength);
            const float speed=gPlayerDowned?0.85f:(gCrouched?2.35f:((buttons&BUTTON_RUN)?8.0f:5.2f));
            const float nx=std::clamp(gPlayer.x+dx*speed*dt,-WORLD_LIMIT+1.0f,WORLD_LIMIT-1.0f);
            const float nz=std::clamp(gPlayer.z+dz*speed*dt,-WORLD_LIMIT+1.0f,WORLD_LIMIT-1.0f);
            moveWithCollision(gPlayer,nx,nz,0.42f);
            // Match the model's -Z forward axis to the actual travel direction.
            gPlayerYaw=std::atan2(-dx,-dz);
            gPlayerXSnapshot.store(gPlayer.x,std::memory_order_relaxed);
            gPlayerZSnapshot.store(gPlayer.z,std::memory_order_relaxed);
        }
        if(jumpPressed&&gJumpHeight<=0.001f)gJumpVelocity=6.2f;
        gJumpHeight+=gJumpVelocity*dt;gJumpVelocity-=16.0f*dt;
        if(gJumpHeight<0){gJumpHeight=0;gJumpVelocity=0;}
    }

    if(aimHeld&&gDropState>=2) gPlayerYaw=gCameraYaw;
    if(swapPressed&&gDropState==3) {
        int next=-1;
        for(int step=1;step<=4;++step) {
            const int candidate=(gWeaponType+step+4)%4;
            if(gWeaponOwned[static_cast<size_t>(candidate)]){next=candidate;break;}
        }
        if(next>=0){gWeaponType=next;setStatus(std::string("EQUIPADO: ")+weaponName());}
        else setStatus("SEM ARMAS • PEGUE UM EQUIPAMENTO NO CHÃO");
    }
    gInteractCooldown=std::max(0.0f,gInteractCooldown-dt);
    if(interactPressed&&gDropState==3&&gInteractCooldown<=0.0f) {
        int nearest=-1;float bestDistance=4.5f;
        for(int i=0;i<gResourceCount;++i) {
            if(gCrystalTaken[static_cast<size_t>(i)])continue;
            float ox=gPlayer.x-gCrystals[static_cast<size_t>(i)].x;
            float oz=gPlayer.z-gCrystals[static_cast<size_t>(i)].z;
            float d=std::sqrt(ox*ox+oz*oz);
            if(d<bestDistance){bestDistance=d;nearest=i;}
        }
        int lootIndex=-1;float lootDistance=4.8f;
        for(int i=0;i<static_cast<int>(gGroundLoot.size());++i) {
            const GroundLoot& loot=gGroundLoot[static_cast<size_t>(i)];
            if(!loot.active)continue;
            const float lx=gPlayer.x-loot.pos.x,lz=gPlayer.z-loot.pos.z;
            const float distance=std::sqrt(lx*lx+lz*lz);
            if(distance<lootDistance){lootDistance=distance;lootIndex=i;}
        }
        if(lootIndex>=0) {
            GroundLoot& loot=gGroundLoot[static_cast<size_t>(lootIndex)];
            const size_t type=static_cast<size_t>(std::clamp(loot.weaponType,0,3));
            gWeaponAmmo[type]=std::min(240,gWeaponAmmo[type]+loot.ammo);
            if(!loot.ammoOnly) {
                gWeaponOwned[type]=true;gWeaponType=static_cast<int>(type);
                setStatus(std::string("ARMA COLETADA: ")+weaponName()+" • "+std::to_string(gWeaponAmmo[type])+" CARGAS");
            } else {
                setStatus("MUNIÇÃO COLETADA • "+std::to_string(gWeaponAmmo[type])+" CARGAS");
            }
            loot.active=false;gInteractCooldown=0.35f;
        } else if(nearest>=0) {
            gCrystalTaken[static_cast<size_t>(nearest)]=true;++gCollected;
            gWood=std::min(250,gWood+20);gInteractCooldown=0.35f;
            setStatus("RECURSO COLETADO  •  +20 MADEIRA");
        } else setStatus("NADA PRÓXIMO • PROCURE ARMAS, MUNIÇÃO OU CRISTAIS");
    }
    if(buildPressed&&gDropState==3&&!gPlayerDowned) {
        if(!gBuildPreview){gBuildPreview=true;setStatus("PRÉVIA DE GELO • TOQUE CONSTRUIR NOVAMENTE PARA CONFIRMAR");}
        else {placeBuildPiece();gBuildPreview=false;gBuildCooldown=0.14f;}
    }
    // Segurar DISPARAR mantém o disparo automático; um toque rápido dispara uma vez.
    if(pulseHeld&&gDropState==3&&!gPlayerDowned&&gPulseCooldown<=0.0f) {
        firePulse();
        // Disparo automático contínuo enquanto o botão estiver pressionado.
        // Cadência curta por arma, sem bloqueio longo do botão de disparo.
        const float cadence[4]={0.12f,0.095f,0.42f,0.075f};
        const int firedType=std::clamp(gWeaponType,0,3);
        gPulseCooldown=cadence[firedType];
    }

    if(gDropState==3) {
        updateBots(dt);
        updateDownedState(dt);
        // Reviver controlado pelo jogador: mantenha USAR pressionado perto do aliado caído.
        if(!interactHeld){gPlayerReviveTarget=-1;gPlayerReviveProgress=0.0f;}
        if(interactHeld&&!gPlayerDowned&&(gSelectedMode==1||gSelectedMode==2)) {
            if(gPlayerReviveTarget<0||gPlayerReviveTarget>=BOT_COUNT||!gBotDowned[static_cast<size_t>(gPlayerReviveTarget)]) {
                gPlayerReviveTarget=-1;float nearest=5.0f;
                for(int i=0;i<BOT_COUNT;++i)if(isAllyBot(i)&&gBotDowned[static_cast<size_t>(i)]){
                    float dx=gBots[static_cast<size_t>(i)].pos.x-gPlayer.x,dz=gBots[static_cast<size_t>(i)].pos.z-gPlayer.z;
                    float d=std::sqrt(dx*dx+dz*dz);if(d<nearest){nearest=d;gPlayerReviveTarget=i;}}
            }
            if(gPlayerReviveTarget>=0){int i=gPlayerReviveTarget;float dx=gBots[static_cast<size_t>(i)].pos.x-gPlayer.x,dz=gBots[static_cast<size_t>(i)].pos.z-gPlayer.z;
                if(dx*dx+dz*dz<=5.0f*5.0f){gPlayerReviveProgress+=dt;std::ostringstream st;st<<"REVIVENDO ALIADO "<<static_cast<int>(std::min(100.0f,gPlayerReviveProgress*25.0f))<<"% • MANTENHA USAR";setStatus(st.str());
                    if(gPlayerReviveProgress>=4.0f){gBotDowned[static_cast<size_t>(i)]=false;gBotHealth[static_cast<size_t>(i)]=BOT_MAX_HEALTH/2;gBotActive[static_cast<size_t>(i)]=true;gBotDownedTimer[static_cast<size_t>(i)]=0;gBotReviveProgress[static_cast<size_t>(i)]=0;gPlayerReviveTarget=-1;gPlayerReviveProgress=0;gInteractCooldown=0.5f;setStatus("ALIADO REVIVIDO • VIDA 50%");}
                }else{gPlayerReviveTarget=-1;gPlayerReviveProgress=0;}}
        }
        // Aliados controlados pela IA também socorrem companheiros caídos próximos.
        for(int down=0;down<BOT_COUNT;++down)if(gBotDowned[static_cast<size_t>(down)]){
            gBotDownedTimer[static_cast<size_t>(down)]=std::max(0.0f,gBotDownedTimer[static_cast<size_t>(down)]-dt);
            float nearest=999.0f;
            for(int ally=0;ally<BOT_COUNT;++ally)if(ally!=down&&sameBotTeam(down,ally)&&gBotActive[static_cast<size_t>(ally)]&&!gBotDowned[static_cast<size_t>(ally)]){
                float dx=gBots[static_cast<size_t>(ally)].pos.x-gBots[static_cast<size_t>(down)].pos.x,dz=gBots[static_cast<size_t>(ally)].pos.z-gBots[static_cast<size_t>(down)].pos.z;
                float d=std::sqrt(dx*dx+dz*dz);if(d<nearest)nearest=d;}
            if(nearest<=3.6f){gBotReviveProgress[static_cast<size_t>(down)]+=dt;}
            else gBotReviveProgress[static_cast<size_t>(down)]=0.0f;
            if(gBotReviveProgress[static_cast<size_t>(down)]>=4.0f){gBotDowned[static_cast<size_t>(down)]=false;gBotHealth[static_cast<size_t>(down)]=BOT_MAX_HEALTH/2;gBotActive[static_cast<size_t>(down)]=true;gBotDownedTimer[static_cast<size_t>(down)]=0;gBotReviveProgress[static_cast<size_t>(down)]=0;setStatus("IA ALIADA REVIVIDA PELO ESQUADRÃO");}
            else if(gBotDownedTimer[static_cast<size_t>(down)]<=0){
                // O próprio jogador vivo conta como socorrista do aliado do seu time.
                bool squadMateAlive=isAllyBot(down)&&!gPlayerDowned;
                for(int mate=0;mate<BOT_COUNT;++mate) if(mate!=down&&sameBotTeam(down,mate)
                        &&gBotActive[static_cast<size_t>(mate)]&&!gBotDowned[static_cast<size_t>(mate)]) squadMateAlive=true;
                if(!squadMateAlive) {
                    gBotDowned[static_cast<size_t>(down)]=false;
                    gBotActive[static_cast<size_t>(down)]=false;
                    ++gAiEliminations;
                } else {
                    gBotDownedTimer[static_cast<size_t>(down)]=10.0f;
                }
            }
        }
        if(gSelectedMode!=3&&!gMatchFinished&&!gPlayerDowned) {
            gZoneDamageTimer=std::max(0.0f,gZoneDamageTimer-dt);
            const float fromCenter=std::sqrt(gPlayer.x*gPlayer.x+gPlayer.z*gPlayer.z);
            if(fromCenter>gZoneRadius&&gZoneDamageTimer<=0.0f) {
                applyPlayerDamage(3,"ELIMINADO PELA ZONA • FIM DA PARTIDA");
                gZoneDamageTimer=1.0f;
            }
        }
    }
    gPlayerXSnapshot.store(gPlayer.x,std::memory_order_relaxed);
    gPlayerZSnapshot.store(gPlayer.z,std::memory_order_relaxed);
    // Mira em terceira pessoa: câmera mais afastada e deslocada para o ombro,
    // deixando a cabeça do operador fora do centro do retículo.
    const float cameraDistance=aimHeld?4.15f:8.5f;
    const float cameraHeight=aimHeld?1.78f:(gCrouched?2.75f:(3.7f+gCameraPitch*3.1f));
    const float shoulder=aimHeld?1.08f:0.0f;
    Vec3 cameraSubject=gPlayer;
    if(gSpectatorMode) {
        if(gSpectatorTarget<0||gSpectatorTarget>=BOT_COUNT||!gBotActive[static_cast<size_t>(gSpectatorTarget)]) {
            gSpectatorTarget=-1;
            for(int i=0;i<BOT_COUNT;++i)if(gBotActive[static_cast<size_t>(i)]&&!isAllyBot(i)){gSpectatorTarget=i;break;}
        }
        if(gSpectatorTarget>=0)cameraSubject=gBots[static_cast<size_t>(gSpectatorTarget)].pos;
    }
    // Durante a queda, a câmera acompanha toda a altitude do jogador.
    // O antigo fator 0.40 deixava a câmera muito abaixo do personagem no ar.
    const float cameraVerticalFollow=(gDropState==2)?gJumpHeight:(gJumpHeight*0.40f);
    Vec3 eye{
        cameraSubject.x+std::sin(gCameraYaw)*(gSpectatorMode?10.0f:cameraDistance)+std::cos(gCameraYaw)*shoulder,
        cameraSubject.y+(gSpectatorMode?5.0f:cameraHeight)+cameraVerticalFollow,
        cameraSubject.z+std::cos(gCameraYaw)*(gSpectatorMode?10.0f:cameraDistance)-std::sin(gCameraYaw)*shoulder
    };
    const float lookAhead=aimHeld?28.0f:0.0f;
    const float aimVertical=aimHeld?(gCameraPitch-0.36f)*6.0f:0.0f;
    Vec3 target{
        cameraSubject.x-std::sin(gCameraYaw)*(gSpectatorMode?0.0f:lookAhead),
        cameraSubject.y+(gSpectatorMode?1.1f:(aimHeld?1.38f+aimVertical:1.0f))+cameraVerticalFollow,
        cameraSubject.z-std::cos(gCameraYaw)*(gSpectatorMode?0.0f:lookAhead)
    };
    Mat4 view=lookAt(eye,target,{0,1,0});
    gProjection=multiply(perspective(aimHeld?0.90f:0.93f,static_cast<float>(gWidth)/gHeight,0.1f,260.0f),view);

    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(gProgram);
    drawWorld(gMatchTime);
    for(int i=0;i<gResourceCount;++i)
        if(!gCrystalTaken[static_cast<size_t>(i)])
            drawCrystal(gCrystals[static_cast<size_t>(i)],gMatchTime,i);
    drawTargets(gMatchTime);drawBuildPieces();drawBuildPreview();drawGroundLoot(gMatchTime);
    if(gWaypointActive&&gDropState==3) {
        drawCylinder({gWaypoint.x,0.72f,gWaypoint.z},{0.32f,0.65f,0.32f},{0.10f,0.90f,0.82f});
        drawSegment({0,0,0},0.0f,{gWaypoint.x,1.05f,gWaypoint.z},
            {gWaypoint.x,7.8f,gWaypoint.z},0.075f,{0.10f,0.90f,0.82f});
        drawSphere({gWaypoint.x,8.1f,gWaypoint.z},{0.48f,0.48f,0.48f},{0.95f,0.78f,0.20f});
    }

    const std::array<Vec3,4> outfits{{{0.035f,0.66f,0.88f},{0.10f,0.18f,0.23f},
        {0.52f,0.12f,0.88f},{0.82f,0.55f,0.08f}}};
    for(int i=0;i<BOT_COUNT;++i) {
        if(!gBotActive[static_cast<size_t>(i)])continue;
        const BotState& bot=gBots[static_cast<size_t>(i)];
        Vec3 botColor=isAllyBot(i)?Vec3{0.16f,0.62f,0.43f}:Vec3{0.82f,0.34f,0.19f};
        const float playerDx=bot.pos.x-gPlayer.x;
        const float playerDz=bot.pos.z-gPlayer.z;
        const float botDistance2=playerDx*playerDx+playerDz*playerDz;
        const float renderDistance=gQuality==0?64.0f:(gQuality==1?88.0f:112.0f);
        if(botDistance2>renderDistance*renderDistance)continue;
        const bool botAiming=bot.aiming||(!isAllyBot(i)&&botDistance2<34.0f*34.0f);
        Vec3 botDrawPos=bot.pos;
        if(gBotDowned[static_cast<size_t>(i)]) {
            drawCube({bot.pos.x,bot.pos.y+0.28f,bot.pos.z},{0.96f,0.34f,0.58f},{0.18f,0.72f,0.60f},bot.yaw);
            drawSphere({bot.pos.x,bot.pos.y+0.42f,bot.pos.z-0.43f},{0.28f,0.28f,0.28f},{0.88f,0.70f,0.55f});
            drawCube({bot.pos.x+0.42f,bot.pos.y+0.14f,bot.pos.z+0.10f},{0.44f,0.18f,0.22f},{0.12f,0.48f,0.38f},bot.yaw);
            drawSphere({bot.pos.x,bot.pos.y+0.08f,bot.pos.z},{1.25f,0.08f,1.25f},{0.10f,0.82f,0.70f});
            continue;
        }
        botDrawPos.y+=gBotDropHeight[static_cast<size_t>(i)];
        drawCharacter(botDrawPos,bot.yaw,botColor,true,
            gMatchTime+bot.phase,std::min(1.0f,bot.speed/4.6f),
            botAiming,bot.fireFlash>0.0f,false);
        if(gBotDropCanopy[static_cast<size_t>(i)]&&gBotDropHeight[static_cast<size_t>(i)]>0.0f) {
            const Vec3 canopy{botDrawPos.x,botDrawPos.y+5.0f,botDrawPos.z};
            drawSphere(canopy,{2.25f,0.72f,2.25f},{0.86f,0.25f,0.18f});
            drawSegment({0,0,0},0.0f,
                {botDrawPos.x-0.28f,botDrawPos.y+1.65f,botDrawPos.z},
                {canopy.x-1.35f,canopy.y-0.18f,canopy.z},0.022f,{0.90f,0.90f,0.85f});
            drawSegment({0,0,0},0.0f,
                {botDrawPos.x+0.28f,botDrawPos.y+1.65f,botDrawPos.z},
                {canopy.x+1.35f,canopy.y-0.18f,canopy.z},0.022f,{0.90f,0.90f,0.85f});
        }
        if(bot.fireFlash>0.0f) {
            const Vec3 muzzle=localPoint(bot.pos,0.0f,1.32f,-1.18f,bot.yaw);
            Vec3 impact=gPlayer;
            if(!bot.targetPlayer&&bot.targetIndex>=0&&bot.targetIndex<BOT_COUNT)
                impact=gBots[static_cast<size_t>(bot.targetIndex)].pos;
            impact.y+=1.25f+(bot.targetPlayer?gJumpHeight:0.0f);
            drawSegment({0,0,0},0.0f,muzzle,impact,0.055f,{0.18f,0.88f,1.0f});
        }
        drawCube({bot.pos.x,bot.pos.y+2.72f,bot.pos.z},
            {0.70f*static_cast<float>(gBotHealth[static_cast<size_t>(i)])/static_cast<float>(BOT_MAX_HEALTH),0.07f,0.07f},
            isAllyBot(i)?Vec3{0.20f,0.90f,0.55f}:Vec3{0.95f,0.30f,0.20f},bot.yaw);
    }
    // Dano flutuante: permanece visível por pouco tempo mesmo se a IA for eliminada.
    glDisable(GL_DEPTH_TEST);
    for(int i=0;i<BOT_COUNT;++i) {
        if(!gBotActive[static_cast<size_t>(i)]||gBotDamageTimer[static_cast<size_t>(i)]<=0.0f) continue;
        const float elapsed=1.20f-gBotDamageTimer[static_cast<size_t>(i)];
        const float rise=elapsed*1.35f;
        if(gWeaponType==2)gCrashTraceStage.store(110,std::memory_order_relaxed);
        drawDamagePopup(gBotDamagePosition[static_cast<size_t>(i)],
            gBotDamageAmount[static_cast<size_t>(i)],gCameraYaw,rise);
        if(gWeaponType==2)gCrashTraceStage.store(111,std::memory_order_relaxed);
    }
    glEnable(GL_DEPTH_TEST);

    // ESP final quando restarem cinco participantes no total, contando o jogador.
    int remainingPlayers=1;
    for(int i=0;i<BOT_COUNT;++i) if(gBotActive[static_cast<size_t>(i)]) ++remainingPlayers;
    if(gSelectedMode!=3 && remainingPlayers<=5) {
        glDisable(GL_DEPTH_TEST);
        for(int i=0;i<BOT_COUNT;++i) {
            if(isAllyBot(i)||!gBotActive[static_cast<size_t>(i)]) continue;
            const Vec3 pos=gBots[static_cast<size_t>(i)].pos;
            const Vec3 markerBase{pos.x,pos.y+3.2f,pos.z};
            const Vec3 markerTop{pos.x,pos.y+12.0f,pos.z};
            drawSegment({0,0,0},0.0f,markerBase,markerTop,0.18f,{1.0f,0.12f,0.06f});
            drawSphere(markerTop,{1.05f,1.05f,1.05f},{1.0f,0.18f,0.08f});
        }
        glEnable(GL_DEPTH_TEST);
    }
    Vec3 playerDraw=gPlayer;playerDraw.y+=gJumpHeight;
    if(gWeaponType==2)gCrashTraceStage.store(112,std::memory_order_relaxed);
    if(!gSpectatorMode) drawCharacter(playerDraw,gPlayerYaw,outfits[static_cast<size_t>(gSelectedCharacter)],
        false,gMatchTime,moving?std::min(1.0f,moveLength):0.0f,
        aimHeld,gPulseFlash>0.0f,(buttons&BUTTON_RUN)!=0);
    if(gWeaponType==2)gCrashTraceStage.store(113,std::memory_order_relaxed);
    drawParachute(gPlayer);

    static float statusTimer=0;statusTimer+=dt;
    if(statusTimer>=0.85f){statusTimer=0;updateStatus();}
    publishMatchStats();
    publishFlightInfo();
    glDisableVertexAttribArray(0);glDisableVertexAttribArray(1);glBindBuffer(GL_ARRAY_BUFFER,0);
    if(gWeaponType==2)gCrashTraceStage.store(120,std::memory_order_relaxed);
}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeGetFlightInfo(JNIEnv* env,jclass) {
    jfloat values[10]={
        static_cast<jfloat>(gDropStateSnapshot.load(std::memory_order_relaxed)),
        gPlaneXSnapshot.load(std::memory_order_relaxed),gPlaneZSnapshot.load(std::memory_order_relaxed),
        gPlayerXSnapshot.load(std::memory_order_relaxed),gPlayerZSnapshot.load(std::memory_order_relaxed),
        gDropTimerSnapshot.load(std::memory_order_relaxed),gZoneRadiusSnapshot.load(std::memory_order_relaxed),
        gWaypointXSnapshot.load(std::memory_order_relaxed),gWaypointZSnapshot.load(std::memory_order_relaxed),
        gWaypointActiveSnapshot.load(std::memory_order_relaxed)?1.0f:0.0f
    };
    jfloatArray result=env->NewFloatArray(10);
    if(result)env->SetFloatArrayRegion(result,0,10,values);
    return result;
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetWaypoint(JNIEnv*,jclass,jfloat worldX,jfloat worldZ) {
    if(!std::isfinite(worldX)||!std::isfinite(worldZ))gWaypointActive=false;
    else {
        gWaypoint.x=std::clamp(static_cast<float>(worldX),-WORLD_LIMIT+3.0f,WORLD_LIMIT-3.0f);
        gWaypoint.y=0.55f;gWaypoint.z=std::clamp(static_cast<float>(worldZ),-WORLD_LIMIT+3.0f,WORLD_LIMIT-3.0f);
        gWaypointActive=true;
    }
    publishFlightInfo();
}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeGetPlayerPosition(JNIEnv* env,jclass) {
    jfloatArray result=env->NewFloatArray(2);
    if(!result)return nullptr;
    jfloat values[2]={
        gPlayerXSnapshot.load(std::memory_order_relaxed),
        gPlayerZSnapshot.load(std::memory_order_relaxed)
    };
    env->SetFloatArrayRegion(result,0,2,values);
    return result;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeGetStatus(JNIEnv* env,jclass) {
    std::lock_guard<std::mutex> lock(gStatusMutex);
    return env->NewStringUTF(gHudStatus.c_str());
}
extern "C" JNIEXPORT jintArray JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeGetMatchStats(JNIEnv* env,jclass) {
    jint values[5]={
        gDamageDealtSnapshot.load(std::memory_order_relaxed),
        gDamageTakenSnapshot.load(std::memory_order_relaxed),
        gAiEliminationsSnapshot.load(std::memory_order_relaxed),
        gAliveEnemiesSnapshot.load(std::memory_order_relaxed),
        gPlayerHealthSnapshot.load(std::memory_order_relaxed)
    };
    jintArray result=env->NewIntArray(5);if(result)env->SetIntArrayRegion(result,0,5,values);return result;
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetSpectator(JNIEnv*,jclass,jboolean enabled) {
    gSpectatorMode=enabled==JNI_TRUE;
    if(gSpectatorMode) {
        gSpectatorTarget=-1;
        for(int i=0;i<BOT_COUNT;++i)if(gBotActive[static_cast<size_t>(i)]&&!isAllyBot(i)){gSpectatorTarget=i;break;}
        gMatchFinished=false;
        setStatus(gSpectatorTarget>=0?"MODO ESPECTADOR • ACOMPANHANDO IA":"MODO ESPECTADOR • PARTIDA ENCERRADA");
    }
}
extern "C" JNIEXPORT jint JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeGetDiagnosticStage(JNIEnv*,jclass) {
    return static_cast<jint>(gCrashTraceStage.load(std::memory_order_relaxed));
}
extern "C" JNIEXPORT jint JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeGetLastEliminationStage(JNIEnv*,jclass) {
    return static_cast<jint>(gLastEliminationStage.load(std::memory_order_relaxed));
}
extern "C" JNIEXPORT jint JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeGetEliminations(JNIEnv*,jclass) {
    return static_cast<jint>(gEliminationsSnapshot.load(std::memory_order_relaxed));
}
extern "C" JNIEXPORT jint JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeGetCollected(JNIEnv*,jclass) {
    return static_cast<jint>(gCollectedSnapshot.load(std::memory_order_relaxed));
}
