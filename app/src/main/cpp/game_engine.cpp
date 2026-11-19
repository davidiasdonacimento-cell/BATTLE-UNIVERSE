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

GLuint gProgram=0, gCubeVbo=0;
GLint gMvpLocation=-1, gColorLocation=-1;
Mat4 gProjection=identity();
int gWidth=1, gHeight=1;
Vec3 gPlayer{0,0.55f,0};
float gPlayerYaw=0, gCameraYaw=0, gCameraPitch=0.36f;
float gJumpHeight=0, gJumpVelocity=0, gMatchTime=0, gZoneRadius=30;
float gInteractCooldown=0;
int gCollected=0, gPreviousButtons=0;
std::array<bool,CRYSTAL_COUNT> gCrystalTaken{};
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
        "uniform mat4 uMVP; out float vLight;\n"
        "void main(){ vec3 l=normalize(vec3(-0.45,0.85,0.35));"
        "vLight=0.48+0.52*max(dot(normalize(aNormal),l),0.0);"
        "gl_Position=uMVP*vec4(aPosition,1.0); }\n";
    const char* fsSource=
        "#version 300 es\nprecision mediump float;\n"
        "in float vLight; uniform vec4 uColor; out vec4 fragColor;\n"
        "void main(){ fragColor=vec4(uColor.rgb*vLight,uColor.a); }\n";
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
    gColorLocation=glGetUniformLocation(gProgram,"uColor");
    createCube(); return gCubeVbo!=0;
}
void drawCube(Vec3 p,Vec3 size,Vec3 color,float yaw=0,float tilt=0) {
    if (!gProgram||!gCubeVbo) return;
    Mat4 model=multiply(translation(p.x,p.y,p.z),
        multiply(rotationY(yaw),multiply(rotationZ(tilt),scaling(size.x,size.y,size.z))));
    Mat4 mvp=multiply(gProjection,model);
    glUniformMatrix4fv(gMvpLocation,1,GL_FALSE,mvp.m);
    glUniform4f(gColorLocation,color.x,color.y,color.z,1.0f);
    glDrawArrays(GL_TRIANGLES,0,36);
}
void drawPalm(float x,float z,float scale) {
    drawCube({x,1.85f*scale,z},{0.34f*scale,2.7f*scale,0.34f*scale},{0.39f,0.23f,0.12f},0,-0.035f);
    for (int i=0;i<7;++i) {
        float a=(2*PI*i)/7.0f;
        drawCube({x+std::cos(a)*0.88f*scale,3.10f*scale,z+std::sin(a)*0.88f*scale},
            {2.25f*scale,0.12f*scale,0.34f*scale},{0.12f,0.44f,0.25f},-a,0.14f);
    }
    drawCube({x,3.18f*scale,z},{0.65f*scale,0.34f*scale,0.65f*scale},{0.15f,0.48f,0.24f});
}
void drawBuilding(float x,float z,float w,float d,float h,Vec3 wall,Vec3 roof) {
    drawCube({x,0.55f+h*0.5f,z},{w,h,d},wall);
    drawCube({x,0.55f+h+0.14f,z},{w+0.35f,0.28f,d+0.35f},roof);
    drawCube({x,1.45f,z-d*0.5f-0.025f},{0.95f,1.8f,0.08f},{0.18f,0.22f,0.28f});
    drawCube({x-w*0.28f,2.7f,z-d*0.5f-0.04f},{0.85f,0.72f,0.07f},{0.12f,0.66f,0.79f});
    drawCube({x+w*0.28f,2.7f,z-d*0.5f-0.04f},{0.85f,0.72f,0.07f},{0.12f,0.66f,0.79f});
}
void drawWorld(float time) {
    drawCube({0,-1.05f,0},{260,0.55f,260},{0.035f,0.31f,0.48f});
    drawCube({0,-0.02f,0},{68,0.65f,68},{0.78f,0.66f,0.42f});
    drawCube({0,0.40f,0},{54,0.25f,54},{0.18f,0.43f,0.22f});
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
    for (size_t i=0;i<trees.size();++i) {
        float sway=std::sin(time*0.8f+static_cast<float>(i))*0.035f;
        drawPalm(trees[i].x,trees[i].z,0.82f+0.08f*static_cast<float>(i%3)+sway);
    }
    drawCube({-20,0.95f,-8},{3.2f,1.2f,2.6f},{0.34f,0.39f,0.35f},0.4f);
    drawCube({19,0.82f,-5},{2.5f,0.95f,3.1f},{0.36f,0.41f,0.34f},-0.3f);
    drawCube({-3,0.78f,23},{3.0f,0.8f,2.2f},{0.14f,0.37f,0.19f});
}
void drawCharacter(Vec3 p,float yaw,Vec3 color,bool bot,float time) {
    float bob=bot?std::sin(time*3.0f+p.x)*0.06f:0.0f;
    drawCube({p.x,p.y+1.05f+bob,p.z},{0.78f,0.98f,0.42f},color,yaw);
    drawCube({p.x,p.y+1.76f+bob,p.z},{0.52f,0.54f,0.50f},{0.83f,0.68f,0.54f},yaw);
    drawCube({p.x-0.48f,p.y+1.08f+bob,p.z},{0.25f,0.83f,0.27f},color,yaw,-0.10f);
    drawCube({p.x+0.48f,p.y+1.08f+bob,p.z},{0.25f,0.83f,0.27f},color,yaw,0.10f);
    drawCube({p.x-0.22f,p.y+0.34f+bob,p.z},{0.29f,0.68f,0.34f},{0.12f,0.17f,0.25f},yaw);
    drawCube({p.x+0.22f,p.y+0.34f+bob,p.z},{0.29f,0.68f,0.34f},{0.12f,0.17f,0.25f},yaw);
    drawCube({p.x,p.y+1.77f+bob,p.z-0.26f},{0.13f,0.10f,0.035f},{0.035f,0.055f,0.07f},yaw);
}
void drawCrystal(Vec3 p,float time,int index) {
    float bob=std::sin(time*2.2f+index)*0.16f;
    drawCube({p.x,p.y+0.62f+bob,p.z},{0.48f,0.82f,0.48f},{0.12f,0.82f,0.96f},time*0.9f+index*0.7f,0.18f);
    drawCube({p.x,p.y+0.62f+bob,p.z},{0.68f,0.08f,0.68f},{0.22f,0.94f,0.98f},time*0.9f+index*0.7f);
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
    out<<"CRISTAIS "<<gCollected<<"/"<<CRYSTAL_COUNT<<"  |  BOTS "<<BOT_COUNT
       <<"  |  ZONA "<<std::fixed<<std::setprecision(0)<<gZoneRadius<<"m";
    if (gCollected==CRYSTAL_COUNT) out<<"  |  OBJETIVO CONCLUÍDO!";
    setStatus(out.str());
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeInit(JNIEnv*,jclass) {
    gPlayer={0,0.55f,0}; gPlayerYaw=0; gCameraYaw=0; gCameraPitch=0.36f;
    gJumpHeight=0; gJumpVelocity=0; gMatchTime=0; gZoneRadius=30;
    gCollected=0; gPreviousButtons=0; gInteractCooldown=0; gCrystalTaken.fill(false);
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
    if (!gProgram||!gCubeVbo) { glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT); return; }
    const float dt=std::clamp(static_cast<float>(deltaSeconds),0.001f,0.05f);
    gMatchTime+=dt; gZoneRadius=std::max(14.0f,30.0f-gMatchTime*0.010f);
    gCameraYaw-=static_cast<float>(lookDX)*4.8f;
    gCameraPitch=std::clamp(gCameraPitch+static_cast<float>(lookDY)*2.2f,0.12f,0.85f);
    const float forward=-static_cast<float>(moveY), strafe=static_cast<float>(moveX);
    float dx=std::cos(gCameraYaw)*strafe+std::sin(gCameraYaw)*forward;
    float dz=std::sin(gCameraYaw)*strafe-std::cos(gCameraYaw)*forward;
    float moveLength=std::sqrt(dx*dx+dz*dz);
    if (moveLength>0.08f) {
        dx/=std::max(1.0f,moveLength); dz/=std::max(1.0f,moveLength);
        gPlayer.x+=dx*6.2f*dt; gPlayer.z+=dz*6.2f*dt; gPlayerYaw=std::atan2(dx,-dz);
    }
    gPlayer.x=std::clamp(gPlayer.x,-25.0f,25.0f);
    gPlayer.z=std::clamp(gPlayer.z,-25.0f,25.0f);

    bool jumpPressed=(buttons&BUTTON_JUMP)&&!(gPreviousButtons&BUTTON_JUMP);
    bool interactPressed=(buttons&BUTTON_INTERACT)&&!(gPreviousButtons&BUTTON_INTERACT);
    gPreviousButtons=buttons;
    if (jumpPressed&&gJumpHeight<=0.001f) gJumpVelocity=6.2f;
    gJumpHeight+=gJumpVelocity*dt; gJumpVelocity-=16.0f*dt;
    if (gJumpHeight<0) { gJumpHeight=0; gJumpVelocity=0; }

    gInteractCooldown=std::max(0.0f,gInteractCooldown-dt);
    if (interactPressed&&gInteractCooldown<=0) {
        int nearest=-1; float bestDistance=3.8f;
        for (int i=0;i<CRYSTAL_COUNT;++i) {
            if (gCrystalTaken[static_cast<size_t>(i)]) continue;
            float ox=gPlayer.x-gCrystals[static_cast<size_t>(i)].x;
            float oz=gPlayer.z-gCrystals[static_cast<size_t>(i)].z;
            float d=std::sqrt(ox*ox+oz*oz);
            if (d<bestDistance) { bestDistance=d; nearest=i; }
        }
        if (nearest>=0) {
            gCrystalTaken[static_cast<size_t>(nearest)]=true; ++gCollected;
            gInteractCooldown=0.35f; updateStatus();
        }
    }

    const float eyeDistance=9.0f, eyeHeight=3.8f+gCameraPitch*3.4f;
    Vec3 eye{gPlayer.x+std::sin(gCameraYaw)*eyeDistance,
             gPlayer.y+eyeHeight+gJumpHeight*0.35f,
             gPlayer.z+std::cos(gCameraYaw)*eyeDistance};
    Vec3 target{gPlayer.x,gPlayer.y+1.0f+gJumpHeight*0.45f,gPlayer.z};
    Mat4 view=lookAt(eye,target,{0,1,0});
    gProjection=multiply(perspective(0.93f,static_cast<float>(gWidth)/gHeight,0.1f,180.0f),view);

    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
    glUseProgram(gProgram); glBindBuffer(GL_ARRAY_BUFFER,gCubeVbo);
    glEnableVertexAttribArray(0); glEnableVertexAttribArray(1);
    glVertexAttribPointer(0,3,GL_FLOAT,GL_FALSE,6*sizeof(float),reinterpret_cast<void*>(0));
    glVertexAttribPointer(1,3,GL_FLOAT,GL_FALSE,6*sizeof(float),reinterpret_cast<void*>(3*sizeof(float)));

    drawWorld(gMatchTime);
    for (int i=0;i<CRYSTAL_COUNT;++i)
        if (!gCrystalTaken[static_cast<size_t>(i)]) drawCrystal(gCrystals[static_cast<size_t>(i)],gMatchTime,i);

    for (int i=0;i<BOT_COUNT;++i) {
        float phase=gMatchTime*0.32f+i*1.5707963f;
        Vec3 bot{std::cos(phase)*(11.0f+(i%2)*3.0f),0.55f,
                 std::sin(phase)*(11.0f+(i%2)*3.0f)};
        drawCharacter(bot,-phase,{0.88f,0.38f,0.19f},true,gMatchTime);
        drawCube({bot.x,2.68f,bot.z},{0.68f,0.08f,0.08f},{0.20f,0.90f,0.98f},-phase);
    }

    drawSafeZone();
    Vec3 playerDraw=gPlayer; playerDraw.y+=gJumpHeight;
    drawCharacter(playerDraw,gPlayerYaw,{0.12f,0.43f,0.90f},false,gMatchTime);
    drawCube({gPlayer.x,gPlayer.y+2.12f+gJumpHeight,gPlayer.z},
             {0.82f,0.10f,0.08f},{0.11f,0.88f,0.98f},gPlayerYaw);

    glDisableVertexAttribArray(0); glDisableVertexAttribArray(1);
    glBindBuffer(GL_ARRAY_BUFFER,0);
    static float statusTimer=0; statusTimer+=dt;
    if (statusTimer>=0.8f) { statusTimer=0; updateStatus(); }
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_battleuniverse_game_GameSurfaceView_nativeGetStatus(JNIEnv* env,jclass) {
    std::lock_guard<std::mutex> lock(gStatusMutex);
    return env->NewStringUTF(gHudStatus.c_str());
}
