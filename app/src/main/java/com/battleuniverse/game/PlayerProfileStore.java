package com.battleuniverse.game;

import android.content.SharedPreferences;

/**
 * Shared local profile/economy boundary used by lobby systems.
 *
 * This store is the offline source of truth only. Online balances and rewards
 * must be confirmed by the authoritative game service before being applied.
 */
public final class PlayerProfileStore {
    public enum PurchaseResult { PURCHASED, ALREADY_OWNED, INSUFFICIENT_FUNDS, SAVE_FAILED }

    private static final Object LOCK = new Object();
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_LEDGER_ENTRIES = 60;
    private final SharedPreferences prefs;

    public PlayerProfileStore(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences");
        prefs = preferences;
        migrate();
    }

    private void migrate() {
        synchronized (LOCK) {
            int version = prefs.getInt("profile_schema_version", 0);
            if (version >= SCHEMA_VERSION) return;
            long xp = Math.max(0L, prefs.getLong("xp", 0L));
            long diamonds = Math.max(0L, prefs.getLong("diamonds", 0L));
            SharedPreferences.Editor edit = prefs.edit()
                    .putLong("xp", xp)
                    .putLong("diamonds", diamonds)
                    .putInt("profile_schema_version", SCHEMA_VERSION);
            if (!prefs.getBoolean("coins_wallet_initialized", false)) {
                edit.putLong("coins", Math.max(0L, prefs.getLong("coins", 1000L)))
                    .putBoolean("coins_wallet_initialized", true);
            } else {
                edit.putLong("coins", Math.max(0L, prefs.getLong("coins", 0L)));
            }
            edit.commit();
        }
    }

    public long getXp() { return Math.max(0L, prefs.getLong("xp", 0L)); }
    public long getDiamonds() { return Math.max(0L, prefs.getLong("diamonds", 0L)); }
    public long getCoins() { return Math.max(0L, prefs.getLong("coins", 0L)); }
    public String getNickname() { return prefs.getString("nickname", "JOGADOR"); }
    public int getLevel() { return (int) Math.min(30L, 1L + getXp() / 500L); }
    public long getLevelProgress() { return getXp() % 500L; }

    public int getMatchesPlayed() { return Math.max(0, prefs.getInt("matches_played", 0)); }
    public int getWins() { return Math.max(0, prefs.getInt("match_wins", 0)); }
    public int getEliminations() { return Math.max(0, prefs.getInt("match_kills", 0)); }

    /** Records one completed local match and its statistics once per session ID. */
    public boolean recordMatch(String matchId, boolean victory, int eliminations,
                               int damage, int resources) {
        if (matchId == null || matchId.trim().isEmpty()) return false;
        synchronized (LOCK) {
            String safeId = safeKey(matchId);
            String marker = "match_stats_tx_" + safeId;
            if (prefs.getBoolean(marker, false) || containsMatchHistoryId(safeId)) return false;
            int kills = Math.max(0, eliminations);
            int dealt = Math.max(0, damage);
            int collected = Math.max(0, resources);
            SharedPreferences.Editor edit = prefs.edit()
                    .putInt("matches_played", getMatchesPlayed() + 1)
                    .putInt("match_wins", getWins() + (victory ? 1 : 0))
                    .putInt("match_kills", getEliminations() + kills)
                    .putInt("match_damage", Math.max(0, prefs.getInt("match_damage", 0)) + dealt)
                    .putInt("match_resources", Math.max(0, prefs.getInt("match_resources", 0)) + collected)
                    .putInt("last_match_kills", kills)
                    .putInt("last_match_damage", dealt)
                    .putInt("last_match_resources", collected)
                    .putString("last_match_result", victory ? "VITÓRIA" : "ELIMINAÇÃO")
                    .putBoolean(marker, true);
            appendMatchHistory(edit, safeId, victory, kills, dealt, collected);
            return edit.commit();
        }
    }

