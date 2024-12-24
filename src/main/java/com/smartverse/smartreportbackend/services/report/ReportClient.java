package com.smartverse.smartreportbackend.services.report;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "report", url = "http://localhost:5071")
public interface ReportClient {

    @PostMapping("report")
    byte[] getReport(@RequestBody Map<String, Object> report);

}


