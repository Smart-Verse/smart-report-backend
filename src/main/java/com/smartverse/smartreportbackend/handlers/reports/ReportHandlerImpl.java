package com.smartverse.smartreportbackend.handlers.reports;

import com.smartverse.smartreportbackend.services.report.ReportService;
import com.smartverse.smartreportbackend_gen.ReportDTO;
import com.smartverse.smartreportbackend_gen.ReportEntity;
import com.smartverse.smartreportbackend_gen.ReportHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportHandlerImpl extends ReportHandler {

    @Autowired
    private ReportService reportService;

    @Override
    public ReportDTO save(ReportDTO obj) {
        var entity = dtoConverter.toEntity(obj, null);
        entityManager.persist(entity);

        reportService.saveDefault(entity);

        return dtoConverter.toDTO(entity, null);
    }

}
