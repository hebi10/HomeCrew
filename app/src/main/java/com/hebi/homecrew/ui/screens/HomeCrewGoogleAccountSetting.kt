package com.hebi.homecrew.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.hebi.homecrew.data.auth.GoogleSignIn
import com.hebi.homecrew.data.auth.GoogleSignInResult
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

    val context = activity.applicationContext
    val sessionEvents = remember(signIn, context) { signIn.observeSession(context) }
    val session by sessionEvents.collectAsState(initial = HomeCrewAuthSession.Restoring)
    val uiState = homeCrewAccountUiState(session, signingIn, lastResult)

    HomeCrewAccountContent(uiState = uiState, onSignIn = {
        if (!signingIn && session == HomeCrewAuthSession.LocalOnly) {
            signingIn = true
            lastResult = null
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
    })
}

@Composable
internal fun HomeCrewAccountContent(
    uiState: HomeCrewAccountUiState,
    onSignIn: () -> Unit
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
