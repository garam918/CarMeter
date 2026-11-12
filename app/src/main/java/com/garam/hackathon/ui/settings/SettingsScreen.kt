package com.garam.hackathon.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.garam.hackathon.R
import com.garam.hackathon.data.DataRetention
import com.garam.hackathon.ui.RecordingIndicator
import com.garam.hackathon.ui.components.DangerButton
import com.garam.hackathon.ui.components.NavIconButton
import com.garam.hackathon.ui.components.SecondaryButton
import com.garam.hackathon.ui.components.Section
import com.garam.hackathon.vehicle.FeatureCategory
import kotlinx.coroutines.delay

/** Switchable categories, in the order shown. [FeatureCategory.SYSTEM] is always on. */
private val Categories = listOf(
    FeatureCategory.CLIMATE to ("공조" to "에어컨, 히터 전원, 온도, 풍량, 내기순환, 성에 제거, 핸들·사이드미러 열선"),
    FeatureCategory.SEAT to ("시트" to "시트 열선, 시트 통풍"),
    FeatureCategory.LIGHT to ("조명" to "전조등, 상향등, 앰비언트 라이트, 방향지시등"),
    FeatureCategory.DRIVING to ("주행 보조" to "와이퍼, 크루즈, 드라이브 모드"),
    FeatureCategory.BODY to ("차체" to "창문, 문 열림"),
)

/** Lines longer than this are hard to follow at arm's length. */
private val ContentWidth = 1120.dp
private val RowHeight = 88.dp
private const val MESSAGE_MS = 3_000L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onRecordingChange: (Boolean) -> Unit,
    onCategoryChange: (FeatureCategory, Boolean) -> Unit,
    onErase: () -> Unit,
    onWithdraw: () -> Unit,
    onMessageShown: () -> Unit,
    onShowGuide: () -> Unit,
) {
    var confirmErase by rememberSaveable { mutableStateOf(false) }
    var confirmWithdraw by rememberSaveable { mutableStateOf(false) }
    val indicator = RecordingIndicator.of(state.settings, state.status)

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_MS)
            onMessageShown()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                contentPadding = PaddingValues(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        NavIconButton(R.drawable.ic_chevron_left, "고지서로", onBack)
                        Text("설정", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                state.message?.let { text -> item { Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) } }
                item {
                    Section("기록") {
                        SwitchRow(
                            title = "백그라운드 기록",
                            subtitle = indicator.text,
                            checked = state.settings.recordingEnabled,
                            enabled = state.settings.hasConsent,
                            onChange = onRecordingChange,
                        )
                        Note(
                            "끄면 기록만 멈추고 지금까지의 기록은 남아요. 주행 여부와 출발·도착 때의 주행거리·연비·배터리 잔량은 " +
                                "기록률 계산에 필요해 기록 중에는 항상 함께 기록돼요.",
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
                        Text(
                            "기록은 이 차 안에만 저장되고, 최근 ${DataRetention.MONTHS_KEPT}개월(이번 달 포함)치만 보관해요. " +
                                "그보다 오래된 기록은 자동으로 지워요.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            DangerButton(if (state.erasing) "지우는 중…" else "모든 기록 지우기", { confirmErase = true }, enabled = !state.erasing)
                            SecondaryButton("기록 안내 다시 보기", onShowGuide)
                        }
                    }
                }
                if (state.settings.hasConsent) {
                    item {
                        Section("동의 철회") {
                            Text(
                                "동의를 철회하면 기록이 멈추고 이 차에 저장된 기록이 모두 지워져요. 다시 쓰려면 새로 동의해야 해요.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            DangerButton("동의 철회", { confirmWithdraw = true }, enabled = !state.erasing)
                        }
                    }
                }
            }
        }
    }

    if (confirmErase) {
        Confirm(
            title = "모든 기록을 지울까요?",
            text = "이 차에 저장된 사용 기록과 고지서가 모두 지워지고 되돌릴 수 없어요. 설정과 동의는 그대로 남아요.",
            confirm = "지우기",
            onConfirm = onErase,
            onDismiss = { confirmErase = false },
        )
    }
    if (confirmWithdraw) {
        Confirm(
            title = "동의를 철회할까요?",
            text = "기록이 멈추고, 이 차에 저장된 사용 기록과 고지서가 모두 지워져요. 되돌릴 수 없어요.",
            confirm = "철회하고 지우기",
            onConfirm = onWithdraw,
            onDismiss = { confirmWithdraw = false },
        )
    }
}

@Composable
private fun Confirm(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            DangerButton(confirm, {
                onDismiss()
                onConfirm()
            })
        },
        dismissButton = { SecondaryButton("취소", onDismiss) },
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The whole row toggles, so the touch target is the row, not just the switch. */
@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
