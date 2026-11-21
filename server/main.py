from __future__ import annotations

import hashlib
import os
import re
from datetime import datetime, timedelta, timezone
from typing import Any, Generator

import jwt
from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field
from pwdlib import PasswordHash
from sqlalchemy import DateTime, Integer, String, create_engine, select
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

DATABASE_URL = os.environ.get("DATABASE_URL", "").strip()
JWT_SECRET = os.environ.get("JWT_SECRET", "").strip()
JWT_ISSUER = os.environ.get("JWT_ISSUER", "battle-universe-accounts")
ACCESS_TOKEN_MINUTES = max(15, min(1440, int(os.environ.get("ACCESS_TOKEN_MINUTES", "60"))))
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
# Client-controlled profile fields only. Wallets, XP, match results and rewards
# are deliberately excluded until authoritative game-service endpoints exist.
PROFILE_FIELDS = {
    "selected_mode", "selected_map", "selected_character", "quality",
    "music", "effects", "look_sensitivity", "sensitivity_general",
    "sensitivity_aim", "invert_camera_y", "show_minimap", "haptics",
    "selected_shirt", "selected_boots", "selected_set",
    "wear_shirt_tactical", "wear_shirt_neon", "wear_shirt_frost",
    "wear_shirt_desert", "wear_boots_combat", "wear_boots_neon",
    "wear_boots_frost", "wear_boots_gold", "wear_set_aurora",
    "wear_set_champion", "wear_set_orbit", "wear_set_desert",
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

app = FastAPI(title="BATTLE UNIVERSE Account API", version="1.0.0")
origins = [x.strip() for x in os.environ.get("CORS_ORIGINS", "").split(",") if x.strip()]
if origins:
    app.add_middleware(CORSMiddleware, allow_origins=origins, allow_credentials=False,
                       allow_methods=["GET", "PATCH", "POST"], allow_headers=["Authorization", "Content-Type"])

@app.middleware("http")
async def security_headers(request, call_next):
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["Cache-Control"] = "no-store"
    response.headers["Referrer-Policy"] = "no-referrer"
    return response

@app.on_event("startup")
def startup() -> None:
    Base.metadata.create_all(bind=engine)

def db_session() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def player_payload(player: Player) -> dict[str, Any]:
    return {
        "id": player.id,
        "username": player.username,
        "profile": player.profile or {},
        "created_at": player.created_at.isoformat(),
        "updated_at": player.updated_at.isoformat(),
    }

def issue_token(player: Player) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(player.id),
        "username": player.username,
        "iss": JWT_ISSUER,
        "iat": now,
        "exp": now + timedelta(minutes=ACCESS_TOKEN_MINUTES),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm="HS256")

def current_player(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(db_session),
) -> Player:
    unauthorized = HTTPException(status_code=status.HTTP_401_UNAUTHORIZED,
                                 detail="Sessão inválida ou expirada.",
                                 headers={"WWW-Authenticate": "Bearer"})
    if credentials is None:
        raise unauthorized
    try:
        claims = jwt.decode(credentials.credentials, JWT_SECRET, algorithms=["HS256"],
                            issuer=JWT_ISSUER, options={"require": ["sub", "exp", "iss"]})
        player = db.get(Player, int(claims["sub"]))
    except (jwt.PyJWTError, ValueError, TypeError):
        raise unauthorized
    if player is None:
        raise unauthorized
    return player

@app.get("/health")
def health() -> dict[str, str]:
    with engine.connect() as connection:
        connection.exec_driver_sql("SELECT 1")
    return {"status": "ok", "service": "battle-universe-accounts"}

@app.post("/v1/accounts/register", status_code=201)
def register(body: RegisterBody, db: Session = Depends(db_session)) -> dict[str, Any]:
    username = body.username.lower()
    if not USERNAME_RE.fullmatch(username):
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
        # The unique database constraint also closes simultaneous registration races.
        if db.scalar(select(Player.id).where(Player.username == username)) is not None:
            raise HTTPException(409, "Esse nickname já está em uso.")
        raise HTTPException(503, "Não foi possível criar a conta agora.")
    return {"access_token": issue_token(player), "token_type": "bearer",
            "expires_in": ACCESS_TOKEN_MINUTES * 60, "player": player_payload(player)}

@app.post("/v1/accounts/login")
def login(body: LoginBody, db: Session = Depends(db_session)) -> dict[str, Any]:
    username = body.username.strip().lower()
    player = db.scalar(select(Player).where(Player.username == username))
    # Keep the response identical for unknown nicknames and wrong passwords.
    if player is None or not password_hash.verify(body.password, player.password_hash):
        raise HTTPException(401, "Nickname ou senha incorretos.")
    return {"access_token": issue_token(player), "token_type": "bearer",
            "expires_in": ACCESS_TOKEN_MINUTES * 60, "player": player_payload(player)}

@app.get("/v1/accounts/me")
def me(player: Player = Depends(current_player)) -> dict[str, Any]:
    return {"player": player_payload(player)}

@app.patch("/v1/accounts/me/profile")
def update_profile(body: ProfileBody, db: Session = Depends(db_session),
                   player: Player = Depends(current_player)) -> dict[str, Any]:
    if len(body.profile) > 80:
        raise HTTPException(413, "Perfil excede o limite de campos.")
    clean: dict[str, Any] = {}
    for key, value in body.profile.items():
        if key not in PROFILE_FIELDS:
            continue
        if isinstance(value, (str, bool, int, float)) or value is None:
            if isinstance(value, str) and len(value) > 256:
                raise HTTPException(422, f"Valor muito longo: {key}")
            clean[key] = value
    player.profile = {**(player.profile or {}), **clean}
    player.updated_at = datetime.now(timezone.utc)
    db.add(player)
    db.commit()
    db.refresh(player)
    return {"player": player_payload(player)}
