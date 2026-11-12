package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.data.auth.GoogleSignInResult
import com.hebi.homecrew.data.auth.HomeCrewAuthSession

internal data class HomeCrewAccountUiState(
    val status: String,
    val description: String,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val showSignIn: Boolean = false,
    val signInEnabled: Boolean = false,
    val signInLabel: String = "Google로 로그인",
    val feedback: String? = null,
    val feedbackIsError: Boolean = false
)

internal fun homeCrewAccountUiState(
    session: HomeCrewAuthSession,
    signingIn: Boolean,
    result: GoogleSignInResult?
): HomeCrewAccountUiState = when (session) {
    HomeCrewAuthSession.Restoring -> HomeCrewAccountUiState(
        status = "계정 상태 확인 중",
        description = "Google 로그인 정보를 확인하고 있습니다."
    )
    is HomeCrewAuthSession.SignedIn -> HomeCrewAccountUiState(
        status = "Google 계정 연결됨",
        description = "Google 계정 인증이 완료되었습니다.",
        accountName = session.user.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "이름 정보 없음",
        accountEmail = session.user.email?.trim()?.takeIf { it.isNotEmpty() } ?: "이메일 정보 없음"
    )
    HomeCrewAuthSession.LocalOnly -> {
        val feedback = when (result) {
            GoogleSignInResult.CANCELLED -> "Google 로그인이 취소되었습니다."
            GoogleSignInResult.SIGNED_IN -> "Google 계정 상태를 확인하고 있습니다."
            GoogleSignInResult.NO_ACCOUNT -> "기기에 Google 계정을 추가한 뒤 다시 시도해 주세요."
            GoogleSignInResult.NETWORK_ERROR -> "인터넷 연결을 확인하고 다시 시도해 주세요."
            GoogleSignInResult.CONFIGURATION_ERROR -> "Google 로그인 설정을 확인할 수 없습니다."
            GoogleSignInResult.INVALID_CREDENTIAL -> "Google 계정 인증에 실패했습니다. 다시 선택해 주세요."
            GoogleSignInResult.IN_PROGRESS -> "이미 Google 로그인이 진행 중입니다."
            GoogleSignInResult.FAILED -> "Google 로그인에 실패했습니다. 다시 시도해 주세요."
            null -> null
        }
        HomeCrewAccountUiState(
            status = if (signingIn) "Google 로그인 진행 중" else "로그인 없이 사용 중",
            description = "현재 기기의 집안일은 로그인하지 않아도 계속 이용할 수 있습니다.",
            showSignIn = true,
            signInEnabled = !signingIn,
            signInLabel = if (signingIn) "로그인 중…" else "Google로 로그인",
            feedback = feedback,
            feedbackIsError = result in setOf(
                GoogleSignInResult.NETWORK_ERROR,
                GoogleSignInResult.CONFIGURATION_ERROR,
                GoogleSignInResult.INVALID_CREDENTIAL,
                GoogleSignInResult.FAILED
            )
        )
    }
}
