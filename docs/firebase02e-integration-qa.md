# FIREBASE-02-E — HomeCrew 인증 통합 QA

기준일: 2026-11-14 (Asia/Seoul)
기준 저장소: hebi10/HomeCrew · Android 패키지: com.hebi.homecrew
기준 main: fca8debba3c7f4d2e55d85ac6ac43d9b6bd43dad
기준 기획: Google Drive 012_HomeCrew_FIREBASE-02_사용자인증_단계별_작업계획_2026-11-12

## 검증 범위와 출시 경계

FIREBASE-02-A~D는 Google 계정 선택/선택형 로그인, Firebase AuthStateListener 세션 복원, 계정 상태 안내, 로그아웃, 최근 로그인 재인증과 Firebase 계정 삭제 경로를 포함합니다. 기존 Room·집 설정·백업 및 구성원 프로필은 Firebase UID와 자동 연결되지 않습니다.

**본 단계의 GitHub CI/에뮬레이터 성공은 실제 Google Play 앱 서명으로 로그인·계정을 삭제해 검증했다는 의미가 아닙니다.** 테스트는 운영 Firebase 사용자를 생성·삭제하거나 원격 집안일 데이터를 변경하지 않습니다.

## 자동 통합 QA (SHA별 검증)

| 검사 | 코드/실행 근거 | 완료 기준 |
|---|---|---|
| 로그인 취소·오프라인 오류·성공 반환/실제 세션 구별 | HomeCrewAuthJourneyIntegrationTest (JVM) | 실제 Firebase 세션 전까지 로그인 완료로 표시하지 않음, 토큰/UID UI 비노출 |
| 로그인 → 세션 변경 → 로그아웃 → 재로그인 → 계정 삭제 실패/성공 상태 | HomeCrewAuthJourneyIntegrationTest / HomeCrewAccountUiStateTest | 잘못된 상태 표시·중복 액션·가짜 성공 문구 없음 |
| 최근 로그인 재인증 필요, 취소, 계정 변경, 두 번째 삭제 실패 | HomeCrewAccountDeletionTest / HomeCrewDeletionFailureIntegrationTest | 다른 계정·취소에서 재삭제 금지, 재시도 횟수 제한 |
| Firebase 세션 구독/재구독과 로컬 데이터·백업 불변 | FirebaseAuthIsolationTest (Android, Firebase 구성 빌드) | AuthStateListener 구독 전후 Room 완료/건너뛰기·백업·기존 로컬 집 설정 보존 |
| OAuth 리소스·Firebase 초기화 | FirebaseBootstrapTest | com.hebi.homecrew / homecrew-1b14b + default_web_client_id 구성 확인 |
| 앱 계정/외부 삭제·개인정보 진입점 | MainScreensScreenshotTest (Android UIAutomator) | 설정 계정·삭제 안내/개인정보 링크 실제 노출, 외부 브라우저나 서버 삭제 미호출 |
| 권한·배포 동결/인증-로컬 저장 경계 | scripts/play/tests/test_firebase02e_boundaries.py | Firebase Auth provider가 Room/SharedPreferences를 직접 변경하지 않으며 Play/Pages는 수동 게이트 |
| 기존 저장·화면·호환성 | Android CI, HomeCrew screen QA, UI-R09 device matrix | API29·35·36, API35 태블릿/160% 글꼴, Kotlin/JVM/Lint/APK, GitGuardian, 최신 Codex P1/P2 해결 |

CI 결과는 PR의 **정확한 최신 HEAD**에 대한 각 Actions 실행 링크로 확인합니다. 이전 PR이나 이전 SHA의 성공으로 최신 실패를 덮지 않습니다. PR이 병합되면 **최신 main SHA**에 대한 Android CI·화면 QA·OPS-08/Play 동결 검사를 별도로 확인합니다. 실기기 실계정 테스트는 아래의 출시 블로커를 해소하기 전까지 **미검증**으로 표시합니다.

## 실기기/서비스 검증 — 출시 시 필수, 현재 보류

1. **Play 서명 OAuth** — Firebase Google 제공업체 활성화, Android 앱 서명 SHA-1/256, OAuth Web Client ID 확인. com.hebi.homecrew 실제 Play 서명 설치본에서 테스트 Google 계정 로그인, Firebase UID 발급 및 계정 일치를 확인합니다.
2. **세션 복원** — 앱 강제 종료·재실행·설정 화면 재진입, 오프라인/네트워크 복구 및 취소 후 계정 상태를 대조합니다.
3. **로컬 자료 보존** — 실제 기기에서 집 이름/구성원/집안일 수·완료자·건너뛰기·수동 백업을 로그인 전후/로그아웃 후/삭제 후 비교합니다.
4. **계정 삭제** — 소유자가 승인한 별도 테스트 계정으로 취소·재인증 요구·다른 계정 선택·실제 삭제를 검증합니다. Firebase Authentication Users에서 삭제가 반영됐는지 확인합니다. 임의의 운영 사용자나 제3자 계정은 삭제하지 않습니다.
5. **앱 외 삭제·개인정보 링크** — 별도 사용자 승인 후 GitHub Pages 수동 게시, HTTP(S) 공개 URL과 이메일 삭제 요청·본인 확인/삭제 응답 검증, 이후 Play Console의 데이터 안전 및 개인정보 URL 수정 여부 확인합니다.
6. **후속 기능 영향** — FIREBASE-03 Firestore, 04 가족 초대/권한, 05 로컬 마이그레이션/양방향 동기화 도입 시 원격 개인정보 삭제 범위·접근 규칙·2기기 동기화 재시험이 필요합니다.

실기기 인증 검증: https://github.com/hebi10/HomeCrew/issues/43
웹 삭제 요청 게시/Play 신고: https://github.com/hebi10/HomeCrew/issues/45

## 릴리스 제약

- 단계별 PR 병합과 CI 통과만으로 Google Play 내부·비공개 테스트 AAB를 업로드하지 않습니다.
- .github/workflows/play-internal.yml의 final_release_approval=HOMECREW_FINAL_RELEASE_APPROVED 조건을 유지합니다. 전 계획 완료와 사용자 최종 승인 전 업로드/트랙 승격/스토어 수정 금지.
- .github/workflows/privacy-pages.yml은 별도의 수동 승인값을 요구합니다. 이 PR에서는 Pages 게시를 수행하지 않습니다.
- OAuth 토큰, Firebase 설정 파일, 개인 UID/계정 비밀번호는 저장소·로그·테스트 산출물에 저장하지 않습니다.
- 최종 단계의 인수조건: GitHub CI·실기기/스토어·공개 정책 URL의 **각각 별도 증빙** 확보. 현재는 코드·에뮬레이터 측 QA만 수행합니다.
