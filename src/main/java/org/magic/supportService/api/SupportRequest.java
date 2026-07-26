package org.magic.supportService.api;

import java.time.Instant;
import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Represents a feature or support request in the system.
 * Tracks the request's metadata, current status, type, and timestamps.
 * Persisted to MongoDB with POJO codec support.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@JsonPropertyOrder({ "id", "title", "description", "contactEmail", "priority",
                     "accountID", "status", "type", "createdOnDate", "lastStatusChangeDate" })
public class SupportRequest {
    private static final Logger LOGGER = LogManager.getLogger(SupportRequest.class);

    @Setter(lombok.AccessLevel.NONE)
    private String id;

    private String title;
    private String description;
    private String contactEmail;
    private SupportPriority priority;

    @Setter(lombok.AccessLevel.NONE)
    private String accountID;

    private SupportStatus status;
    private SupportType type;

    @JsonProperty("createdOnDate")
    @BsonProperty("createdOnDate")
    @Setter(lombok.AccessLevel.NONE)
    private Instant createdOnDate;

    @JsonProperty("lastStatusChangeDate")
    @BsonProperty("lastStatusChangeDate")
    private Instant lastStatusChangeDate;

    @JsonCreator
    @BsonCreator
    public SupportRequest(@JsonProperty("id")                  @BsonProperty("_id")              final String id,
                          @JsonProperty("title")               @BsonProperty("title")             final String title,
                          @JsonProperty("description")         @BsonProperty("description")       final String description,
                          @JsonProperty("contactEmail")        @BsonProperty("contactEmail")      final String contactEmail,
                          @JsonProperty("priority")            @BsonProperty("priority")          final SupportPriority priority,
                          @JsonProperty("accountID")           @BsonProperty("accountID")         final String accountID,
                          @JsonProperty("status")              @BsonProperty("status")            final SupportStatus status,
                          @JsonProperty("type")                @BsonProperty("type")              final SupportType type,
                          @JsonProperty("createdOnDate")       @BsonProperty("createdOnDate")     final Instant createdOnDate,
                          @JsonProperty("lastStatusChangeDate") @BsonProperty("lastStatusChangeDate") final Instant lastStatusChangeDate) {
        this.id = id;
        this.title = Objects.requireNonNull(title, "title is required for SupportRequest");
        this.description = Objects.requireNonNull(description, "description is required for SupportRequest");
        this.contactEmail = Objects.requireNonNull(contactEmail, "contactEmail is required for SupportRequest");
        this.priority = Objects.requireNonNull(priority, "priority is required for SupportRequest");
        this.accountID = accountID;
        this.status = Objects.requireNonNullElse(status, SupportStatus.NEW);
        this.type = Objects.requireNonNull(type, "type is required for SupportRequest");
        this.createdOnDate = Objects.requireNonNullElse(createdOnDate, Instant.now());
        this.lastStatusChangeDate = Objects.requireNonNullElse(lastStatusChangeDate, this.createdOnDate);
    }

    public void setId(final String id) {
        this.id = id;
    }

    public void setAccountID(final String accountID) {
        this.accountID = accountID;
    }

    public void setCreatedOnDate(final Instant createdOnDate) {
        this.createdOnDate = createdOnDate;
    }
}
