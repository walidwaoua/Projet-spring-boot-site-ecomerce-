package com.ecommerce.utilisateurs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void shouldCreateReadUpdateAndDeleteUser() throws Exception {
        Map<String, Object> body = Map.of(
            "nom", "Mehdi",
            "email", "mehdi@example.com",
            "role", "CLIENT",
            "actif", true
        );

        String response = mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.email").value("mehdi@example.com"))
            .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/users/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nom").value("Mehdi"));

        mockMvc.perform(put("/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "nom", "Mehdi Ben Ali",
                    "email", "mehdi@example.com",
                    "role", "ADMIN",
                    "actif", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(delete("/users/{id}", id))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/users/{id}", id))
            .andExpect(status().isNotFound());
    }
}
