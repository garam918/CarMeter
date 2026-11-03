# CarMeter 구현 계획 (M1~M5)

- 작성: 2026-11-03 (M0 산출물). API 이름의 근거는 `docs/sdk-notes.md`.
- 아래 `{pkg}`는 패키지명이다. 현재 `com.example.hyundaihackathon`이고, 바꿀지 결정이 필요하다(맨 아래 질문 1).
- 이 문서는 승인 전 제안이다. 승인 후 M1부터 시작한다.

## 1. 전체 구조

```
app/src/main/java/{pkg}/
├─ CarMeterApp.kt                 Application. AppContainer 생성만 한다
├─ di/AppContainer.kt             수동 DI. DB, Repository, VehicleGateway, Clock을 lazy로 보관
├─ vehicle/                       SDK 래퍼와 FeatureSource 어댑터
│  ├─ VehicleGateway.kt           Vehicle 인스턴스 1개 소유. initialize/release, 진행 중 호출 수 추적, release 전 대기
│  ├─ SdkCalls.kt                 콜백 API → suspend 변환 (suspendCancellableCoroutine), VehicleException → SdkError
│  ├─ SdkError.kt                 ErrorCode를 앱 오류로 매핑 (NotSupported, Permission, Connection, Transient)
│  ├─ FeatureKey.kt               수집 기능 키 enum (HVAC_POWER, HVAC_AC, SEAT_HEAT, ...) + 카테고리
│  ├─ Zone.kt                     areaId(비트마스크) ↔ 표시용 라벨 (운전석, 조수석, 앞유리 …)
│  ├─ FeatureSource.kt            interface: featureKey, checkCapability(), readInitialState(), start(listener), stop()
│  ├─ FeatureEvent.kt             sealed: LevelChanged(key, zone, level, ts) / Occurred(key, zone, ts)
│  ├─ CapabilityResult.kt         supported, zones, bounds
│  ├─ sources/
│  │  ├─ HvacSources.kt           Power, Ac, Temperature, FanSpeed, Recirculation, Defroster (+ 선택: Auto, MaxAc, MaxDefrost, Dual, AirCleaner)
│  │  ├─ SeatSources.kt           SeatHeat, SeatVentilation
│  │  ├─ HeaterSources.kt         SteeringWheelHeat, SideMirrorHeat
│  │  ├─ WiperSource.kt           WiperState 리스너 + 이벤트 때 WiperSwitch 1회 조회
│  │  ├─ LightSources.kt          LowBeam, HighBeam, AmbientState
│  │  ├─ DrivingModeSources.kt    DrivingMode, Cruise
│  │  ├─ WindowSource.kt          창문 위치 → 열림(1)/닫힘(0)
│  │  ├─ DoorSource.kt            닫힘→열림 전이를 Occurred로
│  │  ├─ TurnSignalSource.kt      OFF→LEFT/RIGHT 전이를 Occurred로
│  │  └─ TripMetricsReader.kt     Odometer·Fuel·EvBattery 트립 경계 get (리스너 아님)
│  └─ driving/
│     └─ DrivingStateSource.kt    CarUxRestrictionsManager → FeatureSource(DRIVING) 형태로 노출 (M3에서 UI도 사용)
├─ collector/
│  ├─ CollectorService.kt         FGS specialUse. 생명주기만 담당하고 일은 CollectorEngine에 위임
│  ├─ CollectorController.kt      시작/중단 진입점 (Activity, BootReceiver, 설정 화면에서 호출)
│  ├─ CollectorEngine.kt          capability 확인 → 초기 상태 → 리스너 시작, 단일 스레드 디스패처로 이벤트 직렬화
│  ├─ IntervalTracker.kt          순수 Kotlin. 켜짐/꺼짐/단계 변경/중복 이벤트/일괄 닫기
│  ├─ CounterTracker.kt           순수 Kotlin. 이산 이벤트 카운트
│  ├─ EventBuffer.kt              메모리 버퍼. 60초 또는 N개마다 Repository로 일괄 저장
│  ├─ Heartbeat.kt                60초 단일 타이머: lastAlive 저장 + EventBuffer.flush
│  ├─ RecoveryManager.kt          시작 시 열린 구간을 lastAlive로 닫음 (closedBy=HEARTBEAT)
│  ├─ TripTracker.kt              주행 상태 구간으로 trip 시작·종료, 경계에서 TripMetricsReader 호출
│  ├─ BootReceiver.kt             플래그 off 기본. 헤드리스 시스템 유저면 즉시 반환
│  └─ Clock.kt                    interface + SystemClock 구현 (테스트에서 FakeClock)
├─ data/
│  ├─ db/CarMeterDatabase.kt
│  ├─ db/entity/                  FeatureIntervalEntity, CounterEventEntity, TripEntity,
│  │                              DailySummaryEntity, CapabilitySnapshotEntity, AppStateEntity
│  ├─ db/dao/                     IntervalDao, CounterDao, TripDao, DailySummaryDao, CapabilityDao, AppStateDao
│  └─ repo/                       UsageRepository, CapabilityRepository, SettingsRepository(app_state 기반)
├─ aggregate/
│  ├─ DailyAggregator.kt          구간을 날짜 경계로 잘라 daily_summary 생성 (onSec, count, avgLevel)
│  ├─ MonthlyReportBuilder.kt     이번 달 누계, 지난달 대비 증감, Top 5, 지원되지만 0회인 기능
│  ├─ RecordingRateCalculator.kt  앱 기록 주행 시간 ÷ Odometer 주행 시간. 빈 구간 추정 없음
│  ├─ TipGenerator.kt             한 줄 팁 (규칙 기반)
│  └─ model/MonthlyReport.kt
└─ ui/
   ├─ MainActivity.kt             distractionOptimized. Compose 진입점, 온보딩/리포트/설정 라우팅
   ├─ AppViewModel.kt             주행 상태, 수집 상태를 화면에 공급
   ├─ report/                     ReportScreen(portrait 카드), ReportWideScreen(표+막대 차트), DrivingSummary(큰 숫자 3개), ReportViewModel
   ├─ onboarding/                 OnboardingScreen (기록 항목, 차 안에만 저장, 언제든 끌 수 있음)
   ├─ settings/                   SettingsScreen, SettingsViewModel (카테고리 on/off, 전체 삭제, 기록 상태)
   └─ theme/
app/src/demo/java/{pkg}/demo/
   ├─ SyntheticDataGenerator.kt   시드 고정, 30일, 계절성
   └─ DemoSeeder.kt               demo 빌드에서만 보이는 "합성 데이터 넣기" 진입점
app/src/test/java/{pkg}/
   ├─ collector/IntervalTrackerTest, CounterTrackerTest, RecoveryManagerTest, EventBufferTest
   ├─ aggregate/DailyAggregatorTest, MonthlyReportBuilderTest, RecordingRateCalculatorTest
   └─ fakes/FakeFeatureSource, FakeClock, InMemoryUsageRepository
```

