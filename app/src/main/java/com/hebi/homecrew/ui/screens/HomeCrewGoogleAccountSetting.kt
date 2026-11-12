package com.hebi.homecrew.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hebi.homecrew.data.auth.GoogleSignIn
import com.hebi.homecrew.data.auth.GoogleSignInResult
import com.hebi.homecrew.ui.theme.LocalHomeCrewColors
import kotlinx.coroutines.launch

@Composable
internal fun HomeCrewGoogleAccountSetting(signIn: GoogleSignIn) {
    val activity = LocalActivity.current ?: return
    val scope = rememberCoroutineScope()
    val palette = LocalHomeCrewColors.current
    var isSigningIn by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<GoogleSignInResult?>(null) }
    val signedIn = result == GoogleSignInResult.SIGNED_IN || signIn.isSignedIn(activity)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Google 계정", style = MaterialTheme.typography.titleMedium)
        if (signedIn) {
            Text("Google 로그인 완료", color = palette.muted)
        } else {
            Button(
                onClick = {
                    if (!isSigningIn) {
                        isSigningIn = true
                        result = null
                        scope.launch {
                            try {
                                result = signIn.signIn(activity)
                            } finally {
                                isSigningIn = false
                            }
                        }
                    }
                },
                enabled = !isSigningIn,
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text(if (isSigningIn) "로그인 중…" else "Google 로그인") }
            when (result) {
                GoogleSignInResult.NO_ACCOUNT -> "기기에 Google 계정을 추가한 뒤 다시 시도해 주세요."
                GoogleSignInResult.NETWORK_ERROR -> "인터넷 연결을 확인하고 다시 시도해 주세요."
                GoogleSignInResult.CONFIGURATION_ERROR -> "이 빌드의 Google 로그인 설정을 확인해 주세요."
                GoogleSignInResult.INVALID_CREDENTIAL -> "Google 계정을 확인하지 못했습니다. 다시 선택해 주세요."
                GoogleSignInResult.IN_PROGRESS -> "이미 로그인이 진행 중입니다."
                GoogleSignInResult.FAILED -> "Google 로그인에 실패했습니다. 다시 시도해 주세요."
                else -> null
            }?.let { message -> Text(message, color = palette.error) }
        }
    }
}
