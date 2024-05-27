package org.magic.draft.app.GameCoordination;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MongoService {

    @Inject
    MongoClient mongoClient;
    
    @Inject
    @ConfigProperty(name = "quarkus.mongodb.connection-string")
    String connectionString;

    @PostConstruct
    void init() {
        System.out.println("Using MongoDB connection string: " + connectionString);
    }

    public MongoDatabase getDatabase() {
        CodecProvider pojoCodecProvider = PojoCodecProvider.builder().automatic(true).build();
        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), fromProviders(pojoCodecProvider));
        return mongoClient.getDatabase("MTGames").withCodecRegistry(pojoCodecRegistry);
    }
}
