package com.smartedu.modules.learning.dto.request;

import jakarta.validation.constraints.*;

public record CreateQuestionRequest(

    @NotNull(message = "Vui lòng chọn tài liệu")
    Long materialId,

    @NotBlank(message = "Vui lòng chọn phạm vi nội dung")
    String contentScope,

    String scopeDescription,

    @NotBlank(message = "Vui lòng chọn dạng câu hỏi")
    String questionType,

    @NotNull(message = "Vui lòng nhập số lượng câu hỏi")
    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    @Max(value = 50, message = "Số lượng tối đa là 50")
    Integer questionCount,

    @NotBlank(message = "Vui lòng chọn độ khó")
    String difficulty

) {}
