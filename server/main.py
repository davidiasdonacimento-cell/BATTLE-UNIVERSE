from __future__ import annotations

import os
import re
import secrets
import hashlib
import logging
import uuid
import urllib.request
import urllib.error
from datetime import datetime, timedelta, timezone
from typing import Any, Generator

import jwt
from fastapi import Depends, FastAPI, HTTPException, Query, status
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field
from pwdlib import PasswordHash
from sqlalchemy import DateTime, Integer, String, Text, BigInteger, ForeignKey, UniqueConstraint, create_engine, select, func, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker

DATABASE_URL = os.environ.get("DATABASE_URL", "").strip()
JWT_SECRET = os.environ.get("JWT_SECRET", "").strip()
JWT_ISSUER = os.environ.get("JWT_ISSUER", "battle-universe-accounts")
ACCESS_TOKEN_MINUTES = max(15, min(43200, int(os.environ.get("ACCESS_TOKEN_MINUTES", "43200"))))
ADMIN_TOKEN_MINUTES = max(10, min(720, int(os.environ.get("ADMIN_TOKEN_MINUTES", "120"))))
OWNER_PAIRING_CODE = os.environ.get("OWNER_PAIRING_CODE", "").strip()
OPENROUTER_API_KEY = os.environ.get("OPENROUTER_API_KEY", "").strip()
OPENROUTER_MODEL = os.environ.get("OPENROUTER_MODEL", "openrouter/auto").strip() or "openrouter/auto"
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
    "selected_boots", "selected_set", "equipped_beard", "equipped_helmet",
    "equipped_backpack", "equipped_vest", "selected_pants", "character_display_name",
    "nickname", "profile_schema_version"
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

