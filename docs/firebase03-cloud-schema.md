# FIREBASE-03 — 안전한 Firestore 데이터 경계

## 구현 범위 (2026-11-17)

이 단계는 다음 FIREBASE-04/05 개발을 위한 **기본 스키마 및 소유자 권한 기반** 구현입니다.
Android Firebase 설정이 있는 빌드에만 `firebase-firestore` 의존성이 추가됩니다. 초기 실행, 기존 Google 로그인, 로컬 Room/JSON 백업에 연결하지 않습니다. **앱을 실행하거나 로그인하는 것만으로 데이터를 업로드하지 않습니다.**

### 현재 스키마

- `households/{householdId}`: `ownerUid`, `name`(1~80자), `schemaVersion=1`, `createdAt`, `updatedAt`.
- `households/{householdId}/members/{firebaseUid}`: 향후 초대 수락 시 신뢰된 서버만 기록하는 역할/가입일.
- `households/{householdId}/chores/{choreId}`, `.../occurrences/{occurrenceId}`: 서버 권한이 검증된 후에만 쓰기 허용. 로컬 Room의 UUID·완료 이력은 아직 전송하지 않습니다.
- `users/{firebaseUid}/households/{householdId}`: 구성원별 집 목록을 위한 서버 소유 인덱스. 본인만 읽을 수 있고 클라이언트 쓰기 불가.

### 접근 제어

- 인증 사용자는 자기 소유의 집만 생성·이름 변경할 수 있습니다.
- 다른 계정의 집은 읽기·수정 불가, 공동 집 전체 목록 조회는 금지합니다.
- 신뢰된 서버가 부여한 멤버십이 있는 사용자만 공동 집과 그 하위 기록을 읽습니다.
- 멤버십·초대·완료 회차·삭제는 아직 클라이언트로 허용하지 않습니다. Firebase Admin SDK/Cloud Functions(후속)만 규칙을 우회할 수 있으므로 **서버 IAM과 실제 배포 검사 필수**입니다.
- 집 소유자 변경·삭제/계정 삭제에 연관된 데이터 정리는 추후 서버 트랜잭션 및 GDPR/Play 정책 QA로 처리해야 합니다.
- Firestore Rules는 서버 Admin SDK 호출에 적용되지 않으므로 IAM과 Function 인증 로직을 별도 검증합니다.

### 확인

`npm run test:firestore`는 `demo-homecrew-security-ci` 에뮬레이터에서 비로그인/타인/구성원/소유자/권한 탈취·서버 전용 쓰기를 검증합니다. 실제 Google Cloud 프로젝트에 접속하지 않습니다.

**미완료:** Firebase Console의 데이터베이스 생성, 운영 Rules 배포·읽기 확인, 비용 알림/한도, FIREBASE-04 초대 및 FIREBASE-05 다중 기기 동기화, 실기기 E2E. 운영 데이터베이스로 승격할 때 별도 승인과 검증이 필요합니다.

기준 main: `fca8debba3c7f4d2e55d85ac6ac43d9b6bd43dad`. 로컬 구조와 Play 릴리스 동결 정책 보존.
