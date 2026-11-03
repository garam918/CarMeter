# Hyundai Hackathon · Pleos 개발 환경

Pleos Connect용 3rd-party 앱 CarMeter(내 차 사용 고지서)의 Kotlin 프로젝트입니다. MVP 마일스톤 M0~M5를 마쳤습니다. 백그라운드 수집(공조·시트·열선·조명·와이퍼·크루즈·창문·문·방향지시등·주행 상태), 트립과 기록률 계산, 월간 고지서 화면(주행 중 간략 모드 포함), 첫 실행 동의와 설정(전체·항목별 기록 on/off, 모든 기록 삭제)이 있습니다. 동의 전에는 수집하지 않습니다. `demo` 플레이버에는 공모전 시연용 합성 데이터 생성기가 있고, 제출용 `prod`에는 없습니다. debug 빌드는 고지서 하단의 "수집 확인"으로 원시 기록을 볼 수 있습니다. 부팅 자동 시작은 debug 빌드에만 있고 기본값은 꺼짐이며, 확인 화면의 토글로 켭니다.

| 문서 | 내용 |
| --- | --- |
| `CLAUDE.md` | 지침, 진행 상황, 완료 기준 증빙, 주최 측 확인 필요 항목 |
| `docs/sdk-notes.md` | SDK 조사(클래스·메서드·퍼미션)와 마일스톤별 에뮬레이터 실측 |
| `docs/implementation-plan.md` | 구현 계획과 마일스톤별로 정한 규칙 |
| `docs/demo-script.md` | 공모전 시연 대본 |
| `docs/performance.md` | CPU·RAM·실행 시간 측정 방법과 결과 |

## 구성

| 항목 | 설정 |
| --- | --- |
| 개발 환경 | macOS · Apple Silicon · Android Studio |
| Java | 17 |
| Gradle Wrapper | 8.14.3 · 배포 파일 SHA-256 검증 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.3.21 |
| compileSdk / targetSdk | 36 / 34 (compileSdk는 AndroidX 요구, 동작 기준은 targetSdk 34) |
| minSdk | 28 |
| Android Build Tools | 35.0.0 |
| Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| UI / DB | Jetpack Compose (BOM 2025.12.01), Room 2.8.4 + KSP 2.3.13 |
| 빌드 변형 | `prod`(제출용) / `demo`(합성 데이터 생성기 포함) × `debug` / `release`(R8 축소) |
| Pleos 시스템 이미지 | Connect 2.1.0 · API 34 · ARM64 |
| AVD 이름  | `Pleos_Connect_2_1_0_API_34` |
| AVD 실행 설정 | RAM 4GB · Cold boot |

`settings.gradle.kts`에 Pleos Maven 저장소를 등록했고, 매니페스트에는 Automotive 기기 요구사항과 시작 가이드의 `CAR_DOORS` 권한을 선언했습니다.

시작 가이드에 있는 Vehicle 2.3.1은 확인 시 저장소에서 404를 반환했습니다. 상세 SDK 설치 문서와 공식 Maven 메타데이터에 공개된 2.3.2를 사용합니다.

## Android Studio

이 저장소 폴더를 Open으로 열고 Gradle Sync를 실행합니다. 프로젝트의 `gradle/gradle-daemon-jvm.properties`가 Gradle용 Java 17을 지정합니다. IDE에 포함된 JBR은 그대로 사용합니다.

로컬 Android SDK 경로는 Git에서 제외한 `local.properties`에 설정되어 있습니다. 다른 Mac으로 옮기면 SDK 경로를 다시 지정하세요.

## 터미널 실행

프로젝트 루트에서 실행합니다.

```sh
source scripts/env.sh
./gradlew :app:assembleProdDebug
./gradlew :app:testProdDebugUnitTest :app:testDemoDebugUnitTest
./gradlew :app:lintProdDebug
./gradlew :app:assembleProdRelease -PdebugSignedRelease   # 로컬 설치용, 배포는 정식 인증서로
```

APK: `app/build/outputs/apk/<prod|demo>/<debug|release>/`

