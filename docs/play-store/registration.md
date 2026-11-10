# HomeCrew Play 내부 등록

| 항목 | 준비 값 |
| --- | --- |
| 저장소 | `hebi10/HomeCrew` · ID `1452185829` |
| 패키지 | `com.hebi.homecrew` |
| 버전 | `0.1.0` · 최초 코드 `1`, 이후 전체 Play 인벤토리보다 큰 코드 자동 할당 |
| 기본 언어·가격 | 한국어 `ko-KR` · 무료 |
| 카테고리 후보 | 생산성 |
| 운영 연락처 | `asdlkj0104@gmail.com` |
| 개인정보처리방침 예정 URL | https://hebi10.github.io/HomeCrew/privacy-policy.html |
| 배포 범위 | 내부 테스트만, 비공개·공개·프로덕션 승격 없음 |

`listing-ko.json`은 현재 구현된 로컬 초기 설정과 1-D의 실행 중 집안일 등록·반복·담당·상세 기능을 설명합니다. 앱의 기능 완성, 기기 QA, Play 등록, App Signing, 심사 및 설치 가능 상태는 각각 별도로 확인합니다. 판매자 여부·대상 연령·콘텐츠 등급은 실제 사실을 확인하여 Console에 입력해야 합니다.

## 계정과 앱별 서명

다음 값은 이 앱 전용 설정입니다. 2026-11-10 계정 설정 담당자가 앱 생성, 전용 서비스 계정 활성화, WIF 연결, GitHub secret/변수 등록 및 main 전용 환경 제한을 확인했습니다. 실제 서명 빌드와 Play 제출 결과는 별도로 확인합니다.

- Cloud 프로젝트 `hebi-app-release-80847404` · 번호 `973619009476`
- 전용 서비스 계정 `play-homecrew@hebi-app-release-80847404.iam.gserviceaccount.com`
- WIF 풀 `homecrew-github` · 공급자 `github-main`
- 저장소 ID `1452185829` · 소유자 ID `80847404` · ref `refs/heads/main` · 환경 `google-play`
- 예정 provider: `projects/973619009476/locations/global/workloadIdentityPools/homecrew-github/providers/github-main`

GitHub의 `google-play` 환경은 main 브랜치만 허용합니다. WIF의 신뢰 조건은 위 저장소 ID·소유자 ID·main과 `repo:hebi10/HomeCrew:environment:google-play`를 한정합니다. 서비스 계정은 HomeCrew 앱에만 조회·테스트 출시·테스터 관리 권한이 활성화되어 있으며 프로덕션 권한은 없습니다. 정적 서비스 계정 JSON 인증은 사용하지 않습니다.

| 종류 | 이름 |
| --- | --- |
| Secrets | `ANDROID_RELEASE_KEYSTORE_BASE64`, `ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS`, `ANDROID_RELEASE_KEY_PASSWORD`, `ANDROID_RELEASE_CERT_SHA256` |
| Variables | `PLAY_WORKLOAD_IDENTITY_PROVIDER`, `PLAY_SERVICE_ACCOUNT` |
| 실행 중 값 | `PLAY_CONSOLE_INITIALIZED`: 인증된 실제 AAB/APK 조회와 edit 정리가 성공한 스텝의 출력만 사용. 영구 변수로 설정하지 않음 |

실행 중 키를 생성하거나 디버그 키 또는 다른 앱의 키로 대체하지 않습니다. 비공개로 준비한 HomeCrew 전용 업로드 키와 고정 인증서 SHA-256이 모두 일치해야 합니다. 키·암호·인증값은 코드/문서/로그에 넣지 않습니다. Google Play 설치용 앱 서명 인증서와 업로드 인증서는 구분하여 Console에서 확인합니다.

## 내부 배포 흐름

Actions의 `Play internal release`를 main에서 실행합니다.

| 모드 | 동작 |
| --- | --- |
| `check` | exact main CI와 Python 검사 후 WIF로 임시 edit에서 등록 목록 확인. 업로드·트랙 변경·커밋 없음 |
| `build` | 앱 전용 키로 APK/AAB 빌드, 식별자·버전·서명·네이티브/ZIP 정렬 검증, 산출물 보관. Play 변경 없음 |
| `initialize` | 빌드 전과 업로드 시점 모두 AAB/APK/모든 트랙의 버전이 비었을 때만 code `1`을 내부 `draft`로 제출. 새 edit에서 번들 해시와 초안 재확인 |
| `deploy` | 실제 등록 바이너리를 확인하고 전체 Play 버전보다 높은 코드로 빌드·검증 후 내부 트랙 제출 및 새 edit 재확인 |

같은 저장소의 main push에 대한 `Android CI` 성공은 자동으로 내부 `deploy`를 요청합니다. 초기화를 의도한 신뢰된 main 커밋 제목의 독립 토큰 `[initialize-play]`는 그 실행만 `initialize`로 선택합니다. 수동 초기화는 `mode=initialize`로 선택합니다. 최초 바이너리가 없으면 일반 deploy는 차단되며 자동으로 초기화하지 않습니다.

