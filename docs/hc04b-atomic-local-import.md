# HC-04-B1 — 소규모 원자적 서버 이전

- HC-04-A의 자료 구조를 서버에서 독립 검증합니다. Kotlin과 동일한 길이/타입/키 정렬 SHA-256, 원본 ID와 날짜 기반 결정적 ID, 반복·배정·원본 시간·완료·건너뛰기·업무량·현재 UID를 검증합니다.
- importLocalHouseholdManifest는 인증 소유자, 현재 멤버십, 완전히 빈 chores/occurrences 대상만 허용합니다. 기본 한도는 정의+회차 300개 및 JSON 512KiB. 큰 데이터는 부분 반영 없이 차단합니다.
- 집안일·회차·작업 영수증을 같은 Firestore 트랜잭션으로 생성합니다. 기존 일반 집안일 등록도 household 문서에 쓰도록 직렬화합니다. 동일 fingerprint 요청을 다시 받으면 기존 영수증을 반환하고, 다른 데이터로 덮어쓰지 않습니다.
- Firebase Timestamp와 원본 epoch milliseconds, sourceLocalId, 구형 업무량 원래 값 및 건너뛰기 사유를 보존합니다. 과거 날짜는 마이그레이션에서만 허용합니다. 원본 Room은 수정·삭제하지 않습니다.
- 검증: Node 단위 테스트와 demo Auth/Firestore/Functions 에뮬레이터에서 인증/멤버십 변경, 유효하지 않은 자료의 부분 쓰기 방지, 기록 보존, 동일 요청 재생 및 기존 대상 덮어쓰기 금지.
- Android 실제 전송/재확인, 프로세스 재시작 복구, 대용량 staging 및 원자적 공개, 원본 백업 및 실제 테스트 환경 데이터 이관은 후속입니다. 현재 서버 API 코드만이며 운영 Firebase/Google Play 배포 없음.
