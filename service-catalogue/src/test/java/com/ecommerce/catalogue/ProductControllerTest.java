package com.ecommerce.catalogue;

import com.ecommerce.catalogue.model.ProductStatus;
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
class ProductControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void shouldCreateReadUpdateAndDeleteProduct() throws Exception {
        Map<String, Object> body = Map.of(
            "nom", "Clavier",
            "prix", 49.90,
            "description", "Clavier mécanique",
            "categorie", "Informatique",
            "statut", ProductStatus.DISPONIBLE.name()
        );

        String location = mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.nom").value("Clavier"))
            .andReturn().getResponse().getContentAsString();

        var created = objectMapper.readTree(location);
        long id = created.get("id").asLong();

        mockMvc.perform(get("/products/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.prix").value(49.90));

        mockMvc.perform(put("/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "nom", "Clavier RGB",
                    "prix", 59.90,
                    "description", "Clavier RGB",
                    "categorie", "Informatique",
                    "statut", ProductStatus.DISPONIBLE.name()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nom").value("Clavier RGB"));

        mockMvc.perform(delete("/products/{id}", id))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/products/{id}", id))
            .andExpect(status().isNotFound());
    }
}
