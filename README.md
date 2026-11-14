# CarMeter · 내 차 사용 고지서

아파트 관리비 고지서처럼, 내가 차량 기능을 얼마나 쓰는지 보여주는 Pleos Connect 앱입니다. 시트 열선·통풍, 에어컨, 와이퍼, 크루즈, 창문 같은 기능을 켜고 끈 시간을 백그라운드에서 기록하고, 일간·주간·월간 고지서로 정리합니다. 많이 쓴 기능, 지난달 대비 변화, 지원되지만 이번 달 한 번도 안 쓴 기능을 한눈에 볼 수 있습니다.

현대자동차그룹 차량용 앱 개발 공모전 출품작입니다.

## 제출 정보

| 항목 | 내용 |
| --- | --- |
| 앱 이름 | CarMeter (내 차 사용 고지서) |
| 팀명 | 임가람 (개인 참가) |
| 패키지 | `com.garam.hackathon` |
| 버전 | 1.0.0 (versionCode 5) |
| 릴리스 APK | [`apk/임가람_CarMeter_release.apk`](apk/) (`prodRelease`, 배포용 인증서로 서명, R8 축소) |
| 타깃 | Pleos Connect (Android Automotive OS), minSdk 28 · targetSdk 34 |

## 주요 기능

- **백그라운드 기록:** Vehicle SDK의 상태 변화 리스너로만 기록합니다(폴링 없음). 앱을 화면에서 내려도 포그라운드 서비스(`specialUse`)가 계속 기록하고, 프로세스가 강제 종료되면 마지막 생존 시각으로 열린 구간을 닫고 다시 시작합니다.
- **고지서:** 일간·주간·월간 보기, 기능별 사용 시간과 횟수, 지난 기간 대비 증감, 많이 쓴 기능 Top 5, 안 쓴 기능, 한 줄 팁, 기록률(앱이 기록한 주행 ÷ 차량 기준 주행).
- **차종별 자동 구성:** 기능별 지원 여부(capability)를 확인해 저장하고, 차량이 지원하지 않는 기능은 고지서에 나오지 않습니다.
- **주행 안전:** 주행 중(UX 제한 시)에는 이번 달 값 3개만 큰 글씨로 보여주고 스크롤·버튼을 막습니다. 정차하면 보던 화면으로 돌아갑니다.
- **개인정보:** 첫 실행 때 무엇을 기록하는지 알리고 동의를 받습니다. 동의 전에는 기록하지 않습니다. 네트워크를 쓰지 않아 기록은 차 안에만 남고, 13개월이 지난 기록은 지웁니다. 설정에서 항목별 기록 끄기, 모든 기록 지우기, 동의 철회를 할 수 있습니다.
- **화면:** 세로(창 1개)·가로(창 2개)에 맞춘 반응형 배치, 시스템 주·야간을 따르는 라이트·다크 테마.

차량 제어(set) API는 쓰지 않습니다. 조회 퍼미션만 선언합니다: `CAR_CLIMATE`, `CAR_SEATS`, `CAR_MIRRORS`, `CAR_WINDOWS`, `CAR_LIGHTS`, `CAR_DRIVE`, `CAR_DOORS`, `CAR_ENERGY`.

## 테스트 환경

