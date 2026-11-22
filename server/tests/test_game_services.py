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
            "match_history", "royale_history", "mission_matches",
            "mission_claimed_0", "shop_frame_neon",
        }
        self.assertTrue(expected.issubset(main.PROFILE_FIELDS))
        self.assertTrue({"coins", "diamonds", "economy_ledger"}.isdisjoint(main.PROFILE_FIELDS))




class PromoCodeContractTests(unittest.TestCase):
    def test_promo_code_creation_requires_positive_diamonds_and_use_limit(self):
        body = main.PromoCreateBody(code="BATTLE2026", diamonds=500, max_uses=100)
        self.assertEqual(body.code, "BATTLE2026")
        self.assertEqual(body.diamonds, 500)
        self.assertEqual(body.max_uses, 100)

    def test_promo_code_rejects_invalid_characters(self):
        with self.assertRaises(Exception):
            main.PromoCreateBody(code="BAD CODE!", diamonds=100, max_uses=1)

    def test_promo_redemption_is_unique_per_player_and_code(self):
        constraints = [c for c in main.PromoRedemption.__table__.constraints
                       if getattr(c, "name", None) == "uq_promo_player_redemption"]
        self.assertEqual(len(constraints), 1)

    def test_promo_codes_are_persisted_in_server_database(self):
        self.assertEqual(main.PromoCode.__tablename__, "promo_codes")
        self.assertEqual(main.PromoRedemption.__tablename__, "promo_redemptions")
        self.assertEqual(main.PromoCode.__table__.c.id.type.python_type, str)
        self.assertEqual(main.PromoRedemption.__table__.c.promo_id.type.python_type, str)
        self.assertEqual(main.PromoCode.__table__.c.id.type.length, main.PromoRedemption.__table__.c.promo_id.type.length)


if __name__ == "__main__":
    unittest.main()
