# TikiTaka Backend 개발 안내

이 문서는 저장소를 처음 clone하거나 최신 변경사항을 pull한 팀원이 로컬 환경을 준비하고 검증하기 위한 기준 문서다.

## 현재 상태

현재 공통 기반에는 다음 기능이 준비되어 있다.

- Spring Boot 4.1, Java 17, Gradle Wrapper
- PostgreSQL 16 + pgvector, Flyway, JPA Auditing, `ddl-auto=validate`
- 공통 오류 응답과 주요 MVC 요청 오류의 일관된 4xx 처리
- JSON 필드의 전역 `snake_case` 변환
- 용도별 S3 검증과 업로드·삭제, AWS SDK 예외 변환
- 과제 첨부(50MB/파일, 10개, 총 100MB)와 학생 제출(50MB/파일, 5개, 총 100MB) 정책
- local 환경의 LocalStack S3와 자동 버킷 생성
- Testcontainers 기반 PostgreSQL·LocalStack 테스트
- Cursor 요청·응답과 cursor 인코딩·검증
- Actuator `health`, `info` endpoint

2026-08-21 기준 전체 테스트 37개, Gradle build, Docker Compose 설정 검증이 통과했다.

아직 구현 또는 협의가 필요한 범위는 다음과 같다.

- Entity, Repository, 도메인별 migration과 API
- SecurityFilterChain, JWT, 로그인·회원가입·OAuth, CORS/CSRF
- 운영 profile과 운영 비밀값 전달 방식
- GitHub Actions, Dockerfile, 배포 manifest, webhook, rollback 정책

### 과제 파일 공통 정책

공통 S3 계층에는 다음 제한이 적용되어 있다.

| 용도 | 파일당 최대 | 최대 개수 | 요청 전체 최대 |
|---|---:|---:|---:|
| 교수·조교 과제 첨부 | 50MB | 10개 | 100MB |
| 학생 과제 제출 | 50MB | 5개 | 100MB |

허용 MIME type은 PDF, TXT, CSV, HWP/HWPX, Word, PowerPoint, Excel, JPEG, PNG, WebP, GIF, ZIP이다. 실행파일을 포함해 허용 목록에 없는 형식은 거부한다.

이 설정은 파일의 형식·크기·개수만 검증한다. 파일 또는 코멘트 중 하나 필수, 제출 수정 시 파일 교체와 `version` 증가, DB 반영 전후 S3 정리 순서는 과제 도메인 API 구현 시 적용한다. 확장자·실제 파일 signature, 암호화 ZIP 및 악성코드 검사는 운영 전 보안 보완 범위다. API 계약의 기준은 별도로 관리하는 Notion 명세를 따른다.

## 1. 준비 사항

- Git
- Java 17 (`java -version`으로 확인)
- Docker Desktop 또는 실행 중인 Docker daemon
- Windows PowerShell

Gradle은 별도로 설치하지 않고 저장소의 `gradlew.bat`를 사용한다.

## 2. 저장소를 처음 받았을 때

### 2.1 저장소 받기

```powershell
git clone <repository-url>
Set-Location tikitaka-backend
```

이미 clone한 저장소라면 로컬 변경사항을 먼저 확인한 뒤 pull한다.

```powershell
git status
git pull
```

로컬 변경 때문에 pull이 중단되면 임의로 삭제하거나 reset하지 말고 commit 또는 stash 여부를 확인한다.

### 2.2 `.env` 생성

```powershell
Copy-Item .env.example .env
```

`.env`는 Git에서 제외되므로 커밋하지 않는다. `DB_PASSWORD`를 로컬 값으로 변경한다. Compose와 Spring Boot가 같은 `.env`를 읽으므로 DB 계정 정보는 양쪽에 동일하게 적용된다.

포트를 변경할 때는 다음 값을 함께 맞춘다.

- PostgreSQL: `DB_PORT`와 `DB_URL`의 포트
- LocalStack: `LOCALSTACK_PORT`와 `AWS_S3_ENDPOINT`의 포트

### 2.3 PostgreSQL과 LocalStack 실행

```powershell
docker compose --env-file .env up -d --wait
docker compose --env-file .env ps
```

`postgres`와 `localstack`이 모두 `healthy`인지 확인한다.

| 서비스 | 기본 주소 | 설명 |
|---|---|---|
| PostgreSQL | `localhost:5433` | pgvector 포함 PostgreSQL 16 |
| LocalStack S3 | `http://localhost:4566` | 로컬 S3 호환 서비스 |
| S3 버킷 | `tikitaka-local` | LocalStack 시작 시 자동 생성 |

