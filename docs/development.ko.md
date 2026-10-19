# 개발·서명·배포

## 구조와 제한

- `android/app`: 설정 모델, Keystore vault, foreground HaApi, XMS Binder client, ACK 동기화 세션, 한국어 native UI.
- `EntityCatalog`: `/api/states`를 JsonReader로 순차 파싱하고 ID·이름·도메인만 보관. 상태·추가 속성은 skip. gzip을 풀어 최대 128 MiB/50,000개까지 처리하고, 대량 조회 읽기 타임아웃 30초·전체 읽기 제한 90초 적용. 단건 연결 테스트는 기존 2 MiB/12초 제한 유지. 전체 조회가 실패하면 기존 목록을 유지. 화면은 7개 도메인 중 하나만 펼치고 50개씩 탐색하며 전체 검색·선택 상태를 보존.
- `android/xms-contract`: 원본 공개 AIDL/Parcelable 계약. MIT 고지 보존.
- `src/common/ha-client.js`: 워치 fetch, 12초 타임아웃, HTTP 분류, 안전한 오류, 요청별 결과, close 시 미완료 Promise 취소. Android 호출 경로 없음.
- `entities.js`: 지원 기능 기반 서비스 매핑. 센서는 read-only. POST 이후 최대 3번 상태 확인, 700ms 간격. 비동기 장면/스크립트는 실행 시각 변경 증거가 없으면 미확인 표시. 시계 차이도 미확인 원인이 될 수 있음.
- 워치 메인 목록은 로컬 설정만 읽고 네트워크 클라이언트·상태 캐시·폴링·제어 버튼을 생성하지 않음. 전체 카드 터치가 entityId를 상세화면으로 전달. 48px 아이콘·실제 종류 라벨·두 줄 이름으로 구분하고 ASCII 이름 정규화/대체 표시는 Android 선택목록 미리보기와 같은 규칙을 사용.
- 상세화면에서 진입 시 상태 조회, 기존 제어/실제 상태 확인, 설정한 30/60/120초 폴링을 수행. 숨김/종료 시 클라이언트와 타이머를 정리하고 epoch가 다른 늦은 응답을 무시. Android 상주 중계와 서버 푸시 연결은 추가하지 않음.
- Android는 엔티티 카탈로그의 도메인별 선택 개수를 config 전체에서 집계 (검색/페이지와 무관). 선택목록 탭은 카탈로그 재조회 없이 로컬 config의 전체 항목을 검토·검색·도메인/즐겨찾기 필터·편집·순서 변경·삭제. 순서는 전체 config 기준이며 성공한 저장만 UI·선택 개수에 반영.
- `sync-receiver.js`: `ha-hello/ready`, `ha-begin/chunk/commit`, `ha-ack`, protocol 1, 128비트 랜덤 요청 ID·transfer ID, 최대 메시지 2048바이트, 768바이트 청크(Base64 1024자), 최대 64개 청크/48 KiB, 90초 수신 유휴 만료. CRC32는 손상 검사이며 암호학적 인증이 아님.
- Android는 같은 요청 ID로 최대 3번 전송, 단계별 5초/commit 15초 제한. hello는 1초 간격 최대 15번이며 준비 응답 전에는 토큰을 보내지 않음. XMS 연결 준비 제한은 30초.
- `config-store.js`: inactive 슬롯 set → 동일 데이터 get 확인 → active 인덱스 set/get 확인 후 ACK. 정상 active가 있으면 staging은 채택하지 않음. active 손상 시 이전 슬롯 복원. 실제 전원 장애 원자성은 native storage 구현에 의존하므로 실기기 검증 필요.
- revision은 Android 현재 밀리초 또는 이전 값+1 중 큰 값. 낮은 버전/같은 버전 다른 내용 거부. 같은 버전·같은 checksum은 ACK 유실 후 새 세션으로 재시도 가능. 설정은 전체 교체이므로 삭제가 반영됨.
- 자격 증명은 Android no-backup 파일에서 AES-256-GCM으로 보호. 워치는 일반 storage 두 슬롯에 저장. 삭제는 두 슬롯과 인덱스를 비우지만 물리적 플래시 복구 불가를 보장하지 않음.
- 확장 도메인은 Android `Config.DOMAINS`, 워치 `config.js`의 DOMAINS와 `entities.js` 명령·검증을 함께 추가. climate/number/select/input_boolean은 이번 버전에서 미지원.

