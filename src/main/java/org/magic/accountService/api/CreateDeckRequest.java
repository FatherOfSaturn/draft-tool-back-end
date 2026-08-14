package org.magic.accountService.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new deck. Contains the deck name,
 * description, and initial list of card IDs.
 */
public record CreateDeckRequest(
    @JsonProperty("name") @NotBlank @Size(max = 100) String name,
    @JsonProperty("description") @NotNull @Size(max = 2000) String description,
    @JsonProperty("cardIds") @NotNull @Size(max = 500) List<@NotBlank @Size(max = 64) String> cardIds
) {
}
