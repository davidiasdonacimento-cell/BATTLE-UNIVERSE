# BATTLE UNIVERSE Account API

FastAPI + PostgreSQL account service.

## Endpoints
- GET /health
- POST /v1/accounts/register
- POST /v1/accounts/login
- GET /v1/accounts/me
- PATCH /v1/accounts/me/profile

Nicknames: 3–18 ASCII letters, digits or underscores; unique and case-normalized. Passwords: 8–72 characters, stored as Argon2 hashes only. The API issues a signed JWT with up to 30 days of validity.

The Android client stores only the JWT session token and nickname, encrypted with AES-GCM using an Android Keystore key. It never stores the password. Normal in-place APK updates preserve the session only when the package ID and signing certificate remain unchanged.

The profile endpoint only accepts allowlisted settings/cosmetics. Wallets, diamonds, coins, XP, rewards and match results must be controlled by authoritative server endpoints, not values supplied by the client.

Set DATABASE_URL and JWT_SECRET in the hosting provider. Never commit real credentials. See ../ACCOUNT_SERVER_SETUP.md for deployment and signing continuity.
