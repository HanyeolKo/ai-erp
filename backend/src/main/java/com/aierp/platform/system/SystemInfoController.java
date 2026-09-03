package com.aierp.platform.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemInfoController {

    @GetMapping("/api/v1/system/info")
    SystemInfoResponse systemInfo() {
        return new SystemInfoResponse("AI ERP", "foundation");
    }

    record SystemInfoResponse(String name, String phase) {
    }
}
