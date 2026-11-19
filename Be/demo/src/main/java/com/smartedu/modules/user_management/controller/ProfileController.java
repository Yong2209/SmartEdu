
package main.java.com.smartedu.modules.user_management.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.user_management.service.ProfileService;
import com.smartedu.modules.user_management.dto.request.UpdateProfileRequest;
import com.smartedu.modules.user_management.dto.request.ChangePasswordRequest;
import com.smartedu.modules.user_management.dto.response.ProfileResponse;

@RestController
@RequestMapping("/api/users/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<ProfileResponse> getProfile(
        Authentication authentication
    ) {
        return ApiResponse.success(
            profileService.getProfile(authentication.getName())
        );
    }

    @PutMapping
    public ApiResponse<ProfileResponse> updateProfile(
        Authentication authentication,
        @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ApiResponse.success(
            profileService.updateProfile(
                authentication.getName(), request
            ),
            "Cập nhật hồ sơ thành công"
        );
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
        Authentication authentication,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        profileService.changePassword(
            authentication.getName(), request
        );

        return ApiResponse.success(
            null, "Đổi mật khẩu thành công"
        );
    }
}
