# ASSET-02 — HomeCrew 한국어 스토어 설명 최신화

- 작업일: 2026-11-11
- 앱: HomeCrew / com.hebi.homecrew / ko-KR
- 구현 기준: main의 2-A-02 Room 로컬 저장 및 재실행 복원
- Drive 검증: https://docs.google.com/document/d/1M9HaXG3hLBOP-yDOwckTs2kGo-Chu18GhIFQd6D0IO0/edit
- 2-A-03 PR #12(건너뛰기, 자정 갱신, 백업/복원)은 main 미병합: 소개문에서 제외

## 변경
- title은 기존 HomeCrew 유지
- shortDescription 및 fullDescription을 실제 지원 기능으로 정정
- releaseNotes는 향후 새 바이너리 발행용으로만 정정; 등록정보 워크플로에서는 업로드하지 않음

## 독립 Google Play 스토어 수정
- 소유자 hebi10이 병합된 PR에 정확한 댓글로 실행
- 읽기 전용: /homecrew-listing-check-20261111
- 설명 변경: /homecrew-listing-apply-20261111
- WIF, google-play 환경, 동일 앱 전용 Play 발행 동시성 그룹
- Google Play edits.listings PATCH로 ko-KR의 shortDescription/fullDescription만 변경
- 기존 앱 이름, 동영상, 다른 언어, AAB, 트랙, 스크린샷 보존
- commit 전 검증 및 fresh edit readback. ERROR_IF_IN_REVIEW 및 changesNotSentForReview=true, 계정이 해당 옵션을 거부하면 우회하지 않고 중단
- 심사 상태·테스터 설치 가능 여부는 Play Console에서 별도 확인
- 메타데이터만 바꾸는 main 병합은 [skip ci] 메세지로 CI 후속 자동 AAB 배포를 피함
