package com.daveai.searchengine.api;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HomeController {

    // "" and "/" so both /api/v1 and /api/v1/ answer; Spring no longer treats them as the same path
    @GetMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> home(){
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("search", "GET /api/v1/search?q={query}&page={page}&size={size}");
        endpoints.put("autocomplete", "GET /api/v1/autocomplete?q={prefix}&limit={limit}");
        endpoints.put("document", "GET /api/v1/documents/{id}");
        endpoints.put("file", "GET /api/v1/files/{id}?download={true|false}");
        endpoints.put("upload", "POST /api/v1/documents (multipart, field \"file\")");
        endpoints.put("refresh", "POST /api/v1/index/refresh");
        endpoints.put("health", "GET /actuator/health");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "SearchEngine");
        body.put("endpoints", endpoints);
        return ResponseEntity.ok(body);
    }


}
