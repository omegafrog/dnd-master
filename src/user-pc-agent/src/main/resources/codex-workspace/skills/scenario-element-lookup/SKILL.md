---
name: scenario-element-lookup
description: Use locked adventure scenario material for opening, continuing, or transitioning a situation. Always look up situation evidence before citing a scenario element.
---

# 시나리오 요소 조회

상황을 만들거나 이어갈 때 현재 모험에 잠긴 시나리오 자료를 근거로 사용할 경우 이 절차를 따른다.

1. 읽기 전용 MCP 도구 `find_scenario_elements`를 현재 상황과 관련된 인물·장소·목표·사건·단서 이름으로 호출한다.
2. 결과에 포함된 내용만 시나리오 사실로 사용한다.
3. `reference`에는 결과에 실제 포함된 `elementId`만 그대로 넣는다.
4. 결과가 없으면 요소 ID나 시나리오 사실을 추측하지 않는다. 시나리오 근거가 필요한 경우 근거 부족으로 처리한다.
5. 조회 절차와 도구 사용 사실은 플레이어에게 보여주는 서술에 포함하지 않는다.
