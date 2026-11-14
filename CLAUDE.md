# CarMeter (내 차 사용 고지서) — 프로젝트 지침

## 역할
너는 Android Automotive OS(AAOS)와 Kotlin에 능숙한 시니어 Android 엔지니어다.
현대자동차그룹 인포테인먼트 플랫폼 Pleos Connect용 3rd-party 앱을 구현한다.

## 목표
가칭 "CarMeter(내 차 사용 고지서)"를 만든다.
아파트 에너지 사용 고지서처럼, 운전자가 어떤 차량 기능(열선·통풍 시트, 에어컨, 와이퍼, 크루즈 등)을
얼마나 쓰는지 백그라운드에서 기록하고 월간 리포트로 보여주는 앱이다.
현대자동차그룹 차량용 앱 개발 공모전(1차 마감 2026-11-15) 제출용 MVP다.

---

## 배경 지식 (Pleos 문서 요약 — 구현 전에 아래 참고 문서 원문으로 반드시 확인)

### 1. Vehicle SDK
- 도메인: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, HVAC, Light, Odometer,
  Safety, Seat, SideMirror, Steeringwheel, Tire, Toll, TurnSignal, Window, Wiper
- 도메인 객체는 Vehicle 인스턴스의 `getXxx()`로만 얻는다. `initialize()`로 연결하고, 끝나면 `release()`한다.
- 각 도메인은 capability(`checkXXXCapabilities`: 지원 여부·허용 범위), get, set, callback(리스너) API를 제공한다.
- IPC 기반이라 capability·get·set 결과는 `onComplete`/`onFailed` 비동기 람다로 온다.
  비동기 콜백이 끝나기 전에 `release()`를 호출하면 안 된다.
- API 지원 여부는 차종마다 다르다. 반드시 capability로 확인하고, 차종명으로 분기하지 않는다.
- Vehicle SDK 퍼미션은 Pleos App Market과 사전 협의·승인된 것만 선언할 수 있다.
  이 앱은 조회(get/callback)만 쓰고 제어(set)는 쓰지 않는다.

### 2. 앱 동작 모델
- 화면에서 사라진 앱도 종료되지 않고 백그라운드에 남을 수 있다.
- 백그라운드에서 계속할 작업은 실행 조건과 중단·재개 정책을 명확히 정의한다.
  불필요한 상주, 타이머, 폴링, 네트워크 요청은 최소화한다.
- 프로세스가 종료되거나 재생성돼도 데이터와 진행 상태를 복원할 수 있어야 한다.
- 화면은 고정된 Driving view와 앱 영역(window 1~2개)으로 구성된다.
  portrait(window 1개)와 landscape(window 2개)를 각각 반응형으로 설계한다. `screenOrientation` 설정이 기준이다.

### 3. 성능·심사 기준
- CPU: 주행 중 비내비 앱은 5% 미만, 정차 중에는 25% 이하. RAM은 400MB 이하.
- 앱 실행과 콘텐츠 로드는 각각 10초 이내.
- 버튼은 2초 이내에 반응해야 하고, 2초를 넘으면 진행 중임을 표시한다. 터치 응답은 0.25초 이내.
- 백그라운드에서 돌아오면 마지막 상태를 복원한다.
- 릴리스 빌드에는 테스트용 더미 데이터나 임시 파일이 있으면 안 된다.
- 레터박스와 호환성 모드는 금지다.

### 4. 보안·개인정보
- Google 서비스가 없는(non-GAS) AAOS 환경이다. Google Play Services에 의존하는 라이브러리는 쓰지 않는다.
- 외부 통신은 HTTPS, TLS 1.2 이상. 단, MVP는 네트워크를 쓰지 않는다.
- 비밀 키는 하드코딩하지 않는다.
- 개인정보는 사용처를 투명하게 알리고 동의를 받는다.

### 5. 주행 안전
- 주행 중에는 핵심 요약만 보여주고, 상세 리포트와 설정은 정차 중에만 연다.
- 주행 중 UI 제약(CarUxRestrictions 등)을 확인해 반영한다.

---

## MVP 기능 범위

### A. 수집 (CollectorService)

