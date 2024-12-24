package com.smartverse.smartreportbackend.services.report;

import com.mongodb.client.MongoCollection;
import com.potatotech.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.config.mongo.ConnectionMongoDb;
import com.smartverse.smartreportbackend_gen.SaveTemplateInput;
import org.bson.Document;
import org.bson.types.Binary;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

@Service
public class ReportService {

    public boolean saveTemplate(SaveTemplateInput input){
        var connection = ConnectionMongoDb.getInstance();
        var database = connection.getDatabase();

        MongoCollection<Document> collection = database.getCollection(TenantContext.getCurrentTenant().toLowerCase());

        var map = new HashMap<String, Object>();
        var idReport = UUID.randomUUID();
        map.put("html",input.html);
        map.put("css",input.css);
        map.put("js",input.js);
        map.put("data",input.data);
        map.put("report",input.idreport.toString());
        map.put("templateID",idReport.toString());

        collection.insertOne(new Document(map));

        connection.close();

        return true;
    }
}