| 항목 | 내용 |
| --- | --- |
| 에뮬레이터 | Pleos Connect 2.1.0 시스템 이미지 (`system-images;android-34;pleos_car_emulator;arm64-v8a`), Android 14 (API 34) |
| AVD | `Pleos_Connect_2_1_0_API_34`, RAM 4GB, Cold boot |
| Android Emulator | 37.1.11.0 |
| 기본 디스플레이 | 2560×1440, 160dpi (Connect-S 17"), 세로 방향. 앱 창은 Driving view 오른쪽 영역 |
| 디스플레이 프리셋 | FormFactorConsole의 6종(Connect-S 17"·14.6"·12.9", Connect-WS·WE·WF) × 세로·가로 12가지 크기에서 배치 확인 (`docs/sdk-notes.md` 15절) |
| 차량 상태 입력 | Virtual Console (시트·공조·창문·방향지시등·기어·차속) |
| Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| 호스트 | macOS, Apple Silicon, JDK 17 |

## 실행 방법

### 릴리스 APK로 실행

1. Pleos 에뮬레이터를 켭니다. Android Studio의 Device Manager에서 실행하거나, 이 저장소의 스크립트를 씁니다. 호스트 GPU에서 부팅 직후 꺼지면 `-gpu swiftshader_indirect`를 붙입니다.

   ```sh
   ./scripts/start-emulator.sh
   ```

2. APK를 드라이버 유저에 설치하고 실행합니다. 런처(GNB)의 CarMeter 아이콘으로 열어도 됩니다.

   ```sh
   adb install --user current -r apk/임가람_CarMeter_release.apk
   adb shell am start --user current -n com.garam.hackathon/.ui.MainActivity
   ```

3. 첫 화면에서 "동의하고 시작"을 누르고 알림을 허용합니다.
4. Virtual Console을 엽니다. `adb forward tcp:8088 tcp:8088` 후 브라우저에서 http://127.0.0.1:8088 을 엽니다. 시트 열선·통풍, A/C, 창문, 방향지시등을 바꾸면 앱이 화면에 없어도 기록되고, "일간" 탭에 바로 나타납니다.
5. Virtual Console에서 기어를 D로 바꾸면 주행 중 요약(값 3개)으로 바뀌고, P로 돌아오면 전체 고지서가 다시 나옵니다.

릴리스 APK에는 합성 데이터가 없어 처음에는 고지서가 비어 있습니다. 두 달치 합성 데이터로 채운 고지서는 `demo` 빌드에서 볼 수 있습니다(아래 빌드 명령, 순서는 `docs/demo-script.md`).

`installDebug`나 사용자 지정 없는 `adb install`은 헤드리스 시스템 유저(0)에도 설치하므로, 드라이버 유저에만 설치하는 `--user current`를 씁니다.

### 소스에서 빌드

```sh
source scripts/env.sh
./gradlew :app:assembleProdDebug                                  # 개발용
./gradlew :app:assembleDemoDebug                                  # 시연용 (합성 데이터 생성기 포함)
./gradlew :app:testProdDebugUnitTest :app:testDemoDebugUnitTest   # JVM 단위 테스트
./gradlew :app:lintProdDebug
```

APK 위치: `app/build/outputs/apk/<prod|demo>/<debug|release>/`

| 빌드 변형 | 용도 |
| --- | --- |
| `prodRelease` | 제출·배포용. 합성 데이터·디버그 화면·부팅 리시버 없음, `debuggable=false`, R8 축소, 상세 로그 제거 |
| `prodDebug` | 개발용. 하단 "수집 확인"에서 원시 기록 확인, 부팅 자동 시작 토글(기본 꺼짐) |
| `demoDebug` / `demoRelease` | 공모전 시연용. 시드를 고정한 두 달치 합성 데이터(겨울로 갈수록 열선 사용 증가) |

### 릴리스 서명

서명 키와 비밀번호는 저장소 밖(`~/.carmeter-signing`)에 둡니다. 처음 한 번 키를 만들고, 팀명을 넘겨 제출용 APK를 만듭니다. 앱 이름은 `gradle.properties`의 `submission.app`(CarMeter)을 씁니다.

```sh
./scripts/create-release-key.sh   # 비밀번호를 입력해 키 생성 (한 번만)
./gradlew :app:submissionApk -Psubmission.team=임가람   # prodRelease를 서명해 apk/임가람_CarMeter_release.apk로 복사
```

키가 없으면 릴리스 빌드는 서명 없이 만들어지고 `submissionApk`는 실패합니다. 다른 위치의 키는 `-Pcarmeter.signing=<keystore.properties 경로>`로 지정합니다. 키를 잃으면 같은 앱으로 업데이트할 수 없으니 `~/.carmeter-signing` 폴더를 따로 백업하세요. `-PdebugSignedRelease`는 키가 없는 PC에서 성능 측정용으로 디버그 키를 쓰는 옵션이고, 제출용으로는 쓰지 않습니다.

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| 단위 테스트 | `prod` 110개, `demo` 118개 통과 (구간 집계, 하트비트 복구, 월 경계, 기록률 등) |
| 성능 (`prodRelease`) | 주행 중 CPU 평균 0.03%, 정차 중 최악 5.73%, PSS 최대 57MB, 콜드 실행 178ms 이하 (`docs/performance.md`) |
| 백그라운드 | 다른 앱 5개를 띄워도 같은 프로세스·서비스 유지, 강제 종료 후 재시작·복구 확인 |
| 주행 중 화면 | 기어 D에서 모든 화면이 값 3개 요약으로 바뀜, 스크롤·버튼 없음 |
| 디스플레이 프리셋 | 12가지 크기에서 잘림·겹침 없음 |

수치는 모두 에뮬레이터 실측입니다. 실차 측정은 하지 않았습니다.

## 문서

| 문서 | 내용 |
| --- | --- |
| `docs/sdk-notes.md` | SDK 조사(클래스·메서드·퍼미션)와 마일스톤별 에뮬레이터 실측 |
| `docs/implementation-plan.md` | 구현 계획과 집계·화면 규칙 |
| `docs/performance.md` | CPU·RAM·실행 시간 측정 방법과 결과 (`scripts/measure-performance.sh`) |
| `docs/demo-script.md` | 공모전 시연 대본 |
| `CLAUDE.md` | 개발 지침, 진행 상황, 완료 기준 증빙, 주최 측 확인 필요 항목 |

## 개발 환경

| 항목 | 설정 |
| --- | --- |
| Java | 17 (`gradle/gradle-daemon-jvm.properties`가 Gradle용 JDK 17 지정) |
| Gradle Wrapper | 8.14.3 (배포 파일 SHA-256 검증) |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.3.21 |
| compileSdk / targetSdk / minSdk | 36 / 34 / 28 (compileSdk는 AndroidX 요구, 동작 기준은 targetSdk 34) |
| Android Build Tools | 35.0.0 |
| UI / DB | Jetpack Compose (BOM 2025.12.01), Room 2.8.4 + KSP 2.3.13 |
| DI | 수동 DI (`AppContainer`) |

Android Studio에서는 이 폴더를 Open으로 열고 Gradle Sync를 실행합니다. 로컬 Android SDK 경로는 Git에서 제외한 `local.properties`에 둡니다.

`settings.gradle.kts`에 Pleos Maven 저장소를 등록했습니다. 시작 가이드의 Vehicle 2.3.1은 저장소에서 404를 반환해, 상세 SDK 설치 문서와 Maven 메타데이터에 공개된 2.3.2를 씁니다.

스토어 등록용 512px 아이콘(`docs/store/icon-512.png`)은 `java -Djava.awt.headless=true scripts/IconRenderer.java`로 다시 만듭니다. 앱 안의 아이콘은 같은 도형의 벡터(`res/drawable/ic_launcher_foreground.xml`)입니다.

### Pleos 시스템 이미지 설치

Android Studio의 **SDK Manager → SDK Update Sites**에 다음 저장소를 등록합니다.

- Name: `Pleos Connect System Image`
- URL: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
- Package: `system-images;android-34;pleos_car_emulator;arm64-v8a`

명령줄에서는 Pleos 사용자 지정 저장소를 조회할 수 있는 Command-line Tools 19.0으로 설치합니다. 설치 과정에서 SDK 라이선스가 표시됩니다.

```sh
source scripts/env.sh
"$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" \
  --sdk_root="$ANDROID_HOME" \
  'system-images;android-34;pleos_car_emulator;arm64-v8a'
```

이미지 압축 파일은 약 4.67GB, 압축 해제 후 약 20GB입니다. AVD 데이터와 임시 파일까지 고려해 여유 공간 30GB 이상을 권장합니다.

## 공식 문서

- [앱 개발 시작하기](https://document.pleos.ai/docs/pleos-only/getting-started)
- [Pleos 에뮬레이터 설치 및 실행](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Virtual Console 사용](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [Vehicle SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)
