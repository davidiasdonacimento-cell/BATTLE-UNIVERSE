import os
import re
import sys
import unittest
from pathlib import Path

os.environ.setdefault("DATABASE_URL", "postgresql+psycopg://test:test@127.0.0.1:5432/battle_universe_test")
os.environ.setdefault("JWT_SECRET", "test-only-secret-that-is-long-enough-for-unit-tests")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import main


class AccountIdentityTests(unittest.TestCase):
    def test_recovery_code_has_128_bits_and_human_grouping(self):
        code = main.new_recovery_code()
        self.assertRegex(code, r"^[A-F0-9]{8}(-[A-F0-9]{8}){3}$")
        self.assertEqual(len(main.recovery_digest(code)), 64)

    def test_recovery_digest_ignores_group_separators_and_case(self):
        code = "A1B2C3D4-E5F60718-192A3B4C-5D6E7F80"
        compact = code.replace("-", "").lower()
        self.assertEqual(main.recovery_digest(code), main.recovery_digest(compact))

    def test_device_code_is_public_and_not_recovery_secret(self):
        code = main.make_device_code()
        self.assertRegex(code, r"^BU-[A-F0-9]{5}-[A-F0-9]{5}$")
        self.assertNotEqual(code, main.new_recovery_code())

    def test_online_promo_code_is_fixed_and_server_owned(self):
        self.assertEqual(main.PROMO_CODES["100K"], {"id": "WELCOME_100K", "diamonds": 100000})
        self.assertNotIn("coins", main.PROFILE_FIELDS)
        self.assertNotIn("diamonds", main.PROFILE_FIELDS)

    def test_wallet_schema_is_diamonds_only(self):
        self.assertIn("diamonds", main.PlayerWallet.__table__.columns)
        self.assertNotIn("coins", main.PlayerWallet.__table__.columns)
        self.assertNotIn("coins", main.DailyRewardClaim.__table__.columns)
        self.assertIn("wallet_transactions", main.Base.metadata.tables)

    def test_owner_wallet_and_promo_management_routes_exist(self):
        paths = {route.path for route in main.app.routes}
        self.assertIn("/v1/owner/accounts/{player_id}/wallet", paths)
        self.assertIn("/v1/owner/promos", paths)
        self.assertIn("/v1/owner/promos/{promo_id}", paths)

    def test_promo_code_configuration_is_server_managed(self):
        self.assertIn("promo_codes", main.Base.metadata.tables)
        self.assertIn("promo_code_redemptions", main.Base.metadata.tables)

    def test_royale_probabilities_are_exactly_one_hundred_percent(self):
        self.assertEqual(sum(item["weight"] for item in main.ROYALE_PRIZES), 100)
        self.assertEqual(len({item["id"] for item in main.ROYALE_PRIZES}), 5)
        self.assertEqual(len({item["profile_key"] for item in main.ROYALE_PRIZES}), 5)

    def test_royale_prize_profile_keys_match_existing_catalog(self):
        expected = {
            "wear_shirt_tactical",
            "wear_boots_combat",
            "wear_set_aurora",
            "wear_beard_old",
            "wear_pants_angelical",
        }
        self.assertEqual({item["profile_key"] for item in main.ROYALE_PRIZES}, expected)

    def test_cloud_profile_includes_progression_and_recovery_data(self):
        expected = {
            "xp", "matches_played", "match_wins", "match_kills",
            "match_history", "economy_ledger", "royale_history",
            "mission_matches", "mission_claimed_0", "shop_frame_neon",
        }
        self.assertTrue(expected.issubset(main.PROFILE_FIELDS))



if __name__ == "__main__":
    unittest.main()