시연용 데모 빌드는 `./gradlew :app:assembleDemoDebug`로 만들고, 순서는 `docs/demo-script.md`를 따릅니다.

성능 측정은 `prodRelease`를 설치하고 동의한 뒤 `adb forward tcp:8088 tcp:8088`을 해 두고 실행합니다. 약 8분 걸리고, 결과 표를 출력합니다.

```sh
./scripts/measure-performance.sh
```

에뮬레이터를 실행합니다. 스크립트는 매번 RAM 4GB와 Cold boot 옵션을 적용합니다.

```sh
./scripts/start-emulator.sh
```

에뮬레이터 부팅 후 다른 터미널에서 앱을 설치하고 실행합니다.

```sh
source scripts/env.sh
./gradlew :app:assembleProdDebug
adb install --user current -r app/build/outputs/apk/prod/debug/app-prod-debug.apk
adb shell am start --user current -n com.example.hyundaihackathon/.ui.MainActivity
```

`installDebug`는 헤드리스 시스템 유저(0)에도 앱을 설치하므로, 드라이버 유저에만 설치하는 `adb install --user current`를 씁니다.

Virtual Console은 `adb forward tcp:8088 tcp:8088` 후 브라우저에서 http://127.0.0.1:8088 을 엽니다.

Android Studio에서는 Device Manager에서 위 AVD를 실행한 다음 `app` 구성으로 Run하면 됩니다.

## 시스템 이미지 저장소

Android Studio의 **SDK Manager → SDK Update Sites**에 다음 저장소를 등록합니다.

- Name: `Pleos Connect System Image`
- URL: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
- Package: `system-images;android-34;pleos_car_emulator;arm64-v8a`

이 Mac에는 최신 Command-line Tools와, Pleos 사용자 지정 저장소를 조회할 수 있는 Command-line Tools 19.0을 함께 설치했습니다. 재설치할 때는 SDK Manager UI 또는 아래 명령을 사용합니다. 설치 과정에서 SDK 라이선스가 표시됩니다.

```sh
source scripts/env.sh
"$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" \
  --sdk_root="$ANDROID_HOME" \
  'system-images;android-34;pleos_car_emulator;arm64-v8a'
```

이미지 압축 파일은 약 4.67GB, 압축 해제 후 크기는 약 20GB입니다. AVD 데이터 영역과 임시 파일까지 고려해 설치 시작 전 여유 공간 30GB 이상을 권장합니다.

## 검증 결과 · 2026-10-28

- `:app:assembleDebug` 성공: Vehicle SDK를 포함한 디버그 APK 생성.
- `:app:lintDebug` 성공: 오류 0건. 최신 버전 권고, 백업 설정, 앱 아이콘에 관한 경고 5건은 기본 프로젝트에 남아 있습니다.
- Pleos AVD 부팅 완료: `sys.boot_completed=1`, API 34, Automotive 기능과 VHAL 서비스 확인.
- 기본 APK 설치 및 실행 성공: `MainActivity`가 차량 앱 영역에 표시되는 것까지 확인.
- Android Studio에서 프로젝트 동기화 완료.

현재 앱은 환경 검증용 기본 화면이며 차량 API 호출 검증은 이후 앱 개발 단계에서 진행합니다. AVD 첫 실행 검증에서는 호스트 마이크 권한이 필요하지 않도록 `-no-audio` 옵션을 사용했습니다. 일반 실행 스크립트는 해당 옵션을 강제하지 않습니다.

## 공식 문서

- [앱 개발 시작하기](https://document.pleos.ai/docs/pleos-only/getting-started)
- [Pleos 에뮬레이터 설치 및 실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Vehicle SDK 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
- [프로젝트 생성 및 SDK 사용 신청](https://document.pleos.ai/docs/pleos-only/create-my-project)

SDK 서버 인증이나 앱 등록이 필요해지는 단계에서는 Pleos Playground 계정과 Workspace 프로젝트를 별도로 준비합니다. 현재 기본 프로젝트에는 계정 정보나 API 키가 없습니다.
