package org.magic.accountService.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.magic.common.util.JsonUtility;

class AccountTest {

    @Test
    void shouldDeserializeFromFixture() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("Account.json");
        String json = IOUtils.toString(is, "UTF-8");

        Account account = JsonUtility.getInstance().fromJson(json, Account.class);

        assertNotNull(account);
        assertEquals("507f1f77bcf86cd799439011", account.getAccountID());
        assertEquals("test@example.com", account.getEmail());
        assertEquals("Test User", account.getDisplayName());
        assertEquals("1234567890", account.getGoogleSub());
        assertEquals(0, account.getDeckIDs().size());
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0, 0), account.getCreatedAt());
    }

    @Test
    void shouldRoundTripSerializeDeserialize() throws IOException {
        Account account = new Account();
        account.setAccountID("507f1f77bcf86cd799439011");
        account.setEmail("test@example.com");
        account.setDisplayName("Test User");
        account.setGoogleSub("1234567890");
        account.setDeckIDs(List.of());
        account.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0, 0));

        String json = JsonUtility.getInstance().toJson(account);
        Account deserialized = JsonUtility.getInstance().fromJson(json, Account.class);

        assertEquals(account.getAccountID(), deserialized.getAccountID());
        assertEquals(account.getEmail(), deserialized.getEmail());
        assertEquals(account.getDisplayName(), deserialized.getDisplayName());
        assertEquals(account.getGoogleSub(), deserialized.getGoogleSub());
        assertEquals(account.getDeckIDs(), deserialized.getDeckIDs());
    }
}
