# BATTLE UNIVERSE Account API

FastAPI + PostgreSQL account service. This service replaces Firebase Authentication and Firestore for player identity and the allowlisted profile settings.

## Endpoints
- GET /health — database connectivity check
- POST /v1/accounts/register — create a unique nickname/password account
- POST /v1/accounts/login — authenticate with nickname/password
- GET /v1/accounts/me — return the authenticated account
- PATCH /v1/accounts/me/profile — sync allowlisted cosmetic and settings fields

Nicknames are normalized to lowercase and constrained to 3–18 ASCII letters, digits, or underscores. Passwords are hashed with Argon2. The database unique constraint is the final authority for nickname uniqueness. Passwords are never returned or stored in plaintext. Authentication uses signed, expiring JWT bearer tokens.

The profile endpoint intentionally rejects authority over diamonds, coins, XP, match results, inventory rewards, and the economy ledger. Those require separate server-authoritative game endpoints; accepting client-written currency would permit cheating.

## Environment
Set DATABASE_URL to PostgreSQL and JWT_SECRET to a random secret with at least 32 characters. See .env.example. Do not commit production credentials.

## Local run
```sh
cd server
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
export DATABASE_URL='postgresql+psycopg://user:password@localhost:5432/battle_universe'
export JWT_SECRET="$(python -c 'import secrets; print(secrets.token_urlsafe(48))')"
uvicorn main:app --host 0.0.0.0 --port 8000
```

Interactive API documentation is available at /docs in development. Use HTTPS in production.
