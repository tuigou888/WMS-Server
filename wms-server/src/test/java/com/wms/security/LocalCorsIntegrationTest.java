package com.wms.security;

import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.http.HttpHeaders; import org.springframework.http.HttpMethod; import org.springframework.test.context.ActiveProfiles; import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options; import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header; import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") class LocalCorsIntegrationTest {
 @Autowired MockMvc mvc;
 @Test void viteIncrementedLocalPortCanPreflightLogin() throws Exception {mvc.perform(options("/auth/login").header(HttpHeaders.ORIGIN,"http://localhost:5174").header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,HttpMethod.POST.name())).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,"http://localhost:5174"));}
}
