package org.magic.common.mongo;

import java.util.function.Supplier;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import io.smallrye.mutiny.Uni;

/**
 * Small helpers for the reactive repositories that keep the domain contracts
 * ("must exist", "must have been updated") out of the query code.
 */
public final class ReactiveMongo {

    private ReactiveMongo() {
    }

    /**
     * Fails with the supplied exception when the query yields no item.
     * Used for lookups by business keys where "not found" is an error.
     */
    public static <T> Uni<T> required(final Uni<T> query, final Supplier<? extends RuntimeException> failure) {
        return query.onItem().ifNull().failWith(failure);
    }

    /**
     * Unwraps an optional lookup, failing with the supplied exception when empty.
     */
    public static <T> Uni<T> requiredOptional(final Uni<java.util.Optional<T>> query,
                                              final Supplier<? extends RuntimeException> failure) {
        return query.map(optional -> optional.orElseThrow(failure));
    }

    /**
     * Maps a successful update to the given value, or fails when no document was
     * modified (mirrors the historical "update must have matched" contract).
     */
    public static <T> Uni<T> modified(final Uni<UpdateResult> update, final Supplier<T> value,
                                      final Supplier<? extends RuntimeException> failure) {
        return update.map(result -> {
            if (result.getModifiedCount() > 0) {
                return value.get();
            }
            throw failure.get();
        });
    }

    /** Fails when the update modified no document. */
    public static Uni<Void> modified(final Uni<UpdateResult> update,
                                     final Supplier<? extends RuntimeException> failure) {
        return modified(update, () -> null, failure).replaceWithVoid();
    }

    /** Maps a delete result to whether a document was removed. */
    public static Uni<Boolean> deleted(final Uni<DeleteResult> delete) {
        return delete.map(result -> result.getDeletedCount() > 0);
    }
}
