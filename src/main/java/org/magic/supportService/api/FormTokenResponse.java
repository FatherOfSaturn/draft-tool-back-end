package org.magic.supportService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for {@code GET /support/form-token}: an HMAC-signed token the
 * client must include as {@code formToken} on submit.
 *
 * @param token the signed token
 */
public record FormTokenResponse(@JsonProperty("token") String token) {
}
