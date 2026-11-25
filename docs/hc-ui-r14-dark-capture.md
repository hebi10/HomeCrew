# HC-UI-R14-C — 다크 모드 실제 화면 캡처 누락 보완

기준: #75 병합 main 85ebf598fa7b7508f8ab3d4b36a85838cd22356e. 기존 #72/#73 일반 글꼴 디자인 QA는 captureDesignTabs=true를 통해 일정 월·주·일, 집안일, 통계의 다크 화면을 수집했다. 전체 수동 QA 스크립트에서는 이 옵션이 없으므로 홈 다크만 캡처될 수 있었다.

## 변경
- MainScreensScreenshotTest의 다크 탭 캡처는 captureDarkTabs 옵션으로 분리하고 기본 활성화한다. 기존 captureDesignTabs의 신규 구성원 입력 및 구성원 전체 현황 동작은 그대로 유지한다. 전체 계측에서는 테스트 데이터와 무관하게 다크 탭을 확보한다.
- 기존 Android 전체 QA 스크립트가 captureDarkTabs=true를 명시적으로 전달한다.
- 전체 수집 PNG 7장 최소 기준과 기존 FirebaseBootstrap/계측 실패 검사를 유지한다. 추가로 현재 실행의 manifest에서 정상 app 분류인 홈, 일정 주/월/일, 집안일, 통계 다크 PNG 6장을 각각 검증한다. fixture·진단 이미지 또는 이전 실행으로 대체하지 않는다.
- 기존 PR 선별 main-tabs 및 account-large-font 계측에서 새 기본 경로를 검증한다. 최종 5환경 매트릭스는 자동 중복 실행하지 않으며 최종 main에서 기존 수동 흐름대로 수행한다.

## 제한
#76 공동 화면의 검증과 통합이 별도로 필요하다. 이 PR은 캡처 누락 방지이며 실제 전체 매트릭스 성공이나 27개 화면 시각 검토 완료를 의미하지 않는다. 운영 Firebase 및 Google Play 변경 없음.
