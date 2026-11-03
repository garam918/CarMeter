package com.example.hyundaihackathon.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** What the first-run screen and the "안내 다시 보기" screen tell the driver. */
private val Points = listOf(
    "무엇을 기록하나요" to
        "공조·시트 열선과 통풍·조명·와이퍼·크루즈·창문·문 같은 차량 기능을 언제, 어느 단계로 켰는지 기록해요. " +
        "기록률과 트립 요약을 위해 주행 중인지 여부, 그리고 출발·도착 때 누적 주행거리·연비·배터리 잔량을 한 번씩 읽어요. " +
        "위치, 음성, 영상, 연락처는 기록하지 않아요.",
    "어디에 저장되나요" to
        "기록은 이 차 안에만 저장돼요. 인터넷을 쓰지 않고, 어디로도 보내지 않아요.",
    "언제든 끌 수 있어요" to
        "설정에서 기록 전체나 항목별 기록을 끌 수 있고, 모든 기록을 지울 수 있어요. " +
        "앱을 닫아도 기록하는 동안에는 \"차량 기능 사용을 기록하고 있어요\" 알림이 보여요.",
)

/**
 * First run: explains the three points and asks for consent. With [onAgree] null it is the
 * read-only review opened from settings.
 */
@Composable
fun OnboardingScreen(onAgree: (() -> Unit)?, onDismiss: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("CarMeter · 내 차 사용 고지서", style = MaterialTheme.typography.headlineMedium)
            Text(
                "아파트 관리비 고지서처럼, 이 차의 기능을 얼마나 쓰는지 한 달 단위로 정리해 드려요.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Points.forEach { (title, body) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(body, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (onAgree != null) {
                    Button(onClick = onAgree, modifier = Modifier.heightIn(min = 72.dp)) { Text("동의하고 시작", style = MaterialTheme.typography.titleMedium) }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 72.dp)) { Text("나중에", style = MaterialTheme.typography.titleMedium) }
                } else {
                    Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 72.dp)) { Text("닫기", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}