### 핵심 설계 결정
- **이벤트 흐름:** SDK 콜백(Binder 스레드) → FeatureSource가 `FeatureEvent`로 변환 → CollectorEngine의 단일 스레드 디스패처 → IntervalTracker/CounterTracker → EventBuffer → Room.
- **단계(level) 표현:** Boolean은 1/0, Int 단계는 그대로, enum은 고정 코드(ordinal 대신 명시 상수)로 저장한다. 설정 온도는 °C×10 정수로 저장해 평균을 낸다.
- **주행 상태도 FeatureSource로** 다룬다(featureKey=DRIVING). "창문 열고 주행"과 기록률 분자를 같은 구간 데이터에서 계산할 수 있다.
- **IntervalTracker는 Android 의존 없는 순수 Kotlin**으로 두고 JVM 테스트로 검증한다.
- **비목표 확장점:** 제어(set)는 `FeatureSource`에 넣지 않고, 나중에 `FeatureController` 인터페이스로 따로 붙인다. 서버 전송은 `UsageRepository` 뒤에 `UsageExporter` 인터페이스만 남긴다.

### Room 스키마 (CLAUDE.md 초안에서 조정한 점)
```
feature_interval(id, featureKey, zone INT(areaId), level INT, startTs, endTs NULL, tripId NULL,
                 closedBy TEXT NULL [EVENT|HEARTBEAT|STOP])      -- endTs NULL = 열린 구간
counter_event(id, featureKey, zone, ts, tripId)
trip(tripId(=startTs), startTs, endTs, odoKmStart, odoKmEnd, drivingMinStart, drivingMinEnd,
     fuelEconomyEnd, evEfficiencyEnd, socStart, socEnd)           -- 분 단위 (Odometer가 분 단위)
vehicle_snapshot(id, ts, reason[COLLECTOR_START|COLLECTOR_STOP|TRIP_START|TRIP_END],
     odometerKm, drivingTimeMin, fuelEconomy, evEfficiency, socPercent)  -- 기록률 분모 (M2 추가)
daily_summary(date, itemKey, onSec, count, avgLevel)            -- PK(date, itemKey). M3에서 확정 (아래)
capability_snapshot(featureKey, zone, supported, minLevel, maxLevel, error, checkedAt, appVersionCode)  -- 미지원은 zone=-1 한 행
app_state(key, value)                                             -- lastAlive, consentAt, category_* 플래그 등
```
- 바뀐 점: `zone`을 정수 areaId로, trip의 Odometer 값을 분·km로, capability에 존과 범위 추가.
- M3에서 바뀐 점: `daily_summary`의 키를 기능(featureKey·zone) 대신 리포트 항목(`aggregate/ReportItem`)으로 바꿨다. 시트 열선/통풍 분리, 창문 열고 주행(창문 ∩ 주행)처럼 여러 기능을 합치거나 나누는 항목이 있어서다. 사용이 없던 날은 `_NONE` 행으로 표시해 다시 계산하지 않는다.
- M2에서 바뀐 점: 기록률 분모용 `vehicle_snapshot` 추가(앱이 꺼져 있던 동안의 주행을 시작 시점 판독값으로 잡는다). `daily_summary`는 M3 집계와 함께 만든다(Room 2→3).
- M2에서 추가한 파일: `vehicle/Levels.kt`(SDK 열거형 → 고정 레벨 코드), `vehicle/VehicleMetricsReader.kt`, `vehicle/sources/{HeaterSources,LightSources,BodySources,DrivingModeSources,WiperSource}.kt`, `collector/{CounterTracker,TripTracker,BootReceiver,BootStartSetting}.kt`, `aggregate/{RecordingRateCalculator,RecordingRateQuery}.kt`, `ui/FeatureLabels.kt`. 문 열림·방향지시등은 `FeatureKind.COUNT`라 구간 대신 `counter_event`에만 저장한다.

