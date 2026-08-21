package org.magic.accountService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload for the login endpoint. Contains the account and a signed
 * app JWT that the client must send as an {@code Authorization: Bearer} header
 * on subsequent requests.
 */
public record LoginResponse(@JsonProperty("account") Account account,
                            @JsonProperty("jwt") String jwt,
                            @JsonProperty("expiresIn") long expiresIn) {
}
