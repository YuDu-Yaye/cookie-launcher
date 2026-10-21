package dev.cookielauncher.test;

import dev.cookielauncher.core.config.Account;

/** Offline identity rules: deterministic UUIDs and token hygiene. */
public final class AccountTest {

    public static void testOfflineUuidIsDeterministic() {
        String first = Account.offlineUuid("Steve");
        String second = Account.offlineUuid("Steve");
        Assert.equal(first, second, "same name yields the same UUID");
        Assert.equal(32, first.length(), "offline UUID is 32 hex characters");
        Assert.isFalse(first.contains("-"), "offline UUID has no dashes");
    }

    public static void testOfflineUuidIsCaseSensitive() {
        Assert.notEqual(Account.offlineUuid("Steve"), Account.offlineUuid("steve"),
                "Mojang's rule is case sensitive");
    }

    public static void testOfflineAccountDefaults() {
        Account account = Account.offline("  Steve  ");
        Assert.equal("Steve", account.username, "username is trimmed");
        Assert.equal("offline", account.type, "type is offline");
        Assert.isFalse(account.isOnline(), "offline accounts are not online accounts");
        Assert.isFalse(account.isExpired(), "offline accounts never expire");
        Assert.equal(Account.offlineUuid("Steve"), account.uuid, "uuid derived from the name");
    }

    public static void testOfflineAccountFallsBackForBlankName() {
        Assert.equal("Player", Account.offline("   ").username, "blank name becomes Player");
        Assert.equal("Player", Account.offline(null).username, "null name becomes Player");
    }

    public static void testToStringNeverLeaksTokens() {
        Account account = new Account();
        account.type = "microsoft";
        account.username = "Steve";
        account.uuid = "abc123";
        account.accessToken = "secret-access-token-value";
        account.refreshToken = "secret-refresh-token-value";
        String rendered = account.toString();
        Assert.notContains(rendered, "secret-access-token-value", "access token is not printed");
        Assert.notContains(rendered, "secret-refresh-token-value", "refresh token is not printed");
        Assert.contains(rendered, "Steve", "username is still useful in logs");
    }

    public static void testCopyIsIndependent() {
        Account original = Account.offline("Steve");
        Account copy = original.copy();
        copy.username = "Alex";
        Assert.equal("Steve", original.username, "editing the copy leaves the original alone");
        Assert.equal(original.uuid, copy.uuid, "copy keeps the original uuid");
    }

    public static void testOnlineDetection() {
        Account microsoft = new Account();
        microsoft.type = "microsoft";
        Assert.isTrue(microsoft.isOnline(), "microsoft accounts are online");

        Account authlib = new Account();
        authlib.type = "authlib";
        Assert.isTrue(authlib.isOnline(), "authlib accounts are online");

        Account offline = Account.offline("Steve");
        Assert.isFalse(offline.isOnline(), "offline accounts are not online");
    }

    public static void testExpiry() {
        Account account = new Account();
        account.expiresAt = System.currentTimeMillis() - 1000L;
        Assert.isTrue(account.isExpired(), "a past timestamp is expired");

        account.expiresAt = System.currentTimeMillis() + 600_000L;
        Assert.isFalse(account.isExpired(), "a future timestamp is not expired");

        account.expiresAt = 0L;
        Assert.isFalse(account.isExpired(), "zero means no expiry information");
    }

    public static void testLabelIsReadable() {
        Assert.contains(Account.offline("Steve").label(), "Steve", "label shows the username");
        Assert.contains(Account.offline("Steve").label(), "Offline", "label names the account type");
    }
}
