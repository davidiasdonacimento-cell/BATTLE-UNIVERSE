import os
import sys
import unittest
from pathlib import Path

os.environ.setdefault("DATABASE_URL", "postgresql+psycopg://test:test@127.0.0.1:5432/battle_universe_test")
os.environ.setdefault("JWT_SECRET", "test-only-secret-that-is-long-enough-for-unit-tests")
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import main


class MultiplayerRouteContractTests(unittest.TestCase):
    def test_matchmaking_and_authoritative_match_routes_are_registered(self):
        routes = {(route.path, tuple(sorted(route.methods or []))) for route in main.app.routes}
        expected = {
            ("/v1/matches/queue", ("POST",)),
            ("/v1/matches/queue/{ticket_id}", ("GET",)),
            ("/v1/matches/queue/{ticket_id}", ("DELETE",)),
            ("/v1/matches/{match_id}", ("GET",)),
            ("/v1/matches/{match_id}/start", ("POST",)),
            ("/v1/matches/{match_id}/commands", ("POST",)),
            ("/v1/matches/{match_id}/events", ("GET",)),
            ("/v1/matches/{match_id}/heartbeat", ("POST",)),
            ("/v1/matches/{match_id}/finish", ("POST",)),
        }
        self.assertTrue(expected.issubset(routes))

    def test_multiplayer_tables_are_part_of_shared_database_metadata(self):
        expected = {
            "match_queue_entries",
            "match_sessions",
            "match_participants",
            "match_events",
        }
        self.assertTrue(expected.issubset(set(main.Base.metadata.tables)))

    def test_online_mode_contract_is_declared(self):
        from multiplayer import VALID_MODES, MAX_PLAYERS, QUEUE_WAIT_SECONDS, PROTOCOL_VERSION
        self.assertEqual(VALID_MODES, {"battle_royale": 1, "duo": 2, "squad": 4})
        self.assertEqual(MAX_PLAYERS, 30)
        self.assertEqual(QUEUE_WAIT_SECONDS, 30)
        self.assertEqual(PROTOCOL_VERSION, 1)

    def test_bot_participants_have_no_player_account_identity(self):
        from multiplayer import build_router
        self.assertIsNotNone(build_router)
        # Bot identity is represented by a bot participant ID and a NULL player_id
        # in MatchParticipant, not by a fabricated account.
        self.assertTrue(main.Base.metadata.tables["match_participants"].c["player_id"].nullable)


if __name__ == "__main__":
    unittest.main()
