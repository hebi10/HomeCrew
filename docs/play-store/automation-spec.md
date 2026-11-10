# HomeCrew 내부 테스트 배포 사양

기준: `hebi10/HomeCrew` main `d89481b13e384cd6c5b7004bfa648c13c4235ac6` (1-D 포함). 검증된 TripCanvas `3ede780803e0d40479b0f3b012e2f8652cb24bf6`의 내부 배포 보호 장치를 HomeCrew에 적용합니다.

- 패키지 `com.hebi.homecrew`, 버전 이름 `0.1.0`, Java 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, compile/target SDK 36을 유지합니다. UI, 앱 권한, 기존 앱 테스트는 변경하지 않습니다.
- 정확한 현재 main SHA의 `Android CI` (`android.yml`, 필수 job `verify`) 성공 후만 서명/업로드합니다. 해당 실행의 모든 job이 성공해야 하며, main에 `screenshot-qa.yml`이 존재하면 그 워크플로도 동일 SHA로 성공해야 합니다. 자동 실행만 미완료/미생성 QA를 최대 50분 동안 30초마다 기다리고 각 조회마다 현재 main을 재확인합니다. 수동 실행과 업로드 직전 검사는 즉시 판정하며 최근 재실행이 대기/실패/생략이면 차단합니다. PR·포크·과거 main·건너뛴 job은 허용하지 않습니다.
- `check`는 접근 진단, `build`는 서명 APK/AAB 생성, `initialize`는 빈 Play 인벤토리에 code 1 내부 초안만 등록, `deploy`는 등록된 바이너리가 있을 때 새 내부 버전만 등록합니다. 초기화는 명시적인 수동 선택 또는 신뢰된 main 커밋 제목의 `[initialize-play]`로만 요청합니다.
- 앱 전용으로 준비된 키만 사용하며 생성·대체하지 않습니다. APK/AAB의 패키지·버전·인증서·서명과 ELF32 4 KB/ELF64 16 KB 및 APK ZIP 정렬을 검증합니다. 키 누락/인증서 불일치 시 중단합니다.
- 모든 Play AAB/APK/트랙의 버전(초안 포함)보다 큰 versionCode를 할당하고 업로드 직전 재확인합니다. SHA-256 일치와 새 edit의 내부 트랙 읽기 검증이 필요합니다. 패키지 단위 동시 실행을 직렬화합니다.
- 인증은 GitHub OIDC/WIF를 사용합니다. `google-play` 환경은 main만 허용해야 합니다. 키·토큰·비밀번호·서비스 계정 JSON은 코드나 로그에 기록하지 않습니다.
- 비공개/공개/프로덕션 승격 기능은 포함하지 않습니다. API 성공은 App Signing, 심사 완료, 테스터 설치 가능 또는 기기 QA 완료를 의미하지 않습니다.
- 개인정보처리방침과 한국어 등록 문구는 현재 로컬 설정 및 실행 중 집안일 등록·반복·담당·상세 시연 기능만 설명합니다. 로그인·가족 클라우드 동기화·결제·완료 이력 영속 저장을 주장하지 않습니다.

계정/WIF/Play 앱 등록/서명 secret/Pages 설정의 담당자 확인 상태는 `registration.md`에 기록합니다. 해당 설정과 실제 공개 정책 URL·서명 빌드·Play 제출·기기 QA 결과를 구분합니다.
