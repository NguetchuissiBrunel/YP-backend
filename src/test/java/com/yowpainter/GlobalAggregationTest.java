package com.yowpainter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.AuthResponse;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.LoginRequest;
import com.yowpainter.modules.auth.infrastructure.adapter.in.web.dto.RegisterRequest;
import com.yowpainter.modules.auth.domain.model.UserRole;
import com.yowpainter.modules.artwork.infrastructure.adapter.in.web.dto.ArtworkCreateRequest;
import com.yowpainter.modules.artwork.domain.model.ArtworkTechnique;
import com.yowpainter.modules.artwork.domain.model.ArtworkStyle;
import com.yowpainter.modules.event.infrastructure.adapter.in.web.dto.EventCreateRequest;
import com.yowpainter.modules.shop.infrastructure.adapter.in.web.dto.ProductCreateRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureJson
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GlobalAggregationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String tokenA;
    private static String tokenB;
    private static String slugA;
    private static String slugB;

    @Test
    @Order(1)
    void setupArtists() throws Exception {
        // Register Artist A
        RegisterRequest regA = RegisterRequest.builder()
                .firstName("A").lastName("Artist").email("a@test.com")
                .password("Pass123").role(UserRole.ROLE_ARTIST).artistName("ShopA").slug("shopa").build();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(regA))).andExpect(status().isCreated());
        slugA = "shopa";

        // Register Artist B
        RegisterRequest regB = RegisterRequest.builder()
                .firstName("B").lastName("Artist").email("b@test.com")
                .password("Pass123").role(UserRole.ROLE_ARTIST).artistName("ShopB").slug("shopb").build();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(regB))).andExpect(status().isCreated());
        slugB = "shopb";

        // Login A
        tokenA = login("a@test.com");
        // Login B
        tokenB = login("b@test.com");
    }

    private String login(String email) throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("Pass123");
        MvcResult res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(login))).andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();
    }

    @Test
    @Order(2)
    void shouldAggregateEvents() throws Exception {
        // A creates event
        EventCreateRequest evA = EventCreateRequest.builder()
                .name("Event A")
                .startDateTime(java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS))
                .endDateTime(java.time.Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS))
                .location("Loc A")
                .type(com.yowpainter.modules.event.domain.model.EventType.EXHIBITION)
                .maxCapacity(10)
                .ticketPrice(BigDecimal.ZERO)
                .build();
        mockMvc.perform(post("/api/events").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(evA))).andExpect(status().isCreated());

        // B creates event
        EventCreateRequest evB = EventCreateRequest.builder()
                .name("Event B")
                .startDateTime(java.time.Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS))
                .endDateTime(java.time.Instant.now().plus(4, java.time.temporal.ChronoUnit.DAYS))
                .location("Loc B")
                .type(com.yowpainter.modules.event.domain.model.EventType.WORKSHOP)
                .maxCapacity(10)
                .ticketPrice(BigDecimal.ZERO)
                .build();
        mockMvc.perform(post("/api/events").header("Authorization", "Bearer " + tokenB).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(evB))).andExpect(status().isCreated());

        // Check global aggregation
        mockMvc.perform(get("/api/public/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @Order(3)
    void shouldAggregateArtworks() throws Exception {
        // A creates artwork
        ArtworkCreateRequest artA = ArtworkCreateRequest.builder().title("Art A").technique(ArtworkTechnique.OIL).style(ArtworkStyle.ABSTRACT).imageUrls(List.of("urlA")).build();
        MvcResult resA = mockMvc.perform(post("/api/artworks").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(artA))).andExpect(status().isCreated()).andReturn();
        String idA = JsonPath.read(resA.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(patch("/api/artworks/" + idA + "/status").header("Authorization", "Bearer " + tokenA).param("status", "PUBLISHED")).andExpect(status().isOk());

        // B creates artwork
        ArtworkCreateRequest artB = ArtworkCreateRequest.builder().title("Art B").technique(ArtworkTechnique.OIL).style(ArtworkStyle.ABSTRACT).imageUrls(List.of("urlB")).build();
        MvcResult resB = mockMvc.perform(post("/api/artworks").header("Authorization", "Bearer " + tokenB).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(artB))).andExpect(status().isCreated()).andReturn();
        String idB = JsonPath.read(resB.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(patch("/api/artworks/" + idB + "/status").header("Authorization", "Bearer " + tokenB).param("status", "PUBLISHED")).andExpect(status().isOk());

        // Check global aggregation
        mockMvc.perform(get("/api/public/artworks/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @Order(4)
    void shouldAggregateProducts() throws Exception {
        // A creates product
        ProductCreateRequest pA = ProductCreateRequest.builder().name("Product A").price(BigDecimal.TEN).stockQuantity(5).build();
        mockMvc.perform(post("/api/shop/products").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(pA))).andExpect(status().isCreated());

        // B creates product
        ProductCreateRequest pB = ProductCreateRequest.builder().name("Product B").price(BigDecimal.TEN).stockQuantity(5).build();
        mockMvc.perform(post("/api/shop/products").header("Authorization", "Bearer " + tokenB).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(pB))).andExpect(status().isCreated());

        // Check global aggregation (NEW ENDPOINT)
        mockMvc.perform(get("/api/shop/v1/public/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @Order(5)
    void shouldGetMyArtworks() throws Exception {
        // Check my artworks for Artist A
        mockMvc.perform(get("/api/artworks/me").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Art A"));
    }

    @Test
    @Order(6)
    void shouldUpdateArtworkWithImages() throws Exception {
        // Create an artwork for A with 1 image
        ArtworkCreateRequest art = ArtworkCreateRequest.builder()
                .title("Artwork Original")
                .technique(ArtworkTechnique.OIL)
                .style(ArtworkStyle.ABSTRACT)
                .imageUrls(List.of("/api/files/img_original.jpg"))
                .build();
        MvcResult res = mockMvc.perform(post("/api/artworks")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(art)))
                .andExpect(status().isCreated())
                .andReturn();
        String artworkId = JsonPath.read(res.getResponse().getContentAsString(), "$.id");

        // Update the artwork with 2 new images
        ArtworkCreateRequest updateRequest = ArtworkCreateRequest.builder()
                .title("Artwork Mis a jour")
                .technique(ArtworkTechnique.OIL)
                .style(ArtworkStyle.ABSTRACT)
                .imageUrls(List.of("/api/files/img1.jpg", "/api/files/img2.jpg"))
                .build();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/artworks/" + artworkId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Artwork Mis a jour"))
                .andExpect(jsonPath("$.imageUrls", hasSize(2)))
                .andExpect(jsonPath("$.imageUrls[0]").value("/api/files/img1.jpg"))
                .andExpect(jsonPath("$.imageUrls[1]").value("/api/files/img2.jpg"));
    }

    @Test
    @Order(7)
    void shouldCreateAndUpdateArtworkWithVideos() throws Exception {
        // Create an artwork for A with videos and images
        ArtworkCreateRequest art = ArtworkCreateRequest.builder()
                .title("Artwork avec Videos")
                .technique(ArtworkTechnique.OIL)
                .style(ArtworkStyle.ABSTRACT)
                .imageUrls(List.of("/api/files/img_v.jpg"))
                .videoUrls(List.of("/api/files/vid_v.mp4"))
                .build();
        MvcResult res = mockMvc.perform(post("/api/artworks")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(art)))
                .andExpect(status().isCreated())
                .andReturn();
        String artworkId = JsonPath.read(res.getResponse().getContentAsString(), "$.id");

        // Verify it returns the videos
        mockMvc.perform(get("/api/v1/public/" + slugA + "/artworks/" + artworkId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoUrls", hasSize(1)))
                .andExpect(jsonPath("$.videoUrls[0]").value("/api/files/vid_v.mp4"));

        // Update the artwork, adding more videos
        ArtworkCreateRequest updateRequest = ArtworkCreateRequest.builder()
                .title("Artwork avec Videos modifiée")
                .technique(ArtworkTechnique.OIL)
                .style(ArtworkStyle.ABSTRACT)
                .imageUrls(List.of("/api/files/img_v.jpg"))
                .videoUrls(List.of("/api/files/vid_v.mp4", "/api/files/vid_v2.mov"))
                .build();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/artworks/" + artworkId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Artwork avec Videos modifiée"))
                .andExpect(jsonPath("$.videoUrls", hasSize(2)))
                .andExpect(jsonPath("$.videoUrls[0]").value("/api/files/vid_v.mp4"))
                .andExpect(jsonPath("$.videoUrls[1]").value("/api/files/vid_v2.mov"));
    }
}