LocalStack의 `test/test`는 로컬 전용 가짜 AWS 자격 증명이며 운영에서 사용하지 않는다.

### 2.4 애플리케이션 실행

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun
```

시작 후 다른 PowerShell 창에서 확인한다.

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

응답의 `status`가 `UP`이면 준비가 끝난 것이다. 실행 창에서 `Ctrl+C`로 종료한다.

## 3. 최신 변경사항을 pull한 뒤

```powershell
git pull
docker compose --env-file .env up -d --wait
.\gradlew.bat test
```

`.env.example`에 항목이 추가됐다면 기존 `.env`에도 반영한다. Compose나 image가 변경됐다면 다음 명령으로 갱신한다.

```powershell
docker compose --env-file .env pull
docker compose --env-file .env up -d --wait
```

기존 PostgreSQL volume은 `.env`의 비밀번호만 바꿔도 DB 내부 비밀번호가 바뀌지 않는다. DB 사용자의 비밀번호를 직접 변경하거나, 로컬 데이터를 지워도 될 때만 7장의 초기화 명령을 사용한다.

## 4. 테스트와 빌드

Docker daemon을 실행한 상태에서 수행한다.

```powershell
# 전체 테스트
.\gradlew.bat test

# 컴파일, 테스트, 실행 JAR 생성
.\gradlew.bat build

# 캐시 없이 전체 재검증
.\gradlew.bat build --rerun-tasks

# Compose 설정 검증
docker compose --env-file .env.example config
```

테스트를 위해 Compose를 미리 실행할 필요는 없다. Testcontainers가 격리된 pgvector PostgreSQL과 LocalStack을 실행해 Flyway migration과 S3 업로드·삭제까지 검증한다.

## 5. 환경별 구성

| 환경 | 데이터베이스 | S3 |
|---|---|---|
| `local` | Compose PostgreSQL | Compose LocalStack |
| `test` | Testcontainers PostgreSQL/pgvector | Mock 및 Testcontainers LocalStack |
| 운영 | 외부 PostgreSQL | 실제 AWS S3 |

운영 profile과 비밀값 공급 방식은 아직 확정되지 않았다. 실제 AWS S3에서는 `AWS_S3_ENDPOINT`를 설정하지 않고 `AWS_S3_BUCKET`, `AWS_REGION`과 가능하면 IAM Role을 사용한다.

## 6. Flyway 규칙

- migration은 `src/main/resources/db/migration`에 추가한다.
- 파일명은 `V{번호}__{설명}.sql` 형식을 사용한다. 예: `V2__create_users.sql`
- 이미 공유되거나 적용된 migration은 수정하지 않고 다음 버전을 추가한다.
- 테이블, 컬럼, 자료형, 제약조건, 인덱스는 `DB.md`와 일치시킨다.
- Hibernate는 스키마를 생성하지 않는다. `ddl-auto=validate`는 일치 여부만 검사한다.
- `V1__enable_pgvector.sql`이 pgvector를 활성화한다. 운영 DB에서는 extension 생성 권한을 확인한다.

## 7. Docker 관리

```powershell
# 상태 확인
docker compose --env-file .env ps

# 로그 확인
docker compose --env-file .env logs -f postgres localstack

# DB 데이터를 보존하며 종료
docker compose --env-file .env down

# 주의: 컨테이너와 로컬 DB 데이터를 함께 삭제
docker compose --env-file .env down -v
```

`down -v`는 로컬 PostgreSQL 데이터를 복구할 수 없게 삭제하므로 초기화가 확실히 필요할 때만 실행한다.

## 8. 문제 해결

- `5433` 또는 `4566` 연결 실패: Compose의 `ps`와 `logs`를 확인한다.
- Testcontainers가 Docker를 찾지 못함: Docker Desktop을 실행하고 다시 테스트한다.
- Flyway 또는 Hibernate 실패: migration 적용 상태와 `DB.md`를 비교한다.
- S3 버킷 누락: LocalStack health와 로그를 확인한다.
- DB 인증 실패: `.env`와 기존 PostgreSQL volume 생성 당시 계정이 같은지 확인한다.
- 포트 충돌: 각 서비스의 포트 관련 환경변수 두 곳을 함께 변경한다.
- profile 오류: 현재 PowerShell에 `$env:SPRING_PROFILES_ACTIVE="local"`을 지정한다.

## 9. 명세 문서

- API 계약: `API.md`
- 데이터베이스 설계: `DB.md`
- 로컬 실행과 현재 상태: `HELP.md`

도메인 구현 전에 명세와 현재 migration을 함께 확인한다.