현재 `.github/workflows/android.yml`의 `verify` 및 해당 실행의 모든 job이 exact current main에서 성공해야 합니다. 미래 instrumentation job이 추가되면 그 job도 성공해야 합니다. main에 `screenshot-qa.yml`이 존재하면 같은 SHA의 push/수동 실행이 성공해야 하며 PR 실행은 인정하지 않습니다. 자동 Android CI 완료 실행만 아직 생성되지 않았거나 실행 중인 QA를 최대 50분 동안 30초마다 확인하고 매번 현재 main을 재확인합니다. 실패/생략 job은 즉시 중단하며, 수동 실행은 대기하지 않습니다. 별도 QA 완료 자동 트리거를 추가하지 않아 중복 제출을 피합니다. 빌드 전과 업로드 직전에는 현재 main 및 최신 CI/QA 재실행 상태도 다시 확인합니다. 실패/생략/대기/과거 SHA는 우회하지 않습니다.

버전 할당은 미배정 AAB·APK와 모든 트랙의 초안까지 포함합니다. 빌드 중 더 높은 코드가 나타나면 다시 빌드하도록 중단합니다. 업로드 응답 SHA-256이 로컬 AAB와 같아야 트랙을 변경합니다. 최종 커밋 또는 새 edit 읽기가 불확실하면 성공으로 보고하지 않으며 Console 확인 전 같은 제출을 반복하지 않습니다.

최신 CI 증거는 검증한 attempt 시작 시간 순서로 선택합니다. 최초 attempt만 시작 전 생성 시간으로 대체할 수 있으며, 재실행의 시작 시간이 누락되거나 잘못되면 과거 성공으로 우회하지 않고 차단합니다.

최초 초안의 알려진 HTTP 400 `changesNotSentForReview must not be set`만 같은 edit에서 보류 파라미터를 빼고 한 번 재시도합니다. `ERROR_IF_IN_REVIEW`는 유지합니다. Google의 동작에 따라 Console의 다른 대기 변경이 심사로 전송될 수 있으므로 최초 등록 전 다른 대기 변경도 확인합니다. 일반 배포나 다른 오류에는 이 예외를 적용하지 않습니다.

## 현재 앱의 개인정보와 제한

- 집 이름·구성원 이름·기본 구성원·선택 템플릿은 SharedPreferences에 현재 기기만 저장합니다. 1-D에서 등록·수정한 집안일의 설명·반복 규칙·담당 방식·예정 회차와 완료 체크는 메모리 상태이며 프로세스 종료 뒤 초기화됩니다.
- 로그인·가족 초대·클라우드 동기화·광고·분석·결제·개발자 서버 업로드 기능이 없습니다. 현재 manifest에 인터넷 권한이 없으며 `android:allowBackup="false"`로 설정합니다. `dataExtractionRules`는 별도로 지정하지 않았으므로 Android 버전/제조사별 기기 간 이전까지 불가능하다고 단정하지 않습니다.
- 초기 선택 템플릿은 오늘의 임시 집안일을 만듭니다. 1-D에서는 등록·수정·삭제, 반복 6종, 담당 방식 3종 및 상세를 실행 중 시연 상태로 제공합니다. 저장 시 반복 규칙에 따른 예정 회차를 메모리에 만들지만 집안일/완료 이력 영속화와 백그라운드 알림은 아직 없습니다. 통계도 이 실행 중 상태에서 계산됩니다.
- 실제 APK/AAB의 package·versionName·versionCode와 업로드 인증서, ELF32 최소 4 KB/ELF64 최소 16 KB 및 APK ZIP 정렬을 검사합니다. 네이티브가 없으면 `--allow-no-native`에서만 비적용으로 기록하고 ZIP 정렬 검사는 유지합니다.
- Play 데이터 보안 양식은 출시할 실제 APK/포함 SDK/동작을 재확인한 뒤 신고합니다. 앱의 로컬 처리와 플랫폼 자체 처리를 구분합니다.

## 준비 상태와 남은 확인

2026-11-10 기준 이 작업의 시작 main은 `2826e8ea1718eabda970c5f73275dac161660401`이었습니다. 별도 PR #3의 screenshot QA에 이어 PR #7의 1-D 집안일 등록·반복·담당·상세가 main `d89481b13e384cd6c5b7004bfa648c13c4235ac6`에 병합되었습니다. 이 릴리스 변경은 해당 최신 main의 앱·테스트·의존성과 README의 1-D 설명을 보존하고, 등록 자료만 실제 기능에 맞춰 갱신했습니다. QA 실행 완료 여부는 배포할 exact main의 실제 결과로 별도 확인합니다.

- [x] HomeCrew 앱 Console 생성 및 패키지 `com.hebi.homecrew` 확인
- [x] HomeCrew 전용 업로드 키 준비, 소유자 전용 비공개 백업 및 5개 signer secret 등록
- [x] 전용 Cloud 서비스 계정/WIF/앱별 Play 테스트 권한, 2개 인증 변수 및 main 전용 환경 설정
- [x] GitHub Pages의 Source를 GitHub Actions로 설정
- [ ] App Signing 등록 상태, 무료 설정과 스토어 연락처 최종 확인
- [ ] 정책 Pages 배포 후 위 URL의 공개 HTTPS 응답 확인
- [ ] exact main Android CI 및 추가된 경우 screenshot QA 성공
- [ ] `check`, 서명 `build` 및 최초 내부 `initialize` 결과 확인
- [ ] 내부 테스터 지정, Console 초안/심사/게시 상태 및 실제 설치 확인
- [ ] 화면별 실제 기기/에뮬레이터 QA와 필요 시 16 KB 기기 실행

계정 연결과 비공개 키 보관은 담당자의 확인을 반영했습니다. 공개 정책 URL·실제 서명 빌드·Play 제출·전체 기기 QA의 완료를 의미하지 않습니다.
