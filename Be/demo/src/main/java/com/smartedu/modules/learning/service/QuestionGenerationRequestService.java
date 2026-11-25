package com.smartedu.modules.learning.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smartedu.modules.learning.dto.request.CreateQuestionRequest;
import com.smartedu.modules.learning.dto.response.QuestionGenerationResponse;
import com.smartedu.modules.learning.entity.QuestionGenerationRequest;
import com.smartedu.modules.learning.repository.*;
import com.smartedu.modules.user_management.repository.UserRepository;

@Service
public class QuestionGenerationRequestService {

    private final UserRepository userRepository;
    private final LearningMaterialRepository materialRepository;
    private final MaterialContentRepository contentRepository;
    private final QuestionGenerationRequestRepository requestRepository;

    public QuestionGenerationRequestService(
        UserRepository userRepository,
        LearningMaterialRepository materialRepository,
        MaterialContentRepository contentRepository,
        QuestionGenerationRequestRepository requestRepository
    ) {
        this.userRepository = userRepository;
        this.materialRepository = materialRepository;
        this.contentRepository = contentRepository;
        this.requestRepository = requestRepository;
    }

    @Transactional
    public QuestionGenerationResponse createRequest(
        String username,
        CreateQuestionRequest request
    ) {
        // 1. Lấy người dùng hiện tại
        var user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Tài khoản không tồn tại"
            ));

        // 2. Kiểm tra tài liệu thuộc người dùng
        var material = materialRepository
            .findByIdAndUserIdAndDeletedAtIsNull(
                request.materialId(), user.getId()
            )
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy học liệu"
            ));

        // 3. Kiểm tra trạng thái học liệu
        if (!"COMPLETED".equals(
            material.getProcessingStatus()
        )) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Học liệu chưa được xử lý thành công"
            );
        }

        // 4. Kiểm tra nội dung trích xuất
        var content = contentRepository
            .findByLearningMaterialId(material.getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Học liệu chưa có nội dung trích xuất"
            ));

        if (!"COMPLETED".equals(
            content.getExtractionStatus()
        ) || content.getExtractedText() == null
          || content.getExtractedText().isBlank()) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Nội dung học liệu chưa sẵn sàng"
            );
        }

        // 5. Kiểm tra phạm vi
        String scope = request.contentScope()
            .trim().toUpperCase();

        if (!scope.equals("ALL") && !scope.equals("CUSTOM")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Phạm vi nội dung không hợp lệ"
            );
        }

        if (scope.equals("CUSTOM")
            && (request.scopeDescription() == null
            || request.scopeDescription().isBlank())) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Vui lòng mô tả phạm vi nội dung"
            );
        }

        // 6. Kiểm tra dạng câu hỏi
        String type = request.questionType()
            .trim().toUpperCase();

        if (!type.equals("MULTIPLE_CHOICE")
            && !type.equals("TRUE_FALSE")) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Dạng câu hỏi không hợp lệ"
            );
        }

        // 7. Kiểm tra độ khó
        String difficulty = request.difficulty()
            .trim().toUpperCase();

        if (!difficulty.equals("EASY")
            && !difficulty.equals("MEDIUM")
            && !difficulty.equals("HARD")) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Độ khó không hợp lệ"
            );
        }

        // 8. Lưu yêu cầu
        QuestionGenerationRequest entity =
            new QuestionGenerationRequest();

        entity.setUser(user);
        entity.setLearningMaterial(material);
        entity.setContentScope(scope);
        entity.setScopeDescription(
            request.scopeDescription()
        );
        entity.setQuestionType(type);
        entity.setQuestionCount(request.questionCount());
        entity.setDifficulty(difficulty);
        entity.setStatus("PENDING");

        var saved = requestRepository.save(entity);

        // 9. Trả kết quả
        return new QuestionGenerationResponse(
            saved.getId(),
            material.getId(),
            saved.getContentScope(),
            saved.getQuestionType(),
            saved.getQuestionCount(),
            saved.getDifficulty(),
            saved.getStatus(),
            saved.getRequestedAt()
        );
    }
}