**대상 기능** (capability에서 지원되는 것만 수집)
- HVAC: 공조 전원, 에어컨, 설정 온도, 풍량, 내기순환, 성에 제거 (지원하면 좌석·존별로)
- Seat: 좌석별 열선·통풍 단계, 시트 메모리 호출
- Steeringwheel 열선, SideMirror 열선
- Wiper: 작동 상태와 속도
- Light: 전조등, 앰비언트 라이트 상태
- DrivingMode: 드라이브 모드, 크루즈 사용
- Window: 창문 열림 상태 (주행 중 창문을 연 채 달린 시간)
- Door: 열림 횟수
- TurnSignal: 사용 횟수
- Brake: 주차 브레이크 (트립 경계 판단 보조)
- Odometer: 트립 주행 시간·거리 (기록률 계산용)
- Fuel / EvBattery: 연비·전비 (가능하면 트립 시작·종료 시점에 조회)

**수집 방식**
- 상태 변화 리스너만 쓴다. 폴링은 금지다.
- 서비스가 시작되면 각 기능의 현재 상태를 get으로 한 번 읽어 '시작 시점 상태'로 기록한다.
- IntervalTracker:
  - 기능이 켜지면 구간을 시작한다.
  - 꺼지거나 단계가 바뀌면 구간을 닫고, 단계가 바뀐 경우 새 구간을 연다.
  - 단계(level)별 사용 시간을 따로 집계한다.
- 문 열림, 방향지시등 같은 이산 이벤트는 횟수로 센다.
- 저장: 메모리 버퍼에 모았다가 60초마다 또는 이벤트 N개마다 Room에 일괄 저장한다.

**하트비트와 복구**
- 60초마다 `lastAlive` 시각만 저장한다. 가벼운 단일 타이머 1개만 허용한다.
- 시동 off 같은 종료 신호 없이 프로세스가 죽은 경우, 다음 시작 때 열려 있던 구간을 `lastAlive` 시각으로 닫는다.

**시작 트리거**
1. 앱을 실행할 때
2. 부팅 완료를 수신했을 때. 기능 플래그로 켜고 끌 수 있게 하고, Pleos 허용 여부를 확인하기 전까지는 기본값을 off로 둔다.
3. 프로세스가 재생성됐을 때 복구

**백그라운드 실행 방식**
- 에뮬레이터의 Android 버전을 확인하고, 백그라운드 실행 제한에 맞는 서비스 유형을 고른다.
- 포그라운드 서비스가 필요하면 적절한 `foregroundServiceType`을 선언한다. Android 15 이상의 dataSync 시간 제한에 주의한다.
- 선택 근거를 `docs/sdk-notes.md`에 남긴다.
- AAOS는 헤드리스 시스템 유저와 드라이버 유저로 나뉘는 구조라, 부팅 이벤트 수신이 모바일과 다를 수 있다. 반드시 실측한다.

**capability 스냅샷**
- 최초 실행 시와 앱 업데이트 시 도메인별 지원 여부를 저장한다.
- 리포트 항목은 이 스냅샷으로 자동 구성한다.

### B. 집계 (Aggregator)
- 일별 요약: 기능별 사용 시간(초), 횟수, 평균 단계
- 월별: 이번 달 누계, 지난달 대비 증감률, Top 5, '지원되지만 이번 달 0회'인 기능 목록
- 기록률 = 앱이 기록한 주행 시간 ÷ Odometer 기준 주행 시간. 빈 구간을 추정으로 채우지 않는다.

### C. 리포트 UI (Jetpack Compose 권장. 샘플앱 방식이 다르면 샘플앱을 따른다)
- 월간 고지서 화면 구성: 기간, 기록률, 많이 쓴 기능(시간), 지난달 대비 변화, 안 쓴 기능, 한 줄 팁
- portrait는 요약 카드 중심, landscape는 상세 표와 간단한 막대 차트
- 주행 중에는 큰 숫자 요약 3개만 보여주고 스크롤과 상세 화면을 막는다. 정차하면 전체를 보여준다.
- 문구 예시: "열선 시트 14시간 (지난달 대비 +60%)", "통풍 시트: 이번 달 0회", "기록률 94%"

