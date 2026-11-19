#include <jni.h>
#include <GLES3/gl3.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <array>
#include <cmath>
#include <mutex>
#include <sstream>
#include <iomanip>
#include <string>
#include <vector>

namespace {
constexpr float PI = 3.14159265358979323846f;
constexpr int BOT_COUNT = 29;
constexpr int CRYSTAL_COUNT = 12;
constexpr int BUTTON_JUMP = 1;
constexpr int BUTTON_INTERACT = 2;
constexpr int BUTTON_BUILD = 4;
constexpr int BUTTON_RUN = 8;
constexpr int BUTTON_PULSE = 16;
constexpr int BUTTON_SWAP = 32;
constexpr int BUTTON_AIM = 64;
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

GLuint gProgram=0, gCubeVbo=0, gSphereVbo=0, gCylinderVbo=0, gConeVbo=0;
GLsizei gSphereCount=0, gCylinderCount=0, gConeCount=0;
GLint gMvpLocation=-1, gModelLocation=-1, gColorLocation=-1;
int gScreenMode=0, gSelectedCharacter=0, gSelectedMap=0, gSelectedMode=0, gQuality=1;
Mat4 gProjection=identity();
int gWidth=1, gHeight=1;
Vec3 gPlayer{0,0.55f,0};
std::atomic<float> gPlayerXSnapshot{0.0f},gPlayerZSnapshot{0.0f};
float gPlayerYaw=0, gCameraYaw=0, gCameraPitch=0.36f;
float gJumpHeight=0, gJumpVelocity=0, gMatchTime=0, gZoneRadius=30;
float gInteractCooldown=0;
int gCollected=0, gPreviousButtons=0;
std::array<bool,CRYSTAL_COUNT> gCrystalTaken{};
struct BotState { Vec3 pos; float yaw=0; float speed=0; float phase=0; float fireFlash=0; bool aiming=false; };
std::array<int,BOT_COUNT> gBotHealth{};
std::array<bool,BOT_COUNT> gBotActive{};
std::array<float,BOT_COUNT> gBotRespawn{};
std::array<float,BOT_COUNT> gBotAttackCooldown{};
bool isAllyBot(int index) {
    return (gSelectedMode==1&&index==0)||(gSelectedMode==2&&index<3);
}
std::array<BotState,BOT_COUNT> gBots{};
std::array<Vec3,BOT_COUNT> gBotHomes{{
    {-92,0.55f,-88},{-53,0.55f,-94},{-10,0.55f,-99},{38,0.55f,-92},
    {87,0.55f,-71},{98,0.55f,-18},{88,0.55f,48},{48,0.55f,94},
    {2,0.55f,91},{-47,0.55f,83},{-96,0.55f,42},{-101,0.55f,-22}
}};
struct BuildPiece { Vec3 pos; float yaw=0; int type=0; };
std::array<BuildPiece,BUILD_CAPACITY> gBuildPieces{};
int gBuildCount=0, gBuildType=0, gWood=120, gBuildLimit=12, gResourceCount=5;
bool gContentPackReady=false;
float gBuildCooldown=0;
struct TrainingTarget { Vec3 pos; int energy=3; bool active=true; };
std::array<TrainingTarget,TARGET_COUNT> gTargets{{
    {{-12,1.25f,-13},3,true}, {{13,1.25f,-12},3,true}, {{2,1.25f,18},3,true}
}};
int gPulseAmmo=40, gTrainingScore=0, gWeaponType=0;
int gPlayerHealth=100,gEliminations=0;
bool gMatchFinished=false;
float gPulseCooldown=0, gPulseFlash=0, gDropTimer=0;
float gZoneDamageTimer=0,gLookSensitivity=1.0f;
constexpr float PLANE_COUNTDOWN_SECONDS=7.0f;
int gDropState=0; // 0 lobby, 1 inside transport plane, 2 parachute descent, 3 ground
Vec3 gPlanePosition{-110,24,-32};
Vec3 gPulseStartPosition{0,1.5f,0},gPulseHitPosition{0,1.4f,-8};
bool gParachuteOpen=false;
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
void drawMesh(GLuint vbo,GLsizei count,Vec3 p,Vec3 size,Vec3 color,float yaw=0,float tilt=0,float tiltX=0) {
    if(!gProgram||!vbo||count<=0)return;
    Mat4 model=multiply(translation(p.x,p.y,p.z),
        multiply(rotationY(yaw),multiply(rotationZ(tilt),multiply(rotationX(tiltX),scaling(size.x,size.y,size.z)))));
    Mat4 mvp=multiply(gProjection,model);
    glUniformMatrix4fv(gMvpLocation,1,GL_FALSE,mvp.m);
    glUniformMatrix4fv(gModelLocation,1,GL_FALSE,model.m);
    glUniform4f(gColorLocation,color.x,color.y,color.z,1.0f);
    glBindBuffer(GL_ARRAY_BUFFER,vbo);
    glEnableVertexAttribArray(0); glEnableVertexAttribArray(1);
    glVertexAttribPointer(0,3,GL_FLOAT,GL_FALSE,6*sizeof(float),reinterpret_cast<void*>(0));
    glVertexAttribPointer(1,3,GL_FLOAT,GL_FALSE,6*sizeof(float),reinterpret_cast<void*>(3*sizeof(float)));
    glDrawArrays(GL_TRIANGLES,0,count);
}
void drawCube(Vec3 p,Vec3 size,Vec3 color,float yaw=0,float tilt=0,float tiltX=0) {
    drawMesh(gCubeVbo,36,p,size,color,yaw,tilt,tiltX);
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
void drawBuilding(float x,float z,float w,float d,float h,Vec3 wall,Vec3 roof) {
    drawCube({x,0.55f+h*0.5f,z},{w,h,d},wall);
    drawCube({x,0.55f+h+0.14f,z},{w+0.35f,0.28f,d+0.35f},roof);
    drawCube({x,1.45f,z-d*0.5f-0.025f},{0.95f,1.8f,0.08f},{0.18f,0.22f,0.28f});
    drawCube({x-w*0.28f,2.7f,z-d*0.5f-0.04f},{0.85f,0.72f,0.07f},{0.12f,0.66f,0.79f});
    drawCube({x+w*0.28f,2.7f,z-d*0.5f-0.04f},{0.85f,0.72f,0.07f},{0.12f,0.66f,0.79f});
}
void drawWorld(float time) {
    // Expanded island arena: broad coast, inland districts, roads and forest belts.
    drawSphere({0,-1.05f,0},{420,0.55f,420},{0.025f,0.22f,0.36f});
    Vec3 beach=gSelectedMap==2?Vec3{0.68f,0.47f,0.29f}:Vec3{0.78f,0.68f,0.44f};
    Vec3 land=gSelectedMap==1?Vec3{0.19f,0.24f,0.31f}:
        (gSelectedMap==2?Vec3{0.49f,0.37f,0.24f}:Vec3{0.17f,0.43f,0.23f});
    drawSphere({0,-0.02f,0},{146,0.62f,146},beach);
    drawSphere({0,0.31f,0},{128,0.31f,128},land);

    // Long roads create readable navigation across the larger play space.
    Vec3 road=gSelectedMap==2?Vec3{0.40f,0.37f,0.32f}:Vec3{0.34f,0.37f,0.39f};
    drawCube({0,0.535f,0},{8.0f,0.06f,250.0f},road);
    drawCube({0,0.536f,0},{250.0f,0.06f,8.0f},road);
    drawCube({-48,0.536f,-48},{5.0f,0.06f,120.0f},road,0.60f);
    drawCube({51,0.536f,48},{5.0f,0.06f,120.0f},road,-0.60f);
    drawCube({0,0.537f,-82},{110.0f,0.06f,6.0f},road);
    drawCube({0,0.537f,83},{110.0f,0.06f,6.0f},road);

    const std::array<Vec3,16> buildings{{
        {-76,0,-70},{-38,0,-76},{12,0,-78},{57,0,-73},
        {79,0,-22},{-81,0,-18},{-72,0,35},{-34,0,72},
        {15,0,76},{64,0,72},{32,0,-30},{-38,0,-24},
        {-7,0,37},{78,0,35},{-91,0,82},{91,0,-86}
    }};
    for(size_t i=0;i<buildings.size();++i) {
        Vec3 tint=gSelectedMap==1?Vec3{0.27f+0.025f*(i%3),0.30f,0.38f}:
            (gSelectedMap==2?Vec3{0.64f,0.48f,0.31f}:Vec3{0.66f,0.61f,0.48f});
        Vec3 roof=gSelectedMap==1?Vec3{0.11f,0.18f,0.28f}:
            (gSelectedMap==2?Vec3{0.35f,0.23f,0.16f}:Vec3{0.27f,0.20f,0.16f});
        float width=7.0f+static_cast<float>(i%3)*2.1f;
        float depth=6.0f+static_cast<float>(i%2)*2.0f;
        float height=4.0f+static_cast<float>(i%4)*1.5f;
        drawBuilding(buildings[i].x,buildings[i].z,width,depth,height,tint,roof);
        drawCube({buildings[i].x,0.72f+height+0.30f,buildings[i].z},
            {width*0.52f,0.22f,depth*0.55f},
            gSelectedMap==1?Vec3{0.14f,0.74f,0.83f}:Vec3{0.35f,0.40f,0.43f});
        if(i%3==0)
            drawCylinder({buildings[i].x+width*0.30f,height+1.0f,buildings[i].z},
                {0.20f,1.4f,0.20f},{0.60f,0.66f,0.69f});
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
        else if(gSelectedMap==1) {
            drawCylinder({trees[i].x,1.2f,trees[i].z},{0.16f,2.3f,0.16f},{0.20f,0.25f,0.26f});
            drawSphere({trees[i].x,2.8f,trees[i].z},{1.2f,1.4f,1.0f},{0.12f,0.24f,0.30f});
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
    drawSphere({-58,0.02f,32},{18,0.22f,27},{0.08f,0.36f,0.48f});
    drawCylinder({53,4.0f,9},{0.28f,7.0f,0.28f},{0.42f,0.43f,0.42f});
    drawSphere({53,7.6f,9},{1.6f,0.5f,1.6f},{0.90f,0.58f,0.22f});
}
Vec3 localPoint(Vec3 base,float lx,float ly,float lz,float yaw) {
    float c=std::cos(yaw), sn=std::sin(yaw);
    return {base.x+c*lx+sn*lz,base.y+ly,base.z-sn*lx+c*lz};
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
    // Bloob: corpo cilíndrico azul, cabeça arredondada e olhos grandes.
    // As poses são calculadas por quadro, sem arquivos de animação externos.
    const float gait=std::clamp(movement,0.0f,1.0f);
    const float phase=time*(sprinting?10.8f:8.2f)+(bot?0.7f:0.0f);
    const float swing=std::sin(phase)*(sprinting?0.46f:0.34f)*gait;
    const float bob=std::sin(phase*2.0f)*0.035f*gait+std::sin(time*1.5f)*0.014f;
    const float recoil=firing?0.105f:0.0f;
    const Vec3 shell=color;
    const Vec3 bootColor{0.91f,0.95f,0.98f};
    const Vec3 dark{0.055f,0.095f,0.13f};
    const Vec3 white{0.95f,0.98f,1.0f};
    const float eyeShift=std::sin(time*1.7f+(bot?1.0f:0.0f))*0.018f;

    Vec3 hipL{-0.19f,0.72f+bob,swing*0.34f};
    Vec3 kneeL{-0.20f,0.43f+bob,swing*0.72f};
    Vec3 footL{-0.20f,0.17f+bob,swing*0.88f};
    Vec3 hipR{0.19f,0.72f+bob,-swing*0.34f};
    Vec3 kneeR{0.20f,0.43f+bob,-swing*0.72f};
    Vec3 footR{0.20f,0.17f+bob,-swing*0.88f};
    drawSegment(p,yaw,hipL,kneeL,0.125f,shell);
    drawSegment(p,yaw,kneeL,footL,0.105f,shell);
    drawSegment(p,yaw,hipR,kneeR,0.125f,shell);
    drawSegment(p,yaw,kneeR,footR,0.105f,shell);
    drawSphere(localPoint(p,-0.20f,0.12f+bob,-0.16f+swing*0.88f,yaw),
        {0.25f,0.15f,0.36f},bootColor,yaw);
    drawSphere(localPoint(p,0.20f,0.12f+bob,-0.16f-swing*0.88f,yaw),
        {0.25f,0.15f,0.36f},bootColor,yaw);

    drawCylinder(localPoint(p,0,0.78f+bob,0,yaw),{0.78f,0.52f,0.72f},shell,yaw);
    drawCylinder(localPoint(p,0,1.20f+bob,0,yaw),{0.82f,0.78f,0.74f},shell,yaw);
    drawCylinder(localPoint(p,0,1.61f+bob,0,yaw),{0.38f,0.18f,0.38f},shell,yaw);
    drawCylinder(localPoint(p,0,1.86f+bob,0,yaw),{0.78f,0.48f,0.72f},shell,yaw);
    drawSphere(localPoint(p,0,2.04f+bob,0,yaw),{0.79f,0.32f,0.73f},shell,yaw);

    drawSphere(localPoint(p,-0.205f,1.99f+bob,-0.322f,yaw),{0.275f,0.285f,0.145f},white,yaw);
    drawSphere(localPoint(p,0.205f,1.99f+bob,-0.322f,yaw),{0.275f,0.285f,0.145f},white,yaw);
    drawSphere(localPoint(p,-0.205f+eyeShift,1.99f+bob,-0.448f,yaw),{0.108f,0.12f,0.066f},dark,yaw);
    drawSphere(localPoint(p,0.205f+eyeShift,1.99f+bob,-0.448f,yaw),{0.108f,0.12f,0.066f},dark,yaw);
    drawSphere(localPoint(p,-0.235f+eyeShift,2.035f+bob,-0.505f,yaw),{0.025f,0.027f,0.018f},white,yaw);
    drawSphere(localPoint(p,0.175f+eyeShift,2.035f+bob,-0.505f,yaw),{0.025f,0.027f,0.018f},white,yaw);

    Vec3 shoulderL{-0.40f,1.48f+bob,0.0f}, shoulderR{0.40f,1.48f+bob,0.0f};
    Vec3 elbowL,handL,elbowR,handR;
    if(aiming) {
        elbowL={-0.31f,1.34f+bob,-0.25f}; handL={-0.15f,1.31f+bob,-0.57f};
        elbowR={0.31f,1.36f+bob,-0.24f}; handR={0.15f,1.31f+bob,-0.58f};
    } else {
        elbowL={-0.50f,1.14f+bob,-swing*0.55f}; handL={-0.47f,0.88f+bob,-swing*0.78f};
        elbowR={0.50f,1.14f+bob,swing*0.55f}; handR={0.47f,0.88f+bob,swing*0.78f};
    }
    drawSegment(p,yaw,shoulderL,elbowL,0.105f,shell);
    drawSegment(p,yaw,elbowL,handL,0.083f,shell);
    drawSegment(p,yaw,shoulderR,elbowR,0.105f,shell);
    drawSegment(p,yaw,elbowR,handR,0.083f,shell);
    drawSphere(localPoint(p,handL.x,handL.y,handL.z,yaw),{0.11f,0.12f,0.11f},shell,yaw);
    drawSphere(localPoint(p,handR.x,handR.y,handR.z,yaw),{0.11f,0.12f,0.11f},shell,yaw);

    drawCube(localPoint(p,0,1.32f+bob,-0.75f+recoil,yaw),
        {0.22f,0.16f,0.52f},{0.055f,0.105f,0.15f},yaw);
    drawCube(localPoint(p,0,1.43f+bob,-0.70f+recoil,yaw),
        {0.12f,0.055f,0.22f},{0.12f,0.73f,0.86f},yaw);
    drawCube(localPoint(p,0,1.32f+bob,-1.05f+recoil,yaw),
        {0.105f,0.105f,0.20f},{0.12f,0.62f,0.78f},yaw);
    drawSphere(localPoint(p,0,1.32f+bob,-1.18f+recoil,yaw),
        {firing?0.14f:0.085f,firing?0.14f:0.085f,firing?0.14f:0.085f},
        firing?Vec3{0.45f,0.98f,1.0f}:Vec3{0.10f,0.80f,0.95f},yaw);
}
void drawCrystal(Vec3 p,float time,int index) {
    float bob=std::sin(time*2.2f+index)*0.16f, spin=time*0.55f+index*0.7f;
    drawCone({p.x,p.y+0.58f+bob,p.z},{0.42f,0.86f,0.42f},{0.12f,0.82f,0.96f},spin);
    drawCone({p.x,p.y+0.96f+bob,p.z},{0.24f,0.48f,0.24f},{0.45f,0.98f,1.0f},spin+PI);
    drawSphere({p.x,p.y+0.58f+bob,p.z},{0.48f,0.08f,0.48f},{0.08f,0.55f,0.68f});
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
    static const char* names[]={"PULSO PLASMA","RAIO ARC","NOVA"};
    return names[std::clamp(gWeaponType,0,2)];
}
void updateStatus() {
    std::ostringstream out;
    if(gDropState==1) {
        if(gDropTimer<PLANE_COUNTDOWN_SECONDS) {
            int remaining=static_cast<int>(std::ceil(PLANE_COUNTDOWN_SECONDS-gDropTimer));
            out<<"AVIÃO EM MOVIMENTO  •  SALTO DISPONÍVEL EM "<<remaining<<"s";
        } else {
            out<<"AVIÃO EM ROTA  •  30 PARTICIPANTES  •  TOQUE SALTAR";
        }
    } else if(gDropState==2) {
        out<<(gParachuteOpen?"PARAQUEDAS ABERTO":"QUEDA LIVRE")
           <<"  •  MIRA E CÂMERA LIVRES  •  "<<weaponName();
    } else if(gSelectedMode==3) {
        out<<"TREINAMENTO  •  "<<weaponName()<<"  CARGA "<<gPulseAmmo
           <<"  •  MADEIRA "<<gWood<<"  •  PONTOS "<<gTrainingScore;
    } else {
        int remaining=0;
        for(int i=0;i<BOT_COUNT;++i) if(!isAllyBot(i)&&gBotActive[static_cast<size_t>(i)]) ++remaining;
        out<<"VIDA "<<gPlayerHealth<<"%  •  ELIMINAÇÕES "<<gEliminations
           <<"  •  OPONENTES "<<remaining<<"  •  "<<weaponName()<<" "<<gPulseAmmo;
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
        const float radius=48.0f+static_cast<float>(slot%4)*11.0f;
        gBotHomes[static_cast<size_t>(i)]={std::cos(angle)*radius,0.55f,
            std::sin(angle)*radius};
    }
    const std::array<Vec3,3> allyOffsets{{{-8,0.55f,-7},{8,0.55f,-8},{0,0.55f,9}}};
    gBotHealth.fill(3);
    gBotActive.fill(true);
    gBotRespawn.fill(0.0f);
    for(int i=0;i<BOT_COUNT;++i) {
        if(isAllyBot(i)) {
            const Vec3 offset=allyOffsets[static_cast<size_t>(i%3)];
            gBotHomes[static_cast<size_t>(i)]={
                std::clamp(gPlayer.x+offset.x,-112.0f,112.0f),0.55f,
                std::clamp(gPlayer.z+offset.z,-112.0f,112.0f)};
        }
        gBots[static_cast<size_t>(i)]={gBotHomes[static_cast<size_t>(i)],0,0,static_cast<float>(i)*1.3f};
        gBotAttackCooldown[static_cast<size_t>(i)]=1.2f+static_cast<float>(i%8)*0.38f;
    }
}
void updateBots(float dt) {
    if(gMatchFinished) return;
    for(int i=0;i<BOT_COUNT;++i) {
        BotState& bot=gBots[static_cast<size_t>(i)];
        bot.fireFlash=std::max(0.0f,bot.fireFlash-dt);
        bot.aiming=false;

        if(!gBotActive[static_cast<size_t>(i)]) {
            if(gSelectedMode==3) {
                gBotRespawn[static_cast<size_t>(i)]=std::max(0.0f,gBotRespawn[static_cast<size_t>(i)]-dt);
                if(gBotRespawn[static_cast<size_t>(i)]<=0.0f) {
                    bot.pos=gBotHomes[static_cast<size_t>(i)];
                    bot.speed=0;gBotHealth[static_cast<size_t>(i)]=3;
                    gBotActive[static_cast<size_t>(i)]=true;
                } else continue;
            } else continue;
        }

        const bool ally=isAllyBot(i);
        const float toPlayerX=gPlayer.x-bot.pos.x;
        const float toPlayerZ=gPlayer.z-bot.pos.z;
        const float playerDistance=std::sqrt(toPlayerX*toPlayerX+toPlayerZ*toPlayerZ);
        Vec3 target=bot.pos;
        bool engaging=false;

        if(ally && gDropState==3) {
            const float formationAngle=bot.phase+static_cast<float>(i)*0.75f;
            const float followRadius=7.5f+static_cast<float>(i%2)*2.0f;
            target={gPlayer.x+std::cos(formationAngle)*followRadius,0.55f,
                    gPlayer.z+std::sin(formationAngle)*followRadius};
        } else if(!ally && gSelectedMode!=3 && gDropState==3 && playerDistance<112.0f) {
            // Combat is the first priority. Bots no longer abandon the player to chase floor pickups.
            engaging=true;
            const float radial=playerDistance>0.05f
                ?std::atan2(bot.pos.z-gPlayer.z,bot.pos.x-gPlayer.x):bot.phase;
            const float flank=std::sin(gMatchTime*0.34f+bot.phase)*0.34f;
            const float desiredRadius=11.0f+static_cast<float>(i%6)*1.45f;
            const float angle=radial+flank;
            target={gPlayer.x+std::cos(angle)*desiredRadius,0.55f,
                    gPlayer.z+std::sin(angle)*desiredRadius};
        } else {
            // Before the player lands, bots patrol their own sectors instead of camping on pickups.
            const float phase=gMatchTime*(ally?0.24f:0.17f)+bot.phase;
            const Vec3 home=gBotHomes[static_cast<size_t>(i)];
            const float patrolRadius=ally?4.5f:7.0f;
            target={home.x+std::cos(phase)*patrolRadius,0.55f,
                    home.z+std::sin(phase)*patrolRadius};
        }

        float dx=target.x-bot.pos.x,dz=target.z-bot.pos.z;
        float distance=std::sqrt(dx*dx+dz*dz);
        bot.speed=0.0f;
        if(distance>0.45f) {
            const float inv=1.0f/std::max(0.001f,distance);
            const float dirX=dx*inv,dirZ=dz*inv;
            bot.yaw=std::atan2(-dirX,-dirZ);
            bot.speed=ally?3.0f:(engaging?(playerDistance>24.0f?4.6f:2.2f):2.0f);
            const float step=std::min(distance,bot.speed*dt);
            bot.pos.x+=dirX*step;bot.pos.z+=dirZ*step;
        }

        if(engaging) {
            const float attackDx=gPlayer.x-bot.pos.x;
            const float attackDz=gPlayer.z-bot.pos.z;
            const float attackDistance=std::sqrt(attackDx*attackDx+attackDz*attackDz);
            // Aim at the player even while the bot strafes around its chosen combat position.
            bot.yaw=std::atan2(-attackDx,-attackDz);
            bot.aiming=attackDistance<52.0f;
            gBotAttackCooldown[static_cast<size_t>(i)]=
                std::max(0.0f,gBotAttackCooldown[static_cast<size_t>(i)]-dt);
            if(attackDistance>6.5f && attackDistance<42.0f
                    && gBotAttackCooldown[static_cast<size_t>(i)]<=0.0f) {
                bot.fireFlash=0.20f;
                gPlayerHealth=std::max(0,gPlayerHealth-2);
                gBotAttackCooldown[static_cast<size_t>(i)]=2.4f+static_cast<float>(i%6)*0.32f;
                if(gPlayerHealth<=0) {
                    gMatchFinished=true;
                    setStatus("ELIMINADO • FIM DA PARTIDA");
                } else {
                    std::ostringstream hit;
                    hit<<"DISPARO DE BOT • VIDA "<<gPlayerHealth<<"%";
                    setStatus(hit.str());
                }
            }
        }

        bot.pos.x=std::clamp(bot.pos.x,-116.0f,116.0f);
        bot.pos.z=std::clamp(bot.pos.z,-116.0f,116.0f);

        // Light separation prevents the 29 opponents from stacking into one point.
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
void placeBuildPiece() {
    if(gBuildCount>=gBuildLimit||gBuildCount>=BUILD_CAPACITY) {
        setStatus("LIMITE DE CONSTRUÇÕES ATINGIDO");return;
    }
    if(gWood<10) {setStatus("MADEIRA INSUFICIENTE • COLETE RECURSOS");return;}
    const Vec3 forward{-std::sin(gPlayerYaw),0,-std::cos(gPlayerYaw)};
    Vec3 pos{gPlayer.x+forward.x*4.2f,0.68f,gPlayer.z+forward.z*4.2f};
    // Prevent a new part from occupying the same placement point as another part.
    for(int i=0;i<gBuildCount;++i) {
        float dx=pos.x-gBuildPieces[static_cast<size_t>(i)].pos.x;
        float dz=pos.z-gBuildPieces[static_cast<size_t>(i)].pos.z;
        if(dx*dx+dz*dz<5.0f) {setStatus("LOCAL DE CONSTRUÇÃO BLOQUEADO");return;}
    }
    const int placedType=gBuildType;
    gBuildPieces[static_cast<size_t>(gBuildCount)]={pos,gPlayerYaw,placedType};
    ++gBuildCount;gWood-=10;gBuildType=(gBuildType+1)%3;
    const char* names[]={"PAREDE","PLATAFORMA","RAMPA"};
    std::ostringstream out;out<<names[placedType]<<" CONSTRUÍDA  |  "<<gWood<<" MADEIRA RESTANTE";
    setStatus(out.str());
}
void drawBuildPieces() {
    for(int i=0;i<gBuildCount;++i) {
        const BuildPiece& b=gBuildPieces[static_cast<size_t>(i)];
        if(b.type==0) {
            drawCube({b.pos.x,1.85f,b.pos.z},{3.2f,2.25f,0.24f},{0.50f,0.34f,0.20f},b.yaw);
            drawCube({b.pos.x-0.9f,1.85f,b.pos.z},{0.12f,2.30f,0.32f},{0.70f,0.51f,0.30f},b.yaw);
            drawCube({b.pos.x+0.9f,1.85f,b.pos.z},{0.12f,2.30f,0.32f},{0.70f,0.51f,0.30f},b.yaw);
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
    if(gPulseAmmo<=0){setStatus("SEM CARGA • COLETE ENERGIA");return;}
    --gPulseAmmo;
    gPulseFlash=0.18f;
    gPlayerYaw=gCameraYaw;
    gPulseStartPosition=localPoint(gPlayer,0.49f,1.22f,-0.42f,gCameraYaw);
    const float fx=-std::sin(gCameraYaw),fz=-std::cos(gCameraYaw);
    const float range=gWeaponType==2?42.0f:56.0f;
    const float tolerance=gWeaponType==1?2.7f:2.0f;
    const int damage=gWeaponType==2?3:(gWeaponType==1?2:1);
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
        if(!gBotActive[static_cast<size_t>(i)]||isAllyBot(i))continue;
        const Vec3 pos=gBots[static_cast<size_t>(i)].pos;
        float dx=pos.x-gPlayer.x,dz=pos.z-gPlayer.z;
        float along=dx*fx+dz*fz,side=std::fabs(dx*fz-dz*fx);
        if(along>0.8f&&along<best&&side<tolerance*1.5f) {
            best=along;hitTarget=-1;hitBot=i;
        }
    }
    gPulseHitPosition={gPlayer.x+fx*best,gPlayer.y+1.45f+gJumpHeight,gPlayer.z+fz*best};
    if(hitTarget>=0) {
        TrainingTarget& target=gTargets[static_cast<size_t>(hitTarget)];
        target.energy=std::max(0,target.energy-damage);
        gPulseHitPosition=target.pos;
        if(target.energy==0) {
            target.active=false;gTrainingScore+=100;gWood=std::min(250,gWood+5);
            setStatus("ALVO DE TREINO DESATIVADO  •  +100 PONTOS");
        }
    } else if(hitBot>=0) {
        gBotHealth[static_cast<size_t>(hitBot)]=std::max(0,gBotHealth[static_cast<size_t>(hitBot)]-damage);
        gPulseHitPosition=gBots[static_cast<size_t>(hitBot)].pos;
        if(gBotHealth[static_cast<size_t>(hitBot)]==0) {
            gBotActive[static_cast<size_t>(hitBot)]=false;
            if(gSelectedMode==3) {
                gBotRespawn[static_cast<size_t>(hitBot)]=12.0f;
                gTrainingScore+=150;
            } else {
                gBotRespawn[static_cast<size_t>(hitBot)]=0.0f;
                ++gEliminations;
            }
            gWood=std::min(250,gWood+10);
            int remaining=0;
            for(int i=0;i<BOT_COUNT;++i)
                if(!isAllyBot(i)&&gBotActive[static_cast<size_t>(i)])++remaining;
            if(gSelectedMode!=3&&remaining==0) {
                gMatchFinished=true;
                setStatus("VITÓRIA • TODOS OS OPONENTES ELIMINADOS");
            } else if(gSelectedMode==3) setStatus("ALVO BOT DESATIVADO • +150 PONTOS");
            else setStatus("OPONENTE ELIMINADO • RESTAM "+std::to_string(remaining));
        }
    }
    if(hitTarget<0&&hitBot<0) {
        const char* names[]={"PULSO PLASMA","RAIO ARC","NOVA"};
        std::ostringstream out;out<<names[gWeaponType]<<" • MIRA DA CÂMERA • SEM ALVO NO TRAJETO";
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
        Vec3 boltColor=gWeaponType==2?Vec3{0.92f,0.46f,0.16f}:
            (gWeaponType==1?Vec3{0.38f,0.75f,1.0f}:Vec3{0.25f,0.96f,1.0f});
        drawSegment({0,0,0},0.0f,gPulseStartPosition,gPulseHitPosition,
            gWeaponType==2?0.19f:0.10f,boltColor);
        drawSphere(gPulseHitPosition,{0.33f,0.33f,0.33f},boltColor);
    }
}
void drawTransportPlane(Vec3 pos,float time) {
    // Cabin view: 30 passengers are seated in six rows and five columns.
    drawCube({pos.x-0.35f,pos.y-0.68f,pos.z},{8.7f,0.22f,6.0f},{0.25f,0.30f,0.35f});
    drawCube({pos.x-0.35f,pos.y+0.26f,pos.z-2.95f},{8.7f,1.9f,0.20f},{0.43f,0.48f,0.54f});
    drawCube({pos.x-0.35f,pos.y+0.26f,pos.z+2.95f},{8.7f,1.9f,0.20f},{0.43f,0.48f,0.54f});
    drawCube({pos.x-4.65f,pos.y+0.05f,pos.z},{0.22f,1.7f,5.8f},{0.34f,0.40f,0.46f});
    drawCube({pos.x-4.95f,pos.y-1.05f,pos.z},{1.2f,0.16f,5.5f},{0.54f,0.58f,0.60f},0,0.0f,0.18f);
    drawCone({pos.x+5.55f,pos.y,pos.z},{1.1f,2.0f,1.1f},{0.77f,0.80f,0.83f},0.0f,-PI*0.5f,0.0f);
    drawCube({pos.x-0.15f,pos.y-0.15f,pos.z},{0.55f,0.16f,17.5f},{0.52f,0.58f,0.64f});
    drawCube({pos.x-4.3f,pos.y+0.05f,pos.z},{0.42f,0.13f,7.2f},{0.56f,0.61f,0.66f});
    drawCube({pos.x-4.2f,pos.y+1.0f,pos.z},{2.5f,0.16f,0.22f},{0.38f,0.46f,0.53f});
    drawCylinder({pos.x-1.0f,pos.y-0.72f,pos.z-5.0f},{0.65f,2.2f,0.65f},{0.28f,0.33f,0.38f},0.0f,-PI*0.5f,0.0f);
    drawCylinder({pos.x-1.0f,pos.y-0.72f,pos.z+5.0f},{0.65f,2.2f,0.65f},{0.28f,0.33f,0.38f},0.0f,-PI*0.5f,0.0f);
    for(int i=0;i<5;++i) {
        float x=pos.x-3.2f+i*1.45f;
        drawSphere({x,pos.y+0.32f,pos.z-0.30f},{0.33f,0.15f,0.40f},{0.10f,0.63f,0.79f});
        drawSphere({x,pos.y+0.32f,pos.z+0.30f},{0.33f,0.15f,0.40f},{0.10f,0.63f,0.79f});
    }
    float pulse=0.12f+0.06f*std::sin(time*7.0f);
    drawSphere({pos.x-1.0f,pos.y-0.72f,pos.z-6.3f},{pulse,pulse,pulse},{0.30f,0.78f,1.0f});
    drawSphere({pos.x-1.0f,pos.y-0.72f,pos.z+6.3f},{pulse,pulse,pulse},{0.30f,0.78f,1.0f});
    const std::array<Vec3,4> flightOutfits{{
        {0.035f,0.66f,0.88f},{0.10f,0.18f,0.23f},
        {0.52f,0.12f,0.88f},{0.82f,0.55f,0.08f}
    }};
    for(int i=0;i<=BOT_COUNT;++i) {
        const int row=i/5,col=i%5;
        const float xOffset=-3.45f+static_cast<float>(row)*1.28f;
        const float zOffset=-2.10f+static_cast<float>(col)*1.05f;
        Vec3 passenger{pos.x+xOffset,pos.y-0.62f,pos.z+zOffset};
        const Vec3 outfit=i==0?flightOutfits[static_cast<size_t>(gSelectedCharacter)]:
            flightOutfits[static_cast<size_t>((i-1)%4)];
        drawCharacter(passenger,-PI*0.5f,outfit,true,time+static_cast<float>(i)*0.23f,0.0f);
    }
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
        gPlayer={-110,0.55f,-32};
        gPlayerXSnapshot.store(gPlayer.x);gPlayerZSnapshot.store(gPlayer.z);
        gPlayerYaw=0;gCameraYaw=0;gCameraPitch=0.36f;
        gMatchTime=0;gZoneRadius=112;gCollected=0;gCrystalTaken.fill(false);
        gBuildCount=0;gWood=180;gBuildType=0;gBuildCooldown=0;
        gPulseAmmo=40;gTrainingScore=0;gWeaponType=0;gPulseCooldown=0;gPulseFlash=0;
        gPlayerHealth=100;gEliminations=0;gMatchFinished=false;gZoneDamageTimer=0;
        gDropState=1;gDropTimer=0;gPlanePosition={-110,24,-32};
        gJumpHeight=0;gJumpVelocity=0;gParachuteOpen=false;gPreviousButtons=0;
        gTargets[0]={{0,1.25f,-17},3,true};
        gTargets[1]={{-18,1.25f,-28},3,true};
        gTargets[2]={{20,1.25f,-31},3,true};
        resetBots();updateStatus();
    } else {
        gDropState=0;gJumpHeight=0;gJumpVelocity=0;gParachuteOpen=false;
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetCharacter(JNIEnv*,jclass,jint id) {
    gSelectedCharacter=std::clamp(static_cast<int>(id),0,3);
}
extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetMap(JNIEnv*,jclass,jint id) {
    gSelectedMap=std::clamp(static_cast<int>(id),0,2);
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
    gPulseAmmo=40;gTrainingScore=0;gWeaponType=0;gPulseCooldown=0;gPulseFlash=0;
    gPlayerHealth=100;gEliminations=0;gMatchFinished=false;gZoneDamageTimer=0;
    gBotAttackCooldown.fill(0.0f);gLookSensitivity=1.0f;
    gDropState=0;gDropTimer=0;gPlanePosition={-110,24,-32};gParachuteOpen=false;
    gTargets[0]={{0,1.25f,-10},3,true};
    gTargets[1]={{-10,1.25f,-14},3,true};
    gTargets[2]={{10,1.25f,-14},3,true};
    resetBots();
    glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LEQUAL); glDisable(GL_CULL_FACE);
    glClearColor(0.39f,0.69f,0.85f,1.0f);
    if (!initializeGraphics()) { setStatus("ERRO AO INICIAR OPENGL ES 3.0"); return; }
    updateStatus();
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
    gZoneRadius=std::max(38.0f,112.0f-gMatchTime*0.12f);
    if(gPulseCooldown>0)gPulseCooldown=std::max(0.0f,gPulseCooldown-dt);
    if(gBuildCooldown>0)gBuildCooldown=std::max(0.0f,gBuildCooldown-dt);
    if(gPulseFlash>0)gPulseFlash=std::max(0.0f,gPulseFlash-dt);
    gCameraYaw-=static_cast<float>(lookDX)*4.8f*gLookSensitivity;
    gCameraPitch=std::clamp(gCameraPitch+static_cast<float>(lookDY)*2.2f*gLookSensitivity,0.12f,0.85f);

    const bool jumpPressed=(buttons&BUTTON_JUMP)&&!(gPreviousButtons&BUTTON_JUMP);
    const bool interactPressed=(buttons&BUTTON_INTERACT)&&!(gPreviousButtons&BUTTON_INTERACT);
    const bool buildPressed=(buttons&BUTTON_BUILD)&&!(gPreviousButtons&BUTTON_BUILD);
    const bool pulsePressed=(buttons&BUTTON_PULSE)&&!(gPreviousButtons&BUTTON_PULSE);
    const bool swapPressed=(buttons&BUTTON_SWAP)&&!(gPreviousButtons&BUTTON_SWAP);
    const bool aimHeld=(buttons&BUTTON_AIM)!=0;
    gPreviousButtons=buttons;

    // Battle-royale insertion: the match begins aboard the aircraft.
    if(gDropState==1) {
        gDropTimer+=dt;
        // The aircraft moves from the first frame; the 7-second timer only gates the jump.
        gPlanePosition.x=std::min(112.0f,-110.0f+11.2f*gDropTimer);
        gPlanePosition.y=24.0f+std::sin(gMatchTime*0.72f)*0.22f;
        gPlayer.x=gPlanePosition.x;gPlayer.z=gPlanePosition.z;
        gPlayerXSnapshot.store(gPlayer.x,std::memory_order_relaxed);
        gPlayerZSnapshot.store(gPlayer.z,std::memory_order_relaxed);
        if((jumpPressed&&gDropTimer>=PLANE_COUNTDOWN_SECONDS)||gDropTimer>=19.5f) {
            gDropState=2;gJumpHeight=22.0f;gJumpVelocity=0.0f;
            gParachuteOpen=false;
            gTargets[0]={{gPlayer.x,1.25f,gPlayer.z-18.0f},3,true};
            gTargets[1]={{gPlayer.x-16.0f,1.25f,gPlayer.z-27.0f},3,true};
            gTargets[2]={{gPlayer.x+17.0f,1.25f,gPlayer.z-28.0f},3,true};
            resetBots();
            updateStatus();
        }
        // Elevated interior camera looks down the passenger cabin, not at the plane from outside.
        Vec3 eye{gPlanePosition.x-0.8f,gPlanePosition.y+4.35f,gPlanePosition.z};
        Vec3 target{gPlanePosition.x+3.8f,gPlanePosition.y+0.55f,gPlanePosition.z};
        Mat4 view=lookAt(eye,target,{0,1,0});
        gProjection=multiply(perspective(1.02f,static_cast<float>(gWidth)/gHeight,0.1f,260.0f),view);
        glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(gProgram);
        drawWorld(gMatchTime);drawTransportPlane(gPlanePosition,gMatchTime);
        static float planeStatusTimer=0;planeStatusTimer+=dt;
        if(planeStatusTimer>0.7f){planeStatusTimer=0;updateStatus();}
        glDisableVertexAttribArray(0);glDisableVertexAttribArray(1);glBindBuffer(GL_ARRAY_BUFFER,0);
        return;
    }

    float dx=0.0f,dz=0.0f;
    float moveLength=0.0f;
    bool moving=false;
    if(gDropState==2) {
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
            gPlayer.x=std::clamp(gPlayer.x+dx*2.4f*dt,-116.0f,116.0f);
            gPlayer.z=std::clamp(gPlayer.z+dz*2.4f*dt,-116.0f,116.0f);
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
            const float speed=(buttons&BUTTON_RUN)?8.0f:5.2f;
            float nx=std::clamp(gPlayer.x+dx*speed*dt,-116.0f,116.0f);
            float nz=std::clamp(gPlayer.z+dz*speed*dt,-116.0f,116.0f);
            bool blocked=false;
            for(int i=0;i<gBuildCount;++i) {
                const BuildPiece& piece=gBuildPieces[static_cast<size_t>(i)];
                if(piece.type!=0)continue;
                float rx=nx-piece.pos.x,rz=nz-piece.pos.z;
                float c=std::cos(piece.yaw),sn=std::sin(piece.yaw);
                float localX=c*rx-sn*rz,localZ=sn*rx+c*rz;
                if(std::fabs(localX)<1.75f&&std::fabs(localZ)<0.72f){blocked=true;break;}
            }
            if(!blocked){gPlayer.x=nx;gPlayer.z=nz;}
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
        gWeaponType=(gWeaponType+1)%3;
        const char* names[]={"PULSO PLASMA","RAIO ARC","NOVA"};
        std::string message="EQUIPADO: ";message+=names[gWeaponType];setStatus(message);
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
        if(nearest>=0) {
            gCrystalTaken[static_cast<size_t>(nearest)]=true;++gCollected;
            gWood=std::min(250,gWood+20);gInteractCooldown=0.35f;
            setStatus("RECURSO COLETADO  •  +20 MADEIRA");
        } else setStatus("NENHUM RECURSO PRÓXIMO");
    }
    if(buildPressed&&gDropState==3&&gBuildCooldown<=0.0f) {
        placeBuildPiece();gBuildCooldown=0.32f;
    }
    if(pulsePressed&&gDropState==3&&gPulseCooldown<=0.0f) {
        firePulse();gPulseCooldown=gWeaponType==2?0.55f:0.24f;
    }

    if(gDropState==3) {
        updateBots(dt);
        if(gSelectedMode!=3&&!gMatchFinished) {
            gZoneDamageTimer=std::max(0.0f,gZoneDamageTimer-dt);
            const float fromCenter=std::sqrt(gPlayer.x*gPlayer.x+gPlayer.z*gPlayer.z);
            if(fromCenter>gZoneRadius&&gZoneDamageTimer<=0.0f) {
                gPlayerHealth=std::max(0,gPlayerHealth-3);
                gZoneDamageTimer=1.0f;
                if(gPlayerHealth<=0){gMatchFinished=true;setStatus("ELIMINADO PELA ZONA • FIM DA PARTIDA");}
                else setStatus("FORA DA ZONA SEGURA • VIDA "+std::to_string(gPlayerHealth)+"%");
            }
        }
    }
    gPlayerXSnapshot.store(gPlayer.x,std::memory_order_relaxed);
    gPlayerZSnapshot.store(gPlayer.z,std::memory_order_relaxed);
    const float cameraDistance=aimHeld?5.0f:9.0f;
    const float cameraHeight=aimHeld?2.35f:(3.8f+gCameraPitch*3.4f);
    Vec3 eye{gPlayer.x+std::sin(gCameraYaw)*cameraDistance,
        gPlayer.y+cameraHeight+gJumpHeight*0.35f,
        gPlayer.z+std::cos(gCameraYaw)*cameraDistance};
    Vec3 target{gPlayer.x,gPlayer.y+1.0f+gJumpHeight*0.45f,gPlayer.z};
    Mat4 view=lookAt(eye,target,{0,1,0});
    gProjection=multiply(perspective(aimHeld?0.62f:0.93f,static_cast<float>(gWidth)/gHeight,0.1f,260.0f),view);

    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(gProgram);
    drawWorld(gMatchTime);
    for(int i=0;i<gResourceCount;++i)
        if(!gCrystalTaken[static_cast<size_t>(i)])
            drawCrystal(gCrystals[static_cast<size_t>(i)],gMatchTime,i);
    drawTargets(gMatchTime);drawBuildPieces();

    const std::array<Vec3,4> outfits{{{0.035f,0.66f,0.88f},{0.10f,0.18f,0.23f},
        {0.52f,0.12f,0.88f},{0.82f,0.55f,0.08f}}};
    for(int i=0;i<BOT_COUNT;++i) {
        if(!gBotActive[static_cast<size_t>(i)])continue;
        const BotState& bot=gBots[static_cast<size_t>(i)];
        Vec3 botColor=isAllyBot(i)?Vec3{0.16f,0.62f,0.43f}:Vec3{0.82f,0.34f,0.19f};
        const float playerDx=bot.pos.x-gPlayer.x;
        const float playerDz=bot.pos.z-gPlayer.z;
        const float botDistance2=playerDx*playerDx+playerDz*playerDz;
        const float renderDistance=gQuality==0?44.0f:(gQuality==1?64.0f:92.0f);
        if(botDistance2>renderDistance*renderDistance)continue;
        const bool botAiming=bot.aiming||(!isAllyBot(i)&&botDistance2<34.0f*34.0f);
        drawCharacter(bot.pos,bot.yaw,botColor,true,
            gMatchTime+bot.phase,std::min(1.0f,bot.speed/4.6f),
            botAiming,bot.fireFlash>0.0f,false);
        if(bot.fireFlash>0.0f) {
            const Vec3 muzzle=localPoint(bot.pos,0.0f,1.32f,-1.18f,bot.yaw);
            const Vec3 impact{gPlayer.x,gPlayer.y+1.25f+gJumpHeight,gPlayer.z};
            drawSegment({0,0,0},0.0f,muzzle,impact,0.055f,{0.18f,0.88f,1.0f});
        }
        drawCube({bot.pos.x,bot.pos.y+2.72f,bot.pos.z},
            {0.70f*static_cast<float>(gBotHealth[static_cast<size_t>(i)])/3.0f,0.07f,0.07f},
            isAllyBot(i)?Vec3{0.20f,0.90f,0.55f}:Vec3{0.95f,0.30f,0.20f},bot.yaw);
    }
    Vec3 playerDraw=gPlayer;playerDraw.y+=gJumpHeight;
    drawCharacter(playerDraw,gPlayerYaw,outfits[static_cast<size_t>(gSelectedCharacter)],
        false,gMatchTime,moving?std::min(1.0f,moveLength):0.0f,
        aimHeld,gPulseFlash>0.0f,(buttons&BUTTON_RUN)!=0);
    drawParachute(gPlayer);

    static float statusTimer=0;statusTimer+=dt;
    if(statusTimer>=0.85f){statusTimer=0;updateStatus();}
    glDisableVertexAttribArray(0);glDisableVertexAttribArray(1);glBindBuffer(GL_ARRAY_BUFFER,0);
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
