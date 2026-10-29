# SYNC-01 배포 서버 측정

배포 서버에서 백엔드와 Node Exporter를 실행한다. 로컬 PC의 Prometheus/Grafana는
SSH 터널 컨테이너를 통해 서버 지표를 수집한다. 부하 생성기도 로컬에서 실행한다.
모니터링용 EC2 보안 그룹 포트를 추가하지 않는다.

## 준비된 구성

| 위치 | 항목 | 주소 |
| --- | --- | --- |
| EC2 | Node Exporter | `127.0.0.1:9100` |
| EC2 | 백엔드 management listener | `127.0.0.1:9101` |
| 로컬 Docker 내부 | SSH 포워딩 | `tunnel:19100`, `tunnel:19101` |
| 로컬 PC | Prometheus | `http://localhost:9090` |
| 로컬 PC | Grafana | `http://localhost:3001` |

백엔드의 `monitoring` 프로필을 추가로 활성화할 때만 management listener가 열린다.
기존 API 인증은 유지하고, 모니터링 포트에서는 GET health/prometheus만 허용한다.
Node Exporter 설치와 백엔드 배포는 아래 내용을 검토한 후 실행한다.

## 1. 서버 Node Exporter 설치

`server/install-node-exporter.sh`는 공식 릴리스 1.12.1을 다운로드하고 SHA256을
확인한 뒤 전용 계정/systemd 서비스를 만든다. 기존 설치가 있으면 중단한다.
다운로드 파일은 임시 디렉터리에서 정리하며 백엔드를 재시작하지 않는다.
메모리 상한은 64MB, CPU 상한은 0.2 CPU다. 실제 소비량을 첫 수집에서 확인한다.

로컬 저장소 루트에서 서버로 스크립트를 복사하고 실행한다:

```powershell
scp -i .\tikitaka-ec2-key.pem monitoring/server/install-node-exporter.sh ubuntu@<EC2_HOST>:/tmp/tikitaka-install-node-exporter.sh
ssh -i .\tikitaka-ec2-key.pem ubuntu@<EC2_HOST> 'sudo bash /tmp/tikitaka-install-node-exporter.sh'
```

실행 후 서버에서 확인:

```bash
systemctl is-active node_exporter
ss -ltn 'sport = :9100'
curl --fail --silent http://127.0.0.1:9100/metrics | head
```

9100이 `127.0.0.1`에만 바인딩되어 있어야 한다.

## 2. 백엔드 재배포와 프로필 활성화

이 브랜치의 JAR에는 Micrometer Prometheus registry와 WebSocket 지표가 추가된다.
실제 서버의 서비스는 `/opt/tikitaka/tikitaka.jar`를 실행하며 환경 파일은
`/etc/tikitaka/tikitaka.env`다. 활성 프로필을 확인하고 기존 목록에 `monitoring`을
추가한다. 기존 프로필을 덮어쓰지 않는다. 예를 들어 기존이 `prod`면:

```properties
SPRING_PROFILES_ACTIVE=prod,monitoring
```

현재 CI/CD는 main push 때만 자동 배포한다. 테스트 브랜치를 push하는 것만으로
서버가 바뀌지 않는다. 승인된 배포 방식으로 기존 JAR와 환경 파일을 먼저 백업하고,
새 JAR 교체/프로필 반영 후 `sudo systemctl restart tikitaka`를 실행한다.
이 과정에는 짧은 서비스 중단이 있다. 이 브랜치에는 CI의 health 확인이
8080 또는 management 9101을 사용하도록 수정되어 있다.

```bash
systemctl is-active tikitaka
curl --fail http://127.0.0.1:9101/actuator/health
curl --fail --silent http://127.0.0.1:9101/actuator/prometheus | grep tikitaka_websocket
ss -ltn 'sport = :9101'
```

9101이 `127.0.0.1`에만 바인딩되어 있어야 한다. 재배포하지 않으면 JVM/앱 지표
수집은 불가능하다. 먼저 Node Exporter만 설치해서 OS 지표부터 확인할 수도 있다.

## 3. 로컬 모니터링 시작

Docker Desktop을 먼저 실행한다. Docker Compose는 Linux 컨테이너를 사용한다.
저장소 루트에서:

```powershell
Copy-Item monitoring/.env.example monitoring/.env
# monitoring/.env의 키 경로, known_hosts 경로, Grafana 비밀번호를 설정한다.
docker compose --env-file monitoring/.env -f monitoring/compose.yaml config --quiet
docker compose --env-file monitoring/.env -f monitoring/compose.yaml up -d --build
```

키와 known_hosts는 로컬 파일을 읽기 전용으로 마운트한다. 키를 이미지에 포함하지 않는다.
등록된 호스트 키만 허용한다. known_hosts에 서버가 없다면 먼저 정상 SSH 접속으로
호스트 지문을 확인해야 하며 호스트 검증을 끄지 않는다.
터널의 리슨 포트는 Docker 네트워크 안에서만 열고 PC에는 공개하지 않는다.

Prometheus의 `/targets`에서 두 대상이 UP인지 확인한다.
Grafana는 admin 및 `.env`의 비밀번호로 로그인하고 `Tikitaka / SYNC-01 Deployed server`
대시보드를 연다. Grafana 비밀번호 환경 변수는 볼륨 최초 생성 때 적용된다.
SSH 터널이 끊기면 UP이 0이므로 해당 구간은 서버 장애와 구분해서 해석한다.

