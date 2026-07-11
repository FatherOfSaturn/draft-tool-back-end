package org.magic.pyramidDraft.app.GameCoordination;

import org.bson.codecs.configuration.CodecProvider;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Provides access to the MongoDB database with POJO codec support.
 * Configures a {@link CodecRegistry} that enables automatic POJO serialization/deserialization
 * for MongoDB operations across the application.
 */
@ApplicationScoped
public class MongoService {
    private static final Logger LOGGER = LogManager.getLogger(MongoService.class);

    @Inject
    MongoClient mongoClient;
    
    @Inject
    @ConfigProperty(name = "quarkus.mongodb.database")
    String databaseName;

    private CodecRegistry codecRegistry;

    @PostConstruct
    void init() {
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        this.codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));
        LOGGER.info("MongoService initialized");
    }

    /**
     * Returns the configured MongoDB database instance with POJO codec support.
     *
     * @return the {@link MongoDatabase} ready for typed collection access
     */
    public MongoDatabase getDatabase() {
        return mongoClient.getDatabase(databaseName).withCodecRegistry(codecRegistry);
    }
}
