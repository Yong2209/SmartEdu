package com.smartedu.modules.user_management.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
    String fullName,

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    String phone,

    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    String address,

    String gender

) {}
