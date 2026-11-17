# FIREBASE-04 — 집 초대·멤버십 (미배포 개발 브랜치)

## 코드 구성

- Firebase Functions v2 Callable `createHouseholdInvite`: 소유자 인증·발급 횟수 제한, 24시간 유효·1회 사용, 보안 난수 기반 16자리 코드. 서버에는 SHA-256 해시만 저장합니다.
- `acceptHouseholdInvite`: 사용자 인증, 코드·만료·소유자 동일성 검증, 단일 트랜잭션으로 회원 등록·인덱스·집 인원수 변경.
- `leaveHousehold`, `removeHouseholdMember`: 구성원 자가 탈퇴, 집주인의 구성원 제거. 집주인의 무단 탈퇴를 금지하고 서버에서 권한을 재검증합니다.
- `ensureHouseholdOwnerIndex`: 앱이 명시적으로 생성한 클라우드 집만 자신의 집 목록 인덱스에 등록합니다.
- 보안 규칙: 집 생성 시 `memberCount=1`, 1~8명 제한, 클라이언트가 소유권·인원수·구성원 문서를 직접 조작하지 못하도록 차단.
- Android 함수 호출 어댑터: 인증된 계정에서만 호출 가능. 기존 Room 구성원 이름이나 로컬 데이터로 Firebase UID를 자동 매핑하지 않습니다.

## 출시 전 필수 조건

1. FIREBASE-03 보안 규칙 에뮬레이터 QA 통과 및 운영 Firebase Console 프로젝트 검증.
2. 실제 Functions/Firestore 런타임 권한·서비스 계정·비용 및 Blaze 요금제 확인.
3. 두 실제 Google 계정의 초대 생성·수락·중복·만료·탈퇴·강퇴·8명 제한 E2E 검증.
4. FIREBASE-05에서 동기화/로컬-계정 매핑과 계정 삭제 연관 데이터 처리 확정.
5. App Check, 기능별 호출 제한, 감사 로그 내 비밀 코드 노출 금지, 서비스 계정 IAM 최소권한 확인.
6. **현재 Functions 운영 배포 금지.** Google Play 업로드·웹 배포 없이 코드/에뮬레이터 QA까지만 진행.

기존 내부/비공개 트랙 및 전체 작업 완료 후 수동 최종 배포 정책을 유지합니다.
