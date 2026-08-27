package com.tikitaka.auth.controller;

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

import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
import com.tikitaka.auth.dto.EmailAvailabilityResponse;
import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
import com.tikitaka.auth.dto.LoginRequest;
import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
import com.tikitaka.auth.dto.LoginResponse;
import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
import com.tikitaka.auth.dto.MessageResponse;
import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
import com.tikitaka.auth.dto.PhoneAvailabilityResponse;
import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
import com.tikitaka.auth.dto.PhoneVerificationConfirmRequest;
import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
import com.tikitaka.auth.dto.PhoneVerificationConfirmResponse;
import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
import com.tikitaka.auth.dto.PhoneVerificationSendRequest;
import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
import com.tikitaka.auth.dto.RefreshTokenRequest;
import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
import com.tikitaka.auth.dto.SignupRequest;
import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
import com.tikitaka.auth.dto.SignupResponse;
import com.tikitaka.auth.service.AuthService;
import com.tikitaka.auth.service.PhoneVerificationService;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Validated
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "사용자/인증 API", description = "사용자 계정, 인증 및 서비스 문의 API")
public class AuthController {
    private final AuthService authService;
    private final PhoneVerificationService phoneVerificationService;

    public AuthController(AuthService authService, PhoneVerificationService phoneVerificationService) {
        this.authService = authService;
        this.phoneVerificationService = phoneVerificationService;
    }

    @PostMapping("/phone/verification")
    @Operation(summary = "USR-003 휴대폰 인증번호 발송", description = "회원가입에 사용할 휴대폰 번호로 6자리 인증번호를 발송합니다. "
            + "인증 없이 누구나 호출할 수 있으며, 요청 본문에 phone_number를 전달합니다. "
            + "전화번호는 하이픈 유무와 관계없이 숫자로 정규화하고, 가입된 번호인지와 재전송·발송 횟수 제한을 확인한 뒤 SOLAPI로 문자를 발송합니다. "
            + "인증번호는 3분간 유효하며 응답이나 운영 로그에 노출되지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증번호 발송 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 휴대폰 번호", content = @Content),
            @ApiResponse(responseCode = "409", description = "이미 가입된 휴대폰 번호", content = @Content),
            @ApiResponse(responseCode = "429", description = "재전송 또는 발송 횟수 제한", content = @Content),
            @ApiResponse(responseCode = "503", description = "문자 발송 서비스 장애", content = @Content)
    })
    public MessageResponse sendPhoneVerification(
            @RequestBody @Valid PhoneVerificationSendRequest request,
            @Parameter(hidden = true) HttpServletRequest servletRequest
    ) {
        phoneVerificationService.sendCode(request.phoneNumber(), servletRequest.getRemoteAddr());
        return new MessageResponse("인증번호가 발송되었습니다.");
    }

    @PostMapping("/phone/verification/confirm")
    @Operation(summary = "USR-004 휴대폰 인증번호 확인", description = "문자로 발송된 6자리 인증번호를 확인합니다. "
            + "인증 없이 누구나 호출할 수 있으며, 요청 본문에 phone_number와 verification_code를 전달합니다. "
            + "인증번호의 일치 여부, 3분 만료 시간 및 최대 5회 입력 제한을 검증합니다. "
            + "성공하면 일반·소셜 회원가입에 한 번만 사용할 수 있는 verification_token과 600초의 유효시간을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공 및 일회용 토큰 발급"),
            @ApiResponse(responseCode = "400", description = "입력값 오류, 인증번호 불일치 또는 만료", content = @Content),
            @ApiResponse(responseCode = "429", description = "인증번호 확인 시도 횟수 초과", content = @Content)
    })
    public PhoneVerificationConfirmResponse confirmPhoneVerification(
            @RequestBody @Valid PhoneVerificationConfirmRequest request
    ) {
        return phoneVerificationService.confirmCode(request.phoneNumber(), request.verificationCode());
    }
    @GetMapping("/email/check")
    @Operation(summary = "USR-005 이메일 중복 확인", description = "회원가입에 사용할 이메일이 이미 등록되어 있는지 확인합니다. "
            + "인증 없이 비회원도 호출할 수 있으며, 주요 요청값은 쿼리 파라미터 email입니다. "
            + "입력한 이메일과 사용 가능 여부를 반환합니다. 이메일 형식에 맞는 값을 전달해야 합니다.")
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
    @Operation(summary = "USR-006 휴대폰 번호 중복 확인", description = "회원가입에 사용할 휴대폰 번호가 이미 등록되어 있는지 확인합니다. "
            + "인증 없이 비회원도 호출할 수 있으며, 주요 요청값은 쿼리 파라미터 phone_number입니다. "
            + "입력한 휴대폰 번호와 사용 가능 여부를 반환합니다. 사용 가능한 번호인지 확인한 뒤 휴대폰 인증과 회원가입을 진행해야 합니다.")
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
    @Operation(summary = "USR-007 일반 회원가입", description = "이메일 기반 일반 사용자 계정을 생성합니다. "
            + "인증 없이 호출할 수 있으며, multipart/form-data의 signup_data에 이메일, 비밀번호, 이름, 휴대폰 번호, 휴대폰 인증 토큰, 계정 유형 및 소속 정보를 JSON으로 전달하고 profile_image에 프로필 이미지 파일을 선택적으로 전달합니다. "
            + "가입이 완료되면 생성된 사용자 정보와 프로필 이미지 URL을 반환합니다. 이메일과 휴대폰 번호는 중복될 수 없고, 유효한 휴대폰 인증 토큰이 필요합니다.")
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
    @Operation(summary = "USR-008 이메일 로그인 및 토큰 발급", description = "등록된 이메일과 비밀번호로 사용자를 인증합니다. "
            + "인증 없이 호출할 수 있으며, 요청 본문에 email과 password를 전달합니다. "
            + "인증에 성공하면 액세스 토큰, 리프레시 토큰 및 사용자 요약 정보를 반환합니다. 이메일 또는 비밀번호가 일치하지 않으면 인증에 실패합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 입력값", content = @Content),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치", content = @Content)
    })
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "USR-009 토큰 재발급", description = "리프레시 토큰을 검증하고 새로운 액세스 토큰과 리프레시 토큰을 발급합니다. "
            + "Bearer 액세스 토큰 인증 없이 호출할 수 있으며, 요청 본문에 refresh_token을 전달합니다. "
            + "재발급에 성공하면 새로운 토큰 쌍을 반환합니다. 만료되었거나 유효하지 않은 리프레시 토큰은 사용할 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 입력값", content = @Content),
            @ApiResponse(responseCode = "401", description = "유효하지 않거나 만료된 리프레시 토큰", content = @Content)
    })
    public TokenResponse refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "USR-010 로그아웃 및 리프레시 토큰 폐기", description = "로그인 사용자의 리프레시 토큰을 폐기하여 이후 재발급에 사용할 수 없게 합니다. "
            + "로그인 사용자만 호출할 수 있으며 Bearer 액세스 토큰 인증이 필요하고, 요청 본문에 폐기할 refresh_token을 전달합니다. "
            + "정상 처리되면 로그아웃 완료 메시지를 반환합니다. 인증 사용자에게 발급된 유효한 리프레시 토큰을 전달해야 합니다.")
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