### D. 동의·설정
- 첫 실행 온보딩에서 세 가지를 알린다: 무엇을 기록하는지, 데이터는 차 안에만 저장된다는 점, 언제든 끌 수 있다는 점.
- 카테고리별 수집 on/off, 전체 데이터 삭제, "백그라운드 기록 중" 상태 표시

### E. 데모 (공모전 시연용)
- debug/demo 빌드 플레이버에만 넣고 릴리스에서는 뺀다.
- 30일치 합성 데이터 생성기: 시드를 고정하고 계절성을 반영한다(예: 겨울에는 열선 사용 증가).
- 시연 흐름: Virtual Console로 기능을 실시간으로 켜고 끄는 장면을 보여준 뒤, 합성 데이터로 만든 월간 리포트로 이어간다.

---

## 비목표
MVP에서는 하지 않되, 나중에 붙일 수 있게 인터페이스로 열어둔다.
- 차량 제어(set) API
- 서버 통신, 로그인, 같은 차종 평균 비교
- 기능별 에너지·비용 추정 (같은 속도 구간에서 에어컨 on/off 연비 비교 통계)
- 에어컨 성능 저하 감지, 소모품 교체 알림

---

## 아키텍처 가이드
- 언어와 비동기: Kotlin, Coroutines/Flow
- DI: 수동 DI 또는 Hilt 중 가벼운 쪽
- 패키지 구조:
  - `vehicle/`: SDK 래퍼, FeatureSource 어댑터
  - `collector/`: 서비스, IntervalTracker, Heartbeat
  - `data/`: Room 엔티티, DAO, Repository
  - `aggregate/`
  - `ui/`: report, onboarding, settings
  - `demo/`
- FeatureSource 인터페이스:
  - `featureKey`, `checkCapability()`, `readInitialState()`, `start(listener)`, `stop()`
  - 도메인별로 어댑터를 구현하고, 테스트용 `FakeFeatureSource`를 둔다.
- SDK 래퍼: 콜백형 API를 suspend/Flow로 감싼다. `release()` 전에 대기 중인 콜백이 모두 처리되도록 보장한다.
- 로그에 개인정보를 남기지 않는다.

**Room 스키마 초안** (필요하면 조정)
```
feature_interval(id, featureKey, zone, level, startTs, endTs, tripId, closedBy[EVENT|HEARTBEAT|STOP])
counter_event(id, featureKey, zone, ts, tripId)
trip(tripId, startTs, endTs, odoTripTimeStartSec, odoTripTimeEndSec, distanceKm)
daily_summary(date, featureKey, onSec, count, avgLevel)
capability_snapshot(featureKey, supported, checkedAt)
app_state(key, value)   -- lastAlive 등
```

---

## 작업 방식
- 코드를 쓰기 전에 두 가지를 먼저 한다.
  1. 문서와 샘플앱을 읽고 실제 클래스·메서드·리스너·퍼미션 이름을 `docs/sdk-notes.md`에 표로 정리한다.
  2. 구현 계획을 제시하고 내 확인을 받은 뒤 시작한다.
- SDK API와 퍼미션 이름은 추측하지 않는다. 문서에서 찾지 못하면 `TODO(SDK)`로 표시하고 나에게 질문한다.
- 마일스톤마다 멈추고 결과와 남은 리스크를 보고한 뒤, 아래 "진행 상황"을 갱신한다.

### 마일스톤
- **M0 환경**: SDK 의존성 추가, 에뮬레이터 실행, 샘플앱 수준의 연결 확인, `docs/sdk-notes.md` 작성
- **M1 수집 뼈대**: 서비스 + 기능 2개(좌석 열선, 에어컨) + IntervalTracker + Room + 단위 테스트
- **M2 수집 확장**: MVP 대상 전체 + capability 스냅샷 + 하트비트 복구 + 기록률
- **M3 리포트 UI**: portrait/landscape, 주행 중 간략 모드
- **M4 동의·설정**: 온보딩, 카테고리별 on/off, 데이터 삭제
- **M5 마무리**: 데모 플레이버 + 성능 점검(CPU·RAM 측정 방법 포함) + 완료 체크리스트

### 테스트 (JVM 단위 테스트 필수)
- IntervalTracker: 켜짐, 꺼짐, 단계 변경, 중복 이벤트, 하트비트로 구간 닫기, 프로세스 재시작
- Aggregator: 월 경계, 0회 기능, 기록률