---

## 2. 마일스톤

### M1 수집 뼈대 — 서비스 + 좌석 열선·에어컨 + IntervalTracker + Room
- **빌드:** Compose, Room+KSP, coroutines, test 의존성 추가. `CAR_SEATS`, `CAR_CLIMATE`, FGS 퍼미션 추가.
- **파일:** `CarMeterApp`, `AppContainer`, `VehicleGateway`, `SdkCalls`, `SdkError`, `FeatureKey`, `Zone`, `FeatureSource`, `FeatureEvent`, `sources/SeatSources.kt`(SeatHeat만), `sources/HvacSources.kt`(Ac만), `CollectorService`, `CollectorController`, `CollectorEngine`, `IntervalTracker`, `EventBuffer`, `Clock`, Room(`feature_interval`, `app_state`), 최소 확인 화면(최근 구간 목록, debug 빌드에서만).
- **첫 작업:** `initialize()` → `checkSeatTemperatureCapabilities` 1회 호출로 Vehicle Service 연결을 확인한다(M0에서 못 한 항목).
- **테스트:** IntervalTracker — 켜짐, 꺼짐, 단계 변경, 중복 이벤트, 일괄 닫기(STOP), 재시작 후 열린 구간 처리.
- **에뮬레이터 확인:** 앱 실행 → 홈으로 내림 → VC로 운전석 열선 H1→H3→Off, 에어컨 On/Off → 앱 복귀 시 구간이 기록돼 있음.
- **실측:** sdk-notes 8절 1~5번.
- **리스크:** FGS specialUse가 Pleos에서 불허될 수 있음(폴백 설계 유지), 리스너 초기값 동작.

