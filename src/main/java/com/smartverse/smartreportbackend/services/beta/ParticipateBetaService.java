package com.smartverse.smartreportbackend.services.beta;

import com.mongodb.client.MongoCollection;
import com.potatotech.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.common.FileCommon;
import com.smartverse.smartreportbackend.config.mongo.ConnectionMongoDb;
import com.smartverse.smartreportbackend_gen.ReportEntity;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;

@Service
public class ParticipateBetaService {

    public boolean participateBeta(String email, String name){

        var connection = ConnectionMongoDb.getInstance();
        var database = connection.getDatabase();

        MongoCollection<Document> collection = database.getCollection("beta");

        var map = new HashMap<String, Object>();

        LocalDateTime ldt = LocalDateTime.now();
        Instant instant = ldt.atZone(ZoneId.of("America/Sao_Paulo")).toInstant();
        Date mongoDate = Date.from(instant);

        map.put("name", email);
        map.put("email", name);
        map.put("createdAt", mongoDate);

        collection.insertOne(new Document(map));
        return true;
    }
}