---

## 완료 기준
- [x] 앱을 화면에서 내린 상태에서도 Virtual Console로 바꾼 상태가 기록된다. — Activity STOPPED 상태에서 시트·에어컨·창문·문·방향지시등 등 기록 (`docs/sdk-notes.md` 9·10절)
- [x] 다른 앱을 여러 개 띄워도 수집이 유지되거나, 종료됐다가 재시작하면 `lastAlive` 기준으로 복구된다. — 다른 앱 5개를 띄워도 같은 PID·FGS 유지, 그동안 VC 변경 7건 기록. `kill -9` 후 `START_STICKY` 재시작, 열린 구간을 `lastAlive`로 닫음 (`docs/performance.md`, `docs/sdk-notes.md` 9절)
- [x] capability에서 지원하지 않는 기능은 리포트에 나오지 않는다. — 에뮬레이터 미지원인 MAX 에어컨·드라이브 모드는 고지서·안 쓴 기능·팁·합성 데이터 어디에도 없음. `MonthlyReportBuilder` 단위 테스트로 고정 (`docs/sdk-notes.md` 10·13.2절)
- [x] 주행 중에는 간략 화면만 보인다. — 기어 D에서 고지서·설정·일간·주간 어느 화면이든 이번 달 값 3개만, 스크롤·버튼 없음 (`docs/sdk-notes.md` 11·12·14절)
- [x] CPU·RAM 기준을 넘지 않는다 (측정 결과 첨부). — `prodRelease` 주행 중 CPU 평균 0.03%(최대 0.48%), 정차 중 최악 5.73%(최대 9.95%), PSS 최대 57MB, 콜드 실행 178ms 이하. M5와 M6(새 화면) 두 번 측정 (`docs/performance.md`). 에뮬레이터 수치
- [x] 릴리스 빌드에 데모 데이터가 없다. — `prodRelease` R8 매핑에 합성 데이터 생성기·`Demo`·디버그 화면·부팅 리시버 없음, `debuggable=false` (`docs/sdk-notes.md` 13.3절)
- [x] 단위 테스트를 모두 통과한다. — `testProdDebugUnitTest` 110개, `testDemoDebugUnitTest` 118개 모두 통과 (M6 기준)

---

## 진행 상황
마일스톤을 끝낼 때마다 아래를 갱신한다.

