package com.smartedu.modules.schedule_management.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.schedule_management.dto.SavedScheduleDtos;
import com.smartedu.modules.schedule_management.service.SavedScheduleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/schedules/saved")
@RequiredArgsConstructor
public class SavedScheduleController {

    private final SavedScheduleService savedScheduleService;

    @PostMapping
    public ApiResponse<SavedScheduleDtos.SavedScheduleResponse> save(
            @Valid @RequestBody SavedScheduleDtos.SaveRequest request
    ) {
        return ApiResponse.success(savedScheduleService.save(request));
    }

    @GetMapping
    public ApiResponse<List<SavedScheduleDtos.SavedScheduleResponse>> getMine() {
        return ApiResponse.success(savedScheduleService.getMySchedules());
    }

    @PutMapping("/{id}")
    public ApiResponse<SavedScheduleDtos.SavedScheduleResponse> rename(
            @PathVariable Long id,
            @Valid @RequestBody SavedScheduleDtos.RenameRequest request
    ) {
        return ApiResponse.success(
                savedScheduleService.rename(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        savedScheduleService.delete(id);
        return ApiResponse.success(null, "Đã xóa thời khóa biểu.");
    }
}
