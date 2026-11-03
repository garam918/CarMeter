# SDK 노트 (M0)

- 확인일: 2026-11-03
- 대상: Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2`, Vehicle Service 2.3.22 (에뮬레이터 실측)
- 이 문서는 구현 전에 실제 클래스·메서드·리스너·퍼미션 이름을 고정하기 위한 것이다. 여기 없는 이름은 쓰지 않는다.

## 0. 출처 표기

| 표기 | 출처 | 확인 방법 |
|---|---|---|
| **S** | SDK 소스 jar: `~/.gradle/caches/modules-2/files-2.1/ai.pleos.playground/Vehicle/2.3.2/.../Vehicle-2.3.2-sources.jar`, `vehicle-data-1.1.11-sources.jar` | 인터페이스(`api/*.kt`), 구현(`api/implement/*Impl.kt`)의 시그니처와 `@RequiresCustomPermission`, KDoc 단위를 직접 읽음 |
| **D** | 공식 문서 (각 절에 링크) | 2026-11-03 열람 |
| **E** | 에뮬레이터 실측 `Pleos_Connect_2_1_0_API_34` | `adb shell` 결과 (6절) |
| **VC** | Virtual Console (`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088) | 화면의 컨트롤 목록 |
| **SA** | 샘플앱 [Pleos-Playground/pleos-sample-vehicle-app](https://github.com/Pleos-Playground/pleos-sample-vehicle-app) (commit `959981d`, 2026-10-17) | 코드만 읽음. 빌드·설치는 하지 않았다 (6.4절) |

API 레퍼런스 진입점: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/entry
도메인별 레퍼런스 페이지 URL은 확인하지 않았고, 메서드 단위 근거는 **S**(소스 jar의 파일명)로 적는다.

---

## 1. 공통 API 패턴

출처: S (`Vehicle.kt`, `service/VehicleServiceManager.kt`, `service/BaseServiceConnector.kt`, `callback/PropertyCallback.kt`, `utils/PermissionUtil.kt`), D ([도메인 객체 획득](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object), [오류 코드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-error-code))

### 1.1 수명주기
- `Vehicle(context)` → `initialize()` → `getXxx()` → `release()`. 도메인 getter는 `getHVAC()`, `getSeat()`, `getSteeringWheel()`, `getSideMirror()`, `getWiper()`, `getLight()`, `getDrivingMode()`, `getWindow()`, `getDoor()`, `getTurnSignal()`, `getBrake()`, `getOdometer()`, `getFuel()`, `getEvBattery()` 등. `getGear()`는 `@hide`라 쓰지 않는다.
- `initialize()`는 `bindService`를 호출한다. 대상은 패키지 `ai.pleos.playground.service.vehicle`, 서비스 `ai.pleos.playground.service.vehicle.VehicleService`, action `ai.pleos.playground.vehicle.VEHICLE_START`, 플래그 `BIND_AUTO_CREATE | BIND_IMPORTANT | BIND_ABOVE_CLIENT`. 실패하면 500ms 간격으로 최대 30회 재시도한다.
- 연결되기 전에 호출한 API는 내부 큐에 쌓였다가 연결 후 실행된다. `initialize()` 전이나 `release()` 후에 호출하면 `ERROR_SERVICE_CONNECTION_FAILURE`로 바로 실패한다.
- `release()`는 등록된 리스너를 모두 해제하고 unbind한 뒤 대기 큐를 비운다. 진행 중인 get·capability 콜백은 유실될 수 있다. 문서도 "비동기 콜백이 끝나기 전에 `release()`를 호출하지 않도록 주의"하라고 한다. → 래퍼에서 진행 중인 호출 수를 세고, 0이 된 뒤(또는 타임아웃 후) release한다.
- Vehicle Service가 비정상 종료되면 SDK가 재연결하고, 등록했던 리스너를 자동으로 다시 등록한다(`reConnectedWithService`).
- SA: `Vehicle(context.applicationContext)`를 ViewModel `init`에서 `initialize()`, `onCleared()`에서 `release()`한다. 백그라운드 서비스 예시는 없다.

### 1.2 비동기 결과와 스레드
- capability·get은 `onComplete`/`onFailed` 람다로 결과가 온다. 람다는 Binder 스레드 또는 SDK 내부 `Dispatchers.Default`에서 호출된다(메인 스레드 아님).
- 리스너 콜백도 Binder 스레드에서 온다(M2 로그: `binder:<pid>_N`). `CarUxRestrictions` 리스너는 메인 스레드다. → 수집기는 단일 스레드 디스패처 하나로 모든 이벤트를 직렬화한다.

### 1.3 퍼미션 검사 방식
- SDK가 호출할 때마다 `checkSelfPermission`으로 검사한다(리플렉션으로 `@RequiresCustomPermission`을 읽음).
- capability·get은 퍼미션이 없으면 `onFailed(SecurityException)`을 호출한다.
- **register/unregister는 `SecurityException`을 동기적으로 throw한다.** → 래퍼에서 try/catch가 필수다.

### 1.4 리스너 의미
- 모든 리스너는 `VehicleListener`(sealed) 하위 인터페이스이고, `onXxxUpdated(value?)`와 `onFailed(e: Exception)` 두 메서드를 가진다. 값은 항상 nullable이다.
- 같은 property에 두 번째 리스너를 등록하면 SDK가 현재 값을 한 번 get해서 그 리스너에 바로 전달한다. 첫 리스너 등록 직후에도 현재 값이 한 번 온다(E, M1: `HVAC_SEAT_TEMPERATURE` 4개 영역, `HVAC_AC_ON`). 그래도 `readInitialState()`의 get은 유지한다. 같은 값이 두 번 오면 IntervalTracker가 중복으로 무시한다.
- 리스너 타입 하나를 여러 property가 공유하는 경우가 있다(`LightStateListener`, `DrivingTimeListener`, `DistanceDrivenListener`, `FuelEconomyListener`, `EvBatteryEfficiencyListener`, `HvacRecirculationStateListener`). property마다 별도 인스턴스를 만든다.

### 1.5 영역(Area)
- `SeatArea(vararg SeatValue)`, `WindowArea(vararg WindowValue)`, `DoorArea(vararg DoorValue)`, `MirrorArea(vararg MirrorValue)`. 내부 areaId는 값들의 비트 OR이다.
- capability가 돌려주는 Area는 **여러 좌석을 묶은 존**일 수 있다(예: HVAC 존이 `ROW_1_LEFT|ROW_2_LEFT`). get/register에는 capability가 준 Area 객체를 그대로 쓴다. 단일 좌석을 직접 만들어 호출하면 `ERROR_NOT_SUPPORTED_AREA`가 날 수 있다.
- 값 목록: `SeatValue` = ROW_1~3 × LEFT/CENTER/RIGHT, `WindowValue` = FRONT_WINDSHIELD, REAR_WINDSHIELD, ROW_1_LEFT/RIGHT, ROW_2_LEFT/RIGHT, ROOF_TOP_1/2, `DoorValue` = ROW_1~3_LEFT/RIGHT, HOOD, REAR, `MirrorValue` = DRIVER_LEFT, DRIVER_RIGHT, DRIVER_CENTER.

### 1.6 capability
- 결과 형태는 세 가지다: `(CapabilityConfig)`, `(CapabilityConfig, List<Area>)`, `(CapabilityConfig, Map<Area, AreaBounds<T>>)` 또는 `Map<Area, List<Enum>>`.
- `CapabilityConfig.accessType`: `NONE`, `READ`, `WRITE`, `READ_WRITE`. `AreaBounds<T>(minValue, maxValue)`.
- 같은 이름의 오버로드 중 `CapabilityConfig`가 없는 구 시그니처도 있다. 우리는 `CapabilityConfig`를 받는 쪽을 쓴다.
- "지원" 판정: `onComplete` 수신 && `accessType`이 `READ` 또는 `READ_WRITE` && (영역형이면) 목록·맵이 비어 있지 않음.
- 실측(M2): 미지원 property는 `onFailed`로 오고 메시지가 `ERROR_NOT_SUPPORTED_PROPERTY`다(MAX A/C, 드라이브 모드, 트립 주행 시간, 연비, 전비). 퍼미션이 없으면 `ERROR_SECURITY_PERMISSION`이다. 퍼미션 오류가 섞인 결과는 차량 capability가 아니므로 스냅샷에 저장하지 않는다.

### 1.7 오류 코드 (`ai.pleos.playground.vehicle.constant.ErrorCode`, `VehicleException.errorCode`)

| 코드 | 값 | 문서 권장 처리 | 수집기 처리 |
|---|---|---|---|
| `ERROR_INTERNAL_SERVICE` | 30001 | 재시도 | 이벤트 단위 무시, 다음 이벤트 대기 |
| `ERROR_SERVICE_CONNECTION_FAILURE` | 40001 | initialize·에뮬레이터 확인 | 수집 중단 표시, 재연결 시 재시작 |
| `ERROR_DRIVING_RESTRICTED` | 50001 | 주행 제한 안내 | 조회 API에서 나오면 기록만 |
| `ERROR_NOT_SUPPORTED_PROPERTY` | 60001 | UI 비활성 | capability 스냅샷에서 미지원 처리 |
| `ERROR_NOT_SUPPORTED_VALUE_TYPE` | 60002 | 파라미터 확인 | 버그로 취급 |
| `ERROR_NOT_SUPPORTED_AREA` | 60003 | 영역 변경 | 해당 존 제외 |
| `ERROR_EMPTY_RESULT` | 70001 | 빈 상태 | 초기값 없음으로 처리 |
| `ERROR_SECURITY_PERMISSION` | 800001 | 퍼미션 확인 | 해당 기능 미지원 처리 |
| `ERROR_UNDEFINED` | 99999 | 로그·재시도 | 무시 |

- **문서와 다른 점(M1 빌드에서 확인):** 배포된 AAR의 `classes.jar`에서 `VehicleException`은 난독화돼 `callback/exception/a.class`로 들어 있어 이름으로 참조할 수 없다. `ErrorCode`는 POM상 runtime 범위인 `vehicle-data`에 있어 컴파일 클래스패스에 없다. 그래서 문서가 안내하는 `VehicleException.errorCode`를 쓸 수 없다. SDK는 예외 메시지에 오류 이름(예: `ERROR_NOT_SUPPORTED_PROPERTY`)이나 숫자 코드를 넣으므로, 앱은 메시지를 해석한다(`vehicle/SdkError.kt`).

### 1.8 기타
- SDK 내부 로그(`Log.i/d`)가 property 이름과 값을 빌드 종류와 무관하게 출력한다. 차량 상태 값이라 개인정보는 아니지만, 릴리스 빌드에서는 R8 `-assumenosideeffects class android.util.Log { d(...); i(...); v(...); }`로 제거하는 것을 검토한다.
- AAR에 consumer ProGuard 규칙이 들어 있다(`api.implement.*`와 `RequiresCustomPermission` keep). 추가 keep 규칙은 필요 없을 것으로 본다. **M5에서 릴리스 빌드로 확인**
- 전이 의존성(POM): kotlin-stdlib 1.9.25, kotlin-reflect, kotlinx-coroutines-android 1.6.3, gson 2.10.1, core-ktx 1.8.0, vehicle-aidl 1.1.1, vehicle-data 1.1.11(이 셋 다 runtime 범위라 앱 코드에서 컴파일 참조 불가). **vehicle-data가 `hilt-android 2.51.1`을 끌어온다.** Google Play Services 의존은 없다.

---

## 2. MVP 수집 대상 → SDK 매핑 요약

| 기능 | 도메인 | 퍼미션 | 수집 형태 | VC로 바꿀 수 있나 | 판정 |
|---|---|---|---|---|---|
| 공조 전원 | HVAC | `CAR_CLIMATE` | on/off 구간 | O (HVAC Power) | **수집 확인(M2)** |
| 에어컨 | HVAC | `CAR_CLIMATE` | on/off 구간 | O (Air Conditioning) | **수집 확인(M1)** |
| 설정 온도 | HVAC | `CAR_CLIMATE` | 값 구간 (평균 설정 온도) | O (Target Temperature) | 지원 (범위 15.0~30.0°C) |
| 풍량 | HVAC | `CAR_CLIMATE` | 단계별 구간 | O (Fan Speed) | 지원 (1..8) |
| 내기순환 | HVAC | `CAR_CLIMATE` | on/off 구간 | O (Air Recirculation) | **수집 확인(M2)**, true = 내기 |
| 성에 제거 (앞/뒤) | HVAC | `CAR_CLIMATE` | on/off 구간 | O (Defroster) | **수집 확인(M2)** |
| AUTO, MAX A/C, MAX 성에, 듀얼, 공기청정 | HVAC | `CAR_CLIMATE` | on/off 구간 | AUTO·Dual·Air Cleaner만 O | MAX A/C만 미지원(AVD), 나머지 지원 |
| 시트 열선 | Seat | `CAR_SEATS` | 좌석·단계별 구간 | O (Seat Temperature H1~H3) | **수집 확인(M1·M2)** |
| 시트 통풍 | Seat | `CAR_SEATS` | 좌석·단계별 구간 | O (Seat Temperature V1~V3 → `HvacSeatTemperature` 음수) | **수집 확인(M1)**, `HvacSeatVentilation` 별도 지원 여부는 M2 |
| 시트 메모리 호출 | Seat | — | — | X | **불가**: 조회·리스너 API 없음 |
| 스티어링 휠 열선 | SteeringWheel | `CAR_CLIMATE` | 단계별 구간 | O (Steering Wheel Heat) | **수집 확인(M2)** |
| 사이드미러 열선 | SideMirror | `CAR_MIRRORS` | 단계별 구간 | X | 지원(3개 영역), 에뮬레이터 시연 불가 |
| 와이퍼 작동 | Wiper | `CAR_WINDOWS` | on/off 구간 | △ (Wiper Switch만 있음) | **수집 확인(M2)** |
| 와이퍼 속도 | Wiper | `CAR_WINDOWS` | 작동 이벤트 때 스위치 1회 조회 | △ | 켜질 때 속도만 기록, 작동 중 변경은 놓침(M2 확인) |
| 전조등 (하향/상향) | Light | `CAR_LIGHTS` | on/off 구간 | O (Exterior Lights Mode, High Beam) | **수집 확인(M2)** |
| 앰비언트 라이트 | Light | `CAR_LIGHTS` | on/off 구간 | △ (밝기·색만 있음) | 지원, 에뮬레이터에서 상태가 바뀌지 않아 시연 불가 |
| 드라이브 모드 | DrivingMode | `CAR_DRIVE` | 모드별 구간 | X | 미지원(AVD, `ERROR_NOT_SUPPORTED_PROPERTY`) → 리포트에서 자동 제외 |
| 크루즈 사용 | DrivingMode | `CAR_DRIVE` | ACTIVATED 구간 | O (Cruise Control State) | **수집 확인(M2)**, VC 버튼 대응은 불안정 |
| 창문 열고 주행 | Window + 주행 상태 | `CAR_WINDOWS` | 창문 열림 구간 ∩ 주행 구간 | O (Window Position, 기어) | **수집 확인(M2)**, 교집합 계산은 M3 |
| 문 열림 횟수 | Door | `CAR_DOORS` | 닫힘→열림 전이 횟수 | O (Door Position) | **수집 확인(M2)** |
| 방향지시등 횟수 | TurnSignal | `CAR_LIGHTS` | OFF→LEFT/RIGHT 전이 횟수 | O (Turn Signal State) | **수집 확인(M2)** |
| 주차 브레이크 | (Safety Telltale) | `PRIVATE_CAR_SAFETY` | — | O (Telltales) | **불가**: 3rd-party 퍼미션 아님 |
| 브레이크 페달 | Brake | `CAR_DRIVE` | (선택) 트립 보조 | O (Brake Pedal) | 수집 안 함 (트립은 주행 상태로 판단) |
| 누적 주행거리 | Odometer | `CAR_DRIVE` | 트립 경계 조회 | O (Odometer) | **수집 확인(M2)**, AVD 기록률의 기준 |
| 트립 주행 시간 | Odometer | `CAR_DRIVE` | 트립 경계 조회 (분 단위) | X | 미지원(AVD) → 기록률은 거리 기준으로 대체 |
| 연비 | Fuel | `CAR_ENERGY` | 트립 경계 조회 | X | 미지원(AVD) |
| 전비, SOC | EvBattery | `CAR_ENERGY` | 트립 경계 조회 | SOC만 O | 전비 미지원, SOC 지원(AVD) |
| 주행/정차 상태 | `android.car` CarUxRestrictions | 없음 | 구간 (주행 판정·트립·간략 모드) | O (기어 D) | **수집 확인(M2)**, 앱에서 퍼미션 없이 호출됨 |

---

## 3. 도메인별 상세

표기: 메서드의 마지막 인자 `onFailed: (Exception) -> Unit`은 생략한다. `→`는 리스너 콜백 메서드다.

### 3.1 HVAC — `vehicle.getHVAC()` · `pleos.car.permission.CAR_CLIMATE`
출처: S `api/HVAC.kt`, `api/implement/HVACImpl.kt`, `callback/PropertyCallback.kt` · D [퍼미션](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission)

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·영역 |
|---|---|---|---|---|
| 공조 전원 | `checkHvacPowerCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit)` | `getHvacPowerState(area: SeatArea, onComplete: (Boolean?) -> Unit)` | `registerHvacPowerState(area, HvacPowerStateListener)` / `unregisterHvacPowerState(area, listener)` → `onHvacPowerStateUpdated(Boolean?)` | Boolean, SeatArea 존 |
| 에어컨 | `checkHvacAcCapabilities(onComplete: (CapabilityConfig, List<SeatArea>))` | `getHvacAcState(area, onComplete: (Boolean?))` | `registerHvacAcState` / `unregisterHvacAcState(area, HvacAcStateListener)` → `onHvacAcStateUpdated(Boolean?)` | Boolean |
| 설정 온도 | `checkHvacTemperatureCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Float>>, HvacTemperatureConfigArray?))` | `getHvacTemperature(area, unit: TemperatureUnit, onComplete: (Float?))` | `registerHvacTemperature` / `unregisterHvacTemperature(area, HvacTemperatureListener)` → `onHvacTemperatureUpdated(Float?)` (°C) | Float °C. `HvacTemperatureConfigArray`에 °C/°F 최소·최대·증분 |
| 풍량 | `checkHvacFanSpeedCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>))` | `getHvacFanSpeed(area, onComplete: (Int?))` | `registerHvacFanSpeed` / `unregisterHvacFanSpeed(area, HvacFanSpeedListener)` → `onHvacFanSpeedUpdated(Int?)` | Int 단계, 존별 min..max |
| 내기순환 | `checkHvacRecirculationCapabilities(onComplete: (CapabilityConfig, List<SeatArea>))` | `getHvacRecirculationState(area, onComplete: (Boolean?))` | `registerHvacRecirculationState` / `unregisterHvacRecirculationState(area, HvacRecirculationStateListener)` → `onHvacAutoRecirculationStateUpdated(Boolean?)` | Boolean. 콜백 이름에 Auto가 붙어 있지만 같은 리스너 타입을 재사용한 것. 실측(M2): true = 내기(Recirculate) |
| 성에 제거 | `checkHvacDefrosterCapabilities(onComplete: (CapabilityConfig, List<WindowArea>))` | `getHvacDefrosterState(area: WindowArea, onComplete: (Boolean?))` | `registerHvacDefrosterState` / `unregisterHvacDefrosterState(area: WindowArea, HvacDefrosterStateListener)` → `onHvacDefrosterStateUpdated(Boolean?)` | Boolean, **WindowArea** (앞/뒤 유리) |
| AUTO | `checkHvacAutoCapabilities` (List<SeatArea>) | `getHvacAutoState` | `registerHvacAutoState` / `unregisterHvacAutoState` → `onHvacAutoStateUpdated(Boolean?)` | Boolean |
| MAX A/C | `checkHvacMaxAcCapabilities` (List<SeatArea>) | `getHvacMaxAcState` | `registerHvacMaxAcState` / `unregisterHvacMaxAcState` → `onHvacMaxAcStateUpdated(Boolean?)` | Boolean |
| MAX 성에 | `checkHvacMaxDefrostCapabilities` (List<SeatArea>) | `getHvacMaxDefrostState` | `registerHvacMaxDefrostState` / `unregisterHvacMaxDefrostState` → `onHvacMaxDefrostStateUpdated(Boolean?)` | Boolean, SeatArea |
| 듀얼 | `checkHvacDualCapabilities` (List<SeatArea>) | `getHvacDualState` | `registerHvacDualState` / `unregisterHvacDualState` → `onHvacDualStateUpdated(Boolean?)` | Boolean |
| 공기청정 | `checkHvacAirCleanerCapabilities` (List<SeatArea>) | `getHvacAirCleanerState` | `registerHvacAirCleanerState` / `unregisterHvacAirCleanerState` → `onHvacAirCleanerStateUpdated(Boolean?)` | Boolean |

- 풍향(`getHvacFanDirection`)은 리스너가 없어 수집하지 않는다.
- 설정 온도에는 °C/°F를 함께 주는 `HvacTemperatureValueListener` → `onHvacTemperatureUpdated(celsius: Float?, fahrenheit: Float?)` 오버로드도 있다. 저장은 °C로 통일한다.

### 3.2 Seat — `vehicle.getSeat()` · `pleos.car.permission.CAR_SEATS`
출처: S `api/Seat.kt`, `api/implement/SeatImpl.kt`

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·영역 |
|---|---|---|---|---|
| 열선 | `checkSeatTemperatureCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>))` | `getHvacSeatTemperature(area: SeatArea, onComplete: (Int?))` | `registerHvacSeatTemperature` / `unregisterHvacSeatTemperature(area, HvacSeatTemperatureListener)` → `onHvacSeatTemperatureUpdated(Int?)` | Int 단계. KDoc 표현은 "heating/cooling temperature" |
| 통풍 | `checkSeatVentilationCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>))` | `getHvacSeatVentilationState(area, onComplete: (Int?))` | `registerHvacSeatVentilation` / `unregisterHvacSeatVentilation(area, HvacSeatVentilationListener)` → `onHvacSeatVentilationUpdated(Int?)` | Int 단계 |
| 메모리 호출 | `checkSeatMemoryCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>))` | 없음 | 없음 | `selectSeatMemory(area, value)`는 제어 API(`CONTROL_CAR_SEATS`) → **수집 불가** |

- VC는 운전석/조수석마다 `V3 V2 V1 Off H1 H2 H3` 컨트롤 하나만 있다.
- 실측(E, M1): `checkSeatTemperatureCapabilities` 범위가 1열 좌·우 **-3..3**, 2열 좌·우 **0..3**이다. VC `V2`는 `HvacSeatTemperature` = **-2**로 온다. → 이 AVD에서는 음수가 통풍(냉각), 양수가 열선이다. 저장은 원래 값을 그대로 두고, 리포트에서 부호로 열선/통풍을 나눈다. 실차에서 `HvacSeatVentilation`이 따로 지원되면 둘 다 기록되므로, M2에서 두 property의 capability를 같이 보고 중복 집계 규칙을 정한다.

### 3.3 SteeringWheel — `vehicle.getSteeringWheel()` · `pleos.car.permission.CAR_CLIMATE`
출처: S `api/SteeringWheel.kt`, `api/implement/SteeringWheelImpl.kt`. 문서 퍼미션 표에는 SteeringWheel 행이 없고, 소스상 열선은 `CAR_CLIMATE`, 조향각은 `CAR_DRIVE`다.

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·영역 |
|---|---|---|---|---|
| 열선 | `checkHvacSteeringWheelHeatCapabilities(onComplete: (CapabilityConfig, AreaBounds<Int>))` | `getHvacSteeringWheelHeatState(onComplete: (Int?))` | `registerHvacSteeringWheelHeatState` / `unregisterHvacSteeringWheelHeatState(HvacSteeringWheelHeatStateListener)` → `onHvacSteeringWheelHeatStateUpdated(Int?)` | Int 단계, 영역 없음. VC는 Off/Low/Medium/High |

### 3.4 SideMirror — `vehicle.getSideMirror()` · `pleos.car.permission.CAR_MIRRORS`
출처: S `api/SideMirror.kt`, `api/implement/SideMirrorImpl.kt`

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·영역 |
|---|---|---|---|---|
| 열선 | `checkHvacSideMirrorHeatCapabilities(onComplete: (CapabilityConfig, Map<MirrorArea, AreaBounds<Int>>))` | `getHvacSideMirrorHeatState(area: MirrorArea, onComplete: (Int?))` | `registerHvacSideMirrorHeatState` / `unregisterHvacSideMirrorHeatState(area, HvacSideMirrorHeatStateListener)` → `onHvacSideMirrorHeatStateUpdated(Int?)` | Int 단계, MirrorArea. VC에 없음 |

### 3.5 Wiper — `vehicle.getWiper()` · `pleos.car.permission.CAR_WINDOWS`
출처: S `api/Wiper.kt`, `api/implement/WiperImpl.kt`, `constant/control/WiperState.kt`, `WiperSwitch.kt`

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·영역 |
|---|---|---|---|---|
| 작동 상태 | `checkWiperStateCapabilities(onComplete: (CapabilityConfig, Map<WindowArea, List<WiperState>>))` | `getCurrentWiperState(area: WindowArea, onComplete: (WiperState?))` | `registerWiperState` / `unregisterWiperState(area, WiperStateListener)` → `onWiperStateUpdated(WiperState?)` | `WiperState`: OTHER, OFF, ON, SERVICE. WindowArea (앞/뒤 유리) |
| 스위치(속도) | `checkWiperSwitchCapabilities(onComplete: (CapabilityConfig, Map<WindowArea, List<WiperSwitch>>))` | `getCurrentWiperSwitch(area, onComplete: (WiperSwitch?))` | **없음** | `WiperSwitch`: OTHER, OFF, MIST, INTERMITTENT_LEVEL_1~5, CONTINUOUS_LEVEL_1~5, AUTO, SERVICE |

- 속도는 작동 상태 이벤트가 올 때마다 스위치를 1회 get해서 단계로 기록한다(폴링 아님). 작동 중 속도만 바뀌면 이벤트가 안 올 수 있다.
- 실측(M2): VC Low → `WiperState` ON 이벤트, 이때 읽은 스위치 = `CONTINUOUS_LEVEL_1`(레벨 21로 저장). 작동 중 High·Auto로 바꾸면 이벤트가 없어 속도 변경은 기록되지 않는다(예상한 한계).

### 3.6 Light — `vehicle.getLight()` · `pleos.car.permission.CAR_LIGHTS`
출처: S `api/Light.kt`, `api/implement/LightImpl.kt`, `constant/control/LightState.kt`

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·영역 |
|---|---|---|---|---|
| 하향등 | `checkLowBeamLightStateCapabilities(onComplete: (CapabilityConfig, List<LightState>))` | `getCurrentLowBeamLightState(onComplete: (LightState?))` | `registerLowBeamLightState` / `unregisterLowBeamLightState(LightStateListener)` → `onLightStateUpdated(LightState?)` | `LightState`: OFF, ON (DAYTIME_RUNNING은 deprecated). 영역 없음 |
| 상향등 | `checkHighBeamLightStateCapabilities` | `getCurrentHighBeamLightState` | `registerHighBeamLightState` / `unregisterHighBeamLightState` → `onLightStateUpdated` | 위와 같음 |
| 앰비언트 상태 | `checkAmbientLightStateCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, List<LightState>>))` | `getAmbientLightState(area: SeatArea, onComplete: (LightState?))` | `registerAmbientLightStateListener` / `unregisterAmbientLightStateListener(area, LightStateListener)` → `onLightStateUpdated` | LightState, SeatArea |
| 앰비언트 밝기 | `checkAmbientLightBrightnessCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>))` | `getAmbientLightBrightness(area, onComplete: (Int?))` | `registerAmbientLightBrightnessListener` / `unregisterAmbientLightBrightnessListener(area, AmbientLightBrightnessListener)` → `onBrightnessUpdated(Int?)` | Int |

- 스위치 계열(`getLowBeamLightSwitch` 등, `LightSwitch`: OFF, ON, DAYTIME_RUNNING, AUTOMATIC)은 리스너가 없어 쓰지 않는다. 실제 점등은 State로 본다.
- VC에는 앰비언트 "상태" 컨트롤이 없고 밝기·색만 있다. 실측(M2): VC의 밝기·색 변경으로는 상태 이벤트가 오지 않았다. 에뮬레이터에서 앰비언트 사용은 시연할 수 없다.

### 3.7 DrivingMode — `vehicle.getDrivingMode()` · `pleos.car.permission.CAR_DRIVE`
출처: S `api/DrivingMode.kt`, `api/implement/DrivingModeImpl.kt`, `constant/control/DrivingModeType.kt`, `CruiseControl.kt`

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·영역 |
|---|---|---|---|---|
| 드라이브 모드 | `checkDrivingModeStateCapabilities(onComplete: (CapabilityConfig))` | `getCurrentDrivingModeState(onComplete: (DrivingModeType?))` | `registerDrivingModeState` / `unregisterDrivingModeState(DrivingModeStateListener)` → `onDrivingModeStateUpdated(DrivingModeType?)` | NORMAL, ECO, SPORT |
| 크루즈 상태 | `checkCruiseControlStateCapabilities(onComplete: (CapabilityConfig))` | `getCruiseControlState(onComplete: (CruiseControl?))` | `registerCruiseControlState` / `unregisterCruiseControlState(CruiseControlStateListener)` → `onCruiseControlStateUpdated(CruiseControl?)` | OTHER, ENABLED, ACTIVATED, USER_OVERRIDE, SUSPENDED, FORCED_DEACTIVATION_WARNING. "사용 중" = ACTIVATED 구간 |

- VC 라벨은 Not ready / Ready / Longitudinal / Lateral / Full autonomous다.
- 실측(M2): 값은 2(ACTIVATED), 1(ENABLED), -6이 왔다. 시스템 앱 로그상 2 = Active, 1 = Ready, -6 = Off다. -6은 `CruiseControl`에 없어 SDK가 null로 넘기고, 수집기는 구간을 닫는다. VC 버튼과 값의 대응은 두 번 시험해도 일관되지 않아 확정하지 못했다.
- 드라이브 모드는 VC에 없어 에뮬레이터에서 시연할 수 없다.

### 3.8 Window — `vehicle.getWindow()` · `pleos.car.permission.CAR_WINDOWS`
출처: S `api/Window.kt`, `api/implement/WindowImpl.kt` · SA README (AVD `ROW_1_LEFT` 범위 0..10)

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·영역 |
|---|---|---|---|---|
| 창문 위치 | `checkWindowPositionCapabilities(onComplete: (CapabilityConfig, Map<WindowArea, AreaBounds<Int>>))` | `getCurrentWindowPosition(area: WindowArea, onComplete: (Int?))` | `registerWindowPosition` / `unregisterWindowPosition(area, WindowPositionListener)` → `onWindowPositionUpdated(Int?)` | Int, 창문별 min..max. VC는 % 슬라이더 |

- "열고 달린 시간" = (위치 > min) 구간과 주행 구간(3.14)의 교집합으로 집계 시점에 계산한다. 실측(M2): 앞좌석 왼쪽 창문(영역 16) 범위 0..10, 0이 닫힘

### 3.9 Door — `vehicle.getDoor()` · `pleos.car.permission.CAR_DOORS`
출처: S `api/Door.kt`, `api/implement/DoorImpl.kt` · SA (Door Position 레퍼런스 흐름, AVD 범위 0..1)

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·영역 |
|---|---|---|---|---|
| 문 위치 | `checkDoorPositionCapabilities(onComplete: (CapabilityConfig, Map<DoorArea, AreaBounds<Int>>))` | `getCurrentDoorPosition(area: DoorArea, onComplete: (Int?))` | `registerDoorPosition` / `unregisterDoorPosition(area, DoorPositionListener)` → `onPositionUpdated(Int?)` | Int (AVD 0..1). DoorArea에 HOOD, REAR 포함 |

- 열림 횟수 = min → min 초과 전이 1회. 트렁크(REAR)와 후드(HOOD)는 별도 키로 센다.

### 3.10 TurnSignal — `vehicle.getTurnSignal()` · `pleos.car.permission.CAR_LIGHTS`
출처: S `api/TurnSignal.kt`, `api/implement/TurnSignalImpl.kt`, `constant/area/TurnSignalValue.kt`

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·영역 |
|---|---|---|---|---|
| 방향지시등 | `checkTurnSignalStateCapabilities(onComplete: (CapabilityConfig))` | `getCurrentTurnSignalState(onComplete: (TurnSignalValue?))` | `registerTurnSignalState` / `unregisterTurnSignalState(TurnSignalStateListener)` → `onTurnSignalStateUpdated(TurnSignalValue?)` | OFF, LEFT, RIGHT |

- 횟수 = OFF → LEFT/RIGHT 전이.
- 실측(M2): 점멸 중 반복 이벤트는 없었다(왼쪽 = 2, 오른쪽 = 1, 꺼짐 = 0). 1.5초 병합 규칙은 실차 대비용으로 남긴다. 비상등은 방향지시등 상태로 오지 않는다.

### 3.11 Brake — `vehicle.getBrake()` · `pleos.car.permission.CAR_DRIVE`
출처: S `api/Brake.kt`, `api/implement/BrakeImpl.kt`, `api/Safety.kt`, `constant/telltale/TelltaleType.kt`

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·영역 |
|---|---|---|---|---|
| 브레이크 페달 | `checkBrakePedalStatusCapabilities(onComplete: (CapabilityConfig))` | `getCurrentBrakePedalStatus(onComplete: (Boolean?))` | `registerBrakePedalStatus` / `unregisterBrakePedalStatus(BrakePedalStatusListener)` → `onBrakePedalStatusUpdated(Boolean?)` | Boolean |

- **주차 브레이크는 공개 API가 없다.** Brake 도메인에는 페달만 있다. 경고등(`Safety.getTelltalesInfo`, `registerTelltales`)에 `PARKING_BRAKE`, `ELECTRONIC_PARKING_BRAKE`가 있지만 `PRIVATE_CAR_SAFETY`(SDK KDoc상 signature|privileged)가 필요하다. 샘플앱도 `PRIVATE_*`는 넣지 않았다. → 트립 경계는 주행 상태(3.14)와 Odometer로 판단한다.
- 기어(`getGear()`)는 `@hide`라 쓰지 않는다.

### 3.12 Odometer — `vehicle.getOdometer()` · `pleos.car.permission.CAR_DRIVE`
출처: S `api/Odometer.kt`, `api/implement/OdometerImpl.kt`

| 기능 | capability | get | 리스너 등록 / 해제 → 콜백 | 값·단위 |
|---|---|---|---|---|
| 누적 주행거리 | `checkCurrentDistanceDrivenCapabilities(onComplete: (CapabilityConfig))` | `getCurrentDistanceDriven(onComplete: (Float?))` | `registerDistanceDriven` / `unregisterDistanceDriven(DistanceDrivenListener)` → `onDistanceDrivenUpdated(Float?)` | Float **km** |
| 트립 현재 주행 시간 | `checkTripCurrentDrivingTimeCapabilities` | `getTripCurrentDrivingTime(onComplete: (Int?))` | `registerTripCurrentDrivingTime` / `unregisterTripCurrentDrivingTime(DrivingTimeListener)` → `onDrivingTimeUpdated(Int?)` | Int **분** |
| 트립 누적 주행 시간 | `checkTripCumulativeDrivingTimeCapabilities` | `getTripCumulativeDrivingTime(onComplete: (Int?))` | `registerTripCumulativeDrivingTime` / `unregisterTripCumulativeDrivingTime(DrivingTimeListener)` | Int **분** |
| 트립 현재 주행 거리 | `checkTripCurrentDrivingDistanceCapabilities` | `getTripCurrentDrivingDistance(onComplete: (Float?))` | `registerTripCurrentDrivingDistance` / `unregisterTripCurrentDrivingDistance(DistanceDrivenListener)` | Float **m** |

- `DistanceDrivenListener`를 누적 주행거리(km)와 트립 거리(m)가 같이 쓴다. 단위를 섞지 않도록 property별 인스턴스를 둔다.
- 트립 시간이 **분 단위**라 기록률 분모의 해상도가 1분이다.
- 실측(M2): 에뮬레이터에서 트립 누적 주행 시간은 미지원이고, 누적 주행거리만 지원한다. 그래서 기록률은 거리 기준으로 대체된다. 트립 초기화 시점은 실차에서만 확인할 수 있다.

### 3.13 Fuel / EvBattery — `pleos.car.permission.CAR_ENERGY`
출처: S `api/Fuel.kt`, `api/EvBattery.kt` 및 각 Impl

| 기능 | capability | get | 리스너 | 값·단위 |
|---|---|---|---|---|
| 연비 | `checkCurrentFuelEconomyCapabilities(onComplete: (CapabilityConfig))` | `getCurrentFuelEconomy(onComplete: (Float?))` | `registerCurrentFuelEconomy(FuelEconomyListener)` → `onFuelEconomyUpdated(Float?)` | Float, KDoc상 L/100km |
| 연료 잔량 | `checkFuelPercentCapabilities` | `getFuelPercent(onComplete: (Int?))` | `registerFuelPercent(FuelPercentListener)` → `onFuelPercentUpdated(Int?)` | Int % |
| 전비 | `checkCurrentEvEfficiencyCapabilities` | `getCurrentEvEfficiency(onComplete: (Float?))` | `registerCurrentEvEfficiency(EvBatteryEfficiencyListener)` → `onEvBatteryEfficiencyUpdated(Float?)` | Float, KDoc상 km/kWh |
| 배터리 SOC | `checkEvBatteryStateOfChargeCapabilities` | `getEvBatteryStateOfCharge(onComplete: (Int?))` | `registerEvBatteryStateOfCharge(EvBatteryStateOfChargeListener)` → `onEvBatteryStateOfChargeUpdated(Int?)` | Int % |

- Instantaneous / SinceCharge / Cumulative 변형도 있다. 순간값 리스너는 이벤트가 잦을 수 있어 등록하지 않고, 트립 시작·종료 때 get만 한다.
- 연료차/전기차 구분은 차종명이 아니라 capability로 한다(둘 다 지원하면 둘 다 기록).
- 실측(M2): 에뮬레이터에서 "Current" 연비·전비는 미지원, SOC는 지원. 기준 구간은 실차에서만 확인할 수 있다.

### 3.14 주행 상태 — `android.car` CarUxRestrictions (AAOS 표준)
출처: D [주행 안전 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) ("`CarUxRestrictions`를 기준으로 주행 중 제한 상태를 확인") · E (6.3절)

- 빌드: `useLibrary("android.car")`. 컴파일용 `platforms/android-34/optional/android.car.jar`가 있고, 기기에서는 부트 클래스패스의 `/apex/com.android.car.framework/javalib/android.car-module.jar`가 런타임을 제공한다(E). `CarUxRestrictionsManagerService`에 클라이언트 9개가 등록돼 동작 중이다.
- 사용: `Car.createCar(context)` → `getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager` → `registerListener { r -> r.isRequiresDistractionOptimization }`, `getCurrentCarUxRestrictions()`. AOSP 기준 별도 퍼미션은 필요 없다. **M3에서 실기 호출로 확인**
- 실측: 주차 → `DO=false, UxR=0`. 기어 D(속도 0, idling) → `DO=true, UxR=0x50`(NO_VIDEO | NO_SETUP). moving도 설정상 0x50이다.
- **`distractionOptimized` 메타데이터가 없는 Activity는 주행 중 시스템이 "주행 중에는 사용할 수 없습니다" 화면으로 가린다(E, `.local/m0-driving-block.png`).** → 리포트 Activity에 `<meta-data android:name="distractionOptimized" android:value="true"/>`를 선언하고, 간략 모드는 앱이 직접 구현한다.
- 한계: idling과 moving의 UxR 값이 같아 "정차 중(D단)"과 "달리는 중"을 구분할 수 없다. 3rd-party에 `CarDrivingStateManager`는 공개되지 않는다. 차속(`CarPropertyManager` + `PERF_VEHICLE_SPEED`)은 Pleos 승인 여부가 불명확하다. → MVP는 "DO 필요 상태(D/R단)"를 주행 구간으로 본다. 외부 문의 대상.

---

## 4. 퍼미션

출처: D [퍼미션](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission), [심사 기준](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability) · S (`constant/PleosPermission.kt`, KDoc protection level) · E (`pm list permissions -f`)

- 문서: `CAR_*`는 조회(get), `CONTROL_CAR_*`는 제어(set). 매니페스트에 선언해야 하며 **App Market과 사전 협의·승인된 퍼미션만 선언할 수 있고, 미승인 퍼미션을 선언하면 심사를 통과할 수 없다.**
- E: 아래 `CAR_*`는 모두 `protectionLevel:normal` → 설치 시 자동 부여, 런타임 요청 없음.
- SDK가 매니페스트 병합으로 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 퍼미션과 `<queries>`(action `VEHICLE_START`)를 자동으로 넣는다.

**이 앱의 선언안 (조회만)**

| 퍼미션 | 쓰는 곳 |
|---|---|
| `pleos.car.permission.CAR_CLIMATE` | HVAC, 스티어링 휠 열선 |
| `pleos.car.permission.CAR_SEATS` | 시트 열선·통풍 |
| `pleos.car.permission.CAR_MIRRORS` | 사이드미러 열선 |
| `pleos.car.permission.CAR_WINDOWS` | 창문, 와이퍼 |
| `pleos.car.permission.CAR_LIGHTS` | 전조등, 앰비언트, 방향지시등 |
| `pleos.car.permission.CAR_DRIVE` | 드라이브 모드, 크루즈, 브레이크, Odometer |
| `pleos.car.permission.CAR_DOORS` | 문 (현재 매니페스트에 이미 있음) |
| `pleos.car.permission.CAR_ENERGY` | 연비, 전비, SOC |
| `android.permission.FOREGROUND_SERVICE` | 수집 서비스 (7절 추천안 채택 시) |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | 〃 |
| `android.permission.POST_NOTIFICATIONS` | FGS 알림 표시 (API 33+ 런타임 퍼미션) |
| `android.permission.RECEIVE_BOOT_COMPLETED` | 부팅 자동 시작. **지금은 debug 매니페스트에만 선언**(릴리스에는 승인 후 추가) |

- 넣지 않는 것: `CONTROL_*`, `PRIVATE_*`, `CAR_INFO`, `CAR_SAFETY`, `CAR_TOLL`.
- M2에서 위 8개 `CAR_*`를 모두 매니페스트에 선언했다. 처음 빌드에서 5개를 빠뜨렸을 때 해당 기능은 `ERROR_SECURITY_PERMISSION`으로 미지원 처리되고 앱은 계속 동작했다.
- SA 매니페스트에는 `CAR_SPEED`, `CAR_POWERTRAIN`, `CAR_MILEAGE`, `READ_WINDSHIELD_WIPERS` 등도 있지만 SDK 2.3.2의 `PleosPermission`에 없고 에뮬레이터에도 정의되어 있지 않다(E). 선언하지 않는다.

---

## 5. 빌드 설정

출처: D [프로젝트 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup), [에뮬레이터](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [샘플앱](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app), [앱 보안 가이드](https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide) · 현재 저장소

### 5.1 의존성 (현재 저장소에 반영됨)
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        maven {
            name = "Pleos"
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content { includeGroup("ai.pleos.playground") }
        }
    }
}
// app/build.gradle.kts
dependencies { implementation("ai.pleos.playground:Vehicle:2.3.2") }
```
- 문서 기준 버전은 2.3.2. 샘플앱 `libs.versions.toml`은 아직 `2.3.1`이고, README에 적힌 대로 2.3.1은 저장소에서 404였다.

### 5.2 SDK 레벨

| 항목 | 현재 | 근거 | 제안 |
|---|---|---|---|
| minSdk | 28 | 문서: Playground 배포 앱은 API 28 이상(서명 키 교체·lineage) | 유지 |
| targetSdk | 34 | 에뮬레이터 API 34 (Android 14). SA는 35 | **34 유지** 제안. 35로 올리면 API 35 기기에서 edge-to-edge 강제, dataSync 시간 제한 등이 적용된다. 실차 Android 버전·요구 targetSdk는 문의 |
| compileSdk | **36** (M1에서 변경) | Compose BOM 2025.12, activity-compose 1.12 등이 compileSdk 35 이상을 요구 | 런타임 동작은 targetSdk가 정하므로 영향 없음 |
| JDK / Gradle / AGP / Kotlin | 17 / 8.14.3 / 8.13.2 / 2.3.21 | README | 유지 |

참고: Vehicle Service 앱의 minSdk는 31이다(E). 실차는 API 31 이상으로 추정되지만 minSdk 28이 문서 요구라 그대로 둔다.

### 5.3 M1 이후 추가할 빌드 항목
- Compose: `org.jetbrains.kotlin.plugin.compose` (Kotlin과 같은 버전), Compose BOM, Material3, activity-compose, lifecycle-runtime-compose.
- Room + KSP. **Kotlin 2.3.21과 호환되는 KSP 버전은 M1에서 확인해 고정한다.**
- kotlinx-coroutines-android. SDK가 1.6.3을 끌어오지만 앱에서 최신 버전을 명시한다.
- `useLibrary("android.car")` (CarUxRestrictions, M3).
- 테스트: JUnit4, kotlinx-coroutines-test, (Room은 JVM 테스트에서 Fake Repository로 대체).
- 빌드 플레이버 `mode { prod, demo }`. demo 코드는 `src/demo/`에만 둔다. 릴리스 제출은 `prodRelease`.
- 릴리스: `isMinifyEnabled = true`, 로그 제거 규칙, 정식 서명 인증서(보안 가이드).
- DI: 수동 DI(`AppContainer`). Hilt 런타임은 vehicle-data를 통해 들어오지만 플러그인과 어노테이션 처리는 쓰지 않는다.

### 5.4 매니페스트 선언안
```xml
<manifest>
    <uses-feature android:name="android.hardware.type.automotive" android:required="true" />
    <!-- 4절 퍼미션 -->
    <application android:allowBackup="false" ...>
        <activity android:name=".ui.MainActivity" android:exported="true">
            <meta-data android:name="distractionOptimized" android:value="true" />
            <!-- screenOrientation은 결정 필요 (구현 계획 문서의 질문 참고) -->
            <intent-filter>MAIN / LAUNCHER</intent-filter>
        </activity>
        <service
            android:name=".collector.CollectorService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="vehicle_feature_usage_logging" />
        </service>
        <!-- 기능 플래그 기본 off. 승인 전에는 enabled=false로 둔다 -->
        <receiver
            android:name=".collector.BootReceiver"
            android:enabled="false"
            android:exported="false">
            <intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" /></intent-filter>
        </receiver>
    </application>
</manifest>
```
- `allowBackup="false"`: non-GAS라 Google 자동 백업이 동작하지 않는다(보안 가이드 권장).
- 외부에서 호출할 필요 없는 컴포넌트는 `exported="false"`(보안 가이드 필수). BOOT_COMPLETED는 시스템(uid 1000)이 보내므로 `exported="false"`로도 수신될 것으로 본다. **M2에서 실측**

---

## 6. 에뮬레이터 실측 (2026-11-03)

### 6.1 환경
| 항목 | 값 |
|---|---|
| AVD | `Pleos_Connect_2_1_0_API_34` (device `Pleos_Connect_v2_0`, arm64, RAM 4GB, cold boot) |
| Android | 14 / API 34, `Pleos/crp_avd_arm64/...:14/RELEASE.release_connect_v2.1.0/...:user/test-keys` |
| 유저 | `0` 헤드리스 시스템 유저 (flags 0x813: SYSTEM·ADMIN·PRIMARY, FULL 아님), `10` 드라이버 유저 (flags 0x412, 현재 유저). `ro.fw.mu.headless_system_user=true` |
| Vehicle Service | `ai.pleos.playground.service.vehicle` 2.3.22 (versionCode 74, minSdk 31). SDK 요구 최소 2.3.13(65) 충족. u0·u10 모두 설치 |
| 공유 라이브러리 | `ai.pleos.playground.car`, `car-frameworks-service`, `androidx.window.sidecar` (`ai.pleos.playground.car`의 용도는 미확인) |
| 앱 프리저 | `use_freezer=true` (`dumpsys activity`) |
| FGS 관련 기본값 | `default_background_fgs_starts_restriction_enabled=true`, `fgs_start_foreground_timeout=10000` |
| 앱 대기 버킷 | 환경 확인 앱 `5` = `STANDBY_BUCKET_EXEMPTED` (이 이미지에서 앱 대기 제한이 면제되는지는 추가 확인 필요) |
| Android Studio | 2026.1 (AI-261.26222.65) |

### 6.2 부팅 브로드캐스트 (`dumpsys activity broadcasts history`)
- `LOCKED_BOOT_COMPLETED`와 `BOOT_COMPLETED`가 **u0과 u10에 각각** 전달됐다. u0 → u10 순서, 차이 약 1.4초.
- `adb install`로 설치한 현재 앱은 **u0과 u10 모두에 설치돼 있다.** 리시버를 두면 헤드리스 시스템 유저(u0)에서도 깨어난다. → u0에서는 아무것도 하지 않아야 한다(`UserManager.isHeadlessSystemUserMode()` && `isSystemUser`). 개발 중에는 `adb install --user current`를 쓴다.
- 우리 앱의 리시버가 실제로 깨어나는 시점은 리시버를 만든 뒤 M2에서 실측한다.

### 6.3 주행 상태와 화면 차단
- 주차: `DO=false, UxR=0`, CarDrivingState 0(parked).
- VC에서 기어 D: `DO=true, UxR=0x50`, CarDrivingState 1(idling). VC의 ACCEL을 누르고 있으면 차속이 오른다(화면 안내 "Select D + hold ACCEL").
- 주행 중 `distractionOptimized` 없는 환경 확인 앱을 실행하니 Activity는 RESUMED지만 화면은 "주행 중에는 사용할 수 없습니다"로 가려졌다.
- 확인 후 기어를 P로 되돌렸다.

### 6.4 하지 못한 것
- **(M1에서 해결)** 우리 앱으로 Vehicle Service 연결을 확인했다: `ServiceConnected`, 좌석 열선 4개 영역, 에어컨 1개 영역. 아래는 M0 당시 기록이다.
- 샘플앱 수준의 Vehicle Service 연결 확인은 하지 않았다. 샘플앱 사본을 스크래치 폴더에서 빌드하려 했으나, 외부 코드 실행이라 자동 모드 권한 분류기가 막았다. 방법은 둘 중 하나다.
  1. 사용자가 Android Studio에서 샘플앱을 열고 `libs.versions.toml`의 `vehicleSdk`를 `2.3.2`로 바꿔 실행한다. "Door Position → Capability"가 응답하면 연결 확인.
  2. M1 첫 단계에서 우리 앱 코드로 `initialize()` → capability 1회 호출을 확인한다.

### 6.5 Virtual Console 컨트롤 목록
Gear Selection, Brake Pedal, ETCS, Telltales(주차 브레이크·방향지시등 등 경고등), Notification, Door Position, Door Lock, Trunk/Frunk, Cruise Control State, Cruise Target Speed, Odometer, Remaining Range, Battery Energy/SOC/Capacity, Charge 관련, Steering Angle, Steering Wheel Heat, HVAC Power, Air Conditioning, HVAC Auto, HVAC Dual, Air Recirculation, Fan Speed, Fan Direction, Defroster, Target Temperature, Seat Temperature(V3~H3), Air Cleaner, Cabin Lights, Reading Lights, Ambient Brightness/Color, Exterior Lights Mode, High Beam, Hazard, Stop Lights, Door Lights, Turn Signal State, Window Position, Window Lock, Seat Occupancy, Windshield Wiper Switch, Glove Box, 주행 시각화(ACCEL/BRAKE).
- 없는 것: 드라이브 모드, 사이드미러 열선, 트립 주행 시간, 연비·전비.

---

## 7. 백그라운드 수집 방식 비교와 추천

출처: D [앱 구조·동작 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model), [성능](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/performance-resource-mgmt) · E (6절) · Android 플랫폼 동작(아래 "플랫폼" 표기는 AOSP 일반 동작이며 이 에뮬레이터에서 아직 실측하지 않은 것)

### 7.1 전제
- 문서: 화면에서 사라진 앱은 종료되지 않고 백그라운드에 남을 수 있다. 백그라운드에서 계속할 작업은 실행 조건과 중단·재개 정책을 정의하고, 상주·타이머·폴링·네트워크를 최소화한다. FGS·부팅 자동 시작에 대한 언급은 없다.
- E: Android 14, 앱 프리저 켜짐, 헤드리스 시스템 유저 구조.
- 플랫폼: 화면을 떠난 앱의 일반 started service는 약 1분 뒤 중지된다(Android 8+). 캐시 상태가 된 프로세스는 프리저에 들어가 코드가 실행되지 않으므로 리스너 콜백을 제때 처리하지 못한다. 백그라운드에서 FGS를 시작하는 것은 예외(앱이 보이는 중, BOOT_COMPLETED·MY_PACKAGE_REPLACED 수신 직후 등)를 빼고 막혀 있다.

### 7.2 서비스 유형 비교

| 방식 | 앱을 내린 뒤 수집 | 부팅 자동 시작 | Android 15 이상 영향 | 리스크 | 평가 |
|---|---|---|---|---|---|
| A. 서비스 없음 (화면 범위) | X | X | 없음 | 완료 기준 1번 불충족 | 비추천 |
| B. 일반 started/bound Service | 약 1분 뒤 중지, 프리저 | 불가 | 같음 | 사실상 A와 같음 | 폴백 전용 |
| C. FGS `dataSync` | O | API 34 O / target 35부터 BOOT_COMPLETED에서 시작 불가 | target 35: 24시간에 6시간 제한, `onTimeout` | 용도(업로드·다운로드·백업) 불일치 → 심사 리스크 | 비추천 |
| D. FGS `specialUse` | O | O (API 34·35 모두 BOOT_COMPLETED 시작 허용 타입) | 시간 제한 없음 | 사용 사유(subtype) 기재, 상시 알림, Pleos 허용 여부 미확인 | **추천 (조건부)** |
| E. FGS `connectedDevice` | O | O | 없음 | 전제 런타임 퍼미션(BLUETOOTH_CONNECT 등) 필요, 용도 불일치 | 비추천 |
| F. WorkManager/JobScheduler | X (주기 실행 = 폴링) | — | — | 리스너 방식과 맞지 않음 | 집계용으로도 MVP엔 불필요 |

### 7.3 추천안: FGS `specialUse` 1개 + 리스너 수집
- 서비스: `CollectorService` 1개. `foregroundServiceType="specialUse"`, subtype `vehicle_feature_usage_logging`, `START_STICKY`.
- 시작 조건
  1. 앱 실행 시 (온보딩 동의 완료 후) Activity에서 `startForegroundService`. 화면에 보이는 중이라 FGS 시작이 허용된다.
  2. 부팅 완료 (기능 플래그 기본 off, 리시버 `enabled=false`). u0(헤드리스 시스템 유저)에서는 무시.
  3. 프로세스 재생성: `START_STICKY`로 재시작되면 복구 루틴부터 돈다. 재시작이 FGS 백그라운드 시작 제한에 걸리면 수집은 다음 앱 실행까지 멈추고, 그때 `lastAlive` 기준으로 구간을 닫는다. **실측(E, M1): `kill -9` 후 1초 뒤 시스템이 서비스를 다시 시작했고 FGS 시작도 허용됐다. 열린 구간은 마지막 하트비트 시각으로 닫혔다.**
  4. (선택) `MY_PACKAGE_REPLACED`: 업데이트 후 재시작 + capability 스냅샷 갱신.
- 중단 조건: 사용자가 기록을 끔, 모든 카테고리 off, 동의 철회. 시동 off는 3rd-party가 받을 API가 없다(`CarPowerManager`는 시스템 퍼미션). 프로세스가 종료되면 다음 시작 때 `lastAlive`로 열린 구간을 닫는다(closedBy=HEARTBEAT).
- 리소스: 상태 변화 리스너만 쓴다. 타이머는 60초 하트비트 1개(lastAlive 저장 + 버퍼 flush 겸용). 네트워크·wakelock 없음. 고빈도 리스너(순간 연비, 조향각, 누적거리)는 등록하지 않는다.
- 알림: FGS 알림이 "백그라운드 기록 중" 표시를 겸한다(D 요구사항). **TODO: AAOS/Pleos에서 FGS 알림이 어디에 어떻게 보이는지, POST_NOTIFICATIONS 거부 시 동작 실측**
- 폴백 (FGS 불허 시): B안 + 화면에 있을 때만 수집 + `lastAlive` 복구. 빈 구간은 채우지 않고 기록률로 드러낸다.

### 7.4 부팅 시 자동 시작 방법 비교

| 방법 | 동작 | 리스크 |
|---|---|---|
| 매니페스트 `BOOT_COMPLETED` 리시버 | u0과 u10 모두 수신(E). FGS 시작 허용 예외에 해당 | u0 가드 필요. 드라이버 전환 시 새 유저에서도 수신할 것으로 예상(미실측). Pleos 허용 여부 미확인 |
| `LOCKED_BOOT_COMPLETED` + `directBootAware` | 잠금 해제 전에 실행 | Room(CE 저장소)을 쓸 수 없음. 복잡도만 늘어남 |
| 자동 시작 없음 (앱 실행 시만) | 정책 리스크 없음 | 앱을 열기 전 주행은 미기록 → 기록률로 표시 |

**추천:** 기본은 앱 실행 시 시작. `BOOT_COMPLETED`는 리시버 코드와 u0 가드까지 구현해 두되 플래그 기본 off, Pleos 승인 후 켠다.

**M2 구현과 실측**
- 구현: `collector/BootReceiver.kt`(헤드리스 시스템 유저면 무시), `collector/BootStartSetting.kt`(기능 플래그 = 리시버의 컴포넌트 활성 상태, 시스템이 저장). 리시버와 `RECEIVE_BOOT_COMPLETED`는 `src/debug/AndroidManifest.xml`에만 있고 `android:enabled="false"`다. 확인 화면의 "부팅 시 자동 시작 (디버그 측정용)" 토글로 켜고 끈다.
- 이 유저 빌드에서는 셸(`pm enable`)이 앱 컴포넌트 상태를 바꿀 수 없다(`SecurityException`). 그래서 플래그는 앱이 직접 바꾼다.
- 재부팅 실측: u10에서 `BOOT_COMPLETED` 게시 약 11초 뒤 리시버가 깨어나 FGS 시작이 허용됐고, 약 4초 뒤 수집이 시작됐다. `exported="false"`로도 수신된다.
- u0에 설치만 하고 한 번도 실행하지 않은 앱은 `stopped=true` 상태라 부팅 브로드캐스트를 받지 않았다. 그래서 u0 가드 코드 경로는 실기에서 타지 않았다(코드는 유지).

---

## 8. TODO(SDK) / 실측 목록

| # | 항목 | 언제 |
|---|---|---|
| 1 | ~~Vehicle Service 연결 확인~~ → 확인(M1, 우리 앱) | 완료 |
| 2 | ~~첫 리스너 등록 직후 초기값 콜백~~ → 온다 | 완료 |
| 3 | ~~미지원 property의 capability 결과~~ → `onFailed(ERROR_NOT_SUPPORTED_PROPERTY)` | 완료 |
| 4 | ~~VC V 단계의 property~~ → `HvacSeatTemperature` 음수 | 완료 |
| 5 | ~~콜백 스레드~~ → SDK 리스너는 Binder 스레드(`binder:…`) | 완료 |
| 6 | ~~내기순환 true~~ → 내기(Recirculate) | 완료 |
| 7 | ~~Wiper Switch 변경~~ → 켜짐/꺼짐만 이벤트, 작동 중 속도 변경은 이벤트 없음 | 완료 (한계 확인) |
| 8 | ~~앰비언트 상태~~ → VC 밝기·색 변경으로는 상태 이벤트 없음 | 완료 (AVD 시연 불가) |
| 9 | Cruise 값 의미는 확인(2=Active, 1=Ready, -6=Off, 시스템 앱 로그 기준). VC 버튼↔값 대응은 두 번 시험에서 불일치 | 부분 완료 |
| 10 | ~~창문·문 min~~ → 0이 닫힘(창문 0..10, 문 0..1) | 완료 |
| 11 | ~~점멸 패턴~~ → 점멸 중 반복 이벤트 없음(왼쪽=2, 오른쪽=1). 비상등은 방향지시등으로 오지 않음 | 완료 |
| 12 | 트립 누적 주행 시간은 AVD에서 미지원 → 초기화 시점은 실차에서만 확인 가능 | 실차 확인 필요 |
| 13 | "Current" 연비·전비는 AVD에서 미지원 → 실차에서만 확인 가능 | 실차 확인 필요 |
| 14 | ~~부팅 수신~~ → u10에서 BOOT_COMPLETED 약 11초 뒤 수신, `exported=false`로 수신됨, FGS 시작 허용. 한 번도 실행 안 된 u0 설치본은 stopped 상태라 수신 안 함 | 완료 |
| 15 | ~~START_STICKY 재시작 시 FGS 시작~~ → 허용(Android 14 AVD) | 완료 |
| 16 | ~~FGS 알림 표시~~ → 동의 직후 `POST_NOTIFICATIONS` 시스템 시트로 요청, 허용 시 알림 센터에 "차량 기능 사용을 기록하고 있어요" 표시. 거부해도 FGS는 동작(알림만 안 보임, 코드 경로) | 완료 (거부 경로는 실기 미확인) |
| 17 | ~~CarUxRestrictionsManager 호출~~ → 퍼미션 없이 동작(M2) | 완료 |
| 18 | ~~릴리스(R8) 빌드에서 SDK 동작~~ → SDK 기본 규칙만으로는 실패, 앱 규칙 추가 후 정상 (13절) | 완료 |

---

## 9. M1 에뮬레이터 실측 기록 (2026-11-03)

설치: `adb install --user current` (드라이버 유저 10에만 설치. u0에는 설치되지 않음을 확인)

| 확인 항목 | 결과 |
|---|---|
| 퍼미션 | `CAR_CLIMATE`, `CAR_SEATS`, `CAR_DOORS`, `FOREGROUND_SERVICE(_SPECIAL_USE)` 설치 시 부여. `POST_NOTIFICATIONS`는 미부여(런타임 퍼미션, M4에서 요청) |
| 실행 | `am start -W` TotalTime 501ms |
| 연결 | `ServiceConnected` 후 약 0.6초 만에 capability 2건 응답 |
| capability | 좌석 열선: 1열 좌(1)·우(4) -3..3, 2열 좌(16)·우(64) 0..3. 에어컨: 영역 1개 = 117 (1열 좌·우, 2열 좌·가운데·우를 묶은 존) |
| FGS | `isForeground=true`, `types=0x40000000`(specialUse) |
| 백그라운드 수집 | 설정 앱을 띄워 우리 Activity가 `STOPPED`인 상태에서 VC로 운전석 H1→H3→Off→V2→Off, 에어컨 토글 → 같은 PID로 이벤트 전부 수신, 60초 하트비트로 DB 저장 확인 |
| 저장된 구간 | 열선 1단계 3.0초, 3단계 3.0초, -2단계(통풍) 3.0초, 에어컨 77초(EVENT) 등 |
| 프로세스 강제 종료 | `run-as … kill -9` → 1초 뒤 `START_STICKY` 재시작, FGS 허용, 열린 에어컨 구간을 `lastAlive`(10:58:06)로 닫음(`HEARTBEAT`), 죽어 있던 약 1분은 비워 둠 |
| 기록 중지 버튼 | 열린 구간 `STOP`으로 닫힘, SDK `release()`, 서비스 종료 |
| 자원 (백그라운드, 이벤트 없음) | PSS 약 78MB, CPU 0.0% (`top` 5초 간격 4회). 정식 측정은 M5 |
| 한계 | 마지막 하트비트 이후 최대 60초의 변경은 프로세스가 죽으면 저장되지 않는다(설계상 허용) |

---

## 10. M2 에뮬레이터 실측 기록 (2026-11-03)

설치: versionCode 3, `adb install --user current -r`로 M1 빌드 위에 덮어 설치 → Room 1→2 자동 마이그레이션 정상(데이터 유지, 오류 없음).

| 확인 항목 | 결과 |
|---|---|
| capability | 25개 중 22개 지원. 미지원: MAX A/C, 드라이브 모드(`ERROR_NOT_SUPPORTED_PROPERTY`). 시트 통풍은 별도 property로도 2개 영역 지원 |
| 차량 지표 | 누적 주행거리, SOC 지원. 트립 누적 주행 시간·연비·전비 미지원 → 에뮬레이터 기록률은 거리 기준 |
| capability 스냅샷 | versionCode 3으로 저장. 예: 설정 온도 150..300(15.0~30.0°C), 시트 열선 1열 -3..3·2열 0..3, 창문 4개 0..10, 드라이브 모드는 미지원 행 |
| VC 조작 → 이벤트 (앱 STOPPED 상태) | 내기순환 true/false, 앞유리 성에 제거(영역 1), 공조 전원, 시트 -2/2, 문 열림·닫힘 2회, 하향·상향등, 방향지시등 왼쪽(2)·오른쪽(1), 창문 6→0, 와이퍼 ON(스위치 8=CONTINUOUS_LEVEL_1)→OFF, 크루즈, 핸들 열선 1→0 |
| 이벤트가 오지 않은 조작 | 시트 V2의 통풍 property, 앰비언트 밝기, 비상등, 와이퍼 작동 중 High·Auto 변경 |
| 트립 | 기어 D → 트립 시작(주행거리 0 읽음), 핸들 열선 구간에 tripId 태그, 기어 P → 트립 종료(234,855km 읽음) |
| 카운터 | 문 열림 2회(영역 1), 방향지시등 왼쪽 1회·오른쪽 1회 |
| 기록률 | 트립만 있을 때 100%. 기록을 끄고 주행거리를 670,403km로 올린 뒤 다시 켜자 **35%**(234,855 ÷ 670,403) |
| 부팅 자동 시작 | 7.4절 참고 |
| 자원 (백그라운드, 22개 기능 구독) | PSS 약 96MB, CPU 0.0% (`top` 5초 간격 4회) |
| 발견해 고친 버그 | 확인 화면의 `String.format`에 "%"가 섞여 기록률이 처음 계산될 때 앱이 죽음 → 수정. 퍼미션 오류가 capability 스냅샷에 저장되던 문제 → 저장 조건에서 제외 |

---

## 11. M3 에뮬레이터 실측 기록 (2026-11-03)

| 확인 항목 | 결과 |
|---|---|
| Room 2→3 | 덮어 설치로 자동 마이그레이션 정상(`daily_summary` 추가) |
| `distractionOptimized` 선언 후 주행 | 기어 D에서 시스템 차단 화면 없이 우리 간략 화면(숫자 3개)이 보임. P로 돌리면 전체 고지서로 복귀 |
| `CarUxRestrictionsManager` (화면용) | Activity가 started인 동안만 등록, 퍼미션 없이 동작 |
| 넓은 창 (1682dp, 기본) | 왼쪽 요약 + 오른쪽 기능별 표와 이번 달/지난달 막대 |
| 좁은 창 (`am task resize`로 840dp) | 카드 레이아웃으로 전환, 선택한 달(10월) 유지 |
| 백그라운드 복귀 | 설정 앱으로 가렸다가(우리 Activity `STOPPED`) 다시 열어도 선택한 달 유지 |
| 달 전환 | 탭 즉시 진행 표시, 2.2초 안에 표시 완료(측정 도구 지연 포함 상한) |
| 실행 시간 | `am start -W` TotalTime 0.8~1.0초 |
| 자원 (고지서 표시 중) | PSS 약 89MB, CPU 0.0% |
| 찾아 고친 문제 | ① 로더가 계산한 날을 캐시에 저장한 뒤 다시 읽어 이중 합산(단위 테스트가 잡음) ② 새로고침이 이전 로드를 취소할 때 취소를 실패로 처리 ③ 기록이 없는 달에 모든 기능을 "0회"로 표시 ④ 지난달 화면의 "이번 달" 문구 |
| 확인하지 못한 것 | 프로세스가 죽은 뒤 선택한 달 복원(FGS 때문에 `am kill`로 재현 불가, `SavedStateHandle`로 구현만 함). Pleos UI 디자인 가이드의 버튼·글자 크기 기준 대조 |

---

## 12. M4 에뮬레이터 실측 기록 (2026-11-03)

| 확인 항목 | 결과 |
|---|---|
| 업데이트 후 첫 실행 (동의 기록 없음) | 동의 안내 화면이 먼저 뜨고, 수집 서비스는 시작되지 않음(FGS 0개) |
| 동의 | "동의하고 시작" → 알림 퍼미션 시스템 시트("CarMeter에서 알림을 보내도록 허용하시겠습니까?") → 허용 → FGS 시작, 상태 "백그라운드 기록 중" |
| FGS 알림 | 알림 센터(`cmd statusbar expand-notifications`)에 "차량 기능 사용을 기록하고 있어요" 표시, importance LOW |
| 카테고리 끄기 (시트) | `SEAT_HEAT`·`SEAT_VENTILATION` 리스너 해제, 열린 구간 STOP으로 닫힘. 꺼진 동안 VC로 열선 H2 → 앱에 이벤트 0건, 기록 0건 |
| 카테고리 켜기 | 그 시각 현재 상태(H2)부터 새 구간 시작. 설정은 `app_state`에 저장돼 재설치·재시작 후에도 유지 |
| 전체 기록 끄기·켜기 | 끄면 FGS·알림 사라지고 열린 구간 5개 STOP으로 닫힘, 상태 "기록 꺼짐". 켜면 다시 "백그라운드 기록 중" |
| 모든 기록 지우기 | 확인 대화상자 후 구간 60·카운터 4·트립 2·스냅샷 19·요약 92 → 모두 0(재시작 스냅샷 1개만 새로 생김). capability 43행과 설정은 유지 |
| 처음 설치 상태 (`pm clear`) | 동의 화면 → "나중에" → 고지서 상단 "● 기록하려면 동의가 필요해요"(수집 안 함) → 그것을 누르면 동의 화면 → 동의 → 기록 시작 |
| 주행 중 설정 | 설정 화면에서 기어 D → 간략 화면(숫자 3개)만, P로 돌리면 설정 화면으로 복귀 |
| 찾아 고친 문제 | 카테고리를 바꿀 때마다 무관한 미지원 기능(MAX 에어컨·드라이브 모드)까지 capability를 다시 확인하던 문제 → 실제로 바뀐 카테고리만 처리 |
| 확인하지 못한 것 | 알림 퍼미션 "허용 안 함" 경로(코드상 FGS는 동작), 동의 철회 문구에 대한 Pleos·공모전 측 요구사항 |

---

## 13. M5 릴리스 빌드 실측 (2026-11-03)

### 13.1 SDK와 R8 (주최 측에 알릴 내용)
- **현상:** `isMinifyEnabled = true`인 릴리스 빌드(AGP 8.13, R8 full mode)에서 SDK가 capability 응답을 해석하지 못했다. 로그: `TypeToken must be created with a type argument ... When using code shrinkers (ProGuard, R8, ...) make sure that generic signatures are preserved.` (`onRequestCapability failed`). 결과적으로 모든 기능이 시작되지 않았다.
- **원인:** SDK 내부의 Gson `TypeToken` 익명 하위 클래스가 R8에서 제네릭 시그니처를 잃는다. `Vehicle-2.3.2.aar`에 들어 있는 consumer 규칙(`api.implement.*`, `RequiresCustomPermission` keep)은 Gson 쪽을 다루지 않는다. SDK가 의존하는 gson 2.10.1에는 자체 축소 규칙이 없다.
- **앱 쪽 해결 (`app/proguard-rules.pro`):** `-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*`, `-keep class ai.pleos.playground.** { *; }`, `-keep class * extends com.google.gson.reflect.TypeToken`. 적용 후 릴리스에서 22개 기능 정상 수집, Vehicle Service가 우리 앱으로 이벤트를 보내는 것을 확인했다.
- **제안:** SDK consumer 규칙에 위 Gson 규칙을 포함해 달라고 주최 측에 요청할 만하다.

### 13.2 함께 드러난 앱 버그와 수정
- 위 오류로 모든 capability 확인이 `ERROR_UNDEFINED`로 실패했는데, 당시 저장 조건은 "응답 없음·퍼미션 오류만 제외"여서 **"전부 미지원" 스냅샷이 저장**됐다. 같은 버전에서는 다시 저장하지 않아, SDK 문제를 고친 뒤에도 고지서가 모든 항목을 숨겼다.
- 수정: 확정적인 결과(지원, 또는 `ERROR_NOT_SUPPORTED_*`, 오류 없는 미지원)만 저장한다. 완전한 확인 결과가 저장된 것과 다르면 버전이 같아도 덮어쓴다(`CollectorEngine.saveCapabilitySnapshot`). 에뮬레이터에서 잘못된 스냅샷이 다음 시작 때 고쳐지는 것을 확인했다.

### 13.3 릴리스 APK 내용 (`prodRelease`, 디버그 키로 서명한 로컬 테스트용)
| 확인 | 결과 |
|---|---|
| 크기 | 약 3.8MB (3,771,726바이트) |
| `debuggable` | false |
| 합성 데이터 생성기·`Demo` | 없음 (R8 매핑 기준). `demoRelease`에는 있음 |
| 디버그 수집 화면·부팅 리시버·`BootStartSetting` | 없음 |
| 매니페스트 퍼미션 | `CAR_*` 8개, `FOREGROUND_SERVICE(_SPECIAL_USE)`, `POST_NOTIFICATIONS`. `RECEIVE_BOOT_COMPLETED` 없음 |
| 로그 | `Log.v/d/i` 제거(SDK의 값 출력 포함), `w/e`만 남음. 릴리스 프로세스에서 SDK `onChangeEvent` 로그 0건 확인 |
| 백업 | `allowBackup=false` + `data_extraction_rules`(Android 12+)·`backup_rules`(11 이하)로 클라우드 백업·기기 이전 모두 제외 |

### 13.4 Virtual Console API
- VC 웹 화면은 에뮬레이터의 REST API를 부른다: `PUT /vehicle/hvac?ac=0|1`, `PUT /vehicle/hvac?driver_seat=-3..3`, `PUT /vehicle/gear?gear=Drive|Park`, `PUT /vehicle/controls?speed=..&accelerator=..&brake=..&steering=..`. `scripts/measure-performance.sh`가 이것으로 이벤트를 만든다.
- 유저 빌드라 `cmd car_service inject-vhal-event`는 막혀 있다(`requires non-user build`).
- 모든 변경은 VC 브라우저 세션이 살아 있을 때만 적용된다. 세션이 없으면 `503 emulator_inactive`. 스크립트는 `POST /browser/session?client=<uuid>`를 1초마다 보내 세션을 유지하고, 끝날 때 `&closing=1`로 닫는다(세션 제한 시간 약 3초).

### 13.5 데모 빌드와 성능
- 합성 데이터(`demoDebug`, 시드 20261115): 두 달 전 1일부터 오늘까지, 기록된 트립 122건 + 앱이 꺼져 있던 주행 6건, 생성·저장·표시 약 1초. 10월 고지서(9월 대비): 기록률 96%, 시트 열선 새로 사용, 에어컨 -73%, 크루즈 +295%, "시트 통풍: 10월 0회", 창문 팁. 시연 순서는 `docs/demo-script.md`.
- 성능(`prodRelease`): 주행 중 CPU 평균 0.03%·2초 최대 0.48%(코어 하나), 정차 중 최악 조작 5.67%·9.95%, PSS 최대 57MB, 콜드 실행 178ms 이하. 방법과 표는 `docs/performance.md`.
- 다른 앱 5개를 띄워도 같은 PID·FGS 유지, Activity STOPPED 상태에서 VC 변경 7건 기록.

