package com.smartverse.smartreportbackend.config.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.smartverse.smartreportbackend.config.context.EnumConfigContext;
import lombok.Getter;
import org.springframework.context.annotation.Configuration;

import javax.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

@Singleton
public class ConnectionMongoDb {

    private static ConnectionMongoDb instance;

    @Getter
    private MongoClient mongoClient;
    @Getter
    private MongoDatabase database;


    private ConnectionMongoDb() {
        this.mongoClient = MongoClients.create(getStringConnection());

        this.database = mongoClient.getDatabase("smartreport");
    }

    public static ConnectionMongoDb getInstance() {
        if (instance == null) {
            synchronized (ConnectionMongoDb.class) {
                if (instance == null) {
                    instance = new ConnectionMongoDb();
                }
            }
        }
        return instance;
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    private String getStringConnection(){

        try {
            var encodedPassword = URLEncoder.encode(System.getenv(EnumConfigContext.MONGO_PASSWORD.name()), "UTF-8");
            return String.format("mongodb://%s:%s@localhost:27017",
                    System.getenv(EnumConfigContext.MONGO_USER.name()),encodedPassword);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

    }

}
