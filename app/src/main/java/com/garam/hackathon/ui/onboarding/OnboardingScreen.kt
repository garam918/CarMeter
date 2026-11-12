package com.garam.hackathon.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garam.hackathon.data.DataRetention
import com.garam.hackathon.ui.components.PrimaryButton
import com.garam.hackathon.ui.components.SecondaryButton
import com.garam.hackathon.ui.components.Section

/** What the first-run screen and the "안내 다시 보기" screen tell the driver: what, why, where, how long, how to stop. */
private val Points = listOf(
    "무엇을 기록하나요" to
        "공조·시트 열선과 통풍·조명·와이퍼·크루즈·창문·문 같은 차량 기능을 언제, 어느 단계로 켰는지 기록해요. " +
        "기록률과 트립 요약을 위해 주행 중인지 여부, 그리고 출발·도착 때 누적 주행거리·연비·배터리 잔량을 한 번씩 읽어요. " +
        "위치, 음성, 영상, 연락처는 기록하지 않아요. 기록은 사용 리포트를 만드는 데만 써요.",
    "어디에, 얼마나 저장되나요" to
        "기록은 이 차 안에만 저장돼요. 인터넷을 쓰지 않고, 어디로도 보내지 않아요. " +
        "최근 ${DataRetention.MONTHS_KEPT}개월(이번 달 포함)치만 보관하고, 그보다 오래된 기록은 자동으로 지워요.",
    "언제든 끄고 지울 수 있어요" to
        "설정에서 기록 전체나 항목별 기록을 끄거나 모든 기록을 지울 수 있어요. 동의를 철회하면 기록이 멈추고 저장된 기록도 모두 지워져요. " +
        "앱을 닫아도 기록하는 동안에는 \"차량 기능 사용을 기록하고 있어요\" 알림이 보여요.",
)

private val ContentWidth = 1120.dp

/**
 * First run: explains the points above and asks for consent. With [onAgree] null it is the
 * read-only review opened from settings.
 */
@Composable
fun OnboardingScreen(onAgree: (() -> Unit)?, onDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text("CarMeter  /  내 차 사용 고지서", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("내 차의 사용 습관을 한눈에", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "어떤 기능을 얼마나 쓰는지 일간·주간·월간 고지서로 정리해 드려요.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Points.forEach { (title, body) ->
                    Section(title) { Text(body, style = MaterialTheme.typography.bodyLarge) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (onAgree != null) {
                        PrimaryButton("동의하고 시작", onAgree)
                        SecondaryButton("나중에", onDismiss)
                    } else {
                        PrimaryButton("닫기", onDismiss)
                    }
                }
            }
        }
    }
}
