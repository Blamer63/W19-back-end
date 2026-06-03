package com.example.demo.controller;

import com.example.demo.dto.AdminFeatureStatusResponse;
import com.example.demo.dto.AdminMetricResponse;
import com.example.demo.dto.AdminSummaryResponse;
import com.example.demo.dto.AdminUserPageResponse;
import com.example.demo.dto.AdminUserResponse;
import com.example.demo.service.AdminDashboardService;
import com.example.demo.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminDashboardService adminDashboardService;

    @MockitoBean
    private AdminUserService adminUserService;

    @Test
    void getSummary_Unauthenticated_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/summary"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminDashboardService);
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void getSummary_NonAdmin_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/summary"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminDashboardService);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void getSummary_Admin_ReturnsSummary() throws Exception {
        when(adminDashboardService.getSummary()).thenReturn(AdminSummaryResponse.builder()
                .generatedAt(LocalDateTime.of(2026, 5, 22, 17, 0))
                .metrics(List.of(AdminMetricResponse.builder()
                        .key("users")
                        .label("Users")
                        .value(12)
                        .description("Registered profiles")
                        .build()))
                .futureFeatures(List.of(AdminFeatureStatusResponse.builder()
                        .key("user-management")
                        .label("User Management")
                        .status("PLANNED")
                        .description("Search, review, suspend, or update users.")
                        .enabled(false)
                        .build()))
                .build());

        mockMvc.perform(get("/api/admin/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generated_at").value("2026-05-22T17:00:00"))
                .andExpect(jsonPath("$.metrics.length()").value(1))
                .andExpect(jsonPath("$.metrics[0].key").value("users"))
                .andExpect(jsonPath("$.metrics[0].label").value("Users"))
                .andExpect(jsonPath("$.metrics[0].value").value(12))
                .andExpect(jsonPath("$.metrics[0].description").value("Registered profiles"))
                .andExpect(jsonPath("$.future_features.length()").value(1))
                .andExpect(jsonPath("$.future_features[0].key").value("user-management"))
                .andExpect(jsonPath("$.future_features[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.future_features[0].enabled").value(false));
    }

    @Test
    void listUsers_Unauthenticated_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService);
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void listUsers_NonAdmin_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void listUsers_Admin_ReturnsPage() throws Exception {
        when(adminUserService.listUsers(any(), any())).thenReturn(AdminUserPageResponse.builder()
                .content(List.of(AdminUserResponse.builder()
                        .username("demo_user")
                        .email("demo@locale.app")
                        .roles(List.of("USER", "ADMIN"))
                        .build()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].email").value("demo@locale.app"))
                .andExpect(jsonPath("$.content[0].roles[1]").value("ADMIN"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void reseedUser_Admin_ReturnsUser() throws Exception {
        UUID id = UUID.fromString("10000000-0000-0000-0000-000000000005");
        when(adminUserService.reseedUser(eq(id))).thenReturn(AdminUserResponse.builder()
                .id(id)
                .email("demo@locale.app")
                .build());

        mockMvc.perform(post("/api/admin/users/" + id + "/reseed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("demo@locale.app"));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void reseedUser_NonAdmin_ReturnsForbidden() throws Exception {
        UUID id = UUID.fromString("10000000-0000-0000-0000-000000000005");
        mockMvc.perform(post("/api/admin/users/" + id + "/reseed"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService);
    }
}
