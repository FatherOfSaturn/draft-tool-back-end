package org.magic.supportService.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal response for an accepted support-request submission. Deliberately
 * carries only the generated ticket ID: the anonymous caller gets no echo of
 * the submitted content and no PII.
 *
 * @param id the generated ticket ID
 */
public record CreateSupportAck(@JsonProperty("id") String id) {
}