class MatchReward(Base):
    __tablename__ = "match_rewards"
    __table_args__ = (UniqueConstraint("player_id", "request_id", name="uq_match_player_request"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id", ondelete="CASCADE"), index=True, nullable=False)
    request_id: Mapped[str] = mapped_column(String(64), nullable=False)
    diamonds_awarded: Mapped[int] = mapped_column(BigInteger, nullable=False)
    xp_awarded: Mapped[int] = mapped_column(BigInteger, nullable=False)
    balance_after: Mapped[int] = mapped_column(BigInteger, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class RewardClaim(Base):
    __tablename__ = "reward_claims"
    __table_args__ = (UniqueConstraint("player_id", "reward_type", "reward_key", name="uq_reward_player_type_key"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id", ondelete="CASCADE"), index=True, nullable=False)
    reward_type: Mapped[str] = mapped_column(String(32), nullable=False)
    reward_key: Mapped[str] = mapped_column(String(64), nullable=False)
    diamonds_awarded: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    xp_awarded: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    balance_after: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)
class ShopPurchase(Base):
    __tablename__ = "shop_purchases"
    __table_args__ = (UniqueConstraint("player_id", "request_id", name="uq_shop_player_request"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id", ondelete="CASCADE"), index=True, nullable=False)
    request_id: Mapped[str] = mapped_column(String(64), nullable=False)
    item_key: Mapped[str] = mapped_column(String(64), nullable=False)
    diamonds_spent: Mapped[int] = mapped_column(BigInteger, nullable=False)
    balance_after: Mapped[int] = mapped_column(BigInteger, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)


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
    # Preserve the VARCHAR primary-key type already present in the production database.
    id: Mapped[str] = mapped_column(String(64), primary_key=True, default=lambda: str(uuid.uuid4()))
    code: Mapped[str] = mapped_column(String(32), unique=True, index=True, nullable=False)
    diamonds: Mapped[int] = mapped_column(BigInteger, nullable=False)
    max_uses: Mapped[int] = mapped_column(Integer, nullable=False)
    # Legacy production schema requires this column too; keep both limits synchronized.
    max_redemptions: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
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
    promo_id: Mapped[str] = mapped_column(String(64), ForeignKey("promo_codes.id", ondelete="CASCADE"), index=True, nullable=False)
    diamonds_awarded: Mapped[int] = mapped_column(BigInteger, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class GlobalMail(Base):
    __tablename__ = "global_mail"
    id: Mapped[str] = mapped_column(String(64), primary_key=True, default=lambda: str(uuid.uuid4()))
    request_id: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    title: Mapped[str] = mapped_column(String(80), nullable=False)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    diamonds: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    created_by: Mapped[str] = mapped_column(String(64), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

class GlobalMailClaim(Base):
    __tablename__ = "global_mail_claims"
    __table_args__ = (UniqueConstraint("player_id", "message_id", name="uq_global_mail_player_message"),)
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id", ondelete="CASCADE"), index=True, nullable=False)
    message_id: Mapped[str] = mapped_column(ForeignKey("global_mail.id", ondelete="CASCADE"), index=True, nullable=False)
    diamonds_awarded: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    claimed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), nullable=False)

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

class CoachBody(BaseModel):
    question: str = Field(min_length=2, max_length=800)

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

class MatchCompleteBody(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)
    request_id: str = Field(min_length=16, max_length=64, pattern=r"^[A-Za-z0-9._:-]+$")
    victory: bool = False
    placement: int = Field(ge=1, le=30)
    kills: int = Field(ge=0, le=29)
    damage: int = Field(ge=0, le=50000)
    resources: int = Field(ge=0, le=100)
class ShopPurchaseBody(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)
    request_id: str = Field(min_length=16, max_length=64, pattern=r"^[A-Za-z0-9._:-]+$")
    item_key: str = Field(min_length=3, max_length=64, pattern=r"^[a-z0-9_]+$")


class PromoRedeemBody(BaseModel):
    code: str = Field(min_length=3, max_length=32)

class PromoCreateBody(BaseModel):
    code: str = Field(min_length=3, max_length=32, pattern=r"^[A-Za-z0-9_-]+$")
    diamonds: int = Field(gt=0, le=1000000)
    max_uses: int = Field(default=1, ge=1, le=1000000)
    expires_in_days: int | None = Field(default=None, ge=1, le=3650)

class GlobalMailBody(BaseModel):
    title: str = Field(min_length=2, max_length=80)
    body: str = Field(min_length=2, max_length=1200)
    diamonds: int = Field(default=0, ge=0, le=100000)
    request_id: str = Field(min_length=16, max_length=64, pattern=r"^[A-Za-z0-9._:-]+$")

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

def migrate_promo_code_schema() -> None:
    """Add fields missing from the legacy promo_codes table without deleting existing rows."""
    statements = (
        "ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS max_uses INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS max_redemptions INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS uses INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE",
        "ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ NULL",
        "ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS created_by VARCHAR(64) NOT NULL DEFAULT 'legacy'",
        "ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP",
    )
    with engine.begin() as connection:
        for statement in statements:
            connection.execute(text(statement))


@app.on_event("startup")
def startup() -> None:
    Base.metadata.create_all(bind=engine)
    migrate_promo_code_schema()
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



@app.post("/v1/ai/coach")
def ai_coach(body: CoachBody, player: Player = Depends(current_player)) -> dict[str, Any]:
    """Game coach proxy. The OpenRouter secret is never sent to Android."""
    if not OPENROUTER_API_KEY:
        raise HTTPException(503, "A IA ainda não foi configurada no servidor.")
    question = body.question.strip()
    if len(question) < 2:
        raise HTTPException(422, "Escreva uma dúvida para a IA.")
    payload = {
        "model": OPENROUTER_MODEL,
        "temperature": 0.55,
        "max_tokens": 650,
        "messages": [
            {"role": "system", "content": (
                "Você é o treinador oficial do jogo BATTLE UNIVERSE. Responda em português do Brasil, "
                "com instruções práticas, curtas e adequadas para iniciantes. Explique controles, "
                "modos, mapas, posicionamento, sobrevivência e estratégias gerais. Não invente "
                "funções que o jogo não possui: atualmente há partidas offline com bots e o modo "
                "multiplayer online ainda não está disponível. Não peça senhas, tokens ou dados pessoais. "
                "Se não souber algo específico do jogo, deixe isso claro."
            )},
            {"role": "user", "content": question},
        ],
    }
    request = urllib.request.Request(
        "https://openrouter.ai/api/v1/chat/completions",
        data=__import__("json").dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {OPENROUTER_API_KEY}",
            "Content-Type": "application/json",
            "HTTP-Referer": "https://battle-universe-accounts.onrender.com",
            "X-Title": "BATTLE UNIVERSE Coach",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            result = __import__("json").loads(response.read(1024 * 1024).decode("utf-8"))
        answer = result.get("choices", [{}])[0].get("message", {}).get("content", "")
        if not isinstance(answer, str) or not answer.strip():
            raise ValueError("empty model response")
        return {"answer": answer.strip(), "model": result.get("model", OPENROUTER_MODEL)}
    except urllib.error.HTTPError as exc:
        # Do not expose provider response headers, credentials, or raw secrets.
        detail = "O serviço de IA recusou a solicitação."
        if exc.code in (401, 403):
            detail = "A chave OpenRouter está ausente ou inválida no Render."
        elif exc.code == 429:
            detail = "A IA está ocupada ou atingiu o limite. Tente novamente mais tarde."
        logging.getLogger("battle_universe").warning("OPENROUTER_HTTP_ERROR status=%s player_id=%s", exc.code, player.id)
        raise HTTPException(503, detail)
    except Exception:
        logging.getLogger("battle_universe").exception("OPENROUTER_REQUEST_FAILED player_id=%s", player.id)
        raise HTTPException(503, "Não foi possível conectar à IA agora. Tente novamente.")

@app.get("/admin/monitoramento", response_class=HTMLResponse)
def admin_monitoramento():
    return HTMLResponse("""<!doctype html>
<html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>BATTLE UNIVERSE • Monitoramento</title>
<style>
:root{color-scheme:dark}body{margin:0;background:#08131f;color:#e8f5fa;font:15px system-ui;padding:24px}
main{max-width:900px;margin:auto}h1{color:#69dce5}small{color:#9db6c5}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:14px;margin-top:22px}
.card{background:#122638;border:1px solid #315366;border-radius:16px;padding:20px}
.value{font-size:24px;font-weight:800;margin:12px 0;color:#75e0c9}.muted{color:#9db6c5}
#log{white-space:pre-wrap;overflow-wrap:anywhere}
</style><main><h1>BATTLE UNIVERSE • CENTRAL DE SERVIDORES</h1>
<small>Monitoramento da API de contas e do banco de dados • atualização automática</small>
<div class="grid">
<section class="card"><b>API BATTLE UNIVERSE</b><div class="value" id="api">VERIFICANDO...</div><div class="muted" id="latency">Aguardando resposta</div></section>
<section class="card"><b>Banco de dados</b><div class="value" id="db">VERIFICANDO...</div><div class="muted">Verificação pela rota /health</div></section>
<section class="card"><b>Última verificação</b><div class="value" id="updated">--:--:--</div><div class="muted">Atualiza a cada 15 segundos</div></section>
</div><section class="card" style="margin-top:14px"><b>Registro de monitoramento</b><p id="log" class="muted">Inicializando...</p></section>
</main><script>
const api=document.getElementById('api'),db=document.getElementById('db'),lat=document.getElementById('latency'),
updated=document.getElementById('updated'),log=document.getElementById('log');
async function check(){const start=performance.now();try{const r=await fetch('/health',{cache:'no-store'});const data=await r.json();const ms=Math.round(performance.now()-start);
if(!r.ok||data.status!=='ok')throw new Error('Health check recusado');
api.textContent='🟢 ONLINE';db.textContent='🟢 CONECTADO';lat.textContent='Resposta em '+ms+' ms';
log.textContent='API respondeu normalmente. Banco de dados acessível.\nServiço: '+(data.service||'BATTLE UNIVERSE')+'\nHTTP: '+r.status;
}catch(e){api.textContent='🔴 INDISPONÍVEL';db.textContent='⚠️ NÃO CONFIRMADO';lat.textContent='Falha na verificação';
log.textContent='Falha de comunicação: '+String(e)}updated.textContent=new Date().toLocaleTimeString('pt-BR')}
check();setInterval(check,15000);
</script></html>""")

@app.get("/health")
def health():
    try:
        with engine.connect() as connection:
            connection.exec_driver_sql("SELECT 1")
        return JSONResponse({"status": "ok", "service": "battle-universe-accounts-admin",
                             "database": "ok"},
                            headers={"Access-Control-Allow-Origin": "*"})
    except Exception:
        logging.getLogger("battle_universe").exception("HEALTH_CHECK_FAILED")
        return JSONResponse({"status": "error", "service": "battle-universe-accounts-admin",
                             "database": "unavailable"}, status_code=503,
                            headers={"Access-Control-Allow-Origin": "*"})

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
    profile = dict(player.profile or {})
    profile_changed = False
    owned_keys = (
        "shop_frame_neon", "shop_emblem_bloob", "shop_title_champion",
        "wear_shirt_tactical", "wear_shirt_neon", "wear_shirt_frost", "wear_shirt_desert",
        "wear_boots_combat", "wear_boots_neon", "wear_boots_frost", "wear_boots_gold",
        "wear_set_aurora", "wear_set_champion", "wear_set_orbit", "wear_set_desert",
        "wear_set_volcano", "wear_set_nebula", "wear_set_cerrado",
        "wear_beard_old", "wear_pants_angelical",
    )
    for key in owned_keys:
        if key not in profile:
            profile[key] = False
            profile_changed = True
    if not profile.get("wardrobe_starter_v2_granted"):
        profile["wear_shirt_tactical"] = True
        profile["wear_boots_combat"] = True
        profile["wardrobe_starter_v2_granted"] = True
        profile_changed = True
    if profile_changed:
        player.profile = profile
    if wallet in db.new or profile_changed:
        db.commit()
        db.refresh(wallet)
    return {"diamonds": wallet.diamonds, "currency": "diamonds", "authority": "server"}

SHOP_PRICE_BY_KEY = {
    "shop_frame_neon": 500,
    "shop_emblem_bloob": 1000,
    "shop_title_champion": 1500,
    "wear_shirt_tactical": 650,
    "wear_shirt_neon": 800,
    "wear_shirt_frost": 750,
    "wear_shirt_desert": 700,
    "wear_boots_combat": 450,
    "wear_boots_neon": 600,
    "wear_boots_frost": 550,
    "wear_boots_gold": 700,
    "wear_set_aurora": 1800,
    "wear_set_champion": 2200,
    "wear_set_orbit": 2500,
    "wear_set_desert": 1900,
}

@app.post("/v1/shop/purchase")
def shop_purchase(body: ShopPurchaseBody, db: Session = Depends(db_session),
                  player: Player = Depends(current_player)):
    price = SHOP_PRICE_BY_KEY.get(body.item_key)
    if price is None:
        raise HTTPException(422, "Item não reconhecido pela loja.")
    replay = db.scalar(select(ShopPurchase).where(
        ShopPurchase.player_id == player.id, ShopPurchase.request_id == body.request_id))
    if replay is not None:
        if replay.item_key != body.item_key:
            raise HTTPException(409, "Identificador de compra já utilizado em outro item.")
        wallet_now = db.get(PlayerWallet, player.id)
        return {"purchased": True, "replayed": True, "item_key": replay.item_key,
                "diamonds_spent": replay.diamonds_spent,
                "wallet": {"diamonds": wallet_now.diamonds if wallet_now else replay.balance_after}}
    wallet = db.scalar(select(PlayerWallet).where(
        PlayerWallet.player_id == player.id).with_for_update())
    if wallet is None:
        wallet = PlayerWallet(player_id=player.id, coins=0, diamonds=0)
        db.add(wallet)
        db.flush()
    db.refresh(player)
    profile = dict(player.profile or {})
    if profile.get(body.item_key) is True:
        return {"purchased": False, "already_owned": True, "item_key": body.item_key,
                "wallet": {"diamonds": wallet.diamonds}, "profile": profile}
    if wallet.diamonds < price:
        raise HTTPException(409, "Diamantes insuficientes na carteira online.",
                            headers={"X-BU-Error": "INSUFFICIENT_DIAMONDS"})
    wallet.diamonds -= price
    profile[body.item_key] = True
    player.profile = profile
    row = ShopPurchase(player_id=player.id, request_id=body.request_id,
                       item_key=body.item_key, diamonds_spent=price,
                       balance_after=wallet.diamonds)
    db.add(row)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        replay = db.scalar(select(ShopPurchase).where(
            ShopPurchase.player_id == player.id, ShopPurchase.request_id == body.request_id))
        if replay is None:
            raise HTTPException(503, "Compra não confirmada. Sincronize a carteira antes de tentar novamente.")
        wallet_now = db.get(PlayerWallet, player.id)
        return {"purchased": True, "replayed": True, "item_key": replay.item_key,
                "diamonds_spent": replay.diamonds_spent,
                "wallet": {"diamonds": wallet_now.diamonds if wallet_now else replay.balance_after}}
    return {"purchased": True, "replayed": False, "item_key": body.item_key,
            "diamonds_spent": price, "wallet": {"diamonds": wallet.diamonds},
            "profile": player.profile}


MATCH_MISSION_CONFIG = {
    "0": {"field": "mission_matches", "goal": 1, "xp": 120, "diamonds": 0},
    "1": {"field": "mission_eliminations", "goal": 3, "xp": 180, "diamonds": 0},
    "2": {"field": "mission_crystals", "goal": 3, "xp": 0, "diamonds": 80},
}
ROYALE_TIER_CONFIG = {"0": (2, 40), "1": (4, 60), "2": (6, 80), "3": (8, 100), "4": (10, 120), "5": (12, 150)}
ACHIEVEMENT_CONFIG = {"500": (500, 50), "1500": (1500, 100), "3000": (3000, 200)}

@app.post("/v1/game/matches/complete")
def complete_match(body: MatchCompleteBody, db: Session = Depends(db_session),
                   player: Player = Depends(current_player)):
    existing = db.scalar(select(MatchReward).where(
        MatchReward.player_id == player.id, MatchReward.request_id == body.request_id))
    if existing is not None:
        wallet = db.get(PlayerWallet, player.id)
        return {"recorded": True, "replayed": True, "diamonds_awarded": existing.diamonds_awarded,
                "xp_awarded": existing.xp_awarded,
                "wallet": {"diamonds": wallet.diamonds if wallet else existing.balance_after},
                "profile": player.profile or {}}
    wallet = db.scalar(select(PlayerWallet).where(
        PlayerWallet.player_id == player.id).with_for_update())
    if wallet is None:
        wallet = PlayerWallet(player_id=player.id, coins=0, diamonds=0)
        db.add(wallet)
        db.flush()
    db.refresh(player)
    existing = db.scalar(select(MatchReward).where(
        MatchReward.player_id == player.id, MatchReward.request_id == body.request_id))
    if existing is not None:
        wallet_now = db.get(PlayerWallet, player.id)
        return {"recorded": True, "replayed": True, "diamonds_awarded": existing.diamonds_awarded,
                "xp_awarded": existing.xp_awarded,
                "wallet": {"diamonds": wallet_now.diamonds if wallet_now else existing.balance_after},
                "profile": player.profile or {}}
    now = datetime.now(timezone.utc)
    last_match = db.scalar(select(MatchReward).where(
        MatchReward.player_id == player.id).order_by(MatchReward.id.desc()).limit(1))
    if last_match is not None and last_match.created_at > now - timedelta(seconds=15):
        raise HTTPException(429, "Aguarde alguns segundos antes de registrar outra partida.")
    profile = dict(player.profile or {})
    day = int(now.timestamp() // 86400)
    if profile.get("missions_day") != day:
        profile.update({"missions_day": day, "mission_matches": 0, "mission_eliminations": 0,
                        "mission_crystals": 0, "mission_claimed_0": False,
                        "mission_claimed_1": False, "mission_claimed_2": False})
    placement = 1 if body.victory else body.placement
    placement_score = max(0, 31 - placement)
    diamonds = 20 + body.kills * 8 + min(200, (body.damage // 100) * 3) + body.resources * 5 + placement_score * 2 + (120 if body.victory else 0)
    xp = 60 + body.kills * 30 + min(600, body.damage // 20) + body.resources * 10 + placement_score * 18 + (250 if body.victory else 0)
    wallet.diamonds += diamonds
    profile["xp"] = max(0, int(profile.get("xp", 0) or 0)) + xp
    profile["matches_played"] = max(0, int(profile.get("matches_played", 0) or 0)) + 1
    profile["match_wins"] = max(0, int(profile.get("match_wins", 0) or 0)) + (1 if body.victory else 0)
    profile["match_kills"] = max(0, int(profile.get("match_kills", 0) or 0)) + body.kills
    profile["match_damage"] = max(0, int(profile.get("match_damage", 0) or 0)) + body.damage
    profile["match_resources"] = max(0, int(profile.get("match_resources", 0) or 0)) + body.resources
    profile["last_match_kills"] = body.kills
    profile["last_match_damage"] = body.damage
    profile["last_match_resources"] = body.resources
    profile["last_match_result"] = "VITORIA" if body.victory else "ELIMINADO"
    profile["mission_matches"] = int(profile.get("mission_matches", 0) or 0) + 1
    profile["mission_eliminations"] = min(999, int(profile.get("mission_eliminations", 0) or 0) + body.kills)
    profile["mission_crystals"] = min(999, int(profile.get("mission_crystals", 0) or 0) + body.resources)
    player.profile = profile
    db.add(MatchReward(player_id=player.id, request_id=body.request_id, diamonds_awarded=diamonds,
                       xp_awarded=xp, balance_after=wallet.diamonds, created_at=now))
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        existing = db.scalar(select(MatchReward).where(
            MatchReward.player_id == player.id, MatchReward.request_id == body.request_id))
        if existing is None:
            raise HTTPException(503, "Resultado não confirmado. Sincronize antes de repetir.")
        wallet = db.get(PlayerWallet, player.id)
        return {"recorded": True, "replayed": True, "diamonds_awarded": existing.diamonds_awarded,
                "xp_awarded": existing.xp_awarded,
                "wallet": {"diamonds": wallet.diamonds if wallet else existing.balance_after},
                "profile": player.profile or {}}
    return {"recorded": True, "replayed": False, "diamonds_awarded": diamonds,
            "xp_awarded": xp, "wallet": {"diamonds": wallet.diamonds}, "profile": profile}

@app.post("/v1/game/rewards/{reward_type}/{reward_key}/claim")
def claim_configured_reward(reward_type: str, reward_key: str,
                            db: Session = Depends(db_session),
                            player: Player = Depends(current_player)):
    profile = dict(player.profile or {})
    if reward_type == "mission":
        config = MATCH_MISSION_CONFIG.get(reward_key)
        if config is None:
            raise HTTPException(404, "Missão não encontrada.")
        today = int(datetime.now(timezone.utc).timestamp() // 86400)
        if profile.get("missions_day") != today:
            raise HTTPException(409, "As missões diárias ainda não têm progresso. Jogue uma partida.")
        if int(profile.get(config["field"], 0) or 0) < config["goal"]:
            raise HTTPException(409, "Missão ainda não concluída.")
        claim_key = "mission_claimed_" + reward_key
        xp, diamonds = config["xp"], config["diamonds"]
    elif reward_type == "royale_tier":
        config = ROYALE_TIER_CONFIG.get(reward_key)
        if config is None:
            raise HTTPException(404, "Marco do passe não encontrado.")
        level = min(30, 1 + max(0, int(profile.get("xp", 0) or 0)) // 500)
        if level < config[0]:
            raise HTTPException(409, "Nível do passe insuficiente.")
        claim_key, xp, diamonds = "royale_tier_claimed_" + reward_key, 0, config[1]
    elif reward_type == "achievement":
        config = ACHIEVEMENT_CONFIG.get(reward_key)
        if config is None:
            raise HTTPException(404, "Conquista não encontrada.")
        if max(0, int(profile.get("xp", 0) or 0)) < config[0]:
            raise HTTPException(409, "Experiência insuficiente para esta conquista.")
        claim_key, xp, diamonds = "achievement_" + reward_key, 0, config[1]
    elif reward_type == "event" and reward_key == "season03_welcome":
        claim_key, xp, diamonds = "event_season_claimed", 150, 75
    else:
        raise HTTPException(404, "Recompensa não encontrada.")
    previous = db.scalar(select(RewardClaim).where(
        RewardClaim.player_id == player.id, RewardClaim.reward_type == reward_type,
        RewardClaim.reward_key == reward_key))
    wallet = db.scalar(select(PlayerWallet).where(
        PlayerWallet.player_id == player.id).with_for_update())
    if wallet is None:
        wallet = PlayerWallet(player_id=player.id, coins=0, diamonds=0)
        db.add(wallet)
        db.flush()
    db.refresh(player)
    profile = dict(player.profile or {})
    previous = db.scalar(select(RewardClaim).where(
        RewardClaim.player_id == player.id, RewardClaim.reward_type == reward_type,
        RewardClaim.reward_key == reward_key))
    if previous is not None or profile.get(claim_key):
        return {"claimed": False, "already_claimed": True,
                "diamonds_awarded": previous.diamonds_awarded if previous else diamonds,
                "xp_awarded": previous.xp_awarded if previous else xp,
                "wallet": {"diamonds": wallet.diamonds}, "profile": profile}
    if reward_type == "mission":
        today = int(datetime.now(timezone.utc).timestamp() // 86400)
        if profile.get("missions_day") != today or int(profile.get(config["field"], 0) or 0) < config["goal"]:
            raise HTTPException(409, "Missão ainda não concluída.")
    elif reward_type == "royale_tier":
        level = min(30, 1 + max(0, int(profile.get("xp", 0) or 0)) // 500)
        if level < config[0]:
            raise HTTPException(409, "Nível do passe insuficiente.")
    elif reward_type == "achievement" and max(0, int(profile.get("xp", 0) or 0)) < config[0]:
        raise HTTPException(409, "Experiência insuficiente para esta conquista.")
    wallet.diamonds += diamonds
    profile[claim_key] = True
    if xp:
        profile["xp"] = max(0, int(profile.get("xp", 0) or 0)) + xp
    player.profile = profile
    db.add(RewardClaim(player_id=player.id, reward_type=reward_type, reward_key=reward_key,
                       diamonds_awarded=diamonds, xp_awarded=xp, balance_after=wallet.diamonds))
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        previous = db.scalar(select(RewardClaim).where(
            RewardClaim.player_id == player.id, RewardClaim.reward_type == reward_type,
            RewardClaim.reward_key == reward_key))
        wallet = db.get(PlayerWallet, player.id)
        if previous is None:
            raise HTTPException(503, "Não foi possível confirmar a recompensa.")
        return {"claimed": False, "already_claimed": True,
                "diamonds_awarded": previous.diamonds_awarded, "xp_awarded": previous.xp_awarded,
                "wallet": {"diamonds": wallet.diamonds if wallet else previous.balance_after},
                "profile": player.profile or {}}
    return {"claimed": True, "already_claimed": False, "diamonds_awarded": diamonds,
            "xp_awarded": xp, "wallet": {"diamonds": wallet.diamonds}, "profile": profile}

@app.get("/v1/accounts/me/stats")
def account_stats(db: Session = Depends(db_session), player: Player = Depends(current_player)):
    profile = player.profile or {}
    wallet = db.get(PlayerWallet, player.id)
    return {
        "username": player.username,
        "xp": int(profile.get("xp", 0) or 0),
        "matches_played": int(profile.get("matches_played", 0) or 0),
        "match_wins": int(profile.get("match_wins", 0) or 0),
        "match_kills": int(profile.get("match_kills", 0) or 0),
        "match_damage": int(profile.get("match_damage", 0) or 0),
        "diamonds": int(wallet.diamonds if wallet else 0),
        "authority": "server",
    }


@app.get("/v1/game/leaderboard")
def game_leaderboard(limit: int = Query(default=20, ge=1, le=100),
                     db: Session = Depends(db_session), player: Player = Depends(current_player)):
    # Public game ranking contains only game nickname and aggregate gameplay statistics.
    players = db.scalars(select(Player).limit(5000)).all()
    entries = []
    for item in players:
        profile = item.profile or {}
        control = db.get(AccountControl, item.id)
        if control and control.state != "active":
            continue
        entries.append({
            "username": item.username,
            "nickname": str(profile.get("nickname") or profile.get("character_display_name") or item.username)[:24],
            "xp": max(0, int(profile.get("xp", 0) or 0)),
            "wins": max(0, int(profile.get("match_wins", 0) or 0)),
            "kills": max(0, int(profile.get("match_kills", 0) or 0)),
        })
    entries.sort(key=lambda row: (row["xp"], row["wins"], row["kills"]), reverse=True)
    return {"ranking": [{"rank": index + 1, **row} for index, row in enumerate(entries[:limit])],
            "limit": limit, "sort": "xp_wins_kills"}


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
        wallet_now = db.get(PlayerWallet, player.id)
        return {"spin_id": existing.id, "prize_id": existing.prize_id,
                "prize_name": existing.prize_name, "duplicate": existing.duplicate,
                "diamonds_spent": existing.diamonds_spent,
                "diamonds_balance": wallet_now.diamonds if wallet_now else existing.balance_after,
                "profile": player.profile or {}, "replayed": True}
    latest_spin = db.scalar(select(RoyaleSpin).where(
        RoyaleSpin.player_id == player.id).order_by(RoyaleSpin.id.desc()).limit(1))
    if latest_spin is not None and latest_spin.created_at > datetime.now(timezone.utc) - timedelta(seconds=2):
        raise HTTPException(429, "Aguarde dois segundos entre os giros.")
    wallet = db.scalar(select(PlayerWallet).where(PlayerWallet.player_id == player.id).with_for_update())
    if wallet is None:
        wallet = PlayerWallet(player_id=player.id, coins=0, diamonds=0)
        db.add(wallet)
        db.flush()
    db.refresh(player)
    latest_spin = db.scalar(select(RoyaleSpin).where(
        RoyaleSpin.player_id == player.id).order_by(RoyaleSpin.id.desc()).limit(1))
    if latest_spin is not None and latest_spin.created_at > datetime.now(timezone.utc) - timedelta(seconds=2):
        raise HTTPException(429, "Aguarde dois segundos entre os giros.")
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
        wallet_now = db.get(PlayerWallet, player.id)
        return {"spin_id": replay.id, "prize_id": replay.prize_id,
                "prize_name": replay.prize_name, "duplicate": replay.duplicate,
                "diamonds_spent": replay.diamonds_spent,
                "diamonds_balance": wallet_now.diamonds if wallet_now else replay.balance_after,
                "profile": player.profile or {}, "replayed": True}
    return {"spin_id": row.id, "prize_id": row.prize_id, "prize_name": row.prize_name,
            "duplicate": row.duplicate, "diamonds_spent": row.diamonds_spent,
            "duplicate_compensation": credit, "diamonds_balance": wallet.diamonds,
            "profile": profile, "replayed": False}

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


# Server-authoritative virtual shop, missions, events, mail and achievements.
SHOP_CATALOG = [
    {"id":"shop_frame_neon","name":"MOLDURA NEON","price":500},
    {"id":"shop_emblem_bloob","name":"EMBLEMA BLOOB","price":1000},
    {"id":"shop_title_champion","name":"TÍTULO CAMPEÃO","price":1500},
    {"id":"wear_shirt_tactical","name":"CAMISA TÁTICA","price":650},
    {"id":"wear_shirt_neon","name":"CAMISA NEON","price":800},
    {"id":"wear_shirt_frost","name":"CAMISA FROST","price":750},
    {"id":"wear_shirt_desert","name":"CAMISA DESERTO","price":700},
    {"id":"wear_boots_combat","name":"BOTAS COMBATE","price":450},
    {"id":"wear_boots_neon","name":"BOTAS NEON","price":600},
    {"id":"wear_boots_frost","name":"BOTAS FROST","price":550},
    {"id":"wear_boots_gold","name":"BOTAS DOURADAS","price":700},
    {"id":"wear_set_aurora","name":"CONJUNTO AURORA","price":1800},
    {"id":"wear_set_champion","name":"CONJUNTO CAMPEÃO","price":2200},
    {"id":"wear_set_orbit","name":"CONJUNTO ORBITAL","price":2500},
    {"id":"wear_set_desert","name":"CONJUNTO DESERTO","price":1900},
]
MISSION_CONFIG = [
    {"id":"play_match","name":"JOGUE UMA PARTIDA","goal":1,"xp":120,"diamonds":0,"progress":"mission_matches"},
    {"id":"three_eliminations","name":"ELIMINE 3 OPONENTES","goal":3,"xp":180,"diamonds":0,"progress":"mission_eliminations"},
    {"id":"collect_crystals","name":"COLETE 3 CRISTAIS","goal":3,"xp":0,"diamonds":80,"progress":"mission_crystals"},
]
ACHIEVEMENT_CONFIG = [
    {"id":"first_match","name":"PRIMEIRA PARTIDA","field":"matches_played","goal":1,"xp":100,"diamonds":25},
    {"id":"first_victory","name":"PRIMEIRA VITÓRIA","field":"match_wins","goal":1,"xp":200,"diamonds":50},
    {"id":"eliminator_25","name":"ELIMINADOR","field":"match_kills","goal":25,"xp":300,"diamonds":100},
]

@app.get("/v1/game/shop/catalog")
def shop_catalog(db: Session = Depends(db_session), player: Player = Depends(current_player)):
    profile = player.profile or {}
    wallet = db.get(PlayerWallet, player.id)
    return {"items":[{**item,"owned":bool(profile.get(item["id"],False))} for item in SHOP_CATALOG],
            "diamonds":int(wallet.diamonds if wallet else 0),"currency":"virtual_diamonds"}

@app.post("/v1/game/shop/purchase")
def shop_purchase(item_id: str = Query(min_length=3, max_length=40),
                  db: Session = Depends(db_session), player: Player = Depends(current_player)):
    item = next((entry for entry in SHOP_CATALOG if entry["id"] == item_id), None)
    if item is None:
        raise HTTPException(404,"Item não encontrado na loja.")
    profile = dict(player.profile or {})
    if profile.get(item_id):
        raise HTTPException(409,"Este item já pertence à sua conta.")
    wallet = db.scalar(select(PlayerWallet).where(PlayerWallet.player_id == player.id).with_for_update())
    if wallet is None:
        wallet = PlayerWallet(player_id=player.id,coins=0,diamonds=0);db.add(wallet);db.flush()
    if wallet.diamonds < item["price"]:
        raise HTTPException(409,"Diamantes insuficientes na carteira online.")
    wallet.diamonds -= item["price"]
    profile[item_id] = True
    player.profile = profile
    db.commit()
    return {"purchased":True,"item_id":item_id,"diamonds_spent":item["price"],
            "diamonds_balance":wallet.diamonds,"owned":True}

@app.get("/v1/game/missions")
def game_missions(player: Player = Depends(current_player)):
    profile = player.profile or {}
    today = int(datetime.now(timezone.utc).timestamp() // 86400)
    same_day = profile.get("missions_day") == today
    return {"missions":[{"id":m["id"],"name":m["name"],"goal":m["goal"],
             "progress":min(m["goal"],int(profile.get(m["progress"],0) or 0)) if same_day else 0,
             "xp":m["xp"],"diamonds":m["diamonds"],
             "claimed":bool(profile.get("mission_claimed_"+str(i),False)) if same_day else False}
             for i,m in enumerate(MISSION_CONFIG)],"day":today}

@app.post("/v1/game/missions/{mission_id}/claim")
def claim_game_mission(mission_id: str, db: Session = Depends(db_session),
                       player: Player = Depends(current_player)):
    index = next((i for i,m in enumerate(MISSION_CONFIG) if m["id"] == mission_id),None)
    if index is None: raise HTTPException(404,"Missão não encontrada.")
    mission = MISSION_CONFIG[index]
    profile = dict(player.profile or {})
    today = datetime.now(timezone.utc).date().isoformat()
    if profile.get("missions_day") != today: raise HTTPException(409,"As missões ainda não foram sincronizadas hoje.")
    if int(profile.get(mission["progress"],0) or 0) < mission["goal"]:
        raise HTTPException(409,"Missão ainda não concluída.")
    key="mission_claimed_"+str(index)
    if profile.get(key): raise HTTPException(409,"Esta missão já foi resgatada.")
    wallet=db.scalar(select(PlayerWallet).where(PlayerWallet.player_id==player.id).with_for_update())
    if wallet is None: wallet=PlayerWallet(player_id=player.id,coins=0,diamonds=0);db.add(wallet);db.flush()
    db.refresh(player)
    profile=dict(player.profile or {})
    if profile.get(key): raise HTTPException(409,"Esta missão já foi resgatada.")
    if profile.get("missions_day") != today or int(profile.get(mission["progress"],0) or 0) < mission["goal"]:
        raise HTTPException(409,"Missão ainda não concluída.")
    wallet.diamonds += mission["diamonds"]
    profile[key]=True
    profile["xp"]=int(profile.get("xp",0) or 0)+mission["xp"]
    player.profile=profile
    db.commit()
    return {"claimed":True,"mission_id":mission_id,"xp_awarded":mission["xp"],
            "diamonds_awarded":mission["diamonds"],"diamonds_balance":wallet.diamonds}

@app.get("/v1/game/events")
def game_events(player: Player = Depends(current_player)):
    profile=player.profile or {}
    return {"events":[{"id":"season_welcome_03","name":"DESAFIO DE BOAS-VINDAS",
        "description":"Entre no lobby e resgate o pacote de participação.",
        "diamonds":50,"claimed":bool(profile.get("event_season_claimed",False))}],
        "currency":"virtual_diamonds"}

@app.post("/v1/game/events/{event_id}/claim")
def claim_game_event(event_id: str, db: Session = Depends(db_session),
                     player: Player = Depends(current_player)):
    if event_id!="season_welcome_03": raise HTTPException(404,"Evento não encontrado.")
    profile=dict(player.profile or {})
    if profile.get("event_season_claimed"): raise HTTPException(409,"Recompensa deste evento já coletada.")
    wallet=db.scalar(select(PlayerWallet).where(PlayerWallet.player_id==player.id).with_for_update())
    if wallet is None: wallet=PlayerWallet(player_id=player.id,coins=0,diamonds=0);db.add(wallet);db.flush()
    db.refresh(player)
    profile=dict(player.profile or {})
    if profile.get("event_season_claimed"): raise HTTPException(409,"Recompensa deste evento já coletada.")
    wallet.diamonds+=75;profile["event_season_claimed"]=True
    profile["xp"]=max(0,int(profile.get("xp",0) or 0))+150
    player.profile=profile
    db.commit()
    return {"claimed":True,"xp_awarded":150,"diamonds_awarded":75,"diamonds_balance":wallet.diamonds}

@app.get("/v1/game/mail")
def game_mail(db: Session = Depends(db_session), player: Player = Depends(current_player)):
    profile = player.profile or {}
    messages = [{
        "id": "welcome_gift_01",
        "title": "BOAS-VINDAS AO BATTLE UNIVERSE",
        "body": "Presente de entrada: 50 diamantes virtuais.",
        "diamonds": 50,
        "claimed": bool(profile.get("mail_welcome_claimed", False)),
    }]
    broadcasts = db.scalars(
        select(GlobalMail).where(GlobalMail.created_at >= player.created_at)
        .order_by(GlobalMail.created_at.desc()).limit(100)
    ).all()
    claims = db.scalars(select(GlobalMailClaim).where(
        GlobalMailClaim.player_id == player.id
    )).all()
    claimed_ids = {claim.message_id for claim in claims}
    messages.extend([{
        "id": row.id, "title": row.title, "body": row.body,
        "diamonds": int(row.diamonds), "claimed": row.id in claimed_ids,
        "created_at": row.created_at.isoformat(), "global": True,
    } for row in reversed(broadcasts)])
    return {"messages": messages}

@app.post("/v1/game/mail/{message_id}/claim")
def claim_game_mail(message_id: str, db: Session = Depends(db_session),
                    player: Player = Depends(current_player)):
    if message_id == "welcome_gift_01":
        profile = dict(player.profile or {})
        if profile.get("mail_welcome_claimed"):
            raise HTTPException(409, "Presente desta mensagem já coletado.")
        wallet = db.scalar(select(PlayerWallet).where(
            PlayerWallet.player_id == player.id
        ).with_for_update())
        if wallet is None:
            wallet = PlayerWallet(player_id=player.id, coins=0, diamonds=0)
            db.add(wallet); db.flush()
        db.refresh(player)
        profile = dict(player.profile or {})
        if profile.get("mail_welcome_claimed"):
            raise HTTPException(409, "Presente desta mensagem já coletado.")
        wallet.diamonds += 50
        profile["mail_welcome_claimed"] = True
        player.profile = profile
        db.commit()
        return {"claimed": True, "diamonds_awarded": 50, "diamonds_balance": wallet.diamonds}

    message = db.scalar(select(GlobalMail).where(
        GlobalMail.id == message_id, GlobalMail.created_at >= player.created_at
    ))
    if message is None:
        raise HTTPException(404, "Mensagem não encontrada.")
    existing = db.scalar(select(GlobalMailClaim).where(
        GlobalMailClaim.player_id == player.id,
        GlobalMailClaim.message_id == message.id
    ))
    if existing is not None:
        wallet = db.get(PlayerWallet, player.id)
        return {"claimed": False, "diamonds_awarded": existing.diamonds_awarded,
                "diamonds_balance": int(wallet.diamonds if wallet else 0)}
    wallet = db.scalar(select(PlayerWallet).where(
        PlayerWallet.player_id == player.id
    ).with_for_update())
    if wallet is None:
        wallet = PlayerWallet(player_id=player.id, coins=0, diamonds=0)
        db.add(wallet); db.flush()
    claim = GlobalMailClaim(player_id=player.id, message_id=message.id,
                            diamonds_awarded=int(message.diamonds))
    db.add(claim)
    wallet.diamonds += int(message.diamonds)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        claim = db.scalar(select(GlobalMailClaim).where(
            GlobalMailClaim.player_id == player.id,
            GlobalMailClaim.message_id == message.id
        ))
        wallet = db.get(PlayerWallet, player.id)
        if claim is None:
            raise HTTPException(503, "Não foi possível confirmar o presente.")
        return {"claimed": False, "diamonds_awarded": claim.diamonds_awarded,
                "diamonds_balance": int(wallet.diamonds if wallet else 0)}
    return {"claimed": True, "diamonds_awarded": int(message.diamonds),
            "diamonds_balance": int(wallet.diamonds)}

@app.get("/v1/game/achievements")
def game_achievements(player: Player = Depends(current_player)):
    profile=player.profile or {}
    return {"achievements":[{"id":a["id"],"name":a["name"],"goal":a["goal"],
        "progress":min(a["goal"],max(0,int(profile.get(a["field"],0) or 0))),
        "xp":a["xp"],"diamonds":a["diamonds"],
        "claimed":bool(profile.get("achievement_claimed_"+a["id"],False))}
        for a in ACHIEVEMENT_CONFIG]}

@app.post("/v1/game/achievements/{achievement_id}/claim")
def claim_game_achievement(achievement_id: str, db: Session = Depends(db_session),
                           player: Player = Depends(current_player)):
    achievement=next((a for a in ACHIEVEMENT_CONFIG if a["id"]==achievement_id),None)
    if achievement is None: raise HTTPException(404,"Conquista não encontrada.")
    profile=dict(player.profile or {})
    key="achievement_claimed_"+achievement_id
    progress=max(0,int(profile.get(achievement["field"],0) or 0))
    if progress<achievement["goal"]: raise HTTPException(409,"Conquista ainda não concluída.")
    if profile.get(key): raise HTTPException(409,"Conquista já resgatada.")
    wallet=db.scalar(select(PlayerWallet).where(PlayerWallet.player_id==player.id).with_for_update())
    if wallet is None: wallet=PlayerWallet(player_id=player.id,coins=0,diamonds=0);db.add(wallet);db.flush()
    wallet.diamonds+=achievement["diamonds"]
    profile["xp"]=int(profile.get("xp",0) or 0)+achievement["xp"]
    profile[key]=True;player.profile=profile
    db.commit()
    return {"claimed":True,"achievement_id":achievement_id,"xp_awarded":achievement["xp"],
            "diamonds_awarded":achievement["diamonds"],"diamonds_balance":wallet.diamonds}

@app.get("/admin/correio", response_class=HTMLResponse)
def owner_mail_admin_page():
    return HTMLResponse(r"""
<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>BATTLE UNIVERSE • Correio Global</title><style>
*{box-sizing:border-box}body{margin:0;background:#07111d;color:#eaf7ff;font:16px Arial,sans-serif;padding:22px}
main{max-width:760px;margin:30px auto;background:#0d2030;border:1px solid #1b5265;border-radius:18px;padding:24px;box-shadow:0 18px 55px #0008}
h1{margin:0 0 8px;color:#59d9ed}p{color:#a8c3d2;line-height:1.5}.field{margin:16px 0}label{display:block;margin-bottom:7px;color:#bceaf2;font-weight:bold}
input,textarea{width:100%;padding:13px;border-radius:10px;border:1px solid #28536a;background:#071724;color:white;font:inherit}
textarea{min-height:130px;resize:vertical}.row{display:grid;grid-template-columns:1fr 1fr;gap:14px}
button{border:0;border-radius:10px;padding:14px 18px;background:#12a9c5;color:#00151d;font-weight:bold;font-size:15px;cursor:pointer}
button:disabled{opacity:.5}.secondary{background:#25465a;color:#eaf7ff}.status{margin-top:16px;padding:12px;border-radius:10px;background:#102b3b;white-space:pre-wrap}
small{color:#8baab9}@media(max-width:560px){.row{grid-template-columns:1fr}main{padding:18px;margin:10px auto}}
</style></head><body><main>
<h1>✉ Correio Global</h1><p>Envie uma mensagem para todos os jogadores que já possuem conta. Você pode anexar diamantes virtuais para cada jogador resgatar uma única vez.</p>
<div class="field"><label for="pair">Código de autorização do proprietário</label><input id="pair" type="password" autocomplete="off" placeholder="Cole o código de pareamento"></div>
<button id="connect" class="secondary">Autorizar ADM</button>
<div id="composer" hidden>
<div class="field"><label for="title">Título da mensagem</label><input id="title" maxlength="80" placeholder="Ex.: Presente especial da atualização"></div>
<div class="field"><label for="body">Mensagem para os jogadores</label><textarea id="body" maxlength="1200" placeholder="Escreva o comunicado..."></textarea></div>
<div class="row"><div class="field"><label for="diamonds">Diamantes por jogador</label><input id="diamonds" type="number" min="0" max="100000" value="0"></div><div class="field"><label>Destinatários</label><input value="Todas as contas existentes" disabled></div></div>
<button id="send">ENVIAR PARA TODOS</button>
</div><div id="status" class="status" role="status">Aguardando autorização.</div>
</main><script>
const API=location.origin, $=id=>document.getElementById(id);
let token="";
function show(s){$("status").textContent=s}
$("connect").onclick=async()=>{
 const code=$("pair").value.trim(); if(code.length<32){show("Código de autorização inválido.");return}
 $("connect").disabled=true;
 try{const r=await fetch(API+"/v1/owner/pair",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({pairing_code:code})});
 const d=await r.json();if(!r.ok)throw new Error(d.detail||"Falha na autorização.");
 token=d.access_token;$("pair").value="";$("pair").disabled=true;$("connect").hidden=true;$("composer").hidden=false;show("ADM autorizado. Prepare a mensagem global.");
 }catch(e){show("Erro: "+e.message)}finally{$("connect").disabled=false}
};
$("send").onclick=async()=>{
 const title=$("title").value.trim(),body=$("body").value.trim(),diamonds=Number($("diamonds").value);
 if(title.length<2||body.length<2||!Number.isInteger(diamonds)||diamonds<0||diamonds>100000){show("Confira título, mensagem e quantidade de diamantes.");return}
 if(!confirm("Enviar esta mensagem para todos os jogadores? O presente ficará disponível uma vez por conta."))return;
 $("send").disabled=true;
 try{const request_id=(crypto.randomUUID?crypto.randomUUID():Date.now()+"-"+Math.random().toString(36).slice(2));
 const r=await fetch(API+"/v1/owner/mail/broadcast",{method:"POST",headers:{"Content-Type":"application/json","Authorization":"Bearer "+token},body:JSON.stringify({title,body,diamonds,request_id})});
 const d=await r.json();if(!r.ok)throw new Error(d.detail||"Falha ao enviar.");
 $("title").value="";$("body").value="";$("diamonds").value="0";
 show("🟢 VERDE — mensagem enviada. Contas alcançadas: "+d.recipients+"; presente: "+d.diamonds_per_player+" diamantes por jogador.");
 }catch(e){show("❌ DEU RED! "+e.message)}finally{$("send").disabled=false}
};
</script></body></html>""")

@app.post("/v1/owner/mail/broadcast", status_code=201)
def owner_broadcast_mail(body: GlobalMailBody, db: Session = Depends(db_session),
                         owner: str = Depends(current_owner)):
    existing = db.scalar(select(GlobalMail).where(GlobalMail.request_id == body.request_id))
    if existing is not None:
        recipients = db.scalar(select(func.count(Player.id)).where(Player.created_at <= existing.created_at)) or 0
        return {"sent": True, "replayed": True, "message_id": existing.id,
                "recipients": recipients, "diamonds_per_player": existing.diamonds}
    row = GlobalMail(request_id=body.request_id, title=body.title.strip(),
                     body=body.body.strip(), diamonds=body.diamonds, created_by=owner)
    db.add(row)
    try:
        db.commit()
        db.refresh(row)
    except IntegrityError:
        db.rollback()
        existing = db.scalar(select(GlobalMail).where(GlobalMail.request_id == body.request_id))
        if existing is None:
            raise HTTPException(503, "Não foi possível publicar a mensagem global.")
        row = existing
    recipients = db.scalar(select(func.count(Player.id)).where(Player.created_at <= row.created_at)) or 0
    try:
        audit(db, owner, "global_mail_broadcast", row.id,
              {"title": row.title, "recipients": recipients, "diamonds_per_player": row.diamonds})
        db.commit()
    except Exception:
        db.rollback()
        logging.getLogger("battle_universe").exception("GLOBAL_MAIL_AUDIT_FAILED")
    return {"sent": True, "replayed": False, "message_id": row.id,
            "recipients": recipients, "diamonds_per_player": int(row.diamonds)}

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
                    max_redemptions=body.max_uses, uses=0, active=True,
                    expires_at=expires, created_by=owner)
    db.add(row)
    try:
        # Persist the promo code independently: a legacy audit-table schema must
        # never roll back a valid code or make creation appear to fail.
        db.commit()
        db.refresh(row)
    except IntegrityError as exc:
        db.rollback()
        # Only PostgreSQL unique_violation (23505) means the code name is taken.
        # NOT NULL, CHECK, FK and other constraint failures must not be mislabeled
        # as duplicates; log their exact database diagnostic for a safe repair.
        original = getattr(exc, "orig", None)
        sqlstate = getattr(original, "sqlstate", None) or getattr(original, "pgcode", None)
        diag = getattr(original, "diag", None)
        constraint = getattr(diag, "constraint_name", None) if diag else None
        if sqlstate == "23505":
            raise HTTPException(409, "Já existe um código com esse nome.")
        logging.getLogger("battle_universe").error(
            "PROMO_CODE_INTEGRITY_FAILURE sqlstate=%s constraint=%s error=%r",
            sqlstate, constraint, original, exc_info=True
        )
        raise HTTPException(500, "O banco recusou os dados do código. A falha foi registrada para correção.")
    except Exception as exc:
        db.rollback()
        logging.getLogger("battle_universe").exception("PROMO_CODE_INSERT_FAILED")
        raise HTTPException(500, "Falha ao salvar o código promocional no banco.")

    try:
        audit(db, owner, "promo_created", code, {"diamonds": body.diamonds, "max_uses": body.max_uses})
        db.commit()
    except Exception:
        db.rollback()
        logging.getLogger("battle_universe").exception("PROMO_CODE_AUDIT_FAILED")
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

