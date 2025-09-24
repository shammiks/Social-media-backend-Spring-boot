package com.example.DPMHC_backend.controller;

import com.example.DPMHC_backend.dto.CreateReportDTO;
import com.example.DPMHC_backend.dto.ReportDTO;
import com.example.DPMHC_backend.model.ReportStatus;
import com.example.DPMHC_backend.model.User;
import com.example.DPMHC_backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * Create a new report for a post
     */
    @PostMapping
    public ResponseEntity<?> createReport(
            @RequestBody CreateReportDTO createReportDTO,
            @AuthenticationPrincipal User user) {
        try {
            ReportDTO report = reportService.createReport(createReportDTO, user.getId());
            return ResponseEntity.ok(Map.of(
                "message", "Post reported successfully",
                "report", report
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create report"));
        }
    }

    /**
     * Get all reports for admin review (paginated)
     */
    @GetMapping("/admin")
    public ResponseEntity<Page<ReportDTO>> getAllReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<ReportDTO> reports = reportService.getAllReports(pageable);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get report by ID
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<?> getReportById(@PathVariable Long reportId) {
        try {
            ReportDTO report = reportService.getReportById(reportId);
            return ResponseEntity.ok(report);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to get report"));
        }
    }

    /**
     * Update report status (admin only)
     */
    @PutMapping("/{reportId}/status")
    public ResponseEntity<?> updateReportStatus(
            @PathVariable Long reportId,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal User admin) {
        try {
            ReportStatus newStatus = ReportStatus.valueOf((String) request.get("status"));
            String adminNotes = (String) request.get("adminNotes");
            
            ReportDTO updatedReport = reportService.updateReportStatus(reportId, newStatus, admin.getId(), adminNotes);
            return ResponseEntity.ok(Map.of(
                "message", "Report status updated successfully",
                "report", updatedReport
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to update report status"));
        }
    }

    /**
     * Get reports for a specific post
     */
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<ReportDTO>> getReportsForPost(@PathVariable Long postId) {
        try {
            List<ReportDTO> reports = reportService.getReportsForPost(postId);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get pending reports count
     */
    @GetMapping("/admin/count")
    public ResponseEntity<Map<String, Long>> getPendingReportsCount() {
        try {
            long count = reportService.getPendingReportsCount();
            return ResponseEntity.ok(Map.of("pendingReportsCount", count));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Check if a post has pending reports
     */
    @GetMapping("/post/{postId}/has-reports")
    public ResponseEntity<Map<String, Boolean>> hasPostPendingReports(@PathVariable Long postId) {
        try {
            boolean hasReports = reportService.hasPostPendingReports(postId);
            return ResponseEntity.ok(Map.of("hasPendingReports", hasReports));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