    private boolean containsMatchHistoryId(String id) {
        String history = prefs.getString("match_history", "");
        if (history.isEmpty()) return false;
        String prefix = id + "|";
        for (String row : history.split("\\n")) if (row.startsWith(prefix)) return true;
        return false;
    }

    private void appendMatchHistory(SharedPreferences.Editor edit, String id, boolean victory,
                                    int kills, int damage, int resources) {
        String old = prefs.getString("match_history", "");
        String[] rows = old.isEmpty() ? new String[0] : old.split("\\n");
        StringBuilder next = new StringBuilder();
        int start = Math.max(0, rows.length - 29);
        for (int i = start; i < rows.length; i++) {
            if (rows[i].isEmpty()) continue;
            if (next.length() > 0) next.append('\n');
            next.append(rows[i]);
        }
        if (next.length() > 0) next.append('\n');
        next.append(id).append('|').append(victory ? "W" : "L").append('|')
                .append(kills).append('|').append(damage).append('|').append(resources);
        edit.putString("match_history", next.toString());
    }

    /** Atomically grants a local reward once, updating wallet, XP and claim marker together. */
    public boolean claimOnce(String transactionId, String legacyMarker,
                             long coins, long diamonds, long xp) {
        if (transactionId == null || transactionId.trim().isEmpty()
                || coins < 0L || diamonds < 0L || xp < 0L) return false;
        synchronized (LOCK) {
            String txKey = "economy_tx_" + safeKey(transactionId);
            if (prefs.getBoolean(txKey, false) || containsLedgerTransaction(transactionId)) return false;
            if (legacyMarker != null && prefs.getBoolean(legacyMarker, false)) return false;
            long nextCoins = safeAdd(getCoins(), coins);
            long nextDiamonds = safeAdd(getDiamonds(), diamonds);
            long nextXp = safeAdd(getXp(), xp);
            SharedPreferences.Editor edit = prefs.edit()
                    .putLong("coins", nextCoins)
                    .putLong("diamonds", nextDiamonds)
                    .putLong("xp", nextXp)
                    .putBoolean(txKey, true);
            if (legacyMarker != null) edit.putBoolean(legacyMarker, true);
            appendLedger(edit, transactionId, "REWARD", coins, diamonds, xp);
            return edit.commit();
        }
    }

    /** Atomically debits diamonds and grants ownership; repeat taps cannot double-charge. */
    public PurchaseResult purchase(String itemKey, long price) {
        if (itemKey == null || itemKey.trim().isEmpty() || price < 0L) return PurchaseResult.SAVE_FAILED;
        synchronized (LOCK) {
            if (prefs.getBoolean(itemKey, false)) return PurchaseResult.ALREADY_OWNED;
            long balance = getDiamonds();
            if (balance < price) return PurchaseResult.INSUFFICIENT_FUNDS;
            SharedPreferences.Editor edit = prefs.edit()
                    .putLong("diamonds", balance - price)
                    .putBoolean(itemKey, true);
            appendLedger(edit, "purchase_" + itemKey, "PURCHASE", 0L, -price, 0L);
            return edit.commit() ? PurchaseResult.PURCHASED : PurchaseResult.SAVE_FAILED;
        }
    }

    /** Atomically purchases a profile cosmetic with the soft-currency wallet. */
    public PurchaseResult purchaseWithCoins(String itemKey, long price) {
        if (itemKey == null || itemKey.trim().isEmpty() || price < 0L) return PurchaseResult.SAVE_FAILED;
        synchronized (LOCK) {
            if (prefs.getBoolean(itemKey, false)) return PurchaseResult.ALREADY_OWNED;
            long balance = getCoins();
            if (balance < price) return PurchaseResult.INSUFFICIENT_FUNDS;
            SharedPreferences.Editor edit = prefs.edit()
                    .putLong("coins", balance - price)
                    .putBoolean(itemKey, true);
            appendLedger(edit, "purchase_" + itemKey, "PURCHASE_COINS", -price, 0L, 0L);
            return edit.commit() ? PurchaseResult.PURCHASED : PurchaseResult.SAVE_FAILED;
        }
    }

