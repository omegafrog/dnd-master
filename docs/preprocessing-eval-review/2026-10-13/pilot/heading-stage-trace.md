# 제목 31개의 단계별 추적

이 보고서는 사용자 승인 전 정답 초안을 기준으로 한 진단입니다. PDF 원본의 텍스트 블록과 첫 글자 모양 정보를 읽어 현재 `HeadingDetector` 판정을 다시 계산하고, 2026-10-13 저장 산출물의 제목 연결과 문서 트리에 대조했습니다. 전처리 코드는 변경하지 않았습니다.

## 결과

| 문서 | 원본 제목 | 원본 블록에서 문구 확인 | 현재 판정 재현 통과 | 저장된 제목 연결 | 문서 트리 반영 | 최초 미해결 단계 |
|---|---:|---:|---:|---:|---:|---|
| Basic Roleplaying | 8 | 8 | 0 | 0 | 0 | 제목 후보 판정 |
| Cairn Player Guide | 6 | 6 | 2 | 2 | 2 | 제목 후보 판정 |
| Cairn Warden Guide | 17 | 17 | 1 | 1 | 1 | 제목 후보 판정 |

전체 31개 문구가 원본의 추출 텍스트에 있습니다. 현재 판정을 원본 블록에서 다시 실행했을 때 통과한 제목은 3개이고, 저장된 제목 연결과 문서 트리에도 같은 3개가 반영되어 있습니다. 따라서 누락된 28개는 제목 연결 이후나 문서 트리 저장에서 사라진 것이 아니라, 제목 후보 판정 단계에서 탈락했습니다.

## 탈락 원인

| 문서·페이지 | 제목 | 입력 블록과 글자 모양 | 점수 / 기준 | 판정 |
|---|---|---|---:|---|
| basic-roleplaying 6쪽 | Step One: Name and Characteristics | 34자, 첫 글자 12.0pt Calibri-Bold | 0.45 / 0.65 | 탈락 |
| basic-roleplaying 6쪽 | Step Two: Powers | 16자, 첫 글자 12.0pt Calibri-Bold | 0.45 / 0.65 | 탈락 |
| basic-roleplaying 6쪽 | Step Three: Age | 15자, 첫 글자 12.0pt Calibri-Bold | 0.45 / 0.65 | 탈락 |
| basic-roleplaying 7쪽 | Step Four: Characteristic Rolls | 31자, 첫 글자 12.0pt Calibri-Bold | 0.45 / 0.65 | 탈락 |
| basic-roleplaying 7쪽 | Step Five: Derived Characteristics | 34자, 첫 글자 12.0pt Calibri-Bold | 0.45 / 0.65 | 탈락 |
| basic-roleplaying 8쪽 | Step Six: Personality (Optional) | 32자, 첫 글자 12.0pt Calibri-Bold | 0.45 / 0.65 | 탈락 |
| basic-roleplaying 8쪽 | Personality Types | 17자, 첫 글자 12.0pt Calibri-Bold | 0.45 / 0.65 | 탈락 |
| basic-roleplaying 8쪽 | Step Seven: Profession and Skills | 33자, 첫 글자 12.0pt Calibri-Bold | 0.45 / 0.65 | 탈락 |
| cairn-player 9쪽 | Attributes | 10자, 첫 글자 12.0pt Lora-Regular | 0.20 / 0.65 | 탈락 |
| cairn-player 9쪽 | Hit Protection | 14자, 첫 글자 12.0pt Lora-Regular | 0.20 / 0.65 | 탈락 |
| cairn-player 9쪽 | Inventory | 9자, 첫 글자 12.0pt Lora-Regular | 0.20 / 0.65 | 탈락 |
| cairn-player 9쪽 | Inventory Slots | 149자, 첫 글자 8.5pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-player 10쪽 | Character Traits | 16자, 첫 글자 18.0pt Lora-Regular | 1.00 / 0.65 | 통과 |
| cairn-player 11쪽 | Character Traits | 16자, 첫 글자 18.0pt Lora-Regular | 1.00 / 0.65 | 통과 |
| cairn-warden 32쪽 | Dungeon Seeds | 13자, 첫 글자 20.0pt Lora-Regular | 1.00 / 0.65 | 통과 |
| cairn-warden 32쪽 | Dungeon Principles | 18자, 첫 글자 12.0pt Lora-Regular | 0.20 / 0.65 | 탈락 |
| cairn-warden 32쪽 | Purpose | 327자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 32쪽 | Levels | 240자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 32쪽 | Layout | 183자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 32쪽 | Verticality | 115자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 32쪽 | Secret Areas | 184자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 32쪽 | Rooms | 115자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 32쪽 | Treasure | 189자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 32쪽 | Challenges | 419자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 33쪽 | NPCs | 381자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 33쪽 | Factions | 195자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 33쪽 | Puzzles | 415자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 33쪽 | The Dungeon Map | 258자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 33쪽 | Room Keying | 346자, 첫 글자 10.0pt Lora-Regular | 0.10 / 0.65 | 탈락 |
| cairn-warden 33쪽 | Encounter Tables | 295자, 첫 글자 10.0pt Lora-Regular | 0.00 / 0.65 | 탈락 |
| cairn-warden 119쪽 | Nouns | 5자, 첫 글자 12.0pt Lora-Regular | 0.20 / 0.65 | 탈락 |

