# HC-02-A — 공동 공간 관리 개발 미리보기

## 범위
- Google 계정 설정에서 공동 공간 목록, 새 공간 생성, 가족 초대 코드 발급, 초대 수락 UI를 연결합니다.
- 목록은 인증된 현재 UID의 서버 인덱스와 집 문서를 Source.SERVER로 읽으며, 다른 UID로 전환되면 결과를 사용하지 않습니다.
- 생성 Callable은 인증 UID와 요청 UUID 기반으로 집 ID를 결정하고, 집 문서와 소유자 인덱스를 같은 트랜잭션에서 생성합니다. 동일 요청 재시도는 중복 집을 만들지 않습니다.
- 기존 초대 발급·수락 Callable과 서버 권한 검증을 재사용합니다. 로컬 Room, 구성원, 완료 기록을 전송하지 않습니다.

## 개발 및 출시 게이트
- 기능은 기본 OFF이며 release에서는 항상 OFF입니다. Firebase 설정이 있는 debug 빌드에서 `-PhomecrewSharedHouseholdsPreview=true`와 `-PhomecrewSharedHouseholdsPreviewProject=<승인된 테스트 프로젝트 ID>`를 모두 명시해야 합니다. 실제 프로젝트 ID가 지정값과 다르거나 운영 프로젝트(homecrew-1b14b)면 차단합니다. 이 설정은 테스트 프로젝트 자체를 생성하거나 에뮬레이터에 자동 연결하지 않습니다.
- 현재 PR은 개발 코드입니다. 계정 삭제·멤버십·공동 집 소유권 정책(#51), Firebase App Check/IAM, Functions 및 Rules 운영 검증 전에는 출시나 실제 운영 데이터 생성 금지.
- 실제 2계정의 생성/가입, 만료/중복 초대, 서버 요청 실패·권한 박탈, 계정 변경 중 요청 취소, 160% 글꼴 화면을 후속 검증합니다.
- HC-03 실시간 공유, HC-04 로컬 데이터 이전, HC-05 구성원/소유권·탈퇴 관리는 별도 진행합니다.
- 새 기능에 대한 Play/Firebase 운영 배포는 이 PR에서 수행하지 않습니다.

## HC-02-B — 에뮬레이터 권한 통합 검증
- `npm run test:shared-households`는 demo-homecrew-shared-ci Auth/Firestore/Functions 에뮬레이터만 사용합니다. 임시 계정 3개로 인증 없음/타인 접근, 원자적 생성 및 동일 요청 재시도, 소유자 초대, 사용된 코드 타 계정 재사용 금지, 같은 구성원 응답 유실 후 재시도, 만료, 탈퇴/권한 박탈 후 재가입 차단을 검증합니다.
- 실제 물리 기기 Google 로그인/Android UI 상호작용 및 운영 Rules/IAM/계정 데이터 삭제는 별도 출시 차단 조건입니다.
