package com.hebi.homecrew.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.ui.components.HomeCrewBottomBar
import com.hebi.homecrew.ui.components.HomeCrewEmptyState
import com.hebi.homecrew.ui.components.HomeCrewFilterChip
import com.hebi.homecrew.ui.components.HomeCrewPrimaryButton
import com.hebi.homecrew.ui.components.HomeCrewProgressCard
import com.hebi.homecrew.ui.components.HomeCrewSecondaryButton
import com.hebi.homecrew.ui.components.HomeCrewStatusChip
import com.hebi.homecrew.ui.components.HomeCrewTaskRow
import com.hebi.homecrew.ui.components.HomeCrewTextField
import com.hebi.homecrew.ui.components.HomeTab
import com.hebi.homecrew.ui.model.ChoreStatus
import com.hebi.homecrew.ui.model.PreviewChore
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import com.hebi.homecrew.ui.theme.ThemeMode

@Composable
fun HomeCrewDesignSystemGallery(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val palette = LocalHomeCrewColors.current
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    var selectedFilter by rememberSaveable { mutableStateOf("전체") }
    var choreName by rememberSaveable { mutableStateOf("") }
    var firstChoreDone by rememberSaveable { mutableStateOf(false) }
    var dialogVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = palette.background,
        bottomBar = {
            HomeCrewBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = HomeCrewDimensions.screenPadding,
                vertical = HomeCrewDimensions.space24
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "HomeCrew",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "1-A · 공통 디자인 시스템",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "컴포넌트 동작을 확인하기 위한 미리보기입니다. 실제 집안일은 저장하지 않습니다.",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                GallerySection(title = "테마") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            HomeCrewFilterChip(
                                label = mode.label,
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) }
                            )
                        }
                    }
                }
            }

            item {
                GallerySection(title = "01 · 중립 컬러") {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PaletteSwatch("기본", palette.ink)
                        PaletteSwatch("표면", palette.surface)
                        PaletteSwatch("배경", palette.background)
                        PaletteSwatch("서브", palette.subtle)
                        PaletteSwatch("경계", palette.border)
                        PaletteSwatch("보조", palette.muted)
                    }
                }
            }

            item {
                GallerySection(title = "02 · 타이포그래피") {
                    Text("오늘의 집안일", style = MaterialTheme.typography.headlineSmall)
                    Text("내가 할 일", style = MaterialTheme.typography.titleMedium)
                    Text("설거지 · 분리수거", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "오늘 오후 8시 · 주방",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                    Text(
                        "완료한 작업의 기록",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted
                    )
                }
            }

            item {
                GallerySection(title = "03 · 버튼과 입력") {
                    HomeCrewPrimaryButton(
                        text = "집안일 저장",
                        onClick = { dialogVisible = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HomeCrewSecondaryButton(
                        text = "취소 · 입력 지우기",
                        onClick = { choreName = "" },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HomeCrewTextField(
                        label = "집안일 이름",
                        value = choreName,
                        onValueChange = { choreName = it },
                        placeholder = "예: 설거지"
                    )
                    Text(
                        "버튼 52dp · 터치 영역 최소 48dp · 4dp 모서리",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                GallerySection(title = "04 · 필터와 상태") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("전체", "청소", "주방").forEach { filter ->
                            HomeCrewFilterChip(
                                label = filter,
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeCrewStatusChip(ChoreStatus.PLANNED)
                        HomeCrewStatusChip(ChoreStatus.COMPLETED)
                        HomeCrewStatusChip(ChoreStatus.OVERDUE)
                    }
                }
            }

            item {
                GallerySection(title = "05 · 진행률 및 집안일") {
                    HomeCrewProgressCard(
                        completed = if (firstChoreDone) 5 else 4,
                        total = 7
                    )
                    HomeCrewTaskRow(
                        chore = PreviewChore(
                            id = 1,
                            title = "설거지",
                            detail = "오늘 오후 8시 · 주방",
                            status = if (firstChoreDone) ChoreStatus.COMPLETED
                                else ChoreStatus.PLANNED
                        ),
                        onToggleComplete = { firstChoreDone = !firstChoreDone },
                        onOpenDetails = { dialogVisible = true }
                    )
                    HorizontalDivider(color = palette.border)
                    HomeCrewTaskRow(
                        chore = PreviewChore(
                            id = 2,
                            title = "거실 청소",
                            detail = "오늘 오전 10시 · 나",
                            status = ChoreStatus.COMPLETED
                        ),
                        onToggleComplete = { dialogVisible = true },
                        onOpenDetails = { dialogVisible = true }
                    )
                    HorizontalDivider(color = palette.border)
                    HomeCrewTaskRow(
                        chore = PreviewChore(
                            id = 3,
                            title = "욕실 청소",
                            detail = "어제까지 · 미완료",
                            status = ChoreStatus.OVERDUE
                        ),
                        onToggleComplete = { dialogVisible = true },
                        onOpenDetails = { dialogVisible = true }
                    )
                }
            }

            item {
                GallerySection(title = "06 · 빈 화면") {
                    HomeCrewEmptyState(
                        title = "등록된 집안일이 없어요",
                        description = "첫 집안일을 추가해보세요.",
                        actionLabel = "집안일 추가",
                        onAction = { dialogVisible = true }
                    )
                }
            }

            item {
                GallerySection(title = "07 · 하단 탐색") {
                    Text(
                        "현재 미리보기 탭: ${selectedTab.label}. 아래 4개 탭에서 선택 상태를 확인할 수 있습니다.",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    if (dialogVisible) {
        AlertDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text("디자인 미리보기") },
            text = { Text("실제 집안일 저장 및 상세 화면은 다음 개발 단계에서 연결합니다.") },
            confirmButton = {
                TextButton(onClick = { dialogVisible = false }) {
                    Text("확인")
                }
            }
        )
    }
}

@Composable
private fun GallerySection(
    title: String,
    content: @Composable () -> Unit
) {
    val palette = LocalHomeCrewColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .background(palette.surface, RoundedCornerShape(4.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            color = palette.ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun PaletteSwatch(name: String, color: Color) {
    val palette = LocalHomeCrewColors.current
    Column(
        modifier = Modifier.width(62.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 58.dp, height = 46.dp)
                .border(1.dp, palette.border, RoundedCornerShape(3.dp))
                .background(color, RoundedCornerShape(3.dp))
        )
        Text(name, style = MaterialTheme.typography.labelMedium)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "HomeCrew Light")
@Composable
private fun PreviewLightGallery() {
    HomeCrewTheme(darkTheme = false) {
        HomeCrewDesignSystemGallery(
            themeMode = ThemeMode.LIGHT,
            onThemeModeChange = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "HomeCrew Dark")
@Composable
private fun PreviewDarkGallery() {
    HomeCrewTheme(darkTheme = true) {
        HomeCrewDesignSystemGallery(
            themeMode = ThemeMode.DARK,
            onThemeModeChange = {}
        )
    }
}
