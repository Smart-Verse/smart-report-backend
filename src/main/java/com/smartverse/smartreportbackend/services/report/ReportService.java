package com.smartverse.smartreportbackend.services.report;

import com.mongodb.client.MongoCollection;
import com.potatotech.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.common.FileCommon;
import com.smartverse.smartreportbackend.config.mongo.ConnectionMongoDb;
import com.smartverse.smartreportbackend_gen.GetTemplateOutput;
import com.smartverse.smartreportbackend_gen.ReportEntity;
import com.smartverse.smartreportbackend_gen.SaveTemplateInput;
import org.bson.Document;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Service
public class ReportService {

    public void saveDefault(ReportEntity reportEntity){

        var connection = ConnectionMongoDb.getInstance();
        var database = connection.getDatabase();

        MongoCollection<Document> collection = database.getCollection(TenantContext.getCurrentTenant().toLowerCase());

        var map = new HashMap<String, Object>();

        map.put("html", FileCommon.loadFile("base.html","template"));
        map.put("css",FileCommon.loadFile("base.css","template"));
        map.put("js",FileCommon.loadFile("base.js","template"));
        map.put("data",FileCommon.loadFile("base.json","template"));
        map.put("report",reportEntity.getId().toString());

        collection.insertOne(new Document(map));

    }

    public boolean saveTemplate(SaveTemplateInput input){
        var connection = ConnectionMongoDb.getInstance();
        var database = connection.getDatabase();

        MongoCollection<Document> collection = database.getCollection(TenantContext.getCurrentTenant().toLowerCase());

        Document filter = new Document("report", input.idreport.toString());

        var map = new HashMap<String, Object>();

        map.put("html",input.html);
        map.put("css",input.css);
        map.put("js",input.js);
        map.put("data",input.data);
        map.put("report",input.idreport.toString());


        collection.updateOne(filter,new Document("$set",map));

        return true;
    }

    public GetTemplateOutput getTemplate(UUID reportId){

        var connection = ConnectionMongoDb.getInstance();
        var database = connection.getDatabase();
        MongoCollection<Document> collection = database.getCollection(TenantContext.getCurrentTenant().toLowerCase());

        Document filter = new Document("report", reportId.toString());

        Map<String, Object> foundDocument = collection.find(filter).first();

        var output = new GetTemplateOutput();
        output.html = foundDocument.get("html").toString();
        output.js = foundDocument.get("js").toString();
        output.css = foundDocument.get("css").toString();
        output.data = foundDocument.get("data").toString();
        output.idreport = UUID.fromString(foundDocument.get("report").toString());

        return output;
    }
}