### M2 수집 확장 — 전체 대상 + capability 스냅샷 + 하트비트 복구 + 기록률
- **파일:** 나머지 `sources/*`, `TripMetricsReader`, `driving/DrivingStateSource`(useLibrary 추가), `CounterTracker`, `Heartbeat`, `RecoveryManager`, `TripTracker`, `BootReceiver`, `CapabilityRepository`, 나머지 엔티티·DAO, `RecordingRateCalculator`.
- **capability 스냅샷:** 최초 실행과 versionCode 변경 시 전 기능 capability 저장. 이후 시작 시에는 스냅샷 기준으로 소스를 고르되, 런타임 `ERROR_NOT_SUPPORTED_*`가 나오면 갱신.
- **하트비트:** 60초 타이머 1개. lastAlive 저장과 버퍼 flush를 같이 한다.
- **기록률:** 분자 = 앱이 기록한 주행(DRIVING) 구간 합, 분모 = Odometer 트립 주행 시간(분) 증가분. 앱이 꺼져 있던 동안의 주행은 분모에만 잡힌다. 트립 시간 capability가 없으면 기록률을 "측정 불가"로 표시한다.
- **테스트:** CounterTracker, RecoveryManager(lastAlive로 닫기), EventBuffer(60초/N개 flush), RecordingRateCalculator(분모 0, 분모 미지원).
- **실측:** sdk-notes 8절 6~16번. 부팅 리시버는 플래그를 켠 debug 빌드로만 확인한다.
- **리스크:** 트립 시간이 에뮬레이터에서 안 나오면 기록률을 에뮬레이터에서 시연할 수 없음. 이벤트 폭주 기능 발견 시 디바운스 필요.

### M3 리포트 UI — 집계 + portrait/landscape + 주행 중 간략 모드
- **파일:** `DailyAggregator`, `MonthlyReportBuilder`, `TipGenerator`, `ui/report/*`, `AppViewModel`(주행 상태), MainActivity `distractionOptimized`.
- **집계 시점:** 리포트를 열 때 지난 날짜 중 요약이 없는 날만 계산(백그라운드 작업 없음). 오늘은 원본 구간에서 바로 계산.
- **화면:** portrait(좁은 창) = 요약 카드(기간, 기록률, 많이 쓴 기능, 지난달 대비, 안 쓴 기능, 팁). landscape(넓은 창) = 상세 표 + 막대 차트. 창 크기 클래스로 분기하고 `screenOrientation`은 질문 3 결과에 따름.
- **주행 중:** `isRequiresDistractionOptimization == true`면 큰 숫자 3개만 보여주고 스크롤·상세·설정 진입을 막는다. 정차하면 전체.
- **테스트:** DailyAggregator(자정을 넘는 구간 분할), MonthlyReportBuilder(월 경계, 지난달 0일 때 증감률, 0회 기능은 capability 지원 항목만).
- **확인:** VC 기어 D/P 전환으로 간략↔전체 전환, 창 크기 변경 시 상태 유지, 백그라운드 복귀 시 마지막 화면 복원.
- **리스크:** 차트 라이브러리 선택(외부 의존 없이 Compose Canvas로 직접 그리는 쪽을 제안).

### M4 동의·설정 — 온보딩, 카테고리 on/off, 데이터 삭제
- **파일:** `ui/onboarding/*`, `ui/settings/*`, `SettingsRepository`, CollectorEngine에 카테고리 변경 반영(해당 소스만 stop/start).
- **동작:** 동의 전에는 서비스를 시작하지 않는다. 전체 삭제 = 서비스 정지 → 모든 테이블 삭제(capability 스냅샷 제외) → 재시작. "백그라운드 기록 중" 상태는 앱 상단 표시 + FGS 알림.
- **테스트:** 카테고리 off 시 해당 기능 구간이 STOP으로 닫힘(Engine 단위 테스트, FakeFeatureSource 사용).
- **리스크:** POST_NOTIFICATIONS 요청 UX가 Pleos 가이드와 맞는지.

### M5 마무리 — 데모 플레이버 + 성능 점검 + 체크리스트
- **데모:** `demo` 플레이버에만 `SyntheticDataGenerator`(시드 고정, 30일, 겨울엔 열선↑·여름엔 통풍·에어컨↑)와 `DemoSeeder`. 시연 흐름: VC로 실시간 on/off → 합성 데이터 월간 리포트.
- **릴리스 검증:** `prodRelease` APK에 `demo` 패키지 클래스가 없는지 `apkanalyzer dex packages`로 확인. 디버그 플래그·로그 제거 확인.
- **성능 측정 방법(문서화 + 결과 첨부):**
  - CPU: 주행(기어 D) 상태에서 10분간 VC로 기능을 조작하며 `adb shell top -b -d 5 -p <pid>`와 `dumpsys cpuinfo` 기록 → 5% 미만 확인. 정차 상태 25% 이하.
  - RAM: `adb shell dumpsys meminfo {applicationId}`의 TOTAL PSS → 400MB 이하.
  - 실행 시간: `adb shell am start -W` TotalTime(10초 이내), 리포트 첫 표시 시간 측정.
