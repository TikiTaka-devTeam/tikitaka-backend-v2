package com.tikitaka.user.entity;

import java.util.UUID;

import com.tikitaka.global.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 30)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "phone_number", nullable = false, unique = true, length = 11)
    private String phoneNumber;

    @Column(nullable = false, length = 100)
    private String univ;

    @Column(nullable = false, length = 100)
    private String major;

    @Column(name = "member_id_number", length = 30)
    private String memberIdNumber;

    @Column(name = "profile_url", columnDefinition = "TEXT")
    private String profileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    // 일반 회원가입 정보를 받아 User를 생성하며, 외부에서는 createLocal()을 통해서만 호출
    private User(
            String email,
            String password,
            String name,
            AccountType accountType,
            String phoneNumber,
            String univ,
            String major,
            String memberIdNumber,
            String profileUrl
    ) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.accountType = accountType;
        this.phoneNumber = phoneNumber;
        this.univ = univ;
        this.major = major;
        this.memberIdNumber = memberIdNumber == null || memberIdNumber.isBlank() ? null : memberIdNumber.trim();
        this.profileUrl = profileUrl;
    }

    public static User createLocal(
            String email,
            String encodedPassword,
            String name,
            AccountType accountType,
            String phoneNumber,
            String univ,
            String major,
            String memberIdNumber,
            String profileUrl
    ) {
        return new User(
                email,
                encodedPassword,
                name,
                accountType,
                phoneNumber,
                univ,
                major,
                memberIdNumber,
                profileUrl);
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void changeProfileImage(String profileUrl) {
        this.profileUrl = profileUrl;
    }
}
