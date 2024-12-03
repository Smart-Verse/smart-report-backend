package com.smartverse.smartreportbackend.config.registryservice;


import com.smartverse.smartreportbackend.config.context.EnumConfigContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.Hashtable;

@Service
public class RegistryServiceImpl{

    private final RegistryService registryService;

    @Autowired
    public RegistryServiceImpl(RegistryService feignClient) {
        this.registryService = feignClient;
    }

    @Bean
    public void registerService() {
        Hashtable<String, Object> hashtable = new Hashtable();
        hashtable.put("service", System.getenv(EnumConfigContext.SERVICE_NAME.name()));
        hashtable.put("port", System.getenv(EnumConfigContext.SERVER_PORT.name()));
        //this.registryService.registryService(hashtable);
    }
}