- **완료 체크리스트:** CLAUDE.md "완료 기준" 7개 항목을 하나씩 증빙.

---

## 3. 승인 전에 정할 것

1. **패키지명/applicationId:** 지금 `com.example.hyundaihackathon`. 제출용으로 바꿀지, 바꾼다면 무엇으로 할지.
2. **백그라운드 방식:** FGS `specialUse` 추천안으로 M1을 시작할지 (불허 시 폴백: 화면에 있을 때만 수집 + lastAlive 복구).
3. **화면 방향:** `screenOrientation`을 지정하지 않고(기본 portrait, window 1개) 넓은 창도 반응형으로 지원할지, landscape를 지정해 window 2개를 쓸지.
4. **제외 항목 승인:** 시트 메모리 호출과 주차 브레이크는 공개 API가 없어 제외, 와이퍼 속도는 근사로 기록.
5. **주행 구간 정의:** MVP는 "D/R단(DO 필요 상태)"을 주행으로 본다. 신호 대기도 주행에 포함된다.
6. **targetSdk:** 34 유지.
7. **샘플앱 연결 확인:** 직접 실행해 볼지, M1 첫 작업으로 대신할지.

---

## 4. M3에서 정한 집계·화면 규칙

| 규칙 | 내용 | 근거 |
|---|---|---|
| 사용 시간 | 영역(좌석 등)을 합친 실제 경과 시간. 두 좌석을 동시에 데워도 한 번 | "시트 열선 14시간"을 운전자가 느끼는 시간으로 |
| 사용 횟수 | 맞닿은 구간은 한 번(단계 변경은 새 사용 아님). 자정에 걸친 구간은 시작한 날만 셈 | |
| 평균 단계 | 사용 시간으로 가중 평균(영역별 합) | |
| 시트 통풍 | 그날 그 좌석에 통풍 property 기록이 있으면 그것, 없으면 열선 음수 값 | 에뮬레이터는 음수로만 보고, 실차는 둘 다 올 수 있어 이중 집계 방지 |
| 평균 설정 온도 | 공조 전원이 켜진 동안만 가중 평균 | 꺼진 동안의 설정값은 의미 없음 |
| 지난달 대비 | 지난달의 같은 날짜 구간(1일~오늘 날짜, 지난달 말일에서 자름)과 비교. 지난달 0이면 "새로 사용" | 진행 중인 달을 지난달 전체와 비교하면 항상 줄어 보임 |
| 안 쓴 기능 | capability 스냅샷상 지원 + 이번 달 0회. 스냅샷이 없거나 그달 기록이 하나도 없으면 비움 | 기록이 없는 것과 안 쓴 것은 다르다 |
| 캐시 | 오늘 이전이고, 그날 끝 전에 시작한 열린 구간이 없을 때만 daily_summary에 저장 | 열린 구간은 나중에 하트비트 시각으로 닫힐 수 있음 |
| 화면 분기 | 주행 중(`isRequiresDistractionOptimization`) → 큰 숫자 3개. 정차 중 창 너비 ≥ 1000dp → 표+막대, 미만 → 카드 | `screenOrientation`은 지정하지 않음 |
| 상태 복원 | 선택한 달은 `SavedStateHandle`, 목록 스크롤은 `rememberLazyListState`, 디버그 화면 여부는 `rememberSaveable` | 심사: 백그라운드 복귀 시 마지막 상태 복원 |
| 팁 | 기록률 < 80% → 창문 열고 주행 ≥ 30분 → 핸들 열선 미사용(지원 시) → 1위 항목 +50% 이상 → 안 쓴 기능 → 가장 많이 쓴 기능 순 | 에너지·비용 추정은 비목표라 쓰지 않음 |

## 5. M4에서 정한 동의·설정 규칙