## 단계별 해석

- **Basic Roleplaying 8개:** 모두 원본에서 별도 제목 블록으로 추출됩니다. 글자 모양은 12pt 굵은 글씨지만 현재 점수는 0.45로 기준 0.65보다 낮습니다. 현재 규칙은 굵은 글씨·짧은 문구에 점수를 더하지만, 13pt 이상에만 큰 추가 점수를 주기 때문에 이 제목을 통과시키지 못합니다.
- **Cairn Player 4쪽 제목:** `Attributes`, `Hit Protection`, `Inventory`는 12pt 보통 글씨로 각각 별도 블록에 있지만 0.20점에 그칩니다. `Inventory Slots`는 본문과 합쳐진 149자 블록으로 추출되어 0점입니다. 반면 10·11쪽의 18pt `Character Traits` 두 제목은 1.00점으로 통과했고 연결·문서 트리에도 남았습니다.
- **Cairn Warden 16개 누락:** 20pt `Dungeon Seeds` 하나는 통과했습니다. `Dungeon Principles`와 `Nouns`는 별도 블록이지만 12pt 보통 글씨라 0.20점입니다. 나머지 14개 소제목은 제목과 본문이 한 블록에 합쳐져 있고 첫 글자 크기만 10pt로 전달됩니다. 현재 제목 판정에는 115~419자의 본문 전체가 들어가 0~0.10점으로 탈락합니다.

## 확인 범위와 한계

저장된 전처리 산출물에는 제목 후보 목록과 후보별 점수가 없습니다. 따라서 후보 점수는 PDF 원본에서 얻은 동일 블록 문구와 첫 글자 모양을 현재 판정 함수에 다시 넣어 계산했습니다. 저장된 결과와 비교해 3개 통과·28개 탈락이 일치하는 것을 확인했습니다. 원본 후보 목록이 저장되지 않았으므로 과거 실행의 내부 후보 객체 자체를 복원한 결과라고 주장하지 않습니다.

현재 확인된 공통 문제는 “원본에서 제목 문구를 못 읽음”이 아닙니다. 제목 판정이 블록 전체 문구와 첫 글자 모양만 사용하며, 문서 안의 본문 글자 크기와 비교하지 않고 절대 크기 기준을 사용합니다. 제목과 본문이 한 블록에 들어가면 나머지 글자별 크기 차이도 판정 입력에서 사라집니다. 이번 추적만으로 수정안을 확정하거나 범용성을 입증하지는 않았습니다.

자세한 원본 문구, 글자 모양, 점수, 저장 연결 ID, 문서 트리 ID는 [heading-stage-trace.json](heading-stage-trace.json)에 있습니다.
