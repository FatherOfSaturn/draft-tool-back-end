package org.magic.accountService.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating or updating a deck: name, description, and the
 * ordered list of card IDs. Both write endpoints share identical field sets and
 * validation rules, so they share this payload; the JSON shape is unchanged
 * ({@code name}, {@code description}, {@code cardIds}).
 */
public record DeckPayload(
    @JsonProperty("name") @NotBlank @Size(max = 100) String name,
    @JsonProperty("description") @NotNull @Size(max = 2000) String description,
    @JsonProperty("cardIds") @NotNull @Size(max = 500) List<@NotBlank @Size(max = 64) String> cardIds
) {
}
