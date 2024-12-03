package com.smartverse.smartreportbackend.config.metadata;

import com.smartverse.StarterApplication;
import com.potatotech.authorization.stereotype.Anonymous;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

@RestController
@CrossOrigin(origins="*")
@RequestMapping
public class MetadataHandler {

    @PostMapping("/metadata")
    @Anonymous
    public ResponseEntity<?> getStatus(@RequestBody Map<String, Object> obj){
        var enumMetadata = EnumMetadata.valueOf(obj.get("metadata").toString());

        var ret = switch (enumMetadata){
            case FIELDS -> retFields("properties.json");
            case RESOURCES -> retFields("resources.json");
            default -> "";
        };

        return ResponseEntity.of(Optional.of(ret));
    }

    private String retFields(String fileName){

        ClassLoader classLoader = StarterApplication.class.getClassLoader();

        try {
            InputStream inputStream = classLoader.getResourceAsStream(fileName);

            if (inputStream != null) {
                Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");

                String fileContent = scanner.hasNext() ? scanner.next() : "";

                scanner.close();
                inputStream.close();

                return fileContent;
            } else {
                System.out.println("Arquivo não encontrado: " + fileName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