## 일반 빌드

Node 24, JDK 21, SDK platform android-35와 Build-Tools 35.0.0이 필요합니다. Gradle 8.13 wrapper는 SHA256으로 배포본을 검증합니다.

```sh
npm ci
npm test
npm run build
npm run verify:rpk -- dist/com.vela.rw6ha.debug.0.1.1.rpk
npm run smoke:rpk -- dist/com.vela.rw6ha.debug.0.1.1.rpk
cd android
./gradlew --no-daemon :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
cd ..
npm run test:interop
```

Android 결과: `android/app/build/outputs/apk/release/app-release-unsigned.apk`. 테스트 보고서: `android/app/build/reports/`. RPK debug는 toolkit 기본 개발 키이므로 릴리즈 APK와 섞어 설치하지 마세요.

클라우드 전용 도구 준비는 `python3 scripts/setup-cloud-android.py`입니다. `/workspace/tools`, `/workspace/android-sdk`에 SHA256 검증된 SDK/JDK를 놓습니다. 지정한 전용 Gradle home의 proxy만 설정하며 애플리케이션의 인증서 검증을 바꾸지 않습니다. 해당 환경에서 JDK truststore는 정상 시스템 CA store를 사용해야 합니다. 네트워크 정책을 우회하거나 TLS 검증을 끄지 마세요.

## 릴리즈 서명

새 HA 서명 키는 `sign/release/private.pem`, 인증서는 `sign/release/certificate.pem`입니다. 해당 디렉터리는 gitignore되며 **개인키는 소스·릴리즈·Actions 아티팩트에 넣지 않습니다.** 공개 인증서만 `certificates/release.pem`에 커밋합니다.

초기 v0.1.0에서 생성한 새 키를 이후에도 보존해야 Android 업데이트와 XMS 호환이 유지됩니다. **작업 공간이 사라지기 전에 이 두 파일을 안전한 키 저장소에 백업하고, 필요하면 아래 GitHub Secrets로 등록하세요.** 기존 TOTP 키로 덮어쓰지 마세요. 키를 잃으면 현재 설치에 대한 정상 업데이트 서명을 복원할 수 없습니다.

로컬에서 기존 HA 키를 복원한 뒤:

```sh
npm run release
bash scripts/sign-apk.sh
npm run verify:rpk -- dist/com.vela.rw6ha.release.0.1.1.rpk
npm run smoke:rpk -- dist/com.vela.rw6ha.release.0.1.1.rpk
npm run verify:apk -- dist/rw6-ha-android-0.1.1.apk
```

`ANDROID_HOME` 또는 `ANDROID_SDK_ROOT`를 설정하세요. `sign-apk.sh`는 동일 PEM 인증서를 임시 PKCS12로 변환하여 zipalign과 apksigner v2/v3 서명 후 검증합니다. 임시 비밀번호·PKCS12는 작업 종료 시 제거합니다.

## GitHub Actions

