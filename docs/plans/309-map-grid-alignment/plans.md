# 맵 격자 맞추기 계획 세트

- 부모 Issue: [#309 맵 격자 맞추기](https://github.com/omegafrog/dnd-master/issues/309)
- 기준 브랜치: `main` @ `a43f3a95a48c51b503d0768b639a79dfab9d0719`
- 계획 브랜치: `plan/map-grid-alignment`
- 상태 정본: GitHub Project 5의 `Workflow Status`

## 실행 순서

1. [#310 정렬 전용 저장·적용 API](https://github.com/omegafrog/dnd-master/issues/310)
   - 게임 상태를 건드리지 않는 정렬 저장·조회·적용과 중복·충돌 처리를 구현한다.
2. [#311 공개 영역 이미지 제공](https://github.com/omegafrog/dnd-master/issues/311)
   - #310 뒤. 이미 공개된 픽셀만 제공해 이미지 변환과 확대경에서 비밀을 보호한다.
3. [#312 3단계 편집 화면과 확대경](https://github.com/omegafrog/dnd-master/issues/312)
   - #310, #311 뒤. 기준점·한 칸 크기·미세 조정, 적용·취소·재시도를 구현한다.

## 의존성

`#310 → #311 → #312`, 그리고 `#310 → #312`.

## 관련 명세

- [제품 명세](../../specs/map-grid-alignment/product-spec.md)
- [아키텍처 명세](../../specs/map-grid-alignment/architecture-spec.md)
- [UC-1 유스케이스](../../specs/map-grid-alignment/diagrams/product/UC-1.usecase.svg)
- [UC-1 활동](../../specs/map-grid-alignment/diagrams/product/UC-1.activity.svg)
- [구성요소와 저장 경계](../../specs/map-grid-alignment/diagrams/architecture/map-grid-alignment.class.svg)
- [저장 상태](../../specs/map-grid-alignment/diagrams/architecture/alignment-save.state.svg)
