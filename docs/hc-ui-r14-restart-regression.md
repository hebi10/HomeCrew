# HC-UI-R14-D — 전체 기기 QA 재시작 회귀 수정

## 실패 증거
- main 073a3fe87e7395d40d456e35ca38e31f3d1b7aa9, Android CI 성공: https://github.com/hebi10/HomeCrew/actions/runs/45564394895
- 전체 기기 QA: https://github.com/hebi10/HomeCrew/actions/runs/45564545998
- API29/35/36, API35 글꼴 160%, API35 태블릿 각각 30개 계측 중 동일한 HomeCrewActivityRestartTest#taskAndCompletionRemainAfterNewActivityAndViewModel 1개 실패. 로그의 공통 오류는 "홈 화면에 진입하지 못했습니다."이며 그 밖의 검사 성공은 전체 실행 성공을 대체하지 않는다.
- 기존 테스트는 createEmptyComposeRule를 사용하지만 해당 helper에서 UIAutomator의 wait/click/swipe만 사용했다. 또한 실제 집 이름과 무관하게 "우리 집"을 기다렸다. Compose 초기 Room 복구·ViewModel 구성 대기와 화면 제목을 정합화한다. 제품 데이터 복원 실패 자체가 입증된 것은 아니다.

## 수정
- 이미 성공한 설정 재시작 테스트의 Compose awaitDescription 경로를 재사용한다. 저장된 household.homeName을 기준으로 홈 진입을 확인한다.
- 집안일 LazyColumn에 테스트 태그 하나만 추가하여 Compose scrollToNode와 기존 상세 행 접근성 설명을 사용한다. 집안일 명칭 및 재시작 전 예정·재시작 후 완료 표시를 각각 검사한다.
- 기존 Room 데이터의 완료 기록 확인, 테스트가 만든 집안일의 정리, 설정 재시작 검증을 유지한다. 실패 진단 PNG/XML을 보존한다.
- 기존 Settings targeted regression에 동일 실패 테스트 API35 선별 실행을 추가한다. 전체 기기 매트릭스 자동 반복 실행은 추가하지 않는다.

## 후속
- 최신 PR HEAD의 Android CI와 설정/집안일 재시작 테스트 2종 성공 확인 후 병합.
- 정확한 새 main SHA로 UI-R09 전체 5환경 검증을 수동 재실행한다. 기존 실패 실행의 캡처는 진단용이며 최종 시안 비교 완료로 승격하지 않는다.
- 운영 Firebase 및 Google Play 변경 없음. 실제 비운영 Firebase 연동 및 최종 화면별 비교 별도.