```powershell
docker compose --env-file monitoring/.env -f monitoring/compose.yaml logs --tail 30 tunnel
# 종료: 지표/대시보드 볼륨은 보관한다.
docker compose --env-file monitoring/.env -f monitoring/compose.yaml down
```

## 4. 배포 서버 필기 시뮬레이션

필수: Node.js 24 이상, 테스트 전용 Space/슬라이드, 교수 또는 편집 권한 조교 토큰,
승인된 학생 토큰. 일반 사용자 슬라이드에는 실행하지 않는다. 실제 DB에 필기를 저장한다.
필기와 삭제 작업 기록이 남으므로 자동 정리는 하지 않는다.

```powershell
Set-Location monitoring/load
Copy-Item .env.example .env
# .env에 배포 서버 URL, 테스트 ID, 토큰을 입력한다. 파일은 Git에서 제외된다.
node --env-file=.env sync-01.mjs --confirm-write
```

토큰 유효 시간이 테스트와 초기 연결을 감당할 수 있는지 미리 검사한다.
하나의 학생 토큰을 여러 연결에 재사용할 수 있지만 이 경우 결과는 사용자 수가 아닌
연결 수 테스트다. 서로 다른 사용자 검증에는 연결 수만큼 다른 학생 토큰을 넣는다.
현재 송신자는 한 명이다. 교수/조교 동시 편집 충돌 테스트는 별도 시나리오다.

기본: 수신 연결 5개, 60초, 초당 필기 1개, 필기당 81점/4청크.
각 필기는 START/POINTS×4/END 후 REST로 저장한다. 시작 시 구독 확인 이벤트를
실제로 수신한 뒤 측정을 시작한다. 실행 중간에 학생 연결 한 개를 닫고 10초 후
다시 연결/구독/전체 조회하며, 조회 중 수신한 저장 이벤트를 버퍼에서 적용한다.
전송 도중 version 누락을 발견해도 전체 조회로 복구한다.

오류/예기치 않은 연결 종료/REST 실패 시 부하를 중단한다. 정상 실행은 전송 종료 후
3초까지 수신을 기다리고 누락을 집계한다. Ctrl+C도 부하를 중단한다.
의도적인 연결 종료 경계에서 아직 도착하지 않은 이벤트는 별도 제외 건수로 기록한다.

`results/*.json`에 다음 결과가 저장된다:

- 송신부터 수신까지의 p50/p95/p99/max. 단일 프로세스의 monotonic clock 사용.
- 구독 확인 후 정상 연결 상태인 수신자에게 기대한 고유 라이브 이벤트 수, 누락과 중복.
- REST 저장 응답 시간. 저장 실패는 성공 지연 통계에 섞지 않고 실행 실패로 기록.
- 네트워크 복원 시점부터 재연결/구독 확인/전체 상태 복구까지 걸린 시간.
- 마지막 서버 version 및 필기 ID/스타일/좌표와 각 클라이언트의 상태 일치 여부.

이 테스트는 소켓을 닫는 연결 복구이며 패킷이 응답 없이 사라지는 장애는 재현하지 않는다.
복구 표본은 실행당 1개이므로 복구 p95/p99를 성능 근거로 삼으려면 여러 실행을 모아야 한다.
클라이언트 렌더링 시간은 포함하지 않는다. 서버의 relay timer도 검증/브로커 전달까지만
측정하며 네트워크 전달 지연과 다르다. 인증 단계에서 거절된 메시지는 relay 오류에 포함되지 않는다.

## 5. 측정 순서와 중단 기준

1. 무부하 기준값 2분 기록.
2. 수신자 5명으로 60초 실행하여 기능/누락 확인.
3. 10/30/50명으로 늘려 조건별 180초, 3회 반복. 테스트 사이 1분 대기.
4. 각 실행의 UTC 시작/종료 시각으로 Grafana 동일 구간을 비교.

초기 중단 기준 예시: CPU 85% 이상 1분 지속, 가용 메모리 100MB 미만,
디스크 사용 90% 이상, 채널 큐가 30초 동안 계속 증가하거나 실제 사용자 오류 발생.
이 서버 기준 초기 운영 판단값이며 요구 SLA는 아니다. **OS 지표에 따른 자동 중단은
구현하지 않았으므로 대시보드를 보면서 수동 중단한다.**

서버 메모리가 약 1GB이고 스왑 사용이 관측되어 처음부터 50명을 실행하지 않는다.
부하 생성기와 모니터링도 로컬 CPU를 공유하므로 로컬 CPU가 포화된 결과는 재측정한다.

## 복구/제거

- 백엔드: 백업 JAR/환경 파일 복원 후 서비스 재시작. 또는 기존 프로필에서 `monitoring`만
  제거하고 재시작하면 9101 리스너와 공개 지표가 비활성화된다.
- Node Exporter: `sudo systemctl disable --now node_exporter`로 중단.
  파일/전용 계정 제거는 사용 여부를 확인한 뒤 별도로 수행한다.
- 로컬: 위 compose down. 보관된 측정 볼륨과 결과 JSON은 자동 삭제하지 않는다.

## 검증

```powershell
.\gradlew.bat test --tests 'com.tikitaka.global.config.*Tests' --tests 'com.tikitaka.note.websocket.*Tests' --no-daemon
node --test monitoring/load/sync-01.test.mjs
docker compose --env-file monitoring/.env.example -f monitoring/compose.yaml config --quiet
```

참고: [Spring Actuator endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html),
[WebSocket monitoring](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/stats.html),
[Node Exporter](https://prometheus.io/docs/guides/node-exporter/),
[Grafana Docker](https://grafana.com/docs/grafana/latest/setup-grafana/installation/docker/).