| 마일스톤 | 상태 | 메모 |
|---|---|---|
| M0 환경 | 완료 | 2026-11-03. `docs/sdk-notes.md`, `docs/implementation-plan.md` 작성. 에뮬레이터 실측(Android 14, 헤드리스 유저, Service 2.3.22, UXR). Vehicle Service 연결 확인은 M1 첫 작업으로 이월 |
| M1 수집 뼈대 | 완료 | 2026-11-03. FGS specialUse 서비스, 좌석 열선·에어컨 소스, IntervalTracker, Room, 하트비트·lastAlive 복구. JVM 테스트 26개 통과. 에뮬레이터에서 백그라운드 수집·강제 종료 복구 확인 (`docs/sdk-notes.md` 9절) |
| M2 수집 확장 | 완료 | 2026-11-03. 기능 25개(에뮬레이터 22개 지원), 카운터·트립·차량 지표 스냅샷, capability 스냅샷, 기록률(시간→거리 대체), 부팅 리시버(debug 전용·플래그 off). JVM 테스트 51개 통과. 실측은 `docs/sdk-notes.md` 10절 |
| M3 리포트 UI | 완료 | 2026-11-03. 일별 집계(daily_summary 캐시)·월간 고지서·팁, 넓은 창 표+막대 / 좁은 창 카드, 주행 중 숫자 3개(`distractionOptimized`), 상태 복원. JVM 테스트 82개 통과. 실측은 `docs/sdk-notes.md` 11절, 규칙은 `docs/implementation-plan.md` 4절 |
| M4 동의·설정 | 완료 | 2026-11-03. 첫 실행 동의(3가지 안내), 동의 전 수집 차단, 전체·카테고리별 on/off(실시간 반영), 모든 기록 삭제, 상태 표시와 FGS 알림. JVM 테스트 92개 통과. 실측은 `docs/sdk-notes.md` 12절 |
| M5 마무리 | 완료 | 2026-11-03. `demo` 플레이버와 합성 데이터 생성기(시드 고정, 두 달, 계절 반영), 시연 대본, 릴리스 R8 검증(SDK Gson keep 규칙 추가, capability 스냅샷 오저장 버그 수정), 성능 측정 스크립트와 결과, 완료 기준 7개 증빙. JVM 테스트 prod 95개·demo 103개 통과. 실측은 `docs/sdk-notes.md` 13절, `docs/performance.md`, 시연은 `docs/demo-script.md` |
| M6 제출 전 보완 | 완료 | 2026-11-03. 패키지 `com.garam.hackathon`, 새 아이콘(고지서+사용량 막대)과 스토어용 512px, 라이트·다크 테마(시스템 주·야간)와 24sp 이상 글자, 일간·주간·월간 보기(기간 합계·분포 막대·지난 기간 비교), 동의 철회(기록 삭제), 13개월 보관 정리, 빈 기간 안내, 기록률 없음 사유. JVM 테스트 prod 110개·demo 118개 통과. 실측은 `docs/sdk-notes.md` 14절, 규칙은 `docs/implementation-plan.md` 7절 |
| 제출 준비 | 진행 중 | 2026-11-13. 릴리스 서명(키는 저장소 밖 `~/.carmeter-signing`, v2), 버전 1.0.0(5), 제출 APK 복사 태스크 `:app:submissionApk`, README를 심사용(앱 이름·실행 방법·테스트 환경·APK 위치)으로 개편. 서명한 `prodRelease`를 에뮬레이터에 설치해 수집·주행 중 요약 확인. 2026-11-14 제출 APK `apk/임가람_CarMeter_release.apk`(개인 참가라 팀명은 성명) 생성, 커밋 작성자를 `CarMeter`로 정리, GitHub 비공개 저장소에 푸시. **11/16까지 공개 전환 필요.** 실측은 `docs/sdk-notes.md` 16절 |

**확인 필요 (외부 문의)**
- 부팅 시 자동 시작(BOOT_COMPLETED) 허용 여부 (헤드리스 구조라 u0·u10 모두 수신함)
- 포그라운드 서비스 사용 정책 (타입 `specialUse`, 상시 알림 표시)
- 이 앱이 쓰는 조회 퍼미션 목록의 사전 승인: `CAR_CLIMATE`, `CAR_SEATS`, `CAR_MIRRORS`, `CAR_WINDOWS`, `CAR_LIGHTS`, `CAR_DRIVE`, `CAR_DOORS`, `CAR_ENERGY`
- 3rd-party가 주행/정차(차속·기어·주차 브레이크)와 시동 off를 알 수 있는 공식 방법
- 실차 Android 버전과 요구 targetSdk
- Vehicle SDK 2.3.2 consumer ProGuard 규칙에 Gson `TypeToken` keep 누락 (R8 릴리스에서 SDK가 동작하지 않음, 앱 쪽에서 우회)
- CPU 기준(주행 5%·정차 25%)이 코어 하나 기준인지 전체 기준인지
- 배포용 서명 인증서 발급 절차 (지금은 자체 발급 키로 서명. Pleos가 인증서를 발급하면 `keystore.properties`만 바꾼다)
- 업데이트 안내: 마켓에 자동 업데이트가 없어 앱이 새 버전을 알려야 하는데(앱 업데이트 가이드), 네트워크 없이 새 버전을 알 방법이나 버전 확인 SDK 일정

---

## 참고 문서
- Vehicle SDK 소개: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- 프로젝트 설정: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup
- 도메인 객체 획득: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object
- 퍼미션: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission
- 오류 코드: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code
- API 레퍼런스: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/entry
- 샘플앱: https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app
- 에뮬레이터: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- Virtual Console: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator
- 앱 구조·동작 모델: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model
- 성능 기준: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt
- API 호환성: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability
- 주행 안전 설계: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline
- 보안·개인정보: https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/security-privacy
- 앱 보안 가이드: https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide
- 심사 기준(기능·성능): https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability
- UI 디자인 가이드: https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro
