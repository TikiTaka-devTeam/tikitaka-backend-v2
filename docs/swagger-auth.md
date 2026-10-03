# 운영 Swagger 로그인

`prod` 프로필에서 Swagger UI 및 OpenAPI JSON/YAML 문서에 HTTP Basic 인증을 적용한다.
브라우저에서 Swagger를 열면 기본 아이디·비밀번호 입력창이 표시된다.
일반 API는 기존 JWT 인증을 사용하며, Swagger 계정으로 일반 API에 로그인할 수 없다.
로컬 및 테스트 프로필에서는 Swagger를 로그인 없이 사용한다.

## EC2 설정

코드 배포 전 `/etc/tikitaka/tikitaka.env`에 다음 항목을 설정한다.
실제 아이디·비밀번호는 Git이나 공유 문서에 저장하지 않는다.

```text
SPRING_PROFILES_ACTIVE=prod
SPRINGDOC_SWAGGER_UI_ENABLED=true
SPRINGDOC_API_DOCS_ENABLED=true
SWAGGER_AUTH_USERNAME=직접_정한_아이디
SWAGGER_AUTH_PASSWORD=직접_정한_긴_비밀번호
```

환경변수 변경 후 서버를 재시작한다.

```bash
sudo systemctl restart tikitaka
sudo systemctl status tikitaka --no-pager
```

아이디 또는 비밀번호가 비어 있으면 서버 및 일반 API는 실행되지만 Swagger는 인증을 허용하지 않는다.
접속은 HTTPS 주소를 사용한다. HTTP Basic 인증은 비밀번호를 암호화하는 전송 방식이 아니다.

## 검증

인증 없이 `/swagger-ui/index.html`, `/swagger-ui.html`, `/v3/api-docs`,
`/v3/api-docs/swagger-config`, `/v3/api-docs.yaml`에 요청하면 401과 `WWW-Authenticate: Basic`이 반환된다.
잘못된 아이디·비밀번호는 거부하고, 올바른 계정으로는 Swagger와 문서를 볼 수 있다.
Swagger의 Authorize 버튼에 넣는 값은 기존과 동일하게 API용 JWT이며, Swagger 로그인 비밀번호와 별개다.

브라우저는 Basic 인증 정보를 기억할 수 있으므로 새 팝업을 확인하려면 시크릿 창에서 접속한다.
비밀번호 변경은 EC2 환경변수를 수정하고 서버를 재시작하여 적용한다.
