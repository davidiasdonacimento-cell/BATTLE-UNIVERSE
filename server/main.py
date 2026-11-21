from __future__ import annotations

import os
import re
import secrets
from datetime import datetime, timedelta, timezone
from typing import Any, Generator

import jwt
from fastapi import Depends, FastAPI, HTTPException, Query, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field
from pwdlib import PasswordHash
from sqlalchemy import DateTime, Integer, String, Text, create_engine, select, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

DATABASE_URL = os.environ.get("DATABASE_URL", "").strip()
JWT_SECRET = os.environ.get("JWT_SECRET", "").strip()
JWT_ISSUER = os.environ.get("JWT_ISSUER", "battle-universe-accounts")
ACCESS_TOKEN_MINUTES = max(15, min(43200, int(os.environ.get("ACCESS_TOKEN_MINUTES", "43200"))))
ADMIN_TOKEN_MINUTES = max(10, min(720, int(os.environ.get("ADMIN_TOKEN_MINUTES", "120"))))
OWNER_PAIRING_CODE = os.environ.get("OWNER_PAIRING_CODE", "").strip()
OWNER_TOKEN_MINUTES = 43200
if not DATABASE_URL:
    raise RuntimeError("DATABASE_URL is required")
if len(JWT_SECRET) < 32:
    raise RuntimeError("JWT_SECRET must contain at least 32 characters")
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql+psycopg://", 1)
elif DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg://", 1)

engine = create_engine(DATABASE_URL, pool_pre_ping=True, pool_recycle=300)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
password_hash = PasswordHash.recommended()
bearer = HTTPBearer(auto_error=False)
USERNAME_RE = re.compile(r"^[a-z0-9_]{3,18}$")
PROFILE_FIELDS = {
    "selected_mode", "selected_map", "selected_character", "quality", "music", "effects",
    "look_sensitivity", "sensitivity_general", "sensitivity_aim", "invert_camera_y",
    "show_minimap", "haptics", "mode", "map", "character", "selected_shirt",
    "selected_boots", "selected_set", "wear_shirt_tactical", "wear_shirt_neon",
    "wear_shirt_frost", "wear_shirt_desert", "wear_boots_combat", "wear_boots_neon",
    "wear_boots_frost", "wear_boots_gold", "wear_set_aurora", "wear_set_champion",
    "wear_set_orbit", "wear_set_desert", "wear_set_volcano", "wear_set_nebula",
    "wear_set_cerrado", "equipped_beard", "equipped_helmet", "equipped_backpack", "equipped_vest", "character_display_name",
}

class Base(DeclarativeBase):
    pass

class Player(Base):
    __tablename__ = "players"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    username: Mapped[str] = mapped_column(String(18), unique=True, index=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    profile: Mapped[dict[str, Any]] = mapped_column(JSONB, nullable=False, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc), nullable=False)

