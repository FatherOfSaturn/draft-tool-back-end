package org.magic.common.external;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.magic.common.api.scryfall.BulkDataEntry;
import org.magic.common.api.scryfall.ScryfallCard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Downloads and imports Scryfall's bulk card data into the local
 * {@link ScryfallCardCache}. The download/parse loop is inherently blocking
 * (gzip HTTP stream + line-by-line JSON), so it always runs on the worker
 * executor; the cache and metadata writes use the reactive driver and are
 * awaited inside those worker threads.
 */
@ApplicationScoped
public class ScryfallBulkDataService {
    private static final Logger LOGGER = LogManager.getLogger(ScryfallBulkDataService.class);
    private static final String BULK_DATA_URL = "https://api.scryfall.com/bulk-data";
    private static final String META_COLLECTION = "ScryfallCacheMeta";
    private static final String META_ID = "bulk_data_import";
    private static final int BATCH_SIZE = 500;

    private final boolean enabled;
    private final String bulkDataType;
    private final ScryfallCardCache cardCache;
    private final ReactiveMongoClient mongoClient;
    private final String databaseName;
    private final ObjectMapper objectMapper;
    private final ManagedExecutor managedExecutor;

    @Inject
    public ScryfallBulkDataService(
            @ConfigProperty(name = "scryfall.cache.enabled", defaultValue = "true") final boolean enabled,
            @ConfigProperty(name = "scryfall.cache.bulk-data-type", defaultValue = "default_cards") final String bulkDataType,
            final ScryfallCardCache cardCache,
            final ReactiveMongoClient mongoClient,
            @ConfigProperty(name = "quarkus.mongodb.database") final String databaseName,
            final ObjectMapper objectMapper,
            final ManagedExecutor managedExecutor) {
        this.enabled = enabled;
        this.bulkDataType = bulkDataType;
        this.cardCache = cardCache;
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
        this.objectMapper = objectMapper;
        this.managedExecutor = managedExecutor;
    }

    @PostConstruct
    void init() {
        LOGGER.info("ScryfallBulkDataService initialized (enabled={}, type={})", enabled, bulkDataType);
    }

    @Scheduled(every = "PT168H", delayed = "PT120S")
    void scheduledRefresh() {
        if (!enabled) {
            LOGGER.debug("Scryfall cache disabled, skipping scheduled refresh");
            return;
        }
        managedExecutor.submit(this::doRefresh);
    }

