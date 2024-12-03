package com.smartverse.smartreportbackend.config.healthcheck;


import com.potatotech.authorization.stereotype.Anonymous;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@CrossOrigin(origins="*")
@RequestMapping
public class StatusApi {

    @Value("${server.servlet.context-path}")
    String serviceName;

    @GetMapping("/status")
    @Anonymous
    public ResponseEntity<?> getStatus(){
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service",serviceName.replace("/","").toUpperCase());
        response.put("status","up");
        response.put("httpStatus", HttpStatus.OK.value());
        return ResponseEntity.of(Optional.of(response));
    }
}