class AdminUser(Base):
    __tablename__ = "admin_users"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    username: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    active: Mapped[bool] = mapped_column(default=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class AccountControl(Base):
    __tablename__ = "account_controls"
    player_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    state: Mapped[str] = mapped_column(String(16), nullable=False, default="active")
    reason: Mapped[str] = mapped_column(String(240), nullable=False, default="")
    changed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class GameSetting(Base):
    __tablename__ = "game_settings"
    key: Mapped[str] = mapped_column(String(80), primary_key=True)
    value: Mapped[Any] = mapped_column(JSONB, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class AdminAudit(Base):
    __tablename__ = "admin_audit"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    admin_username: Mapped[str] = mapped_column(String(64), nullable=False)
    action: Mapped[str] = mapped_column(String(48), nullable=False)
    target: Mapped[str] = mapped_column(String(100), nullable=False, default="")
    details: Mapped[dict[str, Any]] = mapped_column(JSONB, nullable=False, default=dict)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class RegisterBody(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)
    username: str = Field(min_length=3, max_length=18, pattern=r"^[A-Za-z0-9_]+$")
    password: str = Field(min_length=8, max_length=72)

class LoginBody(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)
    username: str = Field(min_length=3, max_length=18)
    password: str = Field(min_length=8, max_length=72)

class ProfileBody(BaseModel):
    profile: dict[str, Any] = Field(default_factory=dict)

class OwnerPairBody(BaseModel):
    pairing_code: str = Field(min_length=32, max_length=256)

class GameSettingsBody(BaseModel):
    game_name: str = Field(min_length=2, max_length=40)
    character_names: dict[str, str] = Field(default_factory=dict)

app = FastAPI(title="BATTLE UNIVERSE Account & Admin API", version="1.2.0")
origins = [x.strip() for x in os.environ.get("CORS_ORIGINS", "").split(",") if x.strip()]
if origins:
    app.add_middleware(CORSMiddleware, allow_origins=origins, allow_credentials=False,
                       allow_methods=["GET", "PUT", "PATCH", "POST", "DELETE"], allow_headers=["Authorization", "Content-Type"])

@app.middleware("http")
async def security_headers(request, call_next):
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["Cache-Control"] = "no-store"
    response.headers["Referrer-Policy"] = "no-referrer"
    return response

def db_session() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def audit(db: Session, admin: str, action: str, target: str = "", details: dict[str, Any] | None = None) -> None:
    db.add(AdminAudit(admin_username=admin, action=action, target=target, details=details or {}))

@app.on_event("startup")
def startup() -> None:
    Base.metadata.create_all(bind=engine)
    # Owner pairing code stays in the hosting environment and is never embedded in the APK.
    if OWNER_PAIRING_CODE and len(OWNER_PAIRING_CODE) < 32:
        raise RuntimeError("OWNER_PAIRING_CODE must contain at least 32 characters")

def player_payload(player: Player) -> dict[str, Any]:
    return {"id": player.id, "username": player.username, "profile": player.profile or {},
            "created_at": player.created_at.isoformat(), "updated_at": player.updated_at.isoformat()}

def issue_token(subject: str, username: str, role: str, minutes: int) -> str:
    now = datetime.now(timezone.utc)
    return jwt.encode({"sub": subject, "username": username, "role": role, "iss": JWT_ISSUER,
                       "iat": now, "exp": now + timedelta(minutes=minutes)}, JWT_SECRET, algorithm="HS256")

def token_claims(credentials: HTTPAuthorizationCredentials | None) -> dict[str, Any]:
    if credentials is None:
        raise HTTPException(401, "Sessão inválida ou expirada.", headers={"WWW-Authenticate": "Bearer"})
    try:
        return jwt.decode(credentials.credentials, JWT_SECRET, algorithms=["HS256"], issuer=JWT_ISSUER,
                          options={"require": ["sub", "exp", "iss", "role"]})
    except jwt.PyJWTError:
        raise HTTPException(401, "Sessão inválida ou expirada.", headers={"WWW-Authenticate": "Bearer"})

def current_player(credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
                   db: Session = Depends(db_session)) -> Player:
    claims = token_claims(credentials)
    if claims.get("role") != "player":
        raise HTTPException(403, "Acesso negado.")
    try:
        player = db.get(Player, int(claims["sub"]))
    except (ValueError, TypeError):
        player = None
    if player is None:
        raise HTTPException(401, "Conta não encontrada.")
    control = db.get(AccountControl, player.id)
    if control and control.state != "active":
        raise HTTPException(403, "Esta conta está suspensa ou encerrada.")
    return player

def current_owner(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)) -> str:
    claims = token_claims(credentials)
    if claims.get("role") != "owner" or claims.get("sub") != "owner-device":
        raise HTTPException(403, "Aparelho não autorizado para configurar o jogo.")
    return "owner-device"

@app.get("/health")
def health() -> dict[str, str]:
    with engine.connect() as connection:
        connection.exec_driver_sql("SELECT 1")
    return {"status": "ok", "service": "battle-universe-accounts-admin"}

@app.post("/v1/accounts/register", status_code=201)
def register(body: RegisterBody, db: Session = Depends(db_session)) -> dict[str, Any]:
    username = body.username.lower()
    if not re.fullmatch(r"^[a-z0-9_]{3,18}$", username):
        raise HTTPException(422, "Use 3 a 18 letras, números ou _ no nickname.")
    if db.scalar(select(Player.id).where(Player.username == username)) is not None:
        raise HTTPException(409, "Esse nickname já está em uso.")
    player = Player(username=username, password_hash=password_hash.hash(body.password), profile={})
    db.add(player)
    try:
        db.commit()
        db.refresh(player)
    except Exception:
        db.rollback()
        if db.scalar(select(Player.id).where(Player.username == username)) is not None:
            raise HTTPException(409, "Esse nickname já está em uso.")
        raise HTTPException(503, "Não foi possível criar a conta agora.")
    return {"access_token": issue_token(str(player.id), username, "player", ACCESS_TOKEN_MINUTES),
            "token_type": "bearer", "expires_in": ACCESS_TOKEN_MINUTES * 60,
            "player": {"id": player.id, "username": player.username, "profile": {}}}

@app.post("/v1/accounts/login")
def login(body: LoginBody, db: Session = Depends(db_session)) -> dict[str, Any]:
    username = body.username.strip().lower()
    player = db.scalar(select(Player).where(Player.username == username))
    if player is None or not password_hash.verify(body.password, player.password_hash):
        raise HTTPException(401, "Nickname ou senha incorretos.")
    control = db.get(AccountControl, player.id)
    if control and control.state != "active":
        raise HTTPException(403, "Esta conta está suspensa ou encerrada.")
    return {"access_token": issue_token(str(player.id), username, "player", ACCESS_TOKEN_MINUTES),
            "token_type": "bearer", "expires_in": ACCESS_TOKEN_MINUTES * 60, "player": player_payload(player)}

@app.get("/v1/accounts/me")
def me(player: Player = Depends(current_player)) -> dict[str, Any]:
    return {"player": {"id": player.id, "username": player.username, "profile": player.profile or {},
                       "created_at": player.created_at.isoformat(), "updated_at": player.updated_at.isoformat()}}

@app.patch("/v1/accounts/me/profile")
def update_profile(body: ProfileBody, db: Session = Depends(db_session),
                   player: Player = Depends(current_player)) -> dict[str, Any]:
    if len(body.profile) > 80:
        raise HTTPException(413, "Perfil excede o limite de campos.")
    clean: dict[str, Any] = {}
    for key, value in body.profile.items():
        if key in PROFILE_FIELDS and (isinstance(value, (str, bool, int, float)) or value is None):
            if isinstance(value, str) and len(value) > 256:
                raise HTTPException(422, f"Valor muito longo: {key}")
            clean[key] = value
    player.profile = {**(player.profile or {}), **clean}
    player.updated_at = datetime.now(timezone.utc)
    db.add(player); db.commit(); db.refresh(player)
    return {"player": {"id": player.id, "username": player.username, "profile": player.profile}}

@app.get("/v1/game/settings")
def player_game_settings(db: Session = Depends(db_session), player: Player = Depends(current_player)):
    rows = db.scalars(select(GameSetting)).all()
    values = {row.key: row.value for row in rows}
    return {"game_name": values.get("game_name", "BATTLE UNIVERSE"),
            "character_names": values.get("character_names", {}),
            "account_character_name": (player.profile or {}).get("character_display_name", "")}

@app.post("/v1/owner/pair")
def owner_pair(body: OwnerPairBody) -> dict[str, Any]:
    if not OWNER_PAIRING_CODE:
        raise HTTPException(503, "Ativação do proprietário ainda não foi configurada no servidor.")
    if not secrets.compare_digest(body.pairing_code, OWNER_PAIRING_CODE):
        raise HTTPException(401, "Código de ativação inválido.")
    return {"access_token": issue_token("owner-device", "owner", "owner", OWNER_TOKEN_MINUTES),
            "token_type": "bearer", "expires_in": OWNER_TOKEN_MINUTES * 60}

@app.get("/v1/owner/settings")
def owner_get_settings(db: Session = Depends(db_session), owner: str = Depends(current_owner)):
    rows = db.scalars(select(GameSetting)).all()
    settings = {row.key: row.value for row in rows}
    return {"game_name": settings.get("game_name", "BATTLE UNIVERSE"),
            "character_names": settings.get("character_names", {})}

@app.put("/v1/owner/settings")
def owner_put_settings(body: GameSettingsBody, db: Session = Depends(db_session),
                       owner: str = Depends(current_owner)):
    if len(body.character_names) > 100:
        raise HTTPException(413, "Limite de nomes de personagens excedido.")
    clean_names = {}
    for key, value in body.character_names.items():
        if not re.fullmatch(r"[a-zA-Z0-9_-]{1,48}", key) or not value.strip() or len(value.strip()) > 32:
            raise HTTPException(422, "ID ou nome de personagem inválido.")
        clean_names[key] = value.strip()
    values = {"game_name": body.game_name.strip(), "character_names": clean_names}
    for key, value in values.items():
        row = db.get(GameSetting, key) or GameSetting(key=key, value=value)
        row.value = value
        row.updated_at = datetime.now(timezone.utc)
        db.add(row)
    audit(db, "owner-device", "game_settings_update", "global", values)
    db.commit()
    return {"settings": values, "saved": True}

