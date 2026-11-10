package com.hebi.homecrew.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.data.local.HomeCrewLocalBackup
import com.hebi.homecrew.ui.components.HomeCrewPrimaryButton
import com.hebi.homecrew.ui.components.HomeCrewSecondaryButton
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeCrewLocalDataScreen(
    viewModel: DemoChoreViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val palette = LocalHomeCrewColors.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val data = pendingExport
        pendingExport = null
        if (uri != null && data != null) {
            scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) {
                        val stream = checkNotNull(context.contentResolver.openOutputStream(uri)) {
                            "파일을 열 수 없습니다."
                        }
                        stream.bufferedWriter(Charsets.UTF_8).use { it.write(data) }
                    }
                    message = "집안일 백업 파일을 저장했습니다."
                } catch (error: Exception) {
                    message = "백업 파일을 저장하지 못했습니다."
                } finally {
                    busy = false
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                try {
                    pendingRestore = withContext(Dispatchers.IO) {
                        val stream = checkNotNull(context.contentResolver.openInputStream(uri)) {
                            "파일을 열 수 없습니다."
                        }
                        stream.use(::readLimitedBackup)
                    }
                } catch (error: Exception) {
                    pendingRestore = null
                    message = "백업 파일을 읽지 못했습니다. 16MB 이하의 JSON 파일인지 확인해 주세요."
                } finally {
                    busy = false
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = HomeCrewDimensions.screenPadding,
            vertical = HomeCrewDimensions.space24
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { TextButton(onClick = onBack) { Text("이전") } }
        item { ScreenHeader("로컬 데이터 관리", "집안일 기록 백업·복원") }
        item { DemoNotice() }
        item {
            BorderedSection {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading("백업")
                    Text(
                        "집안일, 반복·담당 설정, 완료·건너뛰기 기록을 JSON 파일로 저장합니다. " +
                            "저장 위치는 직접 선택하며 자동으로 외부에 전송되지 않습니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HomeCrewPrimaryButton(
                        text = "백업 파일 저장",
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    pendingExport = viewModel.exportLocalBackup()
                                    exportLauncher.launch("HomeCrew-" + LocalDate.now() + ".json")
                                } catch (error: Exception) {
                                    message = error.message ?: "백업 데이터를 준비하지 못했습니다."
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            BorderedSection {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeading("복원")
                    Text(
                        "같은 집 이름과 구성원 순서로 저장된 백업만 복원할 수 있습니다. " +
                            "복원하면 현재 집안일과 모든 회차 기록이 백업 내용으로 교체됩니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HomeCrewSecondaryButton(
                        text = "백업 파일 선택",
                        onClick = { if (!busy) importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (message != null) {
            item { Text(message.orEmpty(), color = palette.muted) }
        }
        item {
            Text(
                "앱을 삭제하거나 앱 데이터를 지우면 저장된 정보가 사라질 수 있습니다. " +
                    "백업 파일은 사용자가 지정한 위치에만 저장됩니다.",
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("기존 집안일을 교체할까요?") },
            text = {
                Text("현재 모든 집안일·완료·건너뛰기 기록을 백업 데이터로 교체합니다. " +
                    "필요한 경우 먼저 현재 기록을 백업해 주세요.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val data = pendingRestore ?: return@TextButton
                    pendingRestore = null
                    scope.launch {
                        busy = true
                        try {
                            viewModel.restoreLocalBackup(data)
                            message = "백업 데이터를 복원했습니다."
                        } catch (error: Exception) {
                            message = error.message ?: "복원에 실패했습니다. 기존 데이터는 유지됩니다."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("기록 교체") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("취소") }
            }
        )
    }
}

private fun readLimitedBackup(stream: InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        require(output.size() + count <= HomeCrewLocalBackup.MAX_BYTES) {
            "백업 크기 제한을 초과했습니다."
        }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}
