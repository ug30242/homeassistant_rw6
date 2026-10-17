# TOTP 참조 구현 분석

검토한 원본: [ug30242/totp_rw6 @ 5c42e468777e41c56d468657dab2e18278d9b108](https://github.com/ug30242/totp_rw6/tree/5c42e468777e41c56d468657dab2e18278d9b108).

원본 저장소를 읽기용으로 별도 clone하여 분석했습니다. 원본 코드·앱 식별자·릴리즈·키를 변경하지 않았으며 원본 개인키도 사용하지 않았습니다.

## 재사용한 부분

- `XmsClient.java`: Mi Fitness 두 패키지의 서비스 탐색, `com.xiaomi.wearable.XMS_WEARABLE_SERVICE` 바인딩, 권한 확인·요청, 설치 확인, 노드별 메시지 수신, 워치 수신 화면 실행, 작업 스레드와 메인 스레드 분리, generation으로 이전 연결 콜백 무시. HA 패키지로 변경하고 연결 타임아웃을 추가했습니다.
- `registerServiceConnectedListener`: 노드 조회 전에 XMS Binder 트랜잭션 14 등록. 등록용 Binder를 보관하고 `IServiceConnectedListener` 토큰을 확인하는 슬롯 1 one-way ACK를 수락합니다. 자동 생성된 빈 AIDL Stub만으로는 해당 ACK가 처리되지 않습니다.
- `android/xms-contract`: [MIT 공개 clean-room 계약](../android/xms-contract/NOTICE.md)의 AIDL·Parcelable을 그대로 사용합니다. Xiaomi 비공개 바이너리 SDK나 대체 서비스는 포함하지 않습니다.
- `phone-wire.js`: 원문/JSON/중첩 `data`/UTF-8 바이트 파싱과 버전·요청 ID 검증을 재사용했습니다.
- `phone-channel.js`, `pages/receive`: native `connect.send({data:{data:json}})` 구조와 페이지 lease 종료를 반영했습니다. 오래된 화면이 닫혀도 새 화면이 등록한 singleton 수신 핸들러를 덮어쓰지 않습니다.
- `VaultCipher.java`, vault 구조: Android Keystore의 AES-256-GCM 키, 버전·nonce·인증 태그, AAD, no-backup AtomicFile, 메모리 byte 배열 정리 구조를 재사용했습니다. 키 alias·파일·AAD는 HA 전용입니다.
- Gradle wrapper, APK/RPK 서명·검증 도구 구조. 공개 인증서만 저장소에 넣고 새 HA 개인키로 두 앱을 서명합니다.

TOTP의 `SyncSession`은 계정 하나씩 저장 확인하는 구조입니다. 본 앱은 같은 hello/ready, 요청 ID·epoch·ACK 원칙을 유지하면서 **전체 설정 begin → chunk → commit**으로 확장했습니다. 전송 전에 전체 크기를 제한하고, 저장 완료를 별도 단계로 확인합니다. CRC32는 전송 손상 검사이며 인증·암호화를 대체하지 않습니다.

## 1006과 실기기 검증 구분

원본 `docs/xms-reply-1006.md`는 중국판 펌웨어 3.100.043에서 phone → watch 수신은 되지만 watch → phone 응답이 1006으로 실패한 보고를 기록합니다. 등록용 Binder 누락과 슬롯 1 ACK 처리를 고쳤고 Robolectric 회귀 테스트는 통과했지만, 문서는 **수정 후 실기기 왕복 성공을 확정하지 않습니다.**

따라서 본 앱에서도 코드 회귀 통과만으로 Mi Fitness/워치 연결 성공을 주장하지 않습니다. 등록 순서·Parcel Proxy/Stub·역방향 ACK·등록 실패·재연결 후 과거 콜백 무시를 Android 테스트에서 확인합니다. 실제 왕복과 영구 저장은 현장에서 확인해야 합니다.

## 새 식별자

| 항목 | 기존 TOTP | 새 HA 앱 |
| --- | --- | --- |
| APK/RPK package | `com.vela.rw6totp` | `com.vela.rw6ha` |
| Android key alias | `rw6_totp_vault_v1` | `rw6_ha_config_v1` |
| Android vault | `accounts.v1` | `ha-config.v1` |
| XMS protocol | `rw6-totp-*` | `ha-*`, version 1 |
| 워치 저장소 | 기존 계정 키 | `ha.config.a`, `.b`, `.active` |
| 서명 | 원본 TOTP 인증서 | `certificates/release.pem`의 새 HA 인증서 |

HA APK와 RPK는 XMS 조건에 맞춰 서로 같은 패키지와 인증서를 사용합니다. TOTP와 서명을 일치시킬 필요는 없습니다.
