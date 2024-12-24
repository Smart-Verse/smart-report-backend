package com.smartverse.smartreportbackend.handlers.reports;

import com.smartverse.smartreportbackend.services.report.ReportService;
import com.smartverse.smartreportbackend_gen.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@CrossOrigin(origins = "*")
public class GeneratorReport implements GenerateReport, SaveTemplate, GetTemplate {


    @Autowired
    ReportService reportService;

    @Override
    public ResponseEntity<GenerateReportOutput> generateReport(GenerateReportInput input) {
        byte[] report = reportService.generate(input);
        var output = new GenerateReportOutput();
        output.report = report;
        return ResponseEntity.ok(output);
    }

    @Override
    public ResponseEntity<SaveTemplateOutput> saveTemplate(SaveTemplateInput input) {
        var output = new SaveTemplateOutput();
        output.result = reportService.saveTemplate(input);
        return ResponseEntity.ok(output);
    }

    @Override
    public ResponseEntity<GetTemplateOutput> getTemplate(UUID idreport) {
        var output = reportService.getTemplate(idreport);
        return ResponseEntity.ok(output);
    }
}
