# Codex app-server 인증·실행 계획

## 계획 세트

- Parent: [#357 Codex app-server OAuth 연결과 실행 계획](https://github.com/omegafrog/dnd-master/issues/357)
- Tracker: GitHub Project [#5 dnd-master-workflow](https://github.com/users/omegafrog/projects/5). 이슈 상태의 원본은 프로젝트의 `Workflow Status` 필드다.
- 실행 브랜치: `spec/codex-cli-oauth-onboarding`

## 하위 계획

| 순서 | 이슈 | 구현 요약 | 의존성 |
| --- | --- | --- | --- |
| 1 | [#355 Codex app-server로 설치 연결과 인증 관리](https://github.com/omegafrog/dnd-master/issues/355) | 사용자 PC에서 Codex app-server 계정을 확인하고 기존 로그인을 재사용하거나 새 로그인을 진행한다. 설치 연결만 해제하고 Codex 로그인은 유지한다. | 없음 |
| 2 | [#356 Codex app-server로 AI 실행과 인증 오류 처리](https://github.com/omegafrog/dnd-master/issues/356) | 설치 연결을 확인한 뒤 app-server로 AI 요청을 처리하고, 인증 오류는 재인증 후 수동 재시도하도록 전달한다. | #355 완료 후 진행 |

두 하위 이슈는 GitHub Sub-issues 관계로 #357에 연결했고, #356이 #355에 의존하는 관계도 GitHub에 등록했다.

## 명세

- [Product Spec](../../specs/codex-cli-oauth/product-spec.md)
- [Architecture Spec](../../specs/codex-cli-oauth/architecture-spec.md)

## 다이어그램

- Product: [최초 연결 유스케이스](../../specs/codex-cli-oauth/diagrams/product/UC-COAUTH-01.usecase.svg), [최초 연결 흐름](../../specs/codex-cli-oauth/diagrams/product/UC-COAUTH-01.activity.svg), [연결 복구 유스케이스](../../specs/codex-cli-oauth/diagrams/product/UC-COAUTH-02.usecase.svg), [연결 복구 흐름](../../specs/codex-cli-oauth/diagrams/product/UC-COAUTH-02.activity.svg)
- Architecture: [공통 제공자 구조](../../specs/codex-cli-oauth/diagrams/architecture/ai-provider.class.svg), [설치 연결 상태](../../specs/codex-cli-oauth/diagrams/architecture/codex-installation-connection.state.svg)
