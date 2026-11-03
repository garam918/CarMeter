package com.example.hyundaihackathon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hyundaihackathon.ui.RecordingIndicator
import com.example.hyundaihackathon.vehicle.FeatureCategory
import kotlinx.coroutines.delay

/** Switchable categories, in the order shown. [FeatureCategory.SYSTEM] is always on. */
private val Categories = listOf(
    FeatureCategory.CLIMATE to ("공조" to "에어컨, 히터 전원, 온도, 풍량, 내기순환, 성에 제거, 핸들·사이드미러 열선"),
    FeatureCategory.SEAT to ("시트" to "시트 열선, 시트 통풍"),
    FeatureCategory.LIGHT to ("조명" to "전조등, 상향등, 앰비언트 라이트, 방향지시등"),
    FeatureCategory.DRIVING to ("주행 보조" to "와이퍼, 크루즈, 드라이브 모드"),
    FeatureCategory.BODY to ("차체" to "창문, 문 열림"),
)

private val RowHeight = 80.dp
private const val MESSAGE_MS = 3_000L

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onRecordingChange: (Boolean) -> Unit,
    onCategoryChange: (FeatureCategory, Boolean) -> Unit,
    onErase: () -> Unit,
    onMessageShown: () -> Unit,
    onShowGuide: () -> Unit,
) {
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    val indicator = RecordingIndicator.of(state.settings, state.status)

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_MS)
            onMessageShown()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 64.dp)) { Text("‹ 고지서") }
                    Text("설정", style = MaterialTheme.typography.headlineMedium)
                }
            }
            state.message?.let { text -> item { Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary) } }
            item {
                Section("기록") {
                    SwitchRow(
                        title = "백그라운드 기록",
                        subtitle = indicator.text,
                        checked = state.settings.recordingEnabled,
                        enabled = state.settings.hasConsent,
                        onChange = onRecordingChange,
                    )
                    Text(
                        "주행 여부와 출발·도착 때의 주행거리·연비·배터리 잔량은 기록률 계산에 필요해 기록 중에는 항상 함께 기록돼요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Section("항목별 기록") {
                    Categories.forEach { (category, text) ->
                        SwitchRow(
                            title = text.first,
                            subtitle = text.second,
                            checked = state.settings.isEnabled(category),
                            enabled = state.settings.canRecord,
                            onChange = { onCategoryChange(category, it) },
                        )
                    }
                }
            }
            item {
                Section("데이터") {
                    Text("기록은 이 차 안에만 저장돼요.", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { confirmErase = true },
                            enabled = !state.erasing,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.heightIn(min = 64.dp),
                        ) { Text(if (state.erasing) "지우는 중…" else "모든 기록 지우기") }
                        OutlinedButton(onClick = onShowGuide, modifier = Modifier.heightIn(min = 64.dp)) { Text("기록 안내 다시 보기") }
                    }
                }
            }
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("모든 기록을 지울까요?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("이 차에 저장된 사용 기록과 고지서가 모두 지워지고 되돌릴 수 없어요. 설정은 그대로 남아요.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = {
                    confirmErase = false
                    onErase()
                }, modifier = Modifier.heightIn(min = 64.dp)) { Text("지우기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }, modifier = Modifier.heightIn(min = 64.dp)) { Text("취소") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = RowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