    /** Resolves the Royale debit, prize, duplicate compensation and history in one commit. */
    public String performRoyaleSpin(long cost, String itemKey, long diamondPrize,
                                    long duplicateCompensation, String itemName) {
        if (cost < 0L || diamondPrize < 0L || duplicateCompensation < 0L) return null;
        synchronized (LOCK) {
            long balance = getDiamonds();
            if (balance < cost) return null;
            boolean itemPrize = itemKey != null && !itemKey.isEmpty();
            boolean duplicate = itemPrize && prefs.getBoolean(itemKey, false);
            long credit = itemPrize ? (duplicate ? duplicateCompensation : 0L) : diamondPrize;
            long next = safeAdd(balance - cost, credit);
            String result;
            if (!itemPrize) result = diamondPrize + " DIAMANTES";
            else if (duplicate) result = itemName + " REPETIDO • +" + duplicateCompensation + " DIAMANTES";
            else result = itemName;
            SharedPreferences.Editor edit = prefs.edit()
                    .putLong("diamonds", next)
                    .putString("royale_last_reward", result)
                    .putInt("royale_spin_count", prefs.getInt("royale_spin_count", 0) + 1);
            if (itemPrize && !duplicate) edit.putBoolean(itemKey, true);
            appendLedger(edit, "royale_spin_" + (prefs.getInt("royale_spin_count", 0) + 1),
                    "ROYALE", 0L, credit - cost, 0L);
            return edit.commit() ? result : null;
        }
    }

    public String[] getRecentLedgerEntries(int count) {
        String ledger = prefs.getString("economy_ledger", "");
        if (ledger.isEmpty() || count <= 0) return new String[0];
        String[] rows = ledger.split("\\n");
        int size = Math.min(count, rows.length);
        String[] result = new String[size];
        for (int i = 0; i < size; i++) {
            String[] fields = rows[rows.length - 1 - i].split("\\|");
            if (fields.length < 5) {
                result[i] = rows[rows.length - 1 - i];
                continue;
            }
            result[i] = fields[1] + "  •  " + signed(fields[2]) + " moedas  •  "
                    + signed(fields[3]) + " diamantes  •  " + signed(fields[4]) + " XP";
        }
        return result;
    }

    private static String signed(String value) {
        try {
            long amount = Long.parseLong(value);
            return amount > 0L ? "+" + amount : String.valueOf(amount);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private boolean containsLedgerTransaction(String id) {
        String prefix = safeKey(id) + "|";
        String ledger = prefs.getString("economy_ledger", "");
        if (ledger.isEmpty()) return false;
        for (String row : ledger.split("\\n")) {
            if (row.startsWith(prefix)) return true;
        }
        return false;
    }

    private void appendLedger(SharedPreferences.Editor edit, String id, String type,
                              long coins, long diamonds, long xp) {
        String old = prefs.getString("economy_ledger", "");
        String line = safeKey(id) + "|" + type + "|" + coins + "|" + diamonds + "|" + xp;
        String[] rows = old.isEmpty() ? new String[0] : old.split("\\n");
        StringBuilder next = new StringBuilder();
        int start = Math.max(0, rows.length - (MAX_LEDGER_ENTRIES - 1));
        for (int i = start; i < rows.length; i++) {
            if (rows[i].isEmpty()) continue;
            if (next.length() > 0) next.append('\n');
            next.append(rows[i]);
        }
        if (next.length() > 0) next.append('\n');
        next.append(line);
        edit.putString("economy_ledger", next.toString());
    }

    private static long safeAdd(long a, long b) {
        if (b > 0L && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        if (b < 0L && a < Long.MIN_VALUE - b) return Long.MIN_VALUE;
        return Math.max(0L, a + b);
    }

    private static String safeKey(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
