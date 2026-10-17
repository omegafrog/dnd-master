# 로컬 개발 서버와 E2E 실행

## 사전 조건

- WSL/Linux 셸에서 저장소 루트 기준으로 실행한다.
- Docker Compose, Java, Node.js 24, npm, Graphify가 설치되어 있어야 한다.
- 추출 기능을 검증할 때는 `~/workspace/dnd-master/docs/assets`의 실제 입력 PDF를 사용하고, 테스트할 PDF 경로와 파일을 먼저 확인한다.
- 같은 포트나 데이터베이스를 쓰는 다른 실행이 있는지 확인한다. 독립 실행이면 `BACKEND_SERVER_PORT`, `FRONTEND_DEV_PORT`, `POSTGRES_PORT`, `REDIS_PORT`, `LOCAL_AGENT_CONNECTION_RELAY_PORT`에 사용하지 않는 포트를 지정하고 `COMPOSE_PROJECT_NAME`을 고유하게 지정한다.

## 전체 개발 서버 실행

저장소 루트에서 실행한다. 브라우저로 룰북을 업로드해 처리 경로를 검증할 때는 로컬 카탈로그 자동 등록을 끈다. 그래야 업로드 UI만 추출 작업을 시작한다.

```bash
START_DEV_SKIP_LOCAL_RULEBOOK_CATALOG_SEED=true src/start-dev.sh
```

진단 로그가 필요한 경우에만 다음처럼 켠다. 이 설정은 로컬 개발용이며, 로그에 플레이어가 입력한 내용이 포함될 수 있으므로 실행 로그를 외부에 공유하기 전에 확인한다.

```bash
START_DEV_SKIP_LOCAL_RULEBOOK_CATALOG_SEED=true \
ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED=true \
src/start-dev.sh
```

실행 스크립트가 PostgreSQL·Redis 컨테이너를 준비한 뒤 로컬 연결 중계기, 백엔드, 사용자 PC 에이전트, 프런트엔드를 시작한다. 실행 중 표시되는 주소를 사용한다. 기본값은 다음과 같다.

- 프런트엔드: `http://127.0.0.1:5173`
- 백엔드와 상태 확인: `http://127.0.0.1:8080/actuator/health`
- 로컬 연결 중계기: `http://127.0.0.1:8081/actuator/health`
- API 문서: `http://127.0.0.1:8080/swagger-ui.html`

프런트엔드와 백엔드는 같은 `src/start-dev.sh` 실행에서 띄운 프로세스를 사용한다. 기존 포트 리스너가 있으면 다른 체크아웃에서 실행 중인지 확인하고, 이 체크아웃의 E2E 결과로 보고하기 전에 현재 실행을 이 체크아웃에서 다시 시작한다. 포트가 충돌하면 위 환경 변수로 별도 포트를 지정한다.

종료할 때는 실행한 터미널에서 `Ctrl+C`를 누른다. 실행 스크립트가 자신이 띄운 백엔드·프런트엔드·중계기·사용자 PC 에이전트를 종료한다. PostgreSQL·Redis 컨테이너는 유지되므로 완전히 내릴 때는 실행 때 사용한 것과 같은 Compose 프로젝트 이름으로 다음 명령을 실행한다.

```bash
docker compose -p "$COMPOSE_PROJECT_NAME" -f src/infra/compose.yaml down
```

Compose 프로젝트 이름을 직접 지정하지 않았다면 실행 스크립트가 `src` 경로에서 기본 이름을 계산한다. 병렬 실행이나 나중에 컨테이너를 내릴 필요가 있는 테스트에서는 시작할 때 `COMPOSE_PROJECT_NAME`을 직접 지정하고 그 값을 보관한다.

기본 프로젝트 이름으로 실행했다면 저장소 루트에서 다음 명령으로 인프라를 내릴 수 있다.

```bash
compose_root="$(git rev-parse --show-toplevel)/src"
compose_project="dnd-master-$(printf '%s' "$compose_root" | sha256sum | cut -c1-8)"
docker compose -p "$compose_project" -f "$compose_root/infra/compose.yaml" down
```

## 브라우저 기반 룰북 추출 E2E

1. 위 명령으로 현재 체크아웃의 전체 서버를 시작하고 브라우저에서 `http://127.0.0.1:5173`을 연다.
2. 사용자 화면에서 룰북 업로드를 시작한다. 테스트 전용 PDF를 사용하고 기존 공유 카탈로그의 자료를 덮어쓰지 않는다.
3. 화면의 처리 상태가 완료 또는 경고 포함 완료가 될 때까지 기다린다. 실패하면 브라우저 오류와 서버 로그에서 단계 및 실패 원인을 확인한다.
4. 결과에 PyMuPDF에서 온 본문과 Docling에서 온 표 셀이 함께 포함되는지, 페이지 출처가 보존되는지 확인한다. Docling 표 후보는 문맥과 표 구조를 원본 PDF에 대조한다. 두 제공자의 결과를 단순히 합쳤다는 사실만으로 추출 정확도가 확인된 것은 아니다.
5. 실행 결과와 임시 산출물은 저장소 밖에 보관한다. E2E 실행 전후의 미추적·무시 파일을 비교하고, 실행 중 생성된 산출물만 정리한다. 기존 미추적 파일이나 무시 파일은 삭제하지 않는다.

기본 로그인 정보와 테스트 입력 자료 경로는 `src/start-dev.sh`에서 확인한다. 이 개발용 계정은 로컬 테스트에만 사용한다.

## 테스트 산출물과 실행 증거

E2E 결과에는 체크아웃 경로, `git rev-parse HEAD`, 실행 명령, 백엔드·프런트엔드 PID와 작업 디렉터리, URL, 사용한 입력 파일, 화면상 처리 결과, 관련 로그, 종료 및 정리 결과를 남긴다. 새 산출물은 저장소 밖에 기록하고, 저장소 안에 생성된 항목은 실행 전 상태와 대조해 새 항목만 제거한다.

Codex 연결 경로를 검증하는 경우에만 같은 체크아웃에서 사용자 PC 에이전트를 연결하고 연결 로그를 확인한다. 다른 AI 제공자를 사용한 테스트는 Codex 연결 경로를 검증한 것으로 보고하지 않는다.
