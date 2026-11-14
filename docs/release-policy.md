# HomeCrew — 통합 개발 후 Google Play 최종 배포 정책

시행 기준: 2026-11-12 사용자 결정. 이 문서는 HomeCrew 앱의 개발/배포 운영 정책이며, 실제 Play Console의 이미 제출된 릴리스를 취소하지는 않습니다.

## 현재 결정

- 개발 중에는 Kotlin/Compose/Firebase 및 Room 기능 구현, 화면·데이터 QA, GitHub PR 검증과 순차 병합을 계속 진행합니다.
- **작업 단위/PR 병합 단위 Google Play 내부·비공개 트랙 AAB 자동 업로드는 하지 않습니다.**
- Firebase-02 사용자인증(02-A~E), Firebase-03 데이터·권한, Firebase-04 집·가족 초대, Firebase-05 Room 연동·동기화와 기존 HomeCrew 미완료 항목을 완수하고 통합 QA를 통과한 뒤 사용자 승인으로 한 번에 Play 배포합니다.
- 코드 변경 시 실행하는 Android CI, API29/35/36, 160% 글꼴, 태블릿, 화면 캡처 등 QA는 계속합니다.
- PR #28 Google 로그인 구현은 실기기 Play 서명 인증 검증이 대기 중이며 완성/배포로 표시하지 않습니다. 현재의 계정 인증 또는 모든 집안일 동기화 기능이 출시됐다고 주장하지 않습니다.
- 기존 Play 내부 테스트 버전은 덮어쓰기/상위 코드 자동 게시 금지. 이미 제출·대기 중인 이전 Actions에는 본 변경이 소급 적용되지 않으므로 별도 취소·상태 확인이 필요할 수 있습니다.

## 기술적 차단

- `.github/workflows/play-internal.yml`: `workflow_run` 및 `issue_comment` 트리거 제거. `workflow_dispatch`만 지원.
- `deploy` / `initialize` 업로드 단계에는 명시적인 `final_release_approval=HOMECREW_FINAL_RELEASE_APPROVED` 입력이 반드시 필요합니다.
- 수동 `check`는 읽기 전용, `build`는 서명 AAB 아티팩트 생성 전용이며 Play 릴리스 업로드를 수행하지 않습니다.
- 과거 PR #28 승인 댓글 `/publish-homecrew-firebase02a-v14`는 자동 게시 트리거가 아니며, 보호된 사전 배포용 job을 제거합니다. 운영자가 댓글을 다시 남겨도 Play API 업로드가 시작되지 않습니다.
- 기존 업로드 키·Google WIF·패키지 ID·버전 할당·서명 검증·API readback 코드는 보존합니다.
- 비공개 테스트 승격/스토어 설명과 이미지 등록은 최종 릴리스 단계에서 별도 검증합니다. 유지되는 레거시 수동 명령도 사용자가 명시적으로 승인하기 전까지 실행하지 않습니다.

## 최종 배포 체크리스트

1. 확정된 기능·디자인·데이터 모델 전 단계 완료 및 PR 병합.
2. 기존 로컬 Room 데이터 마이그레이션·복원/충돌·구성원 권한·보안 규칙·개인정보 삭제/탈퇴 정책 검증.
3. 실기기 Google 로그인·Firebase UID·Play 앱 서명 일치 등 필요한 수동 QA 완료.
4. Android API29/35/36, 태블릿/폰/큰 글꼴/라이트·다크 전 화면 캡처 및 통합 QA 성공.
5. 스토어 등록 텍스트·스크린샷·대표 이미지·개인정보처리방침, Google Play Console 심사 항목 점검.
6. 사용자가 **최종 배포 승인**한 뒤에만 `workflow_dispatch`로 `mode=deploy`와 위 `final_release_approval` 값을 설정.
7. 릴리스는 기존 signer로 서명하고 Play internal API commit/readback 확인. 실제 테스터 설치 검증 후 필요한 경우 비공개 트랙 승격을 별도로 승인.

## 현재 진행 중/이전에 승인된 배포에 대한 주의

2026-11-12 이전에 승인한 보호된 Play Preview 실행은 이미 독립된 GitHub Actions 작업입니다. 새 workflow 정책으로 진행 중인 기존 작업을 자동 취소할 수 없습니다. 업로드를 원치 않으면 해당 Actions 실행 화면에서 **Cancel workflow**를 선택해야 합니다. 이미 Play에 게시된 버전은 작업 완료 후의 정책 변경으로 취소되지 않습니다.

관련: https://github.com/hebi10/HomeCrew/pull/28
