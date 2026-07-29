package org.magic.supportService.api;

import java.time.Instant;

import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a feature or support request in the system.
 * Tracks the request's metadata, current status, type, and timestamps.
 * Persisted to MongoDB with POJO codec support.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({ "id", "title", "description", "contactEmail", "priority",
                     "accountID", "status", "type", "createdOnDate", "lastStatusChangeDate" })
public class SupportRequest {

    @BsonProperty("_id")
    @JsonProperty("id")
    private String id;

    @BsonProperty("title")
    @JsonProperty("title")
    private String title;

    @BsonProperty("description")
    @JsonProperty("description")
    private String description;

    @BsonProperty("contactEmail")
    @JsonProperty("contactEmail")
    private String contactEmail;

    @BsonProperty("priority")
    @JsonProperty("priority")
    private SupportPriority priority;

    @BsonProperty("accountID")
    @JsonProperty("accountID")
    private String accountID;

    @BsonProperty("status")
    @JsonProperty("status")
    private SupportStatus status;

    @BsonProperty("type")
    @JsonProperty("type")
    private SupportType type;

    @BsonProperty("createdOnDate")
    @JsonProperty("createdOnDate")
    private Instant createdOnDate;

    @BsonProperty("lastStatusChangeDate")
    @JsonProperty("lastStatusChangeDate")
    private Instant lastStatusChangeDate;
}
