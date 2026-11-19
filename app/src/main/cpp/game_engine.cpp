#include <jni.h>
#include <GLES3/gl3.h>
#include <android/log.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <mutex>
#include <sstream>
#include <iomanip>
#include <string>
#include <vector>

namespace {
constexpr float PI = 3.14159265358979323846f;
constexpr int BOT_COUNT = 4;
constexpr int CRYSTAL_COUNT = 5;
constexpr int BUTTON_JUMP = 1;
constexpr int BUTTON_INTERACT = 2;
constexpr int BUTTON_BUILD = 4;
constexpr int BUTTON_RUN = 8;
constexpr int BUTTON_PULSE = 16;
constexpr int BUILD_LIMIT = 12;
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
float gPlayerYaw=0, gCameraYaw=0, gCameraPitch=0.36f;
float gJumpHeight=0, gJumpVelocity=0, gMatchTime=0, gZoneRadius=30;
float gInteractCooldown=0;
int gCollected=0, gPreviousButtons=0;
std::array<bool,CRYSTAL_COUNT> gCrystalTaken{};
struct BotState { Vec3 pos; float yaw=0; float speed=0; float phase=0; };
std::array<BotState,BOT_COUNT> gBots{};
struct BuildPiece { Vec3 pos; float yaw=0; int type=0; };
std::array<BuildPiece,BUILD_LIMIT> gBuildPieces{};
int gBuildCount=0, gBuildType=0, gWood=120;
float gBuildCooldown=0;
struct TrainingTarget { Vec3 pos; int energy=3; bool active=true; };
std::array<TrainingTarget,TARGET_COUNT> gTargets{{
    {{-12,1.25f,-13},3,true}, {{13,1.25f,-12},3,true}, {{2,1.25f,18},3,true}
}};
int gPulseAmmo=24, gTrainingScore=0;
float gPulseCooldown=0, gPulseFlash=0;
Vec3 gPulseHitPosition{0,1.4f,-5};
std::mutex gStatusMutex;
std::string gHudStatus="CARREGANDO CENÁRIO 3D...";

const std::array<Vec3,CRYSTAL_COUNT> gCrystals{{
    {8,0.55f,-9},{-10,0.55f,-6},{12,0.55f,10},{-14,0.55f,12},{0,0.55f,19}
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
    drawSphere({0,-1.05f,0},{260,0.55f,260},{0.025f,0.27f,0.43f});
    Vec3 beach=gSelectedMap==2?Vec3{0.66f,0.43f,0.24f}:Vec3{0.78f,0.66f,0.42f};
    Vec3 land=gSelectedMap==1?Vec3{0.22f,0.27f,0.32f}:(gSelectedMap==2?Vec3{0.47f,0.35f,0.22f}:Vec3{0.18f,0.43f,0.22f});
    drawSphere({0,-0.02f,0},{68,0.65f,68},beach);
    drawSphere({0,0.40f,0},{54,0.25f,54},land);
    drawCube({0,0.535f,0},{4.2f,0.045f,31},{0.48f,0.43f,0.32f});
    drawCube({-9,0.535f,2},{13,0.05f,2.5f},{0.43f,0.45f,0.43f});
    drawCube({9,0.535f,-4},{12,0.05f,2.5f},{0.43f,0.45f,0.43f});
    drawBuilding(-10,-13,8,6,4.2f,{0.72f,0.65f,0.48f},{0.33f,0.19f,0.15f});
    drawBuilding(11,-13,7,6,3.8f,{0.73f,0.76f,0.72f},{0.16f,0.31f,0.43f});
    drawBuilding(-14,9,6,5.5f,3.4f,{0.72f,0.57f,0.39f},{0.40f,0.22f,0.16f});
    drawBuilding(14,10,7,6,4.5f,{0.64f,0.70f,0.66f},{0.22f,0.29f,0.32f});
    const std::array<Vec3,12> trees{{
        {-22,0,-18},{-20,0,1},{-22,0,19},{-8,0,22},{8,0,23},{22,0,20},
        {22,0,2},{22,0,-19},{2,0,-23},{-13,0,-22},{-7,0,11},{8,0,7}
    }};
    size_t treeLimit=gQuality==0?7:trees.size();
    for(size_t i=0;i<treeLimit;++i){
        float sway=std::sin(time*0.8f+static_cast<float>(i))*0.035f;
        if(gSelectedMap==0) drawPalm(trees[i].x,trees[i].z,0.82f+0.08f*static_cast<float>(i%3)+sway);
        else if(gSelectedMap==1) {
            drawCylinder({trees[i].x,1.2f,trees[i].z},{0.12f,2.1f,0.12f},{0.20f,0.28f,0.25f});
            drawSphere({trees[i].x,2.6f,trees[i].z},{1.0f,1.0f,1.0f},{0.17f,0.24f,0.28f});
        } else {
            drawCylinder({trees[i].x,0.9f,trees[i].z},{0.12f,1.7f,0.12f},{0.30f,0.24f,0.16f});
            drawSphere({trees[i].x,1.9f,trees[i].z},{0.30f,0.8f,0.30f},{0.20f,0.43f,0.25f});
        }
    }
    drawCube({-20,0.95f,-8},{3.2f,1.2f,2.6f},{0.34f,0.39f,0.35f},0.4f);
    drawCube({19,0.82f,-5},{2.5f,0.95f,3.1f},{0.36f,0.41f,0.34f},-0.3f);
    drawCube({-3,0.78f,23},{3.0f,0.8f,2.2f},{0.14f,0.37f,0.19f});
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
void drawCharacter(Vec3 p,float yaw,Vec3 color,bool bot,float time,float movement=0.0f) {
    const float gait=std::clamp(movement,0.0f,1.0f);
    const float phase=time*8.2f+(bot?0.7f:0.0f);
    const float swing=std::sin(phase)*0.36f*gait;
    const float bob=std::sin(phase*2.0f)*0.025f*gait+std::sin(time*1.5f)*0.014f;
    const Vec3 skin{0.68f,0.47f,0.34f},pants{0.075f,0.11f,0.16f},boots{0.025f,0.035f,0.045f};
    drawSphere(localPoint(p,0,1.16f+bob,0,yaw),{0.66f,0.93f,0.39f},color,yaw);
    drawSphere(localPoint(p,0,1.31f+bob,-0.035f,yaw),{0.63f,0.62f,0.43f},
        {std::min(1.0f,color.x*1.12f),std::min(1.0f,color.y*1.12f),std::min(1.0f,color.z*1.12f)},yaw);
    drawSphere(localPoint(p,0,1.19f+bob,-0.245f,yaw),{0.49f,0.72f,0.115f},{0.055f,0.105f,0.14f},yaw);
    drawSphere(localPoint(p,0,1.48f+bob,-0.30f,yaw),{0.37f,0.12f,0.055f},{0.10f,0.65f,0.72f},yaw);
    drawSphere(localPoint(p,0,1.42f+bob,0.245f,yaw),{0.45f,0.58f,0.25f},{0.07f,0.13f,0.17f},yaw);
    drawSphere(localPoint(p,-0.43f,1.43f+bob,0,yaw),{0.23f,0.26f,0.25f},{0.13f,0.18f,0.21f},yaw);
    drawSphere(localPoint(p,0.43f,1.43f+bob,0,yaw),{0.23f,0.26f,0.25f},{0.13f,0.18f,0.21f},yaw);
    drawCylinder(localPoint(p,0,1.70f+bob,0,yaw),{0.17f,0.18f,0.17f},skin,yaw);
    drawSphere(localPoint(p,0,1.96f+bob,0,yaw),{0.34f,0.43f,0.32f},skin,yaw);
    drawSphere(localPoint(p,0,2.15f+bob,0.005f,yaw),{0.365f,0.205f,0.35f},{0.045f,0.065f,0.085f},yaw);
    drawSphere(localPoint(p,0,2.035f+bob,-0.29f,yaw),{0.245f,0.065f,0.045f},{0.055f,0.075f,0.09f},yaw);
    drawSphere(localPoint(p,-0.115f,1.99f+bob,-0.305f,yaw),{0.035f,0.035f,0.022f},{0.025f,0.035f,0.04f},yaw);
    drawSphere(localPoint(p,0.115f,1.99f+bob,-0.305f,yaw),{0.035f,0.035f,0.022f},{0.025f,0.035f,0.04f},yaw);
    Vec3 hipL{-0.19f,0.82f+bob,swing*0.36f},kneeL{-0.20f,0.47f+bob,swing*0.72f},footL{-0.20f,0.16f+bob,swing*0.86f};
    Vec3 hipR{0.19f,0.82f+bob,-swing*0.36f},kneeR{0.20f,0.47f+bob,-swing*0.72f},footR{0.20f,0.16f+bob,-swing*0.86f};
    drawSegment(p,yaw,hipL,kneeL,0.135f,pants);drawSegment(p,yaw,kneeL,footL,0.105f,pants);
    drawSegment(p,yaw,hipR,kneeR,0.135f,pants);drawSegment(p,yaw,kneeR,footR,0.105f,pants);
    drawSphere(localPoint(p,-0.20f,0.105f+bob,-0.16f+swing*0.86f,yaw),{0.24f,0.13f,0.37f},boots,yaw);
    drawSphere(localPoint(p,0.20f,0.105f+bob,-0.16f-swing*0.86f,yaw),{0.24f,0.13f,0.37f},boots,yaw);
    Vec3 shoulderL{-0.43f,1.53f+bob,0},elbowL{-0.52f,1.13f+bob,-swing*0.55f},handL{-0.48f,0.84f+bob,-swing*0.72f};
    Vec3 shoulderR{0.43f,1.53f+bob,0},elbowR{0.52f,1.13f+bob,swing*0.55f},handR{0.48f,0.84f+bob,swing*0.72f};
    drawSegment(p,yaw,shoulderL,elbowL,0.105f,color);drawSegment(p,yaw,elbowL,handL,0.085f,skin);
    drawSegment(p,yaw,shoulderR,elbowR,0.105f,color);drawSegment(p,yaw,elbowR,handR,0.085f,skin);
    drawSphere(localPoint(p,handL.x,handL.y,handL.z,yaw),{0.115f,0.13f,0.115f},skin,yaw);
    drawSphere(localPoint(p,handR.x,handR.y,handR.z,yaw),{0.115f,0.13f,0.115f},skin,yaw);
    drawCylinder(localPoint(p,0.49f,0.91f+bob,swing*0.72f-0.10f,yaw),{0.095f,0.34f,0.095f},{0.12f,0.72f,0.86f},yaw,0.0f,0.18f);
    drawSphere(localPoint(p,0.49f,1.08f+bob,swing*0.72f-0.10f,yaw),{0.12f,0.12f,0.12f},{0.45f,0.95f,1.0f},yaw);
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
void updateStatus() {
    std::ostringstream out;
    out<<"PULSO "<<gPulseAmmo<<"  |  MADEIRA "<<gWood<<"  |  CONSTRUÇÕES "<<gBuildCount
       <<"/"<<BUILD_LIMIT<<"  |  ALVOS "<<gTrainingScore<<"  |  CRISTAIS "<<gCollected<<"/"<<CRYSTAL_COUNT;
    setStatus(out.str());
}
void resetBots() {
    const std::array<Vec3,BOT_COUNT> starts{{
        {-14,0.55f,-4},{14,0.55f,-5},{-13,0.55f,14},{13,0.55f,13}
    }};
    for(int i=0;i<BOT_COUNT;++i)
        gBots[static_cast<size_t>(i)]={starts[static_cast<size_t>(i)],0,0,static_cast<float>(i)*1.3f};
}
void updateBots(float dt) {
    for(int i=0;i<BOT_COUNT;++i) {
        BotState& bot=gBots[static_cast<size_t>(i)];
        Vec3 target=bot.pos;
        bool ally=(gSelectedMode==1&&i==0)||(gSelectedMode==2&&i<3);
        if(ally) {
            float side=(i%2==0?-1.0f:1.0f)*(1.7f+0.5f*(i/2));
            target=localPoint(gPlayer,side,0,2.3f+0.45f*i,gPlayerYaw);
        } else {
            int nearest=-1;float best=1.0e9f;
            for(int j=0;j<CRYSTAL_COUNT;++j) {
                if(gCrystalTaken[static_cast<size_t>(j)])continue;
                Vec3 c=gCrystals[static_cast<size_t>(j)];
                float dx=c.x-bot.pos.x,dz=c.z-bot.pos.z,d=dx*dx+dz*dz;
                if(d<best){best=d;nearest=j;}
            }
            if(nearest>=0)target=gCrystals[static_cast<size_t>(nearest)];
            else {
                float phase=gMatchTime*0.25f+bot.phase;
                target={std::cos(phase)*17.0f,0.55f,std::sin(phase)*17.0f};
            }
        }
        float dx=target.x-bot.pos.x,dz=target.z-bot.pos.z;
        float distance=std::sqrt(dx*dx+dz*dz);
        bot.speed=0;
        if(distance>0.85f) {
            float inv=1.0f/std::max(0.001f,distance);
            dx*=inv;dz*=inv;
            bot.yaw=std::atan2(dx,-dz);
            bot.speed=ally?3.5f:2.15f;
            float step=std::min(distance-0.45f,bot.speed*dt);
            bot.pos.x+=dx*step;bot.pos.z+=dz*step;
        }
        bot.pos.x=std::clamp(bot.pos.x,-25.0f,25.0f);
        bot.pos.z=std::clamp(bot.pos.z,-25.0f,25.0f);
        if(!ally) {
            for(int j=0;j<CRYSTAL_COUNT;++j) {
                if(gCrystalTaken[static_cast<size_t>(j)])continue;
                Vec3 c=gCrystals[static_cast<size_t>(j)];
                float ox=bot.pos.x-c.x,oz=bot.pos.z-c.z;
                if(ox*ox+oz*oz<1.45f*1.45f) {
                    gCrystalTaken[static_cast<size_t>(j)]=true;
                    ++gCollected;gWood=std::min(200,gWood+5);
                    break;
                }
            }
        }
        for(int other=0;other<i;++other) {
            Vec3 op=gBots[static_cast<size_t>(other)].pos;
            float sx=bot.pos.x-op.x,sz=bot.pos.z-op.z;
            float d2=sx*sx+sz*sz;
            if(d2<1.15f*1.15f&&d2>0.001f) {
                float inv=0.18f/std::sqrt(d2);
                bot.pos.x+=sx*inv;bot.pos.z+=sz*inv;
            }
        }
    }
}
void placeBuildPiece() {
    if(gBuildCount>=BUILD_LIMIT||gWood<10) {
        setStatus(gWood<10?"RECURSOS INSUFICIENTES":"LIMITE DE CONSTRUÇÕES ATINGIDO");
        return;
    }
    Vec3 forward{std::sin(gPlayerYaw),0,-std::cos(gPlayerYaw)};
    Vec3 pos{gPlayer.x+forward.x*3.0f,0.68f,gPlayer.z+forward.z*3.0f};
    gBuildPieces[static_cast<size_t>(gBuildCount)]={pos,gPlayerYaw,gBuildType};
    ++gBuildCount;gWood-=10;gBuildType=(gBuildType+1)%3;
    updateStatus();
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
        } else {
            drawCube({b.pos.x,0.95f,b.pos.z},{3.0f,0.20f,3.0f},{0.58f,0.41f,0.24f},b.yaw,0.0f,0.22f);
        }
    }
}
void firePulse() {
    if(gPulseAmmo<=0){setStatus("PULSO SEM CARGA");return;}
    --gPulseAmmo;gPulseFlash=0.16f;
    gPulseHitPosition={gPlayer.x+std::sin(gPlayerYaw)*7.0f,1.5f,gPlayer.z-std::cos(gPlayerYaw)*7.0f};
    const float fx=std::sin(gPlayerYaw),fz=-std::cos(gPlayerYaw);
    int hit=-1;float best=18.0f;
    for(int i=0;i<TARGET_COUNT;++i) {
        TrainingTarget& target=gTargets[static_cast<size_t>(i)];
        if(!target.active)continue;
        float dx=target.pos.x-gPlayer.x,dz=target.pos.z-gPlayer.z;
        float along=dx*fx+dz*fz,side=std::fabs(dx*fz-dz*fx);
        if(along>0.8f&&along<18.0f&&side<1.25f&&along<best){best=along;hit=i;}
    }
    if(hit>=0) {
        TrainingTarget& target=gTargets[static_cast<size_t>(hit)];
        --target.energy;gPulseHitPosition=target.pos;
        if(target.energy<=0){target.active=false;gTrainingScore+=100;gWood=std::min(200,gWood+5);}
    }
    updateStatus();
}
void drawTargets(float time) {
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
    if(gPulseFlash>0.0f)
        drawSphere(gPulseHitPosition,{0.33f,0.33f,0.33f},{0.25f,0.96f,1.0f});
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeSetScreen(JNIEnv*,jclass,jint mode) {
    gScreenMode=std::clamp(static_cast<int>(mode),0,1);
    if(gScreenMode==1){
        gPlayer={0,0.55f,0};gMatchTime=0;gZoneRadius=30;gCollected=0;
        gCrystalTaken.fill(false);gBuildCount=0;gWood=120;gBuildType=0;
        gPulseAmmo=24;gTrainingScore=0;
        gTargets[0]={{0,1.25f,-10},3,true};
        gTargets[1]={{-10,1.25f,-14},3,true};
        gTargets[2]={{10,1.25f,-14},3,true};
        resetBots();updateStatus();
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
Java_com_battleuniverse_game_GameSurfaceView_nativeInit(JNIEnv*,jclass) {
    gPlayer={0,0.55f,0};gPlayerYaw=0;gCameraYaw=0;gCameraPitch=0.36f;
    gScreenMode=0;gSelectedCharacter=0;gSelectedMap=0;gSelectedMode=0;gQuality=1;
    gJumpHeight=0;gJumpVelocity=0;gMatchTime=0;gZoneRadius=30;
    gCollected=0;gPreviousButtons=0;gInteractCooldown=0;gCrystalTaken.fill(false);
    gBuildCount=0;gBuildType=0;gWood=120;gBuildCooldown=0;
    gPulseAmmo=24;gTrainingScore=0;gPulseCooldown=0;gPulseFlash=0;
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
    if(gPulseCooldown>0)gPulseCooldown=std::max(0.0f,gPulseCooldown-dt);
    if(gBuildCooldown>0)gBuildCooldown=std::max(0.0f,gBuildCooldown-dt);
    if(gPulseFlash>0)gPulseFlash=std::max(0.0f,gPulseFlash-dt);

    if(gScreenMode==0) {
        Vec3 eye{std::sin(gMatchTime*0.035f)*3.0f,7.0f,25.0f};
        Vec3 target{3.1f,1.25f,0.0f};
        Mat4 view=lookAt(eye,target,{0,1,0});
        gProjection=multiply(perspective(0.88f,static_cast<float>(gWidth)/gHeight,0.1f,220.0f),view);
        glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(gProgram);
        drawWorld(gMatchTime);
        const std::array<Vec3,4> formation{{{2.8f,0.55f,1.0f},{4.4f,0.55f,-0.5f},{6.1f,0.55f,-0.7f},{7.7f,0.55f,0.9f}}};
        const std::array<Vec3,4> outfits{{{0.12f,0.48f,0.80f},{0.72f,0.24f,0.18f},{0.18f,0.57f,0.42f},{0.55f,0.34f,0.76f}}};
        for(int i=0;i<4;++i)
            drawCharacter(formation[static_cast<size_t>(i)],PI,
                i==0?outfits[static_cast<size_t>(gSelectedCharacter)]:outfits[static_cast<size_t>(i)],
                true,gMatchTime+static_cast<float>(i),0.0f);
        glDisableVertexAttribArray(0);glDisableVertexAttribArray(1);glBindBuffer(GL_ARRAY_BUFFER,0);
        return;
    }

    gZoneRadius=std::max(14.0f,30.0f-gMatchTime*0.010f);
    gCameraYaw-=static_cast<float>(lookDX)*4.8f;
    gCameraPitch=std::clamp(gCameraPitch+static_cast<float>(lookDY)*2.2f,0.12f,0.85f);
    // moveY is positive when the player drags the joystick upward.
    const float forward=static_cast<float>(moveY),strafe=static_cast<float>(moveX);
    float dx=std::cos(gCameraYaw)*strafe+std::sin(gCameraYaw)*forward;
    float dz=std::sin(gCameraYaw)*strafe-std::cos(gCameraYaw)*forward;
    float moveLength=std::sqrt(dx*dx+dz*dz);
    bool moving=moveLength>0.08f;
    if(moving) {
        dx/=std::max(1.0f,moveLength);dz/=std::max(1.0f,moveLength);
        const float speed=(buttons&BUTTON_RUN)?8.0f:5.2f;
        float nx=std::clamp(gPlayer.x+dx*speed*dt,-25.0f,25.0f);
        float nz=std::clamp(gPlayer.z+dz*speed*dt,-25.0f,25.0f);
        // Placed walls are solid; floors and ramps remain traversable.
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
        gPlayerYaw=std::atan2(dx,-dz);
    }

    bool jumpPressed=(buttons&BUTTON_JUMP)&&!(gPreviousButtons&BUTTON_JUMP);
    bool interactPressed=(buttons&BUTTON_INTERACT)&&!(gPreviousButtons&BUTTON_INTERACT);
    bool buildPressed=(buttons&BUTTON_BUILD)&&!(gPreviousButtons&BUTTON_BUILD);
    bool pulsePressed=(buttons&BUTTON_PULSE)&&!(gPreviousButtons&BUTTON_PULSE);
    gPreviousButtons=buttons;
    if(jumpPressed&&gJumpHeight<=0.001f)gJumpVelocity=6.2f;
    gJumpHeight+=gJumpVelocity*dt;gJumpVelocity-=16.0f*dt;
    if(gJumpHeight<0){gJumpHeight=0;gJumpVelocity=0;}

    gInteractCooldown=std::max(0.0f,gInteractCooldown-dt);
    if(interactPressed&&gInteractCooldown<=0.0f) {
        int nearest=-1;float bestDistance=3.8f;
        for(int i=0;i<CRYSTAL_COUNT;++i) {
            if(gCrystalTaken[static_cast<size_t>(i)])continue;
            float ox=gPlayer.x-gCrystals[static_cast<size_t>(i)].x;
            float oz=gPlayer.z-gCrystals[static_cast<size_t>(i)].z;
            float d=std::sqrt(ox*ox+oz*oz);
            if(d<bestDistance){bestDistance=d;nearest=i;}
        }
        if(nearest>=0) {
            gCrystalTaken[static_cast<size_t>(nearest)]=true;++gCollected;
            gWood=std::min(200,gWood+15);gInteractCooldown=0.35f;
            updateStatus();
        }
    }
    if(buildPressed&&gBuildCooldown<=0.0f) {
        placeBuildPiece();gBuildCooldown=0.32f;
    }
    if(pulsePressed&&gPulseCooldown<=0.0f) {
        firePulse();gPulseCooldown=0.22f;
    }

    updateBots(dt);
    Vec3 eye{gPlayer.x+std::sin(gCameraYaw)*9.0f,
        gPlayer.y+3.8f+gCameraPitch*3.4f+gJumpHeight*0.35f,
        gPlayer.z+std::cos(gCameraYaw)*9.0f};
    Vec3 target{gPlayer.x,gPlayer.y+1.0f+gJumpHeight*0.45f,gPlayer.z};
    Mat4 view=lookAt(eye,target,{0,1,0});
    gProjection=multiply(perspective(0.93f,static_cast<float>(gWidth)/gHeight,0.1f,180.0f),view);

    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glUseProgram(gProgram);
    drawWorld(gMatchTime);
    for(int i=0;i<CRYSTAL_COUNT;++i)
        if(!gCrystalTaken[static_cast<size_t>(i)])
            drawCrystal(gCrystals[static_cast<size_t>(i)],gMatchTime,i);
    drawTargets(gMatchTime);
    drawBuildPieces();

    const std::array<Vec3,4> outfits{{{0.12f,0.48f,0.80f},{0.72f,0.24f,0.18f},
        {0.18f,0.57f,0.42f},{0.55f,0.34f,0.76f}}};
    for(int i=0;i<BOT_COUNT;++i) {
        const BotState& bot=gBots[static_cast<size_t>(i)];
        drawCharacter(bot.pos,bot.yaw,{0.82f,0.38f,0.22f},true,
            gMatchTime+bot.phase,std::min(1.0f,bot.speed/3.5f));
    }
    Vec3 playerDraw=gPlayer;playerDraw.y+=gJumpHeight;
    drawCharacter(playerDraw,gPlayerYaw,outfits[static_cast<size_t>(gSelectedCharacter)],
        false,gMatchTime,moving?std::min(1.0f,moveLength):0.0f);

    static float statusTimer=0;
    statusTimer+=dt;
    if(statusTimer>=0.65f){statusTimer=0;updateStatus();}
    glDisableVertexAttribArray(0);glDisableVertexAttribArray(1);glBindBuffer(GL_ARRAY_BUFFER,0);
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeGetStatus(JNIEnv* env,jclass) {
    std::lock_guard<std::mutex> lock(gStatusMutex);
    return env->NewStringUTF(gHudStatus.c_str());
}
