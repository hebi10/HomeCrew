package com.hebi.homecrew.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.hebi.homecrew.data.auth.GoogleSignIn
import com.hebi.homecrew.data.auth.GoogleSignInResult
import com.hebi.homecrew.data.auth.HomeCrewAccountActionResult
import com.hebi.homecrew.data.auth.HomeCrewAuthSession
import com.hebi.homecrew.data.auth.HomeCrewAuthUser
import com.hebi.homecrew.ui.theme.HomeCrewDimensions
import com.hebi.homecrew.ui.theme.HomeCrewTheme
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun HomeCrewGoogleAccountSetting(signIn: GoogleSignIn) {
    val activity = LocalActivity.current ?: return
    val scope = rememberCoroutineScope()
    var signingIn by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<GoogleSignInResult?>(null) }
    var accountAction by remember { mutableStateOf<HomeCrewAccountAction?>(null) }
    var accountResult by remember { mutableStateOf<HomeCrewAccountActionResult?>(null) }
    var confirmingDeletion by rememberSaveable { mutableStateOf(false) }
    var webError by remember { mutableStateOf(false) }

    val context = activity.applicationContext
    val sessionEvents = remember(signIn, context) { signIn.observeSession(context) }
    val session by sessionEvents.collectAsState(initial = HomeCrewAuthSession.Restoring)
    val uiState = homeCrewAccountUiState(session, signingIn, lastResult, accountAction, accountResult)

    fun runAccountAction(action: HomeCrewAccountAction) {
        if (session !is HomeCrewAuthSession.SignedIn || accountAction != null || signingIn) return
        accountAction = action
        accountResult = null
        scope.launch {
            try {
                accountResult = when (action) {
                    HomeCrewAccountAction.SIGN_OUT -> signIn.signOut(context)
                    HomeCrewAccountAction.DELETE_ACCOUNT -> signIn.deleteAccount(activity)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                accountResult = HomeCrewAccountActionResult.FAILED
            } finally {
                accountAction = null
            }
        }
    }

    HomeCrewAccountContent(
        uiState = uiState,
        onSignIn = {
            if (!signingIn && accountAction == null && session == HomeCrewAuthSession.LocalOnly) {
                signingIn = true
                lastResult = null
                accountResult = null
                scope.launch {
                    try {
                        lastResult = signIn.signIn(activity)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        lastResult = GoogleSignInResult.FAILED
                    } finally {
                        signingIn = false
                    }
                }
            }
        },
        onSignOut = { runAccountAction(HomeCrewAccountAction.SIGN_OUT) },
        onDeleteAccount = { confirmingDeletion = true },
        onOpenDeletionWeb = {
            webError = !openHomeCrewWebPage(activity, HOMECREW_DELETE_ACCOUNT_URL)
        }
    )
    if (webError) {
        Text(
            "브라우저에서 계정 삭제 안내를 열 수 없습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalHomeCrewColors.current.error
        )
    }

    if (confirmingDeletion && session is HomeCrewAuthSession.SignedIn) {
        AlertDialog(
            onDismissRequest = { confirmingDeletion = false },
            title = { Text("Firebase 로그인 계정을 삭제할까요?") },
            text = {
                Text(
                    "Firebase 인증 계정을 삭제합니다. 보안상 Google 계정을 다시 선택해야 할 수 있으며, " +
                        "확인을 취소하거나 인증에 실패하면 계정은 삭제되지 않습니다. " +
                        "집 이름·구성원·집안일·완료 기록은 이 기기에 남습니다. " +
                        "Google 계정 자체는 삭제되지 않습니다."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDeletion = false
                    runAccountAction(HomeCrewAccountAction.DELETE_ACCOUNT)
                }) { Text("Firebase 계정 삭제", color = LocalHomeCrewColors.current.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDeletion = false }) { Text("취소") }
            }
        )
    }
}

@Composable
internal fun HomeCrewAccountContent(
    uiState: HomeCrewAccountUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    onOpenDeletionWeb: () -> Unit = {}
) {
    val palette = LocalHomeCrewColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HomeCrewDimensions.space8)
    ) {
        Text("계정", style = MaterialTheme.typography.titleMedium)
        Text(uiState.status, style = MaterialTheme.typography.bodyLarge, color = palette.ink)
        Text(uiState.description, style = MaterialTheme.typography.bodySmall, color = palette.muted)

        if (uiState.accountName != null) {
            Text("이름", style = MaterialTheme.typography.labelMedium, color = palette.muted)
            Text(uiState.accountName, style = MaterialTheme.typography.bodyMedium)
        }
        if (uiState.accountEmail != null) {
            Text("이메일", style = MaterialTheme.typography.labelMedium, color = palette.muted)
            Text(uiState.accountEmail, style = MaterialTheme.typography.bodyMedium)
        }

        if (uiState.showSignIn) {
            Button(
                onClick = onSignIn,
                enabled = uiState.signInEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = HomeCrewDimensions.touchTarget)
            ) {
                Text(uiState.signInLabel)
            }
        }

        if (uiState.showSignOut) {
            TextButton(
                onClick = onSignOut,
                enabled = uiState.accountActionsEnabled,
                modifier = Modifier.heightIn(min = HomeCrewDimensions.touchTarget)
            ) { Text("로그아웃") }
        }
        if (uiState.showDeleteAccount) {
            TextButton(
                onClick = onDeleteAccount,
                enabled = uiState.accountActionsEnabled,
                modifier = Modifier.heightIn(min = HomeCrewDimensions.touchTarget)
            ) { Text("계정 삭제", color = palette.error) }
        }
        TextButton(
            onClick = onOpenDeletionWeb,
            modifier = Modifier.heightIn(min = HomeCrewDimensions.touchTarget)
        ) { Text("앱 외 계정 삭제 요청 안내") }

        uiState.feedback?.let { feedback ->
            Text(
                feedback,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.feedbackIsError) palette.error else palette.muted
            )
        }

        Text(
            "Google 로그인은 계정 인증만 연결합니다. 집안일·구성원·완료 기록은 아직 다른 기기로 공유되지 않습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted
        )
    }
}

@Preview(showBackground = true, widthDp = 393)
@Composable
private fun PreviewAccountLocalLight() {
    HomeCrewTheme(darkTheme = false) {
        Surface {
            HomeCrewAccountContent(
                uiState = homeCrewAccountUiState(HomeCrewAuthSession.LocalOnly, false, null),
                onSignIn = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393)
@Composable
private fun PreviewAccountSignedInDark() {
    HomeCrewTheme(darkTheme = true) {
        Surface {
            HomeCrewAccountContent(
                uiState = homeCrewAccountUiState(
                    HomeCrewAuthSession.SignedIn(
                        HomeCrewAuthUser(
                            uid = "example-firebase-uid",
                            displayName = "예시 사용자",
                            email = "user@example.invalid"
                        )
                    ),
                    false,
                    null
                ),
                onSignIn = {}
            )
        }
    }
}
