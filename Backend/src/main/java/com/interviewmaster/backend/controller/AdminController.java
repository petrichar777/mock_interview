package com.interviewmaster.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.interviewmaster.backend.model.dto.request.AdminModuleUpsertRequest;
import com.interviewmaster.backend.model.dto.request.CategoryStatusRequest;
import com.interviewmaster.backend.model.dto.request.CategoryUpsertRequest;
import com.interviewmaster.backend.model.dto.request.DirectionStatusRequest;
import com.interviewmaster.backend.model.dto.request.DirectionUpsertRequest;
import com.interviewmaster.backend.model.dto.request.AdminQuestionStatusRequest;
import com.interviewmaster.backend.model.dto.request.AdminQuestionUpsertRequest;
import com.interviewmaster.backend.model.dto.request.AdminUserStatusRequest;
import com.interviewmaster.backend.model.dto.request.GenerateQuestionsRequest;
import com.interviewmaster.backend.model.dto.request.GrantQuotaRequest;
import com.interviewmaster.backend.model.dto.request.RegisterRequest;
import com.interviewmaster.backend.model.dto.response.AdminQuestionImportResponse;
import com.interviewmaster.backend.model.dto.response.ReviewDraftQuestionResponse;
import com.interviewmaster.backend.service.AdminService;
import com.interviewmaster.backend.util.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SaCheckRole("ADMIN")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @SaCheckLogin
    @PostMapping("/auth/register")
    public ResponseEntity<?> registerAdmin(@Valid @RequestBody RegisterRequest request) {
        Long userId = adminService.registerAdmin(request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("userId", userId)));
    }

    @SaCheckLogin
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String role,
                                       @RequestParam(required = false) Integer status,
                                       @RequestParam Integer page,
                                       @RequestParam Integer size) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(adminService.listUsers(requesterId, keyword, role, status, page, size)));
    }

    @SaCheckLogin
    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable Long userId,
                                              @Valid @RequestBody AdminUserStatusRequest request) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        adminService.updateUserStatus(requesterId, userId, request.getStatus());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("userId", userId, "status", request.getStatus())));
    }

    @SaCheckLogin
    @PostMapping("/users/{userId}/quota")
    public ResponseEntity<?> grantQuota(@PathVariable Long userId,
                                         @RequestBody GrantQuotaRequest request) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        adminService.grantQuota(requesterId, userId, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("userId", userId)));
    }

    @SaCheckLogin
    @DeleteMapping("/users/{userId}/quota")
    public ResponseEntity<?> clearQuota(@PathVariable Long userId) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        adminService.clearQuota(requesterId, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("userId", userId)));
    }

    @SaCheckLogin
    @GetMapping("/directions")
    public ResponseEntity<?> listDirections() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listDirections(StpUtil.getLoginIdAsLong())));
    }

    @SaCheckLogin
    @PostMapping("/directions")
    public ResponseEntity<?> createDirection(@Valid @RequestBody DirectionUpsertRequest request) {
        Long id = adminService.createDirection(StpUtil.getLoginIdAsLong(), request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @SaCheckLogin
    @PutMapping("/directions/{directionId}")
    public ResponseEntity<?> updateDirection(@PathVariable Long directionId,
                                             @Valid @RequestBody DirectionUpsertRequest request) {
        adminService.updateDirection(StpUtil.getLoginIdAsLong(), directionId, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("directionId", directionId)));
    }

    @SaCheckLogin
    @PatchMapping("/directions/{directionId}/status")
    public ResponseEntity<?> updateDirectionStatus(@PathVariable Long directionId,
                                                   @Valid @RequestBody DirectionStatusRequest request) {
        adminService.updateDirectionStatus(StpUtil.getLoginIdAsLong(), directionId, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("directionId", directionId, "status", request.getStatus())));
    }

    @SaCheckLogin
    @DeleteMapping("/directions/{directionId}")
    public ResponseEntity<?> deleteDirection(@PathVariable Long directionId) {
        adminService.deleteDirection(StpUtil.getLoginIdAsLong(), directionId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("directionId", directionId)));
    }

    @SaCheckLogin
    @GetMapping("/categories")
    public ResponseEntity<?> listCategories() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listCategories(StpUtil.getLoginIdAsLong())));
    }

    @SaCheckLogin
    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@Valid @RequestBody CategoryUpsertRequest request) {
        Long id = adminService.createCategory(StpUtil.getLoginIdAsLong(), request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id)));
    }

    @SaCheckLogin
    @PutMapping("/categories/{categoryId}")
    public ResponseEntity<?> updateCategory(@PathVariable Long categoryId,
                                            @Valid @RequestBody CategoryUpsertRequest request) {
        adminService.updateCategory(StpUtil.getLoginIdAsLong(), categoryId, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("categoryId", categoryId)));
    }

    @SaCheckLogin
    @PatchMapping("/categories/{categoryId}/status")
    public ResponseEntity<?> updateCategoryStatus(@PathVariable Long categoryId,
                                                   @Valid @RequestBody CategoryStatusRequest request) {
        adminService.updateCategoryStatus(StpUtil.getLoginIdAsLong(), categoryId, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("categoryId", categoryId, "status", request.getStatus())));
    }

    @SaCheckLogin
    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long categoryId) {
        adminService.deleteCategory(StpUtil.getLoginIdAsLong(), categoryId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("categoryId", categoryId)));
    }

    @SaCheckLogin
    @GetMapping("/modules")
    public ResponseEntity<?> listModules(@RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) String categoryName,
                                         @RequestParam(required = false) Integer status) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(adminService.listModules(requesterId, categoryId, categoryName, status)));
    }

    @SaCheckLogin
    @PostMapping("/modules")
    public ResponseEntity<?> createModule(@Valid @RequestBody AdminModuleUpsertRequest request) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        Long moduleId = adminService.createModule(requesterId, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("moduleId", moduleId)));
    }

    @SaCheckLogin
    @PutMapping("/modules/{moduleId}")
    public ResponseEntity<?> updateModule(@PathVariable Long moduleId,
                                          @Valid @RequestBody AdminModuleUpsertRequest request) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        adminService.updateModule(requesterId, moduleId, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("moduleId", moduleId)));
    }

    @SaCheckLogin
    @DeleteMapping("/modules/{moduleId}")
    public ResponseEntity<?> deleteModule(@PathVariable Long moduleId) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        adminService.deleteModule(requesterId, moduleId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("moduleId", moduleId)));
    }

    @SaCheckLogin
    @GetMapping("/questions")
    public ResponseEntity<?> listQuestions(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long moduleId,
                                           @RequestParam(required = false) Integer type,
                                           @RequestParam(required = false) Integer difficulty,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam Integer page,
                                           @RequestParam Integer size) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.listQuestions(requesterId, keyword, moduleId, type, difficulty, status, page, size)));
    }

    @SaCheckLogin
    @GetMapping("/questions/{questionId}")
    public ResponseEntity<?> getQuestionDetail(@PathVariable Long questionId) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(adminService.getQuestionDetail(requesterId, questionId)));
    }

    @SaCheckLogin
    @PostMapping("/questions")
    public ResponseEntity<?> createQuestion(@Valid @RequestBody AdminQuestionUpsertRequest request) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        Long questionId = adminService.createQuestion(requesterId, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("questionId", questionId)));
    }

    @SaCheckLogin
    @PutMapping("/questions/{questionId}")
    public ResponseEntity<?> updateQuestion(@PathVariable Long questionId,
                                            @Valid @RequestBody AdminQuestionUpsertRequest request) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        adminService.updateQuestion(requesterId, questionId, request);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("questionId", questionId)));
    }

    @SaCheckLogin
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<?> deleteQuestion(@PathVariable Long questionId) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        adminService.deleteQuestion(requesterId, questionId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("questionId", questionId)));
    }

    @SaCheckLogin
    @PatchMapping("/questions/{questionId}/status")
    public ResponseEntity<?> updateQuestionStatus(@PathVariable Long questionId,
                                                  @Valid @RequestBody AdminQuestionStatusRequest request) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        adminService.updateQuestionStatus(requesterId, questionId, request.getStatus());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("questionId", questionId, "status", request.getStatus())));
    }

    @SaCheckLogin
    @PostMapping("/questions/import")
    public ResponseEntity<?> importQuestions(@RequestParam("file") MultipartFile file) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        AdminQuestionImportResponse data = adminService.importQuestions(requesterId, file);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/questions/generate")
    public ResponseEntity<?> generateQuestions(@Valid @RequestBody GenerateQuestionsRequest request) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(adminService.generateQuestions(requesterId, request)));
    }

    @SaCheckLogin
    @PostMapping("/questions/drafts/review")
    public ResponseEntity<?> reviewDraftQuestions() {
        Long requesterId = StpUtil.getLoginIdAsLong();
        String batchId = adminService.startBatchReview(requesterId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("batchId", batchId)));
    }

    @SaCheckLogin
    @GetMapping("/questions/drafts/review/{batchId}")
    public ResponseEntity<?> getBatchReviewProgress(@PathVariable String batchId) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(adminService.getBatchReviewProgress(requesterId, batchId)));
    }

    @SaCheckLogin
    @PostMapping("/questions/{questionId}/review")
    public ResponseEntity<?> reviewDraftQuestion(@PathVariable Long questionId) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        ReviewDraftQuestionResponse data = adminService.reviewDraftQuestion(requesterId, questionId);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @SaCheckLogin
    @PostMapping("/questions/{questionId}/publish")
    public ResponseEntity<?> publishDraftQuestion(@PathVariable Long questionId) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        adminService.publishDraftQuestion(requesterId, questionId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("questionId", questionId, "status", 0)));
    }

    @SaCheckLogin
    @GetMapping("/questions/drafts")
    public ResponseEntity<?> listDraftQuestions(@RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        Long requesterId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(adminService.listDraftQuestions(requesterId, page, size)));
    }
}
