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
import org.magic.common.api.scryfall.BulkDataEntry;
import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.pyramidDraft.app.GameCoordination.MongoService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.eclipse.microprofile.context.ManagedExecutor;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
    private final MongoService mongoService;
    private final ObjectMapper objectMapper;
    private final ManagedExecutor managedExecutor;

    @Inject
    public ScryfallBulkDataService(
            @ConfigProperty(name = "scryfall.cache.enabled", defaultValue = "true") final boolean enabled,
            @ConfigProperty(name = "scryfall.cache.bulk-data-type", defaultValue = "default_cards") final String bulkDataType,
            final ScryfallCardCache cardCache,
            final MongoService mongoService,
            final ObjectMapper objectMapper,
            final ManagedExecutor managedExecutor) {
        this.enabled = enabled;
        this.bulkDataType = bulkDataType;
        this.cardCache = cardCache;
        this.mongoService = mongoService;
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

            String lastImported = getLastImportedUpdatedAt();
            if (target.updatedAt().equals(lastImported)) {
                LOGGER.info("Bulk data unchanged since last import ({}), skipping", lastImported);
                return;
            }

            int count = performImport(target);
            setLastImportedUpdatedAt(target.updatedAt());
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

    public CacheStatus getStatus() {
        return new CacheStatus(
                cardCache.isAvailable(),
                cardCache.count(),
                getLastImportedUpdatedAt(),
                bulkDataType
        );
    }

    public int triggerRefresh() throws IOException {
        if (!enabled) {
            throw new IllegalStateException("Scryfall cache is disabled");
        }
        LOGGER.info("Manual Scryfall bulk data refresh triggered...");
        List<BulkDataEntry> entries = fetchBulkDataEntries();
        BulkDataEntry target = findTargetEntry(entries);
        if (target == null) {
            throw new IllegalStateException("No bulk data entry found for type: " + bulkDataType);
        }
        int count = performImport(target);
        setLastImportedUpdatedAt(target.updatedAt());
        LOGGER.info("Manual Scryfall bulk data refresh complete: {} cards imported", count);
        return count;
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
        cardCache.dropCollection();
        cardCache.createIndexes();

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
                    cardCache.bulkInsert(batch);
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            cardCache.bulkInsert(batch);
        }

        return total;
    }

    private MongoCollection<Document> getMetaCollection() {
        return mongoService.getDatabase().getCollection(META_COLLECTION);
    }

    String getLastImportedUpdatedAt() {
        Document doc = getMetaCollection().find(Filters.eq("_id", META_ID)).first();
        return doc != null ? doc.getString("updatedAt") : null;
    }

    void setLastImportedUpdatedAt(final String updatedAt) {
        getMetaCollection().replaceOne(
                Filters.eq("_id", META_ID),
                new Document("_id", META_ID)
                        .append("updatedAt", updatedAt)
                        .append("importedAt", Instant.now().toString()),
                new ReplaceOptions().upsert(true)
        );
    }

    public record CacheStatus(
            boolean available,
            long cardCount,
            String lastImportedAt,
            String bulkDataType
    ) {}
}
