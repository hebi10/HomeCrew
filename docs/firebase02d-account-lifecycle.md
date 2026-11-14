# HomeCrew — FIREBASE-02-D 계정 종료·재인증·삭제

기준: 2026-11-14 · 범위: `com.hebi.homecrew` Firebase Authentication(선택형 Google 로그인)  
선행: FIREBASE-02-A~C 병합 `main` `1faf1b99`  
배포: **Google Play 업로드 금지, GitHub Pages 수동 승인 전 공개 금지**

## 상태 및 데이터 경계

- 로그인/세션: `GoogleSignIn`와 Firebase AuthStateListener를 그대로 사용합니다.
- 집·구성원·Room 집안일·반복 회차·완료 및 건너뛰기 기록, 사용자 지정 JSON 백업은 Firebase UID와 연동하지 않습니다.
- 로그아웃 또는 Firebase Auth 계정 삭제 시 로컬 Room/SharedPreferences 데이터는 **변경하지 않습니다**.
- Firebase Authentication 사용자 삭제는 Google 본계정 삭제와 다릅니다.
- 현재 버전에는 Firestore 사용자/집안일 원격 데이터가 없습니다. FIREBASE-03~05 전환 시 서버 연관 데이터 삭제·권한 검증이 새 필수 작업입니다.

## 기능

1. `로그아웃`: Firebase Auth 로그아웃 → Credential Manager의 현재 앱 인증 선택 상태 정리. 두 단계 결과를 구분해 표시합니다.
2. `계정 삭제`: 설정 화면에서 삭제 범위를 명시하고 사용자 확인을 받습니다. `FirebaseUser.delete()`를 시도하고, `FirebaseAuthRecentLoginRequiredException`에만 Google Credential Manager를 이용해 **같은 Firebase 사용자**로 재인증한 뒤 재시도합니다.
3. 삭제 실패·취소 시 성공 문구를 표시하지 않습니다. 계정 변경·이중 요청을 차단하며 동일 UID 확인 없이 다른 계정으로 삭제를 재시도하지 않습니다.
4. Firebase 삭제가 성공하더라도 Credential Manager 정리가 실패하면 **계정 삭제 성공 / 기기 자격 증명 정리 실패**를 따로 알립니다.
5. 사용자에게 표시하는 메시지에 ID 토큰·Firebase UID·비밀 설정 원문을 노출하지 않습니다. 동일 UID는 인증 흐름 내부에서만 비교합니다.

## 개인정보 및 앱 외 삭제 요청

- `docs/privacy-policy.html`: 선택형 Firebase 인증과 로컬 저장·외부 JSON 백업의 실제 범위를 반영한 개정안입니다.
- `docs/delete-account.html`: 앱 없이 Google 계정에 접근하는 사용자가 개발자에게 계정 삭제를 요청할 수 있는 **이메일 요청 경로**를 제공합니다. 요청자의 본인 확인 전에는 계정을 임의로 삭제하지 않습니다.
- 앱에는 개인정보처리방침·계정 삭제 안내 링크를 제공하지만, Pages 공개 전까지 새 웹 주소는 검증된 서비스가 아닙니다.
- `.github/workflows/privacy-pages.yml`은 `workflow_dispatch`와 별도 승인 입력이 있어야만 게시되도록 변경합니다. 기존 자동 `push` 게시를 제거합니다.
- **최종 출시 전 필수**: Pages 수동 게시 승인 → 공개 HTTPS URL 직접 접속·삭제 요청 이메일 동작 검증 → Google Play Data Safety 내 삭제 URL·개인정보처리방침 최신화.

## 검증

- JVM: 최근 로그인 여부에 따른 단일/재시도 삭제, 재인증 실패/타 사용자 변경 시 중단, UI 상태·중복 버튼·취소·성공·부분 실패 표시 테스트
- Android CI: Firebase 설정 포함/미포함 컴파일, Kotlin·Lint·APK, Firebase Auth 초기화 회귀
- 화면 QA: API 29/35/36, API35 태블릿·160% 폰트, 라이트/다크 계정 영역·확인창·개인정보 링크
- 물리 기기: Play 서명 Google 계정 로그인, 로그아웃, 재로그인, Firebase 계정 삭제 후 실제 Auth Users에서 사용자 제거, 로그아웃·삭제 전후 Room 데이터 동일 여부 (이슈 #43)
- 보안: 다른 계정으로 재인증 시 원본 계정이 삭제되지 않는지 확인, 로그에 토큰 미노출, 외부 백업 비자동 삭제 고지
- 실제 Google 계정 삭제·Firebase 콘솔 반영·Pages 게시/웹 삭제 URL·Play 내부 테스트 업로드는 CI 성공만으로 완료 처리하지 않습니다.

## 릴리스 보호

- `AGENTS.md`, `docs/release-policy.md`에 따라 PR당 Play AAB 제출/트랙 승격/서명키 변경은 수행하지 않습니다.
- 외부 웹 Pages 게시 역시 사용자의 별도 승인 전에는 수행하지 않습니다. CI 성공 시에도 게시되지 않습니다.
