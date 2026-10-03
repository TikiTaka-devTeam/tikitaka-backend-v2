# Space 자동보관

Space의 `year`와 `semester`를 기준으로 한국 시간(Asia/Seoul)에 처리한다.

- 1학기: 해당 연도 7월 1일 00시부터 보관 대상
- 2학기: 다음 연도 1월 1일 00시부터 보관 대상
- 정기 실행: 매년 1월 1일과 7월 1일 00시
- 서버 시작 시에도 한 번 실행하여 서버 중단 중 놓친 대상을 처리한다.

활성 Space만 갱신하며 `active_status=false`, `archived_at`과 `updated_at`을 실행 시각으로 설정한다.
이미 보관된 Space의 기록은 유지하며, 현재 학기와 미래 학기는 보관하지 않는다.
지난 학기 Space를 수동 복원하면 다음 정기 실행 또는 서버 재시작 시 다시 보관된다.
최초 배포 후 서버 시작 시 기존의 지난 학기 활성 Space도 보관된다.

## 로컬 검증

날짜 계산 코드를 바꾸지 않고 다음 테스트를 실행한다.

```powershell
.\gradlew.bat test --tests 'com.tikitaka.space.service.SpaceAutoArchive*'
```

고정 Clock으로 한국 시간 경계 직전·직후를 검증한다.
PostgreSQL 테스트는 Docker가 있을 때 실행하며, 종료 대상·현재 및 미래 학기·재실행·기존 보관 기록을 확인한다.

실제 스케줄 실행을 보려면 격리된 로컬 DB에 지난 학기의 활성 테스트 Space를 만들고 다음 환경변수를 설정한다.

```text
SPACE_AUTO_ARCHIVE_CRON=*/5 * * * * *
```

5초마다 실행하지만 보관 기준 날짜는 바뀌지 않는다. `Space automatic archival completed. archivedCount=...` 로그와
테스트 Space의 `active_status`, `archived_at` 및 보관 목록 응답을 확인한다.
현재 학기의 Space는 이 설정에서도 보관되지 않는다. 검증 후 환경변수를 제거한다.

자동 실행을 끄려면 `SPACE_AUTO_ARCHIVE_ENABLED=false`로 설정한다.
이 설정은 정기 실행과 서버 시작 시 확인을 모두 비활성화한다.
