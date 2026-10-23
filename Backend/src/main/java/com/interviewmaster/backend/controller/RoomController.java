package com.interviewmaster.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.interviewmaster.backend.model.dto.request.CreateRoomRequest;
import com.interviewmaster.backend.model.dto.request.SaveRoomQuestionsRequest;
import com.interviewmaster.backend.service.InterviewRoomService;
import com.interviewmaster.backend.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final InterviewRoomService interviewRoomService;

    @SaCheckLogin
    @PostMapping
    public ResponseEntity<?> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(interviewRoomService.createRoom(userId, request)));
    }

    @SaCheckLogin
    @GetMapping("/question-options")
    public ResponseEntity<?> listQuestionOptions(@RequestParam(required = false) Long moduleId) {
        return ResponseEntity.ok(ApiResponse.ok(
                interviewRoomService.listQuestionOptions(moduleId)));
    }

    @SaCheckLogin
    @PostMapping("/{roomCode}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomCode) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(interviewRoomService.joinRoom(userId, roomCode)));
    }

    @SaCheckLogin
    @GetMapping("/{roomCode}")
    public ResponseEntity<?> getRoomDetail(@PathVariable String roomCode) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(interviewRoomService.getRoomDetail(userId, roomCode)));
    }

    @SaCheckLogin
    @PutMapping("/{roomCode}/questions")
    public ResponseEntity<?> saveQuestions(@PathVariable String roomCode,
                                           @Valid @RequestBody SaveRoomQuestionsRequest request) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(
                interviewRoomService.saveQuestions(userId, roomCode, request)));
    }

    @SaCheckLogin
    @GetMapping("/{roomCode}/rtc-token")
    public ResponseEntity<?> getRtcToken(@PathVariable String roomCode) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(
                interviewRoomService.issueRtcToken(userId, roomCode)));
    }

    @SaCheckLogin
    @PostMapping("/{roomCode}/finish")
    public ResponseEntity<?> finishRoom(@PathVariable String roomCode) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(interviewRoomService.finishRoom(userId, roomCode)));
    }

    @SaCheckLogin
    @GetMapping("/{roomCode}/report")
    public ResponseEntity<?> getReport(@PathVariable String roomCode) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ResponseEntity.ok(ApiResponse.ok(interviewRoomService.getReport(userId, roomCode)));
    }
}