    private void doRefresh() {
        LOGGER.info("Starting scheduled Scryfall bulk data refresh...");
        try {
            List<BulkDataEntry> entries = fetchBulkDataEntries();
            BulkDataEntry target = findTargetEntry(entries);
            if (target == null) {
                LOGGER.warn("No bulk data entry found for type: {}", bulkDataType);
                return;
            }

            String lastImported = getLastImportedUpdatedAt().await().indefinitely();
            if (target.updatedAt().equals(lastImported)) {
                LOGGER.info("Bulk data unchanged since last import ({}), skipping", lastImported);
                return;
            }

            int count = performImport(target);
            setLastImportedUpdatedAt(target.updatedAt()).await().indefinitely();
            LOGGER.info("Scheduled Scryfall bulk data refresh complete: {} cards imported", count);
        } catch (Exception e) {
            LOGGER.error("Scheduled Scryfall bulk data refresh failed", e);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBulkDataType() {
        return bulkDataType;
    }

    /**
     * Computes the current cache status. Runs on the reactive stack: the card
     * count, availability and last-import timestamp are all reactive reads.
     */
    public Uni<CacheStatus> getStatus() {
        return cardCache.isAvailable()
                .chain(available -> cardCache.count()
                        .chain(count -> getLastImportedUpdatedAt()
                                .map(lastImported -> new CacheStatus(
                                        available, count, lastImported, bulkDataType))));
    }

    /**
     * Triggers a full blocking refresh (download + import) on the worker executor.
     *
     * @return a {@link Uni} emitting the number of imported cards
     */
    public Uni<Integer> triggerRefresh() {
        if (!enabled) {
            return Uni.createFrom().failure(new IllegalStateException("Scryfall cache is disabled"));
        }
        return Uni.createFrom().item(this::blockingRefresh)
                .runSubscriptionOn(managedExecutor);
    }

    private int blockingRefresh() {
        try {
            LOGGER.info("Manual Scryfall bulk data refresh triggered...");
            List<BulkDataEntry> entries = fetchBulkDataEntries();
            BulkDataEntry target = findTargetEntry(entries);
            if (target == null) {
                throw new IllegalStateException("No bulk data entry found for type: " + bulkDataType);
            }
            int count = performImport(target);
            setLastImportedUpdatedAt(target.updatedAt()).await().indefinitely();
            LOGGER.info("Manual Scryfall bulk data refresh complete: {} cards imported", count);
            return count;
        } catch (IOException e) {
            throw new IllegalStateException("Cache refresh failed: " + e.getMessage(), e);
        }
    }

    List<BulkDataEntry> fetchBulkDataEntries() throws IOException {
        URL url = new URL(BULK_DATA_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        try {
            int status = conn.getResponseCode();
            if (status != 200) {
                throw new IOException("Bulk data endpoint returned HTTP " + status);
            }
            JsonNode root = objectMapper.readTree(conn.getInputStream());
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                throw new IOException("Unexpected bulk-data response format: missing 'data' array");
            }
            return objectMapper.readValue(
                    dataNode.traverse(),
                    new TypeReference<List<BulkDataEntry>>() {});
        } finally {
            conn.disconnect();
        }
    }

    BulkDataEntry findTargetEntry(final List<BulkDataEntry> entries) {
        if (entries == null) return null;
        return entries.stream()
                .filter(e -> bulkDataType.equals(e.type()))
                .findFirst()
                .orElse(null);
    }

    int performImport(final BulkDataEntry entry) throws IOException {
        LOGGER.info("Downloading {} bulk data ({} bytes compressed)...",
                entry.type(), entry.compressedSize());

        // Start fresh: drop old collection and recreate indexes
        cardCache.dropCollection().await().indefinitely();
        cardCache.createIndexes().await().indefinitely();

        URL url = new URL(entry.jsonlDownloadUri());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("Bulk data download returned HTTP " + status + " for " + entry.jsonlDownloadUri());
        }

        int total = 0;
        List<ScryfallCard> batch = new ArrayList<>();

        try (InputStream rawIn = conn.getInputStream();
             InputStream gzipIn = new GZIPInputStream(rawIn);
             Scanner scanner = new Scanner(gzipIn, StandardCharsets.UTF_8)) {

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                ScryfallCard card = objectMapper.readValue(line, ScryfallCard.class);
                batch.add(card);
                total++;

                if (batch.size() >= BATCH_SIZE) {
                    cardCache.bulkInsert(batch).await().indefinitely();
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            cardCache.bulkInsert(batch).await().indefinitely();
        }

        return total;
    }

    private ReactiveMongoCollection<Document> getMetaCollection() {
        return mongoClient.getDatabase(databaseName).getCollection(META_COLLECTION);
    }

    Uni<String> getLastImportedUpdatedAt() {
        return getMetaCollection().find(Filters.eq("_id", META_ID)).collect().first()
                .map(doc -> doc != null ? doc.getString("updatedAt") : null);
    }

    Uni<Void> setLastImportedUpdatedAt(final String updatedAt) {
        return getMetaCollection().replaceOne(
                        Filters.eq("_id", META_ID),
                        new Document("_id", META_ID)
                                .append("updatedAt", updatedAt)
                                .append("importedAt", Instant.now().toString()),
                        new ReplaceOptions().upsert(true))
                .replaceWithVoid();
    }

    public record CacheStatus(
            boolean available,
            long cardCount,
            String lastImportedAt,
            String bulkDataType
    ) {}
}
