# Redmi Watch 6 Home Assistant v0.1.0

중국 내수판 Redmi Watch 6용 첫 사전 릴리즈입니다. **실기기 검증 전입니다.**

- Android: 한국어 서버·Keystore 토큰 관리, 엔티티 자동 조회/검색/선택/이름·아이콘·즐겨찾기·그룹·순서 편집, Mi Fitness XMS 설정 동기화.
- 워치: 저장된 설정으로 Home Assistant REST API 직접 호출, 조명·스위치·팬·커튼 제어, 센서 표시, 장면·스크립트 실행, 기능에 따른 밝기·위치, 실제 상태 재조회.
- Android 상주 서비스·별도 중계 서버 없음. 설정 저장 후 관리앱 종료를 전제로 구현했으며 Mi Fitness 네트워크 경로는 필요할 수 있습니다.
- 분할 전송·요청 ID·ACK·재시도·전체 설정 버전/교체·이중 슬롯 저장과 삭제. TOTP의 XMS 등록 Binder 및 슬롯 1 ACK 수정 반영.
- 워치 Info의 GET/POST 네트워크 진단, 자격 증명 삭제. 한글 이름은 ASCII entity_id 이름으로 대체.

## 설치

같은 릴리즈의 APK와 RPK를 함께 사용하세요. 두 파일은 `com.vela.rw6ha`와 같은 새 인증서로 서명되어 기존 TOTP와 독립 설치됩니다. APK는 Android 8.0 이상, RPK는 Notify for Xiaomi로 설치합니다. Mi Fitness 연결·동반 앱 권한을 유지하세요.

[설치·설정 가이드](https://github.com/ug30242/homeassistant_rw6/blob/v0.1.0/docs/setup.ko.md)

## 검증

- JS 테스트 16개, Android 테스트 18개, Android release 빌드 및 Lint 오류 0개.
- 실제 Java 분할 패킷 → 워치 저장/ACK 상호운용 통과.
- 실제 컴파일 RPK의 4개 화면·터치·fetch·XMS·저장·삭제·종료 smoke 통과.
- APK v2/v3와 RPK 서명, 동일 인증서·패키지·버전, SHA256 체크섬 확인.

**실기기에서 확인할 항목:** 중국판 펌웨어의 fetch/TLS/Authorization, Mi Fitness XMS 양방향·1006, 재시작 후 저장, 실제 터치·제어, Android 관리앱 강제 종료 후 GET/POST 및 제어. 위 자동 검증은 하드웨어 성공을 뜻하지 않습니다.

[실기기 검증표](https://github.com/ug30242/homeassistant_rw6/blob/v0.1.0/docs/validation.ko.md)
