package com.smartverse.smartreportbackend.handlers.beta;

import com.potatotech.authorization.stereotype.Anonymous;
import com.smartverse.smartreportbackend.services.beta.ParticipateBetaService;
import com.smartverse.smartreportbackend_gen.ParticipateBeta;
import com.smartverse.smartreportbackend_gen.ParticipateBetaInput;
import com.smartverse.smartreportbackend_gen.ParticipateBetaOutput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ParticipateBetaImpl implements ParticipateBeta {

    @Autowired
    ParticipateBetaService participateBetaService;


    @Override
    @Anonymous
    public ResponseEntity<ParticipateBetaOutput> participateBeta(ParticipateBetaInput input) {
        var output = new ParticipateBetaOutput();
        output.result = participateBetaService.participateBeta(input.email, input.name);
        return ResponseEntity.ok(output);
    }
}