| 규칙 | 내용 |
|---|---|
| 동의 전 | 수집 서비스를 시작하지 않는다. 엔진도 `consentAt`이 없으면 시작을 거부한다(부팅 리시버·`START_STICKY` 재시작 경로 포함) |
| 동의 저장 | `app_state.consentAt`(동의 시각). 전체 스위치 `recordingEnabled`(기본 켜짐), 꺼진 카테고리 `disabledCategories` |
| 카테고리 | 공조·시트·조명·주행 보조·차체 5개. 주행 상태(SYSTEM)는 기록률에 필요해 끌 수 없고, 화면에 그 이유를 적는다 |
| 카테고리 끄기 | 엔진이 설정 흐름을 구독해 해당 소스만 해제하고 열린 구간을 STOP으로 닫는다. capability 확인은 꺼진 카테고리도 시작 때 한 번 한다(스냅샷 완전성) |
| 리포트 | 꺼진 카테고리의 항목은 "안 쓴 기능"에 넣지 않는다 |
| 전체 삭제 | 수집 정지 → 사용 기록 테이블 6종 삭제(한 트랜잭션) → 기록 허용 상태면 재시작. capability 스냅샷과 설정은 남긴다(운전자 정보가 아님) |
| 알림 퍼미션 | 동의 직후 한 번 요청. 답과 무관하게 수집은 시작(알림 표시 여부만 달라짐) |
| 상태 표시 | 고지서 상단 "● 백그라운드 기록 중 / 기록 꺼짐 / 기록하려면 동의가 필요해요 …"(`ui/RecordingIndicator`), 누르면 설정 또는 동의 화면 |


## 6. M5에서 정한 데모·릴리스 규칙

| 규칙 | 내용 |
|---|---|
| 플레이버 | `prod`(제출용)와 `demo`. 두 플레이버에 같은 이름의 `demo.Demo` 객체를 둔다. `prod` 쪽은 `IS_AVAILABLE = false`이고 생성기를 참조하지 않아, 합성 데이터 코드가 `prod` APK에 들어갈 길이 없다 |
| 합성 데이터 범위 | 계획의 30일 대신 **두 달 전 1일부터 오늘까지**. 지난달 고지서가 완결된 한 달이 되고, 그 전달과 비교할 수 있다 |
| 결정성 | 시드 20261115. 날짜마다 `seed xor epochDay`로 난수를 새로 만들어, 범위가 달라도 같은 날은 같은 기록이다. ID는 `demo-<epochDay>-<n>` |
| 계절성 | 바깥 기온 곡선(1월 중순 최저, 7월 중순 최고) → 열선·핸들 열선(추울 때), 통풍·에어컨(더울 때), 창문(15~24°C), 비 오는 날 와이퍼·미러 열선, 해 진 뒤 전조등 |
| 지원 기능만 | 저장된 capability 스냅샷에서 지원되는 기능만 만든다. 미지원 기능은 합성 데이터에도 없다 |
| 기록률 | 주행의 약 6%를 "앱이 꺼져 있던 주행"(주행거리만 늘고 기록 없음)으로 만들어 기록률이 100%가 아니게 한다. 누적 주행거리는 실제 차량 값에서 끝나도록 맞춘다 |
| 불러오기 | 수집 정지 → 사용 기록 삭제 → 합성 데이터 저장 → 기록 허용 상태면 재시작(`CollectorController.replaceUsageData`, 전체 삭제와 같은 경로) |
| 릴리스 검증 | `apkanalyzer` 대신 R8 매핑(`mapping.txt`)과 병합 매니페스트로 확인. `prodRelease`에 `SyntheticDataGenerator`·`Demo`·디버그 수집 화면·부팅 리시버가 없음 |
| 릴리스 축소 | R8 full mode에서 SDK의 Gson `TypeToken`이 깨져 keep 규칙을 추가했다(`docs/sdk-notes.md` 13.1). `Log.v/d/i`는 제거 |
| 성능 측정 | 계획의 `top`·`dumpsys cpuinfo` 대신 `/proc/<pid>/stat` 틱을 2초마다 읽는다(해상도가 높고 시나리오별로 정확히 자를 수 있음). VC REST API로 이벤트를 자동 주입(`scripts/measure-performance.sh`, 결과 `docs/performance.md`) |