- `ci.yml`: main push/PR에서 JS 테스트, RPK 자동 빌드·컴파일 코드 smoke, Android APK 자동 빌드·테스트·Lint, Java/워치 interop, 배포 파일의 서명·체크섬 검사. 업로드되는 unsigned APK/debug RPK는 개발 검증용입니다.
- `release.yml`: 버전 태그 push 시 두 앱을 빌드·테스트하고 GitHub Release로 게시. `releases/<tag>`에 사전 서명된 파일이 있으면 APK/RPK 쌍·SHA256 전체를 요구하고 검증. 파일 없이 NOTES.md만 있으면 GitHub Secrets의 기존 키로 빌드·서명하고 SHA256을 생성한 뒤 인증서·버전·compiled smoke를 검증. 키 누락·불일치 시 게시를 중단하며 개인키는 종료 시 제거. 기존 릴리즈는 덮어쓰지 않으며 하드웨어 미검증 사전 릴리즈로 표시.
- `rebuild-signed.yml`: 수동 실행 또는 main의 서명 스크립트/해당 워크플로 변경 시 GitHub Secrets로 두 앱을 빌드·서명하여 `signed-installable-pair` 아티팩트를 생성합니다. GitHub Release를 게시하지 않고 키 등록을 검증할 수 있습니다. 개인키는 아티팩트에 포함하지 않으며 마지막 단계에서 삭제합니다.

Secrets 이름:

| 이름 | 값 |
| --- | --- |
| `HA_RELEASE_PRIVATE_KEY_PEM` | 새 HA `sign/release/private.pem` 전체 PEM |
| `HA_RELEASE_CERTIFICATE_PEM` | 같은 `sign/release/certificate.pem` 전체 PEM |

[저장소 Settings → Secrets and variables → Actions](https://github.com/ug30242/homeassistant_rw6/settings/secrets/actions)에서 `New repository secret`으로 두 항목을 추가하고 각 PEM 파일의 전체 내용을 값에 붙여 넣습니다. `load-signing.cjs`는 Secrets 인증서가 커밋된 공개 인증서와 일치하는지, 개인키가 해당 공개키와 짝인지 검사한 후에만 서명에 사용합니다. 개인키 값은 로그에 표시하지 않습니다.

등록 후 [Actions의 Rebuild signed packages from Secrets](https://github.com/ug30242/homeassistant_rw6/actions/workflows/rebuild-signed.yml) → `Run workflow`에서 main을 선택해 실행하면 키 유효성과 실제 APK/RPK 서명을 확인할 수 있습니다. 서명 스크립트나 해당 워크플로가 변경된 main push에서도 자동 검증합니다. 릴리즈 워크플로는 게시할 APK/RPK·체크섬·고지·설명 파일만 `signed-release-vX.Y.Z` 아티팩트에도 남기므로 서명 키를 포함하지 않고 검증 결과를 받을 수 있습니다.

Secrets 등록 후에도 복구를 위한 별도의 안전한 키 백업을 보관하세요.

버전 변경 시 `package.json`·`package-lock.json` version과 `src/manifest.json` versionName/versionCode를 함께 갱신하고, 같은 소스에서 새 쌍을 빌드·검증한 뒤 `releases/vX.Y.Z`에 설치 파일·NOTES·THIRD_PARTY_NOTICES·SHA256SUMS를 커밋합니다. GitHub Secrets로 CI에서 서명할 때는 NOTES.md만 커밋하고 설치 파일 생성·검증은 태그 릴리즈 워크플로에서 수행할 수 있습니다. main push 후 같은 커밋에 `vX.Y.Z` 태그를 push하면 릴리즈 워크플로가 동작합니다. 개인키나 실제 Home Assistant 토큰을 커밋하지 마세요.

기존 태그를 다시 게시하려면 `releases/vX.Y.Z/PUBLISH`의 첫 줄에 해당 태그의 전체 커밋 SHA를 기록해 main에 푸시할 수 있습니다. 현재 package.json 버전의 PUBLISH 변경만 사용하며, 워크플로는 SHA 일치를 검증한 뒤 그 태그의 소스를 체크아웃해 빌드합니다. 추가 줄에 재시도 사유를 기록해 다시 요청할 수 있습니다. 기존 태그나 게시된 릴리즈는 덮어쓰지 않습니다. 수동 Run workflow와 태그 push도 계속 지원합니다.
