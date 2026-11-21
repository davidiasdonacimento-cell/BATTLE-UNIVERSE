from __future__ import annotations

import hashlib
import math
import secrets
import time
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any, Callable

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import (
    BigInteger, DateTime, ForeignKey, Index, Integer, String, Text,
    UniqueConstraint, select, func, and_, or_, update
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, Session, mapped_column


PROTOCOL_VERSION = 1
QUEUE_WAIT_SECONDS = 30
MAX_PLAYERS = 30
MAX_EVENTS_PER_BATCH = 128
MAX_COMMAND_BYTES = 2048
MATCH_HEARTBEAT_SECONDS = 20
PLAYER_TIMEOUT_SECONDS = 45
VALID_MODES = {"battle_royale": 1, "duo": 2, "squad": 4}
VALID_MAPS = set(range(8))


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def safe_mode(value: str) -> str:
    mode = (value or "").strip().lower()
    if mode not in VALID_MODES:
        raise HTTPException(422, "Modo online inválido.")
    return mode


class MatchQueueEntry:
    pass


def build_router(Base, db_session, current_player) -> APIRouter:
    class QueueEntry(Base):
        __tablename__ = "match_queue_entries"
        __table_args__ = (
            UniqueConstraint("player_id", name="uq_match_queue_player"),
            Index("ix_match_queue_search", "mode", "map_id", "region", "state", "created_at"),
        )
        id: Mapped[str] = mapped_column(String(36), primary_key=True)
        player_id: Mapped[int] = mapped_column(ForeignKey("players.id", ondelete="CASCADE"), nullable=False)
        username: Mapped[str] = mapped_column(String(18), nullable=False)
        mode: Mapped[str] = mapped_column(String(24), nullable=False)
        map_id: Mapped[int] = mapped_column(Integer, nullable=False)
        region: Mapped[str] = mapped_column(String(16), nullable=False)
        state: Mapped[str] = mapped_column(String(16), nullable=False, default="queued")
        match_id: Mapped[str | None] = mapped_column(String(36), nullable=True, index=True)
        created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
        updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)

    class MatchSession(Base):
        __tablename__ = "match_sessions"
        id: Mapped[str] = mapped_column(String(36), primary_key=True)
        mode: Mapped[str] = mapped_column(String(24), nullable=False)
        map_id: Mapped[int] = mapped_column(Integer, nullable=False)
        region: Mapped[str] = mapped_column(String(16), nullable=False)
        status: Mapped[str] = mapped_column(String(16), nullable=False, default="ready")
        protocol_version: Mapped[int] = mapped_column(Integer, nullable=False, default=PROTOCOL_VERSION)
        roster: Mapped[list] = mapped_column(JSONB, nullable=False, default=list)
        state: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
        event_sequence: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
        created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
        started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
        finished_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    class MatchParticipant(Base):
        __tablename__ = "match_participants"
        __table_args__ = (
            UniqueConstraint("match_id", "participant_id", name="uq_match_participant"),
            Index("ix_match_participant_player", "player_id", "match_id"),
        )
        id: Mapped[int] = mapped_column(Integer, primary_key=True)
        match_id: Mapped[str] = mapped_column(ForeignKey("match_sessions.id", ondelete="CASCADE"), nullable=False)
        participant_id: Mapped[str] = mapped_column(String(48), nullable=False)
        player_id: Mapped[int | None] = mapped_column(ForeignKey("players.id", ondelete="SET NULL"), nullable=True)
        display_name: Mapped[str] = mapped_column(String(24), nullable=False)
        is_bot: Mapped[bool] = mapped_column(nullable=False, default=False)
        team_id: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
        connected: Mapped[bool] = mapped_column(nullable=False, default=True)
        last_seen_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
        score: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
        eliminations: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
        position: Mapped[dict] = mapped_column(JSONB, nullable=False, default=lambda: {"x": 0.0, "y": 0.0, "z": 0.0})
        last_client_sequence: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)

    class MatchEvent(Base):
        __tablename__ = "match_events"
        __table_args__ = (
            UniqueConstraint("match_id", "sequence", name="uq_match_event_sequence"),
            Index("ix_match_events_match_sequence", "match_id", "sequence"),
        )
        id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
        match_id: Mapped[str] = mapped_column(ForeignKey("match_sessions.id", ondelete="CASCADE"), nullable=False)
        sequence: Mapped[int] = mapped_column(BigInteger, nullable=False)
        actor_id: Mapped[str] = mapped_column(String(48), nullable=False)
        event_type: Mapped[str] = mapped_column(String(24), nullable=False)
        payload: Mapped[dict] = mapped_column(JSONB, nullable=False, default=dict)
        created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)

    class QueueRequest(BaseModel):
        model_config = ConfigDict(str_strip_whitespace=True)
        mode: str = Field(default="battle_royale", min_length=3, max_length=24)
        map_id: int = Field(default=0, ge=0, le=7)
        region: str = Field(default="sa-east", min_length=2, max_length=16, pattern=r"^[a-z0-9-]+$")

    class CommandRequest(BaseModel):
        model_config = ConfigDict(extra="forbid")
        sequence: int = Field(ge=1, le=2**63 - 1)
        command: str = Field(min_length=1, max_length=24, pattern=r"^(move|look|jump|crouch|interact|fire|reload|select_item)$")
        data: dict[str, Any] = Field(default_factory=dict)

    class FinishRequest(BaseModel):
        result_nonce: str = Field(min_length=24, max_length=64)
        winner_team: int = Field(ge=0, le=30)
        final_state_hash: str = Field(min_length=64, max_length=64)

    router = APIRouter(tags=["multiplayer"])

    def roster_for(db: Session, match: MatchSession) -> list[dict[str, Any]]:
        rows = db.scalars(select(MatchParticipant).where(
            MatchParticipant.match_id == match.id).order_by(MatchParticipant.team_id, MatchParticipant.id)).all()
        return [{
            "participant_id": p.participant_id,
            "player_id": p.player_id,
            "name": p.display_name,
            "is_bot": p.is_bot,
            "team_id": p.team_id,
            "connected": p.connected,
            "position": p.position,
            "eliminations": p.eliminations,
            "score": p.score,
        } for p in rows]

    def make_match(db: Session, entries: list[QueueEntry]) -> MatchSession:
        first = entries[0]
        match_id = str(uuid.uuid4())
        match = MatchSession(
            id=match_id, mode=first.mode, map_id=first.map_id, region=first.region,
            status="ready", protocol_version=PROTOCOL_VERSION,
            state={"phase": "waiting", "server_tick": 0, "winner_team": None, "result_nonce": secrets.token_hex(16)},
        )
        db.add(match)
        db.flush()
        team_size = VALID_MODES[first.mode]
        roster = []
        for index, entry in enumerate(entries):
            team_id = index // team_size if team_size > 1 else index
            participant_id = "p:" + str(entry.player_id)
            db.add(MatchParticipant(
                match_id=match_id, participant_id=participant_id, player_id=entry.player_id,
                display_name=entry.username, is_bot=False, team_id=team_id,
            ))
            roster.append({"participant_id": participant_id, "player_id": entry.player_id,
                           "name": entry.username, "is_bot": False, "team_id": team_id})
            entry.state = "matched"
            entry.match_id = match_id
            entry.updated_at = utcnow()

        # Fill the remaining 30-player slots only after the matchmaking deadline.
        # Bots have explicit identities and are never represented as human accounts.
        if len(entries) < MAX_PLAYERS:
            bot_count = MAX_PLAYERS - len(entries)
            for index in range(bot_count):
                bot_number = index + 1
                bot_id = f"bot:{match_id[:8]}:{bot_number:02d}"
                team_id = (len(entries) + index) // team_size if team_size > 1 else len(entries) + index
                name = f"IA {bot_number:02d}"
                db.add(MatchParticipant(
                    match_id=match_id, participant_id=bot_id, player_id=None,
                    display_name=name, is_bot=True, team_id=team_id,
                    position={"x": 0.0, "y": 0.0, "z": 0.0},
                ))
                roster.append({"participant_id": bot_id, "player_id": None,
                               "name": name, "is_bot": True, "team_id": team_id})
        match.roster = roster
        match.state["phase"] = "ready"
        match.state["human_count"] = len(entries)
        match.state["bot_count"] = MAX_PLAYERS - len(entries)
        db.add(match)
        return match

    def attempt_matchmaking(db: Session, entry: QueueEntry) -> MatchSession | None:
        if entry.state == "matched" and entry.match_id:
            return db.get(MatchSession, entry.match_id)
        if entry.state != "queued":
            return None
        # Serialize one search bucket so simultaneous polls cannot create duplicate rooms.
        rows = db.scalars(
            select(QueueEntry).where(
                QueueEntry.mode == entry.mode,
                QueueEntry.map_id == entry.map_id,
                QueueEntry.region == entry.region,
                QueueEntry.state == "queued",
            ).order_by(QueueEntry.created_at, QueueEntry.id).with_for_update()
        ).all()
        if not rows:
            return None
        oldest = rows[0].created_at
        if oldest.tzinfo is None:
            oldest = oldest.replace(tzinfo=timezone.utc)
        deadline_reached = (utcnow() - oldest).total_seconds() >= QUEUE_WAIT_SECONDS
        if len(rows) < MAX_PLAYERS and not deadline_reached:
            return None
        selected = rows[:MAX_PLAYERS]
        return make_match(db, selected)

    def participant_for(db: Session, match_id: str, player_id: int) -> tuple[MatchSession, MatchParticipant]:
        match = db.get(MatchSession, match_id)
        if match is None:
            raise HTTPException(404, "Partida não encontrada.")
        participant = db.scalar(select(MatchParticipant).where(
            MatchParticipant.match_id == match_id,
            MatchParticipant.player_id == player_id,
            MatchParticipant.is_bot.is_(False),
        ))
        if participant is None:
            raise HTTPException(403, "Você não participa desta partida.")
        return match, participant

    @router.post("/v1/matches/queue", status_code=201)
    def join_queue(body: QueueRequest, db: Session = Depends(db_session), player=Depends(current_player)):
        mode = safe_mode(body.mode)
        if body.map_id not in VALID_MAPS:
            raise HTTPException(422, "Mapa inválido.")
        # One durable queue row per account: reuse it after a match/cancellation
        # because uq_match_queue_player intentionally prevents duplicate tickets.
        existing = db.scalar(select(QueueEntry).where(
            QueueEntry.player_id == player.id
        ).with_for_update())
        if existing and existing.state == "matched" and existing.match_id:
            active_match = db.get(MatchSession, existing.match_id)
            if active_match and active_match.status not in {"completed", "cancelled"}:
                return {"ticket_id": existing.id, "state": "matched", "match_id": active_match.id,
                        "message": "Você já possui uma partida em andamento."}
        if existing and existing.state == "queued":
            if (existing.mode, existing.map_id, existing.region) == (mode, body.map_id, body.region):
                return {"ticket_id": existing.id, "state": "queued", "wait_seconds": QUEUE_WAIT_SECONDS}
            existing.state = "cancelled"
        now = utcnow()
        if existing is None:
            entry = QueueEntry(
                id=str(uuid.uuid4()), player_id=player.id, username=player.username,
                mode=mode, map_id=body.map_id, region=body.region, state="queued",
                created_at=now, updated_at=now,
            )
            db.add(entry)
        else:
            entry = existing
            entry.username = player.username
            entry.mode = mode
            entry.map_id = body.map_id
            entry.region = body.region
            entry.state = "queued"
            entry.match_id = None
            entry.created_at = now
            entry.updated_at = now
        try:
            db.flush()
            match = attempt_matchmaking(db, entry)
            db.commit()
        except Exception:
            db.rollback()
            raise
        return {"ticket_id": entry.id, "state": "matched" if match else "queued",
                "match_id": match.id if match else None, "wait_seconds": QUEUE_WAIT_SECONDS,
                "message": "Partida encontrada." if match else "Procurando jogadores humanos."}

    @router.get("/v1/matches/queue/{ticket_id}")
    def queue_status(ticket_id: str, db: Session = Depends(db_session), player=Depends(current_player)):
        entry = db.scalar(select(QueueEntry).where(
            QueueEntry.id == ticket_id, QueueEntry.player_id == player.id).with_for_update())
        if entry is None:
            raise HTTPException(404, "Fila não encontrada.")
        match = attempt_matchmaking(db, entry) if entry.state == "queued" else (
            db.get(MatchSession, entry.match_id) if entry.match_id else None)
        db.commit()
        if match is None:
            elapsed = max(0, int((utcnow() - (entry.created_at.replace(tzinfo=timezone.utc)
                if entry.created_at.tzinfo is None else entry.created_at)).total_seconds()))
            return {"ticket_id": entry.id, "state": entry.state, "elapsed_seconds": elapsed,
                    "remaining_seconds": max(0, QUEUE_WAIT_SECONDS - elapsed),
                    "human_players_searching": db.scalar(select(func.count()).select_from(QueueEntry).where(
                        QueueEntry.mode == entry.mode, QueueEntry.map_id == entry.map_id,
                        QueueEntry.region == entry.region, QueueEntry.state == "queued")) or 0}
        return {"ticket_id": entry.id, "state": "matched", "match_id": match.id,
                "match": {"mode": match.mode, "map_id": match.map_id, "status": match.status,
                          "protocol_version": match.protocol_version, "roster": roster_for(db, match),
                          "human_count": match.state.get("human_count", 0),
                          "bot_count": match.state.get("bot_count", 0)}}

    @router.delete("/v1/matches/queue/{ticket_id}")
    def leave_queue(ticket_id: str, db: Session = Depends(db_session), player=Depends(current_player)):
        entry = db.scalar(select(QueueEntry).where(
            QueueEntry.id == ticket_id, QueueEntry.player_id == player.id).with_for_update())
        if entry is None:
            raise HTTPException(404, "Fila não encontrada.")
        if entry.state == "queued":
            entry.state = "cancelled"
            entry.updated_at = utcnow()
            db.commit()
        return {"cancelled": entry.state == "cancelled", "state": entry.state}

    @router.get("/v1/matches/{match_id}")
    def get_match(match_id: str, db: Session = Depends(db_session), player=Depends(current_player)):
        match, participant = participant_for(db, match_id, player.id)
        now = utcnow()
        participant.connected = True
        participant.last_seen_at = now
        db.commit()
        return {"match_id": match.id, "status": match.status, "protocol_version": match.protocol_version,
                "mode": match.mode, "map_id": match.map_id, "roster": roster_for(db, match),
                "state": match.state, "event_sequence": match.event_sequence,
                "self": {"participant_id": participant.participant_id, "team_id": participant.team_id}}

    @router.post("/v1/matches/{match_id}/start")
    def start_match(match_id: str, db: Session = Depends(db_session), player=Depends(current_player)):
        match, participant = participant_for(db, match_id, player.id)
        if match.status == "ready":
            match.status = "active"
            match.started_at = utcnow()
            match.state = {**(match.state or {}), "phase": "aircraft", "started_at": match.started_at.isoformat()}
            db.add(match)
            db.commit()
        elif match.status != "active":
            raise HTTPException(409, "Esta partida não pode ser iniciada.")
        return {"match_id": match.id, "status": match.status, "state": match.state}

    @router.post("/v1/matches/{match_id}/commands")
    def submit_command(match_id: str, body: CommandRequest, db: Session = Depends(db_session),
                       player=Depends(current_player)):
        match, participant = participant_for(db, match_id, player.id)
        if match.status != "active":
            raise HTTPException(409, "A partida não está ativa.")
        if body.sequence <= participant.last_client_sequence:
            raise HTTPException(409, "Comando repetido ou fora de ordem.")
        encoded = str(body.model_dump()).encode("utf-8")
        if len(encoded) > MAX_COMMAND_BYTES:
            raise HTTPException(413, "Comando excede o limite.")
        data = body.data
        if body.command in {"move", "look"}:
            allowed = {"x", "y", "z"} if body.command == "move" else {"yaw", "pitch"}
            if set(data) - allowed or any(not isinstance(v, (int, float)) or not math.isfinite(float(v)) for v in data.values()):
                raise HTTPException(422, "Dados de movimento inválidos.")
            if body.command == "move":
                x = float(data.get("x", 0)); z = float(data.get("z", 0))
                magnitude = math.sqrt(x*x + z*z)
                if magnitude > 1.01:
                    raise HTTPException(422, "Vetor de movimento fora do limite.")
                dt = min(0.25, max(0.01, float(data.get("dt", 0.05))))
                speed = 5.0
                pos = dict(participant.position or {})
                pos["x"] = max(-150.0, min(150.0, float(pos.get("x", 0)) + x * speed * dt))
                pos["z"] = max(-150.0, min(150.0, float(pos.get("z", 0)) + z * speed * dt))
                participant.position = pos
        elif body.command == "fire":
            target = data.get("target_id")
            if not isinstance(target, str) or len(target) > 48 or target == participant.participant_id:
                raise HTTPException(422, "Alvo inválido.")
            opponent = db.scalar(select(MatchParticipant).where(
                MatchParticipant.match_id == match_id,
                MatchParticipant.participant_id == target,
            ).with_for_update())
            if opponent is None or opponent.team_id == participant.team_id or not opponent.connected:
                raise HTTPException(422, "Alvo indisponível.")
            # The server owns scoring. Client-supplied damage, health and eliminations are ignored.
            opponent.score = max(0, opponent.score - 1)
            participant.eliminations += 1
        elif body.command not in {"jump", "crouch", "interact", "reload", "select_item"}:
            raise HTTPException(422, "Comando não permitido.")
        participant.last_client_sequence = body.sequence
        match.event_sequence += 1
        db.add(MatchEvent(match_id=match_id, sequence=match.event_sequence,
                          actor_id=participant.participant_id, event_type=body.command,
                          payload={"data": data if body.command != "fire" else {"target_id": data.get("target_id")}}))
        db.add(participant); db.add(match)
        db.commit()
        return {"accepted": True, "sequence": body.sequence, "server_sequence": match.event_sequence,
                "position": participant.position, "eliminations": participant.eliminations}

    @router.get("/v1/matches/{match_id}/events")
    def match_events(match_id: str, after: int = Query(default=0, ge=0),
                     db: Session = Depends(db_session), player=Depends(current_player)):
        match, participant = participant_for(db, match_id, player.id)
        rows = db.scalars(select(MatchEvent).where(
            MatchEvent.match_id == match_id, MatchEvent.sequence > after
        ).order_by(MatchEvent.sequence).limit(MAX_EVENTS_PER_BATCH)).all()
        participants = db.scalars(select(MatchParticipant).where(
            MatchParticipant.match_id == match_id)).all()
        for row in participants:
            if row.player_id == player.id:
                row.connected = True
                row.last_seen_at = utcnow()
        db.commit()
        return {"match_id": match_id, "status": match.status, "server_sequence": match.event_sequence,
                "events": [{"sequence": e.sequence, "actor_id": e.actor_id,
                            "type": e.event_type, "payload": e.payload, "created_at": e.created_at.isoformat()}
                           for e in rows],
                "participants": [{"participant_id": p.participant_id, "name": p.display_name,
                                  "is_bot": p.is_bot, "team_id": p.team_id, "position": p.position,
                                  "eliminations": p.eliminations, "connected": p.connected}
                                 for p in participants]}

    @router.delete("/v1/matches/{match_id}/leave")
    def leave_match(match_id: str, db: Session = Depends(db_session), player=Depends(current_player)):
        match, participant = participant_for(db, match_id, player.id)
        if match.status in {"completed", "cancelled"}:
            return {"left": True, "status": match.status}
        participant.connected = False
        participant.last_seen_at = utcnow()
        db.add(participant)
        db.commit()
        return {"left": True, "status": match.status}

    @router.post("/v1/matches/{match_id}/heartbeat")
    def match_heartbeat(match_id: str, db: Session = Depends(db_session), player=Depends(current_player)):
        match, participant = participant_for(db, match_id, player.id)
        participant.connected = True
        participant.last_seen_at = utcnow()
        db.commit()
        return {"ok": True, "server_time": utcnow().isoformat(), "status": match.status}

    @router.post("/v1/matches/{match_id}/finish")
    def finish_match(match_id: str, body: FinishRequest, db: Session = Depends(db_session),
                     player=Depends(current_player)):
        match, participant = participant_for(db, match_id, player.id)
        if match.status == "completed":
            return {"accepted": True, "status": "completed", "result": match.state.get("result")}
        if match.status != "active":
            raise HTTPException(409, "Partida não está ativa.")
        expected_nonce = str(match.state.get("result_nonce", ""))
        if not secrets.compare_digest(body.result_nonce, expected_nonce):
            raise HTTPException(403, "Identificador de resultado inválido.")
        # A client may request closure, but cannot submit winner/score authority.
        participants = db.scalars(select(MatchParticipant).where(
            MatchParticipant.match_id == match_id)).all()
        humans_alive = [p for p in participants if not p.is_bot and p.connected]
        if len(humans_alive) > 1:
            raise HTTPException(409, "O servidor ainda detecta participantes humanos ativos.")
        team_scores: dict[int, int] = {}
        for p in participants:
            team_scores[p.team_id] = team_scores.get(p.team_id, 0) + p.eliminations
        winner = max(team_scores, key=team_scores.get) if team_scores else participant.team_id
        result = {"winner_team": winner, "team_scores": team_scores,
                  "human_participants": sum(not p.is_bot for p in participants),
                  "bot_participants": sum(p.is_bot for p in participants),
                  "finished_at": utcnow().isoformat(), "authority": "server"}
        match.status = "completed"
        match.finished_at = utcnow()
        match.state = {**(match.state or {}), "phase": "completed", "result": result}
        db.add(match)
        db.commit()
        return {"accepted": True, "status": "completed", "result": result}

    return router
