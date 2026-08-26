package com.tikitaka.auth;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.auth.dto.EmailAvailabilityResponse;
import com.tikitaka.auth.dto.LoginRequest;
import com.tikitaka.auth.dto.LoginResponse;
import com.tikitaka.auth.dto.MessageResponse;
import com.tikitaka.auth.dto.PhoneAvailabilityResponse;
import com.tikitaka.auth.dto.RefreshTokenRequest;
import com.tikitaka.auth.dto.SignupRequest;
import com.tikitaka.auth.dto.SignupResponse;
import com.tikitaka.auth.dto.TokenResponse;
import com.tikitaka.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Validated
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "회원가입, 로그인 및 토큰 관리 API")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/email/check")
    @Operation(summary = "이메일 중복 확인", description = "회원가입에 사용할 이메일의 사용 가능 여부를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "중복 확인 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 이메일", content = @Content)
    })
    public EmailAvailabilityResponse checkEmail(
            @Parameter(description = "중복 확인할 이메일", example = "32221234@dankook.ac.kr")
            @RequestParam @NotBlank @Email String email
    ) {
        return authService.checkEmail(email);
    }

    @GetMapping("/phone/check")
    @Operation(summary = "휴대폰 번호 중복 확인", description = "회원가입에 사용할 휴대폰 번호의 사용 가능 여부를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "중복 확인 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 휴대폰 번호", content = @Content)
    })
    public PhoneAvailabilityResponse checkPhone(
            @Parameter(description = "중복 확인할 휴대폰 번호", example = "010-1234-1234")
            @RequestParam(name = "phone_number")
            @NotBlank
            @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$")
            String phoneNumber
    ) {
        return authService.checkPhone(phoneNumber);
    }

    @PostMapping(value = "/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "일반 회원가입", description = "회원가입 정보와 선택적 프로필 이미지를 multipart/form-data로 전송합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 또는 휴대폰 인증 토큰 오류", content = @Content),
            @ApiResponse(responseCode = "409", description = "이미 가입된 이메일 또는 휴대폰 번호", content = @Content)
    })
    public SignupResponse signup(
            @Parameter(description = "회원가입 정보(JSON)", required = true,
                    schema = @Schema(implementation = SignupRequest.class))
            @RequestPart("signup_data") @Valid SignupRequest signupData,
            @Parameter(description = "프로필 이미지 파일(선택)")
            @RequestPart(value = "profile_image", required = false) MultipartFile profileImage
    ) {
        return authService.signup(signupData, profileImage);
    }

    @PostMapping("/login")
    @Operation(summary = "이메일 로그인", description = "이메일과 비밀번호로 로그인하고 액세스·리프레시 토큰을 발급합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 입력값", content = @Content),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치", content = @Content)
    })
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "토큰 재발급", description = "유효한 리프레시 토큰을 사용해 토큰 쌍을 재발급합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 입력값", content = @Content),
            @ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 리프레시 토큰", content = @Content)
    })
    public TokenResponse refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그인 사용자의 리프레시 토큰을 폐기합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 입력값", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
    })
    public MessageResponse logout(
            @Parameter(hidden = true)
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody @Valid RefreshTokenRequest request
    ) {
        authService.logout(authenticatedUser.userId(), request.refreshToken());
        return new MessageResponse("로그아웃되었습니다.");
    }
}
