from __future__ import annotations

import os
import re
import secrets
import hashlib
from datetime import datetime, timedelta, timezone
from typing import Any, Generator

import jwt
from fastapi import Depends, FastAPI, HTTPException, Query, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field
from pwdlib import PasswordHash
from sqlalchemy import DateTime, Integer, String, Text, BigInteger, ForeignKey, UniqueConstraint, create_engine, select, func
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
    "wear_set_cerrado", "wear_beard_old", "wear_pants_angelical", "equipped_beard", "equipped_helmet", "equipped_backpack", "equipped_vest", "selected_pants", "character_display_name",
    "nickname", "profile_schema_version",
    "matches_played", "match_wins", "match_kills", "match_damage", "match_resources",
    "last_match_kills", "last_match_damage", "last_match_resources", "last_match_result", "match_history",
    "royale_history", "royale_spin_count",
    "mission_matches", "mission_eliminations", "mission_crystals", "missions_day",
    "mission_claimed_0", "mission_claimed_1", "mission_claimed_2", "wardrobe_starter_v2_granted",
    "shop_frame_neon", "shop_emblem_bloob", "shop_title_champion",
    "last_match_reward_xp",
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

class PlayerDevice(Base):
    __tablename__ = "player_devices"
    __table_args__ = (UniqueConstraint("player_id", "install_id", name="uq_player_device_install"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id", ondelete="CASCADE"), index=True, nullable=False)
    install_id: Mapped[str] = mapped_column(String(64), nullable=False)
    device_code: Mapped[str] = mapped_column(String(24), unique=True, index=True, nullable=False)
    device_label: Mapped[str] = mapped_column(String(40), nullable=False, default="Android")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
    last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class RecoveryCredential(Base):
    __tablename__ = "account_recovery_credentials"
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id", ondelete="CASCADE"), primary_key=True)
    code_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    failed_attempts: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    locked_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    rotated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class PlayerWallet(Base):
    __tablename__ = "player_wallets"
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id", ondelete="CASCADE"), primary_key=True)
    coins: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    diamonds: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)

class RoyaleSpin(Base):
    __tablename__ = "royale_spins"
    __table_args__ = (UniqueConstraint("player_id", "request_id", name="uq_royale_player_request"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id", ondelete="CASCADE"), index=True, nullable=False)
    request_id: Mapped[str] = mapped_column(String(64), nullable=False)
    prize_id: Mapped[str] = mapped_column(String(40), nullable=False)
    prize_name: Mapped[str] = mapped_column(String(80), nullable=False)
    duplicate: Mapped[bool] = mapped_column(nullable=False, default=False)
    diamonds_spent: Mapped[int] = mapped_column(BigInteger, nullable=False)
    balance_after: Mapped[int] = mapped_column(BigInteger, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class DailyRewardClaim(Base):
    __tablename__ = "daily_reward_claims"
    __table_args__ = (UniqueConstraint("player_id", "reward_day", name="uq_daily_reward_player_day"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id", ondelete="CASCADE"), index=True, nullable=False)
    reward_day: Mapped[str] = mapped_column(String(10), nullable=False)
    coins: Mapped[int] = mapped_column(BigInteger, nullable=False, default=250)
    diamonds: Mapped[int] = mapped_column(BigInteger, nullable=False, default=100)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class PromoCode(Base):
    __tablename__ = "promo_codes"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    code: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    diamonds: Mapped[int] = mapped_column(BigInteger, nullable=False)
    max_uses: Mapped[int] = mapped_column(Integer, nullable=False)
    uses: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    active: Mapped[bool] = mapped_column(nullable=False, default=True)
    expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_by: Mapped[str] = mapped_column(String(64), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class PromoRedemption(Base):
    __tablename__ = "promo_redemptions"
    __table_args__ = (UniqueConstraint("player_id", "promo_id", name="uq_promo_player_redemption"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id", ondelete="CASCADE"), index=True, nullable=False)
    promo_id: Mapped[int] = mapped_column(ForeignKey("promo_codes.id", ondelete="CASCADE"), index=True, nullable=False)
    diamonds_awarded: Mapped[int] = mapped_column(BigInteger, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

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

class DeviceRegisterBody(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)
    install_id: str = Field(min_length=16, max_length=64, pattern=r"^[A-Za-z0-9._:-]+$")
    device_label: str = Field(default="Android", min_length=1, max_length=40)

class RecoveryRotateBody(BaseModel):
    confirm: bool = True

class RecoverAccountBody(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)
    username: str = Field(min_length=3, max_length=18)
    recovery_code: str = Field(min_length=20, max_length=80)

class RoyaleSpinBody(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)
    request_id: str = Field(min_length=16, max_length=64, pattern=r"^[A-Za-z0-9._:-]+$")

class PromoRedeemBody(BaseModel):
    code: str = Field(min_length=3, max_length=32)

class PromoCreateBody(BaseModel):
    code: str = Field(min_length=3, max_length=32, pattern=r"^[A-Za-z0-9_-]+$")
    diamonds: int = Field(gt=0, le=1000000)
    max_uses: int = Field(default=1, ge=1, le=1000000)
    expires_in_days: int | None = Field(default=None, ge=1, le=3650)

class OwnerPairBody(BaseModel):
    pairing_code: str = Field(min_length=32, max_length=256)

class AccountEditBody(BaseModel):
    username: str | None = Field(default=None, min_length=3, max_length=18, pattern=r"^[A-Za-z0-9_]+$")
    character_name: str | None = Field(default=None, min_length=1, max_length=32)
    state: str | None = Field(default=None, pattern=r"^(active|suspended)$")
    reason: str | None = Field(default=None, max_length=240)

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
    # One-currency migration: transfer legacy coin balances into diamonds once.
    with SessionLocal() as db:
        wallets = db.scalars(select(PlayerWallet).where(PlayerWallet.coins > 0)).all()
        for wallet in wallets:
            wallet.diamonds += wallet.coins
            wallet.coins = 0
        if wallets:
            db.commit()
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
    recovery_code = new_recovery_code()
    player = Player(username=username, password_hash=password_hash.hash(body.password), profile={})
    db.add(player)
    db.flush()
    db.add(PlayerWallet(player_id=player.id, coins=0, diamonds=0))
    db.add(RecoveryCredential(player_id=player.id, code_hash=recovery_digest(recovery_code)))
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
            "player": {"id": player.id, "username": player.username, "profile": {}}, "recovery_code": recovery_code}

@app.post("/v1/accounts/login")
def login(body: LoginBody, db: Session = Depends(db_session)) -> dict[str, Any]:
    username = body.username.strip().lower()
    player = db.scalar(select(Player).where(Player.username == username))
    if player is None or not password_hash.verify(body.password, player.password_hash):
        raise HTTPException(401, "Nickname ou senha incorretos.")
    control = db.get(AccountControl, player.id)
    if control and control.state != "active":
        raise HTTPException(403, "Esta conta está suspensa ou encerrada.")
    if db.get(PlayerWallet, player.id) is None:
        db.add(PlayerWallet(player_id=player.id, coins=0, diamonds=0))
        db.commit()
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
    if {"coins", "diamonds", "economy_ledger", "last_match_reward_diamonds",
        "last_match_reward_coins", "match_reward_recorded"}.intersection(body.profile):
        raise HTTPException(403, "Carteira e recompensas são controladas exclusivamente pelo servidor.")
    clean: dict[str, Any] = {}
    for key, value in body.profile.items():
        if key in PROFILE_FIELDS and (isinstance(value, (str, bool, int, float)) or value is None):
            max_length = 8192 if key == "economy_ledger" else (4096 if key in {"match_history", "royale_history"} else 256)
            if isinstance(value, str) and len(value) > max_length:
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

ROYALE_PRIZES = [
    {"id": "shirt_tactical", "name": "CAMISA TÁTICA", "weight": 40, "profile_key": "wear_shirt_tactical"},
    {"id": "boots_combat", "name": "BOTAS COMBATE", "weight": 30, "profile_key": "wear_boots_combat"},
    {"id": "set_aurora", "name": "CONJUNTO AURORA", "weight": 20, "profile_key": "wear_set_aurora"},
    {"id": "beard_old", "name": "BARBINHA DO VELHO", "weight": 9, "profile_key": "wear_beard_old"},
    {"id": "pants_angelical", "name": "CALÇA ANGELICAL", "weight": 1, "profile_key": "wear_pants_angelical"},
]
ROYALE_COST = 100
ROYALE_DUPLICATE_COMPENSATION = 100

def new_recovery_code() -> str:
    raw = secrets.token_hex(16).upper()
    return "-".join(raw[i:i + 8] for i in range(0, len(raw), 8))

def recovery_digest(code: str) -> str:
    normalized = re.sub(r"[^A-Fa-f0-9]", "", code).upper()
    return hashlib.sha256(normalized.encode("ascii")).hexdigest()

def make_device_code() -> str:
    raw = secrets.token_hex(5).upper()
    return "BU-" + raw[:5] + "-" + raw[5:]

@app.post("/v1/accounts/me/devices/register")
def register_device(body: DeviceRegisterBody, db: Session = Depends(db_session),
                    player: Player = Depends(current_player)):
    now = datetime.now(timezone.utc)
    device = db.scalar(select(PlayerDevice).where(
        PlayerDevice.player_id == player.id, PlayerDevice.install_id == body.install_id))
    if device is None:
        device = PlayerDevice(player_id=player.id, install_id=body.install_id,
                              device_code=make_device_code(), device_label=body.device_label.strip(),
                              created_at=now, last_seen_at=now)
        db.add(device)
    else:
        device.device_label = body.device_label.strip()
        device.last_seen_at = now
    db.commit()
    db.refresh(device)
    return {"device_id": device.device_code, "device_label": device.device_label,
            "created_at": device.created_at.isoformat(), "last_seen_at": device.last_seen_at.isoformat()}

@app.get("/v1/accounts/me/devices")
def list_devices(db: Session = Depends(db_session), player: Player = Depends(current_player)):
    rows = db.scalars(select(PlayerDevice).where(PlayerDevice.player_id == player.id)
                      .order_by(PlayerDevice.last_seen_at.desc())).all()
    return {"devices": [{"device_id": row.device_code, "device_label": row.device_label,
                         "created_at": row.created_at.isoformat(), "last_seen_at": row.last_seen_at.isoformat()}
                        for row in rows]}

@app.post("/v1/accounts/me/recovery-code/rotate")
def rotate_recovery_code(body: RecoveryRotateBody, db: Session = Depends(db_session),
                         player: Player = Depends(current_player)):
    code = new_recovery_code()
    row = db.get(RecoveryCredential, player.id) or RecoveryCredential(player_id=player.id)
    row.code_hash = recovery_digest(code)
    row.failed_attempts = 0
    row.locked_until = None
    row.rotated_at = datetime.now(timezone.utc)
    db.add(row)
    db.commit()
    return {"recovery_code": code, "shown_once": True,
            "message": "Guarde este código fora do aparelho. Ele não será exibido novamente."}

@app.post("/v1/accounts/recover")
def recover_account(body: RecoverAccountBody, db: Session = Depends(db_session)):
    username = body.username.strip().lower()
    player = db.scalar(select(Player).where(Player.username == username))
    if player is None:
        secrets.compare_digest(recovery_digest(body.recovery_code), hashlib.sha256(secrets.token_bytes(32)).hexdigest())
        raise HTTPException(401, "Dados de recuperação inválidos.")
    credential = db.get(RecoveryCredential, player.id)
    now = datetime.now(timezone.utc)
    if credential is None:
        raise HTTPException(401, "Dados de recuperação inválidos.")
    if credential.locked_until and credential.locked_until > now:
        raise HTTPException(429, "Muitas tentativas. Aguarde antes de tentar novamente.")
    supplied = recovery_digest(body.recovery_code)
    if not secrets.compare_digest(supplied, credential.code_hash):
        credential.failed_attempts += 1
        if credential.failed_attempts >= 5:
            credential.failed_attempts = 0
            credential.locked_until = now + timedelta(minutes=15)
        db.add(credential)
        db.commit()
        raise HTTPException(401, "Dados de recuperação inválidos.")
    control = db.get(AccountControl, player.id)
    if control and control.state != "active":
        raise HTTPException(403, "Esta conta está suspensa ou encerrada.")
    replacement = new_recovery_code()
    credential.code_hash = recovery_digest(replacement)
    credential.failed_attempts = 0
    credential.locked_until = None
    credential.rotated_at = now
    db.add(credential)
    db.commit()
    return {"access_token": issue_token(str(player.id), username, "player", ACCESS_TOKEN_MINUTES),
            "token_type": "bearer", "expires_in": ACCESS_TOKEN_MINUTES * 60,
            "player": player_payload(player), "new_recovery_code": replacement,
            "message": "Acesso recuperado. Guarde o novo código; o anterior foi invalidado."}

@app.get("/v1/accounts/me/wallet")
def get_wallet(db: Session = Depends(db_session), player: Player = Depends(current_player)):
    wallet = db.get(PlayerWallet, player.id)
    if wallet is None:
        wallet = PlayerWallet(player_id=player.id, coins=0, diamonds=0)
        db.add(wallet)
        db.commit()
        db.refresh(wallet)
    return {"diamonds": wallet.diamonds, "currency": "diamonds", "authority": "server"}

@app.post("/v1/game/rewards/daily")
def claim_daily_reward(db: Session = Depends(db_session), player: Player = Depends(current_player)):
    day = datetime.now(timezone.utc).date().isoformat()
    existing = db.scalar(select(DailyRewardClaim).where(
        DailyRewardClaim.player_id == player.id, DailyRewardClaim.reward_day == day))
    if existing is not None:
        wallet = db.get(PlayerWallet, player.id)
        return {"claimed": False, "reward_day": day, "diamonds_awarded": existing.diamonds,
                "wallet": {"diamonds": wallet.diamonds if wallet else 0}}
    wallet = db.scalar(select(PlayerWallet).where(PlayerWallet.player_id == player.id).with_for_update())
    if wallet is None:
        wallet = PlayerWallet(player_id=player.id, coins=0, diamonds=0)
        db.add(wallet)
        db.flush()
    wallet.diamonds += 100
    claim = DailyRewardClaim(player_id=player.id, reward_day=day, coins=0, diamonds=100)
    db.add(claim)
    try:
        db.commit()
    except Exception:
        db.rollback()
        existing = db.scalar(select(DailyRewardClaim).where(
            DailyRewardClaim.player_id == player.id, DailyRewardClaim.reward_day == day))
        wallet = db.get(PlayerWallet, player.id)
        if existing is None:
            raise HTTPException(503, "Não foi possível confirmar a recompensa diária.")
        return {"claimed": False, "reward_day": day, "diamonds_awarded": existing.diamonds,
                "wallet": {"diamonds": wallet.diamonds if wallet else 0}}
    return {"claimed": True, "reward_day": day, "diamonds_awarded": 100,
            "wallet": {"diamonds": wallet.diamonds}}

@app.get("/v1/royale/config")
def royale_config(player: Player = Depends(current_player)):
    return {"cost": ROYALE_COST, "duplicate_compensation": ROYALE_DUPLICATE_COMPENSATION,
            "probabilities": [{"id": item["id"], "name": item["name"], "percent": item["weight"]}
                              for item in ROYALE_PRIZES],
            "total_percent": sum(item["weight"] for item in ROYALE_PRIZES)}

@app.post("/v1/royale/spin")
def royale_spin(body: RoyaleSpinBody, db: Session = Depends(db_session),
                player: Player = Depends(current_player)):
    existing = db.scalar(select(RoyaleSpin).where(
        RoyaleSpin.player_id == player.id, RoyaleSpin.request_id == body.request_id))
    if existing is not None:
        return {"spin_id": existing.id, "prize_id": existing.prize_id,
                "prize_name": existing.prize_name, "duplicate": existing.duplicate,
                "diamonds_spent": existing.diamonds_spent, "diamonds_balance": existing.balance_after,
                "replayed": True}
    wallet = db.scalar(select(PlayerWallet).where(PlayerWallet.player_id == player.id).with_for_update())
    if wallet is None:
        wallet = PlayerWallet(player_id=player.id, coins=0, diamonds=0)
        db.add(wallet)
        db.flush()
    if wallet.diamonds < ROYALE_COST:
        raise HTTPException(409, "Diamantes insuficientes na carteira online.")
    roll = secrets.randbelow(100)
    cursor = 0
    selected = ROYALE_PRIZES[-1]
    for item in ROYALE_PRIZES:
        cursor += item["weight"]
        if roll < cursor:
            selected = item
            break
    profile = dict(player.profile or {})
    duplicate = bool(profile.get(selected["profile_key"], False))
    credit = ROYALE_DUPLICATE_COMPENSATION if duplicate else 0
    wallet.diamonds = wallet.diamonds - ROYALE_COST + credit
    if not duplicate:
        profile[selected["profile_key"]] = True
        player.profile = profile
    row = RoyaleSpin(player_id=player.id, request_id=body.request_id,
                     prize_id=selected["id"], prize_name=selected["name"],
                     duplicate=duplicate, diamonds_spent=ROYALE_COST,
                     balance_after=wallet.diamonds, created_at=datetime.now(timezone.utc))
    db.add(row)
    try:
        db.commit()
        db.refresh(row)
    except Exception:
        db.rollback()
        replay = db.scalar(select(RoyaleSpin).where(
            RoyaleSpin.player_id == player.id, RoyaleSpin.request_id == body.request_id))
        if replay is None:
            raise HTTPException(503, "Não foi possível confirmar o giro. Tente sincronizar antes de repetir.")
        return {"spin_id": replay.id, "prize_id": replay.prize_id,
                "prize_name": replay.prize_name, "duplicate": replay.duplicate,
                "diamonds_spent": replay.diamonds_spent, "diamonds_balance": replay.balance_after,
                "replayed": True}
    return {"spin_id": row.id, "prize_id": row.prize_id, "prize_name": row.prize_name,
            "duplicate": row.duplicate, "diamonds_spent": row.diamonds_spent,
            "duplicate_compensation": credit, "diamonds_balance": wallet.diamonds,
            "replayed": False}

@app.get("/v1/royale/history")
def royale_history(limit: int = Query(default=20, ge=1, le=100),
                   db: Session = Depends(db_session), player: Player = Depends(current_player)):
    rows = db.scalars(select(RoyaleSpin).where(RoyaleSpin.player_id == player.id)
                      .order_by(RoyaleSpin.id.desc()).limit(limit)).all()
    return {"history": [{"spin_id": row.id, "prize_id": row.prize_id, "prize_name": row.prize_name,
                         "duplicate": row.duplicate, "diamonds_spent": row.diamonds_spent,
                         "diamonds_balance": row.balance_after, "created_at": row.created_at.isoformat()}
                        for row in rows]}

@app.post("/v1/owner/pair")
def owner_pair(body: OwnerPairBody) -> dict[str, Any]:
    if not OWNER_PAIRING_CODE:
        raise HTTPException(503, "Ativação do proprietário ainda não foi configurada no servidor.")
    if not secrets.compare_digest(body.pairing_code, OWNER_PAIRING_CODE):
        raise HTTPException(401, "Código de ativação inválido.")
    return {"access_token": issue_token("owner-device", "owner", "owner", OWNER_TOKEN_MINUTES),
            "token_type": "bearer", "expires_in": OWNER_TOKEN_MINUTES * 60}

@app.post("/v1/promo-codes/redeem")
def redeem_promo_code(body: PromoRedeemBody, db: Session = Depends(db_session),
                      player: Player = Depends(current_player)):
    code = body.code.strip().upper()
    promo = db.scalar(select(PromoCode).where(PromoCode.code == code).with_for_update())
    now = datetime.now(timezone.utc)
    if promo is None or not promo.active:
        raise HTTPException(404, "Código promocional inválido ou inativo.")
    if promo.expires_at is not None and promo.expires_at <= now:
        raise HTTPException(410, "Este código promocional expirou.")
    if promo.uses >= promo.max_uses:
        raise HTTPException(409, "Este código promocional atingiu o limite de resgates.")
    if db.scalar(select(PromoRedemption.id).where(PromoRedemption.player_id == player.id,
            PromoRedemption.promo_id == promo.id)) is not None:
        raise HTTPException(409, "Você já resgatou este código.")
    wallet = db.scalar(select(PlayerWallet).where(PlayerWallet.player_id == player.id).with_for_update())
    if wallet is None:
        wallet = PlayerWallet(player_id=player.id, coins=0, diamonds=0)
        db.add(wallet); db.flush()
    wallet.diamonds += promo.diamonds
    promo.uses += 1
    db.add(PromoRedemption(player_id=player.id, promo_id=promo.id, diamonds_awarded=promo.diamonds))
    try:
        db.commit()
    except Exception:
        db.rollback()
        raise HTTPException(409, "O resgate não pôde ser confirmado. Atualize a carteira e tente novamente.")
    return {"redeemed": True, "code": code, "diamonds_awarded": promo.diamonds,
            "diamonds_balance": wallet.diamonds, "message": "Diamantes adicionados à carteira."}

@app.get("/v1/owner/promo-codes")
def owner_list_promo_codes(db: Session = Depends(db_session), owner: str = Depends(current_owner)):
    rows = db.scalars(select(PromoCode).order_by(PromoCode.id.desc()).limit(100)).all()
    return {"promo_codes": [{"id": row.id, "code": row.code, "diamonds": row.diamonds,
            "uses": row.uses, "max_uses": row.max_uses, "active": row.active,
            "expires_at": row.expires_at.isoformat() if row.expires_at else None} for row in rows]}

@app.post("/v1/owner/promo-codes", status_code=201)
def owner_create_promo_code(body: PromoCreateBody, db: Session = Depends(db_session),
                            owner: str = Depends(current_owner)):
    code = body.code.strip().upper()
    if db.scalar(select(PromoCode.id).where(PromoCode.code == code)) is not None:
        raise HTTPException(409, "Já existe um código com esse nome.")
    expires = datetime.now(timezone.utc) + timedelta(days=body.expires_in_days) if body.expires_in_days else None
    row = PromoCode(code=code, diamonds=body.diamonds, max_uses=body.max_uses,
                    uses=0, active=True, expires_at=expires, created_by=owner)
    db.add(row)
    audit(db, owner, "promo_created", code, {"diamonds": body.diamonds, "max_uses": body.max_uses})
    try:
        db.commit(); db.refresh(row)
    except Exception:
        db.rollback()
        raise HTTPException(409, "Não foi possível criar o código promocional.")
    return {"id": row.id, "code": row.code, "diamonds": row.diamonds,
            "max_uses": row.max_uses, "uses": row.uses,
            "expires_at": row.expires_at.isoformat() if row.expires_at else None}

@app.get("/v1/owner/overview")
def admin_overview(db: Session = Depends(db_session), owner: str = Depends(current_owner)):
    total = db.scalar(select(func.count(Player.id))) or 0
    suspended = db.scalar(select(func.count(AccountControl.player_id)).where(AccountControl.state == "suspended")) or 0
    deleted = db.scalar(select(func.count(AccountControl.player_id)).where(AccountControl.state == "deleted")) or 0
    return {"accounts_total": total, "accounts_suspended": suspended, "accounts_deleted": deleted,
            "admin": owner}

@app.get("/v1/owner/accounts")
def admin_accounts(search: str = Query(default="", max_length=40), limit: int = Query(default=50, ge=1, le=100),
                   offset: int = Query(default=0, ge=0), db: Session = Depends(db_session),
                   owner: str = Depends(current_owner)):
    stmt = select(Player).order_by(Player.id.desc()).limit(limit).offset(offset)
    if search.strip():
        stmt = select(Player).where(Player.username.ilike(f"%{search.strip()}%")).order_by(Player.id.desc()).limit(limit).offset(offset)
    players = db.scalars(stmt).all()
    rows = []
    for p in players:
        ctl = db.get(AccountControl, p.id)
        rows.append({"id": p.id, "username": p.username, "character_name": (p.profile or {}).get("character_display_name", ""),
                     "state": ctl.state if ctl else "active", "created_at": p.created_at.isoformat()})
    return {"accounts": rows, "limit": limit, "offset": offset}

@app.patch("/v1/owner/accounts/{player_id}")
def admin_edit_account(player_id: int, body: AccountEditBody, db: Session = Depends(db_session),
                       owner: str = Depends(current_owner)):
    player = db.get(Player, player_id)
    if player is None:
        raise HTTPException(404, "Conta não encontrada.")
    changes = {}
    if body.username is not None:
        new_name = body.username.lower()
        conflict = db.scalar(select(Player.id).where(Player.username == new_name, Player.id != player_id))
        if conflict:
            raise HTTPException(409, "Esse nickname já está em uso.")
        changes["username"] = {"from": player.username, "to": new_name}
        player.username = new_name
    if body.character_name is not None:
        profile = dict(player.profile or {})
        profile["character_display_name"] = body.character_name.strip()
        player.profile = profile
        changes["character_name"] = body.character_name.strip()
    if body.state is not None:
        control = db.get(AccountControl, player_id) or AccountControl(player_id=player_id)
        control.state = body.state
        control.reason = (body.reason or "").strip()
        control.changed_at = datetime.now(timezone.utc)
        db.add(control)
        changes["state"] = body.state
    player.updated_at = datetime.now(timezone.utc)
    audit(db, owner, "account_update", str(player_id), changes)
    db.add(player); db.commit(); db.refresh(player)
    return {"account": {"id": player.id, "username": player.username,
                        "character_name": (player.profile or {}).get("character_display_name", ""),
                        "state": body.state or "active"}, "updated": changes}

@app.delete("/v1/owner/accounts/{player_id}")
def admin_delete_account(player_id: int, db: Session = Depends(db_session),
                         owner: str = Depends(current_owner)):
    player = db.get(Player, player_id)
    if player is None:
        raise HTTPException(404, "Conta não encontrada.")
    control = db.get(AccountControl, player_id) or AccountControl(player_id=player_id)
    control.state = "deleted"
    control.reason = "Encerrada pelo administrador"
    control.changed_at = datetime.now(timezone.utc)
    player.password_hash = password_hash.hash(secrets.token_urlsafe(48))
    player.profile = {}
    audit(db, owner, "account_deleted", str(player_id), {"soft_delete": True})
    db.add(control); db.add(player); db.commit()
    return {"deleted": True, "player_id": player_id, "mode": "soft_delete"}

@app.get("/v1/owner/audit")
def admin_audit(limit: int = Query(default=50, ge=1, le=100), db: Session = Depends(db_session),
                owner: str = Depends(current_owner)):
    rows = db.scalars(select(AdminAudit).order_by(AdminAudit.id.desc()).limit(limit)).all()
    return {"events": [{"id": r.id, "admin": r.admin_username, "action": r.action,
                        "target": r.target, "details": r.details, "created_at": r.created_at.isoformat()}
                       for r in rows]}

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

