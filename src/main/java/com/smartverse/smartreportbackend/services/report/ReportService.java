package com.smartverse.smartreportbackend.services.report;

import com.google.gson.Gson;
import com.mongodb.client.MongoCollection;
import com.smartverse.smartreportbackend_gen.authorization.tenant.TenantContext;
import com.smartverse.smartreportbackend.common.FileCommon;
import com.smartverse.smartreportbackend.config.mongo.ConnectionMongoDb;
import com.smartverse.smartreportbackend.repository.report.ReportCustomRepository;
import com.smartverse.smartreportbackend_gen.dtos.ReportDTO;
import com.smartverse.smartreportbackend_gen.endpoints.GetMetricsOutput;
import com.smartverse.smartreportbackend_gen.endpoints.GetTemplateOutput;
import com.smartverse.smartreportbackend_gen.endpoints.SaveTemplateInput;
import com.smartverse.smartreportbackend_gen.entities.ReportEntity;
import com.smartverse.smartreportbackend_gen.enums.PageFormat;
import com.smartverse.smartreportbackend_gen.enums.PageOrientation;
import com.smartverse.smartreportbackend_gen.repositories.RepositoryRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Service
public class ReportService extends com.smartverse.smartreportbackend_gen.services.ReportService {

    @Autowired
    ReportClient reportClient;

    @Autowired
    ReportCustomRepository reportRepository;

    @Autowired
    RepositoryRepository repositoryRepository;

    @Override
    @Transactional
    public ReportDTO save(ReportDTO obj) {
        if (obj.pageFormat == null) obj.pageFormat = PageFormat.A4;
        if (obj.pageOrientation == null) obj.pageOrientation = PageOrientation.PORTRAIT;
        var saved = super.save(obj);
        saveDefault(dtoConverter.toEntity(saved, null));
        return saved;
    }



    @Override
    @Transactional
    public ReportDTO update(ReportDTO obj, UUID id) {
        var existing = repository.findById(id).orElseThrow();
        if (obj.pageFormat == null) obj.pageFormat = existing.getPageFormat();
        if (obj.pageOrientation == null) obj.pageOrientation = existing.getPageOrientation();
        return super.update(obj, id);
    }


    public void saveDefault(ReportEntity reportEntity){

        var connection = ConnectionMongoDb.getInstance();
        var database = connection.getDatabase();

        MongoCollection<Document> collection = database.getCollection(TenantContext.getCurrentTenant().toLowerCase());

        var map = new HashMap<String, Object>();

        var templateName = reportEntity.getTemplateType() == null
                ? "standard"
                : reportEntity.getTemplateType().name().toLowerCase();

        map.put("html", FileCommon.loadFile(templateName + ".html", "template"));
        map.put("css", FileCommon.loadFile(templateName + ".css", "template"));
        map.put("js", FileCommon.loadFile(templateName + ".js", "template"));
        map.put("data", FileCommon.loadFile(templateName + ".json", "template"));
        map.put("report", reportEntity.getId().toString());

        collection.insertOne(new Document(map));

    }

    public boolean saveTemplate(SaveTemplateInput input){
        var reportEntity = reportRepository.findById(input.idreport).orElseThrow();
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

        if (input.pageFormat != null) reportEntity.setPageFormat(input.pageFormat);
        if (input.pageOrientation != null) reportEntity.setPageOrientation(input.pageOrientation);
        if (input.pageFormat != null || input.pageOrientation != null) {
            reportRepository.save(reportEntity);
        }

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
        var reportEntity = reportRepository.findById(reportId).orElseThrow();
        output.pageFormat = reportEntity.getPageFormat() == null
                ? PageFormat.A4
                : reportEntity.getPageFormat();
        output.pageOrientation = reportEntity.getPageOrientation() == null
                ? PageOrientation.PORTRAIT
                : reportEntity.getPageOrientation();

        return output;
    }

    public byte[] generate(UUID idreport, Map data) {
        var templateProperties = this.getTemplate(idreport);
        var reportEntity = reportRepository.findById(idreport).orElseThrow();

        var template = FileCommon.loadFile("index.html","template");

        var gson = new Gson();

        var script = Objects.requireNonNullElse(templateProperties.js, "");
        var functionMatcher = Pattern.compile(
                "\\bfunction\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(")
                .matcher(script);
        var methods = functionMatcher.results()
                .map(result -> result.group(1))
                .distinct()
                .collect(Collectors.joining(","));

        template = template
                .replace("${css}", templateProperties.css)
                .replace("${js}", script)
                .replace("${methods}", methods)
                .replace("${html}", templateProperties.html)
                .replace("${json}", (data == null ? templateProperties.data : gson.toJson(data)));


        var report = new LinkedHashMap<String, Object>();
        report.put("report",template);
        report.put("pageFormat", (reportEntity.getPageFormat() == null
                ? PageFormat.A4
                : reportEntity.getPageFormat()).name());
        report.put("pageOrientation", (reportEntity.getPageOrientation() == null
                ? PageOrientation.PORTRAIT
                : reportEntity.getPageOrientation()).name());

        return reportClient.getReport(report);
    }

    public GetMetricsOutput metrics(){

        var output = new GetMetricsOutput();

        output.generateds = reportRepository.sumAmount();
        output.report = reportRepository.count();
        output.repository = repositoryRepository.count();

        return output;
    }

    public void addAmount(UUID reportID){
        var report = reportRepository.findById(reportID).orElse(null);
        if(report != null){
            report.setAmount((report.getAmount() == null ? 0 : report.getAmount()) + 1);
            reportRepository.save(report);
        }
    }


}
