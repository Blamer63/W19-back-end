package com.example.demo.controller;

import com.example.demo.dto.AdminSeedRunResponse;
import com.example.demo.dto.AdminSummaryResponse;
import com.example.demo.dto.AdminUserPageResponse;
import com.example.demo.dto.AdminUserResponse;
import com.example.demo.service.AdminDashboardService;
import com.example.demo.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminDashboardService adminDashboardService;
    private final AdminUserService adminUserService;

    @GetMapping("/summary")
    public ResponseEntity<AdminSummaryResponse> getSummary() {
        return ResponseEntity.ok(adminDashboardService.getSummary());
    }

    @GetMapping("/users")
    public ResponseEntity<AdminUserPageResponse> listUsers(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(adminUserService.listUsers(query, pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(adminUserService.getUser(id));
    }

    @PostMapping("/users/{id}/reseed")
    public ResponseEntity<AdminUserResponse> reseedUser(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(adminUserService.reseedUser(id));
    }

    @GetMapping("/seed-runs")
    public ResponseEntity<List<AdminSeedRunResponse>> listSeedRuns() {
        return ResponseEntity.ok(adminUserService.listSeedRuns());
    }
}
