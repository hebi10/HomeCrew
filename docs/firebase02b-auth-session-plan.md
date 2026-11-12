# HomeCrew FIREBASE-02-B — 인증 상태 및 세션 복원

진행 상태: 구현 PR / Android CI·계측 QA 확인 전. Google Play 업로드 금지.

## 목적

Firebase Auth의 인증 세션과 기존 Room 기반 공동 공간·구성원 프로필을 분리합니다. 앱 재실행, 설정 화면 재진입 또는 프로세스 재생성 시 `FirebaseAuth.AuthStateListener`가 실제 인증 상태를 다시 전달하도록 연결합니다.

## 구현

- `HomeCrewAuthSession.Restoring`: Firebase의 초기 콜백 이전에 표시되는 확인 상태.
- `HomeCrewAuthSession.LocalOnly`: 로그인하지 않았으며 기기 내 Room 데이터는 그대로 이용하는 상태.
- `HomeCrewAuthSession.SignedIn(HomeCrewAuthUser)`: Firebase UID로 구분되는 인증 사용자. 표시 이름과 이메일은 세션 모델의 선택 필드이며 로컬 구성원과 자동 연결하지 않습니다.
- `GoogleSignIn.observeSession(context)`: `callbackFlow`로 `FirebaseAuth.AuthStateListener`를 등록하고 인증 사용자 변경 시 화면에 반영합니다. 화면이 사라지면 리스너를 해제하고 동일 스냅샷은 중복 전달하지 않습니다.
- 설정 화면은 로그인 함수의 일회성 성공 결과를 인증 상태로 취급하지 않고 실제 `observeSession`을 관찰합니다.
- UI에 토큰/UID를 표시하거나 별도로 영속 저장하지 않습니다. Firebase SDK가 내부적으로 세션을 관리합니다.
- 기존 Room, SharedPreferences, 로컬 집 이름, 구성원, 업무 분담/완료 이력과 관계없습니다.

## 검증 및 완료 기준

- JVM: 로컬 모드와 인증 모드 상태 모델, 유효 Firebase UID, 중복 인증 이벤트 비교 등 확인.
- Firebase Android 계측: 현재 Firebase 사용자와 `observeSession`의 초기 이벤트 일치 검증. 테스트 코드에서 사용자를 임의로 생성하거나 로그아웃시키지 않습니다.
- Android CI 및 API 29·35·36, API35 태블릿·160% 글꼴, 라이트/다크 설정 화면 캡처 회귀 검증.
- 앱 재실행·설정 화면 닫기/다시 열기·로그인 취소·네트워크 끊김 이후 로컬 집안일 기능이 유지되는지 실기기 확인.
- 실기기 Google 로그인·Firebase UID 발급은 앞선 `FIREBASE-02-A`와 최종 통합 QA의 보류 조건으로 계속 표시합니다.

## GitHub 작업 방식

이 브랜치는 `FIREBASE-02-A` Draft PR #28의 헤드에서 분기한 후속 개발 브랜치이며, 별도 `FIREBASE-02-B` PR을 #28 기능 브랜치 대상으로 생성합니다. #28 병합이 완료된 후에는 B PR의 기준 브랜치를 `main`으로 변경해 변경점·CI를 다시 검증합니다.

## 배포 정책

모든 단계의 코드·PR 병합·통합 QA가 끝나고 사용자가 최종 승인하기 전까지 Google Play 내부·비공개 테스트에 AAB를 업로드하지 않습니다. 현재 `main`의 자동 Play 업로드 트리거는 제거되었습니다. `FIREBASE-02-C` 계정 화면, `02-D` 로그아웃·계정 삭제, `02-E` 통합 QA, `03~05` 가족 공유·동기화는 별도 후속 단계입니다.

기준 기획서: https://docs.google.com/document/d/1QJASOpndoYPr9Xp5jKm76BSTVhOdyFBmGRB-cyitdSw/edit
