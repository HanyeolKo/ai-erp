package com.aierp;

import com.aierp.identity.api.ApplicationPrincipal;
import com.aierp.platform.web.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.*;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HttpProblemRegressionTest.ProbeController.class)
@Import({SecurityConfiguration.class, ApiExceptionHandler.class, HttpProblemRegressionTest.ProbeController.class})
class HttpProblemRegressionTest {
    @RestController static class ProbeController {
        @PostMapping(value="/api/v1/problem-probe", consumes="application/json")
        Map<String, String> post(@RequestBody Map<String, String> body) { return body; }
        @GetMapping("/api/v1/problem-probe/failure")
        void failure() { throw new RuntimeException("private database diagnostic must never escape"); }
    }
    @Autowired MockMvc mvc;
    final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        new ApplicationPrincipal(UUID.randomUUID(), "test@example.test", true), null, List.of());
    @Test void unsupportedMethodHasCorrelatedProblemEnvelope() throws Exception {
        verify(get("/api/v1/problem-probe"), 405, "METHOD_NOT_ALLOWED");
    }
    @Test void unsupportedMediaTypeHasCorrelatedProblemEnvelope() throws Exception {
        verify(post("/api/v1/problem-probe").contentType("text/plain").content("payload"), 415, "UNSUPPORTED_MEDIA_TYPE");
    }
    @Test void unexpectedFailureHasSafeCorrelatedProblemEnvelope() throws Exception {
        verify(get("/api/v1/problem-probe/failure"), 500, "INTERNAL_ERROR");
    }
    private void verify(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int status, String code) throws Exception {
        var result = mvc.perform(request.with(authentication(auth)).with(csrf()))
            .andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.fieldErrors").isArray()).andReturn();
        var body = new tools.jackson.databind.json.JsonMapper().readTree(result.getResponse().getContentAsString());
        assertThat(body.path("traceId").asText()).isNotBlank().isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private database", "RuntimeException");
    }
}
