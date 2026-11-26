package com.smartedu.modules.schedule_management.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.schedule_management.dto.SavedScheduleDtos;
import com.smartedu.modules.schedule_management.entity.SavedSchedule;
import com.smartedu.modules.schedule_management.repository.SavedScheduleRepository;
import com.smartedu.modules.schedule_management.repository.ScheduleOptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SavedScheduleService {

    private final SavedScheduleRepository savedRepository;
    private final ScheduleOptionRepository optionRepository;
    private final CurrentUserResolver currentUserResolver;

    @Transactional
    public SavedScheduleDtos.SavedScheduleResponse save(
            SavedScheduleDtos.SaveRequest request
    ) {
        var user = currentUserResolver.requireCurrentUser();

        var option = optionRepository
                .findByIdAndScheduleGenerationRegistrationPlanUserId(
                        request.optionId(),
                        user.getId()
                )
                .orElseThrow(() -> new BusinessException(
                        "Không tìm thấy phương án.",
                        HttpStatus.NOT_FOUND
                ));

        if (savedRepository.existsByUserIdAndScheduleOptionId(
                user.getId(),
                option.getId()
        )) {
            throw new BusinessException(
                    "Phương án này đã được lưu trước đó.",
                    HttpStatus.CONFLICT
            );
        }

        SavedSchedule saved = SavedSchedule.builder()
                .user(user)
                .scheduleOption(option)
                .displayName(request.displayName())
                .build();

        return toResponse(savedRepository.save(saved));
    }

    @Transactional(readOnly = true)
    public List<SavedScheduleDtos.SavedScheduleResponse> getMySchedules() {
        Long userId = currentUserResolver.requireCurrentUser().getId();

        return savedRepository.findByUserIdOrderBySavedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SavedScheduleDtos.SavedScheduleResponse rename(
            Long savedId,
            SavedScheduleDtos.RenameRequest request
    ) {
        Long userId = currentUserResolver.requireCurrentUser().getId();

        SavedSchedule saved = getOwnedSavedSchedule(savedId, userId);
        saved.setDisplayName(request.displayName());

        return toResponse(savedRepository.save(saved));
    }

    @Transactional
    public void delete(Long savedId) {
        Long userId = currentUserResolver.requireCurrentUser().getId();

        SavedSchedule saved = getOwnedSavedSchedule(savedId, userId);
        savedRepository.delete(saved);
    }

    private SavedSchedule getOwnedSavedSchedule(Long id, Long userId) {
        return savedRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(
                        "Không tìm thấy thời khóa biểu đã lưu.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private SavedScheduleDtos.SavedScheduleResponse toResponse(
            SavedSchedule saved
    ) {
        return new SavedScheduleDtos.SavedScheduleResponse(
                saved.getId(),
                saved.getScheduleOption().getId(),
                saved.getScheduleOption().getOptionName(),
                saved.getDisplayName(),
                saved.getSavedAt() == null
                        ? null
                        : saved.getSavedAt().toString()
        );
    }
}
