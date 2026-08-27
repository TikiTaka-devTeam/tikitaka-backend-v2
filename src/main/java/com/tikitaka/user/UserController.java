package com.tikitaka.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tikitaka.auth.dto.MessageResponse;
import com.tikitaka.global.security.AuthenticatedUser;
import com.tikitaka.user.dto.PasswordChangeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "사용자/인증 API", description = "사용자 계정, 인증 및 서비스 문의 API")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/me/password")
    @Operation(summary = "USR-011 비밀번호 변경", description = "로그인 사용자의 현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다. "
            + "로그인 사용자만 호출할 수 있으며 Bearer 액세스 토큰 인증이 필요합니다. "
            + "요청 본문에 current_password와 new_password를 전달하며, 정상 처리되면 비밀번호 변경 완료 메시지를 반환합니다. 현재 비밀번호가 일치해야 하고 새 비밀번호는 비밀번호 정책을 충족해야 합니다.")
    public MessageResponse changePassword(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid PasswordChangeRequest request
    ) {
        userService.changePassword(authenticatedUser.userId(), request);
        return new MessageResponse("비밀번호가 변경되었습니다.");
    }
}
