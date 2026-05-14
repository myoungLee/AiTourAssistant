/*
 * @author myoung
 */
package com.aitour.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TripControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateDraftAndQueryTrips() throws Exception {
        String token = registerAndGetToken();

        String response = mockMvc.perform(post("/api/trips/draft")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destination":"成都","startDate":"2099-06-01","days":3,"budget":3000,"peopleCount":2,"preferences":["美食"],"userInput":"想吃火锅"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String planId = response.replaceAll(".*\"planId\":([0-9]+).*", "$1");

        mockMvc.perform(get("/api/trips").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("成都3日智能行程"));

        mockMvc.perform(get("/api/trips/" + planId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Long.parseLong(planId)));
    }

    private String registerAndGetToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"trip-user","password":"password123","nickname":"Trip User"}
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
