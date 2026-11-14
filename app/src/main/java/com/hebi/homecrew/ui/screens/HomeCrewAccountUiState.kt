package com.hebi.homecrew.ui.screens

import com.hebi.homecrew.data.auth.GoogleSignInResult
import com.hebi.homecrew.data.auth.HomeCrewAccountActionResult
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
    val feedbackIsError: Boolean = false,
    val showSignOut: Boolean = false,
    val showDeleteAccount: Boolean = false,
    val accountActionsEnabled: Boolean = false
)

internal enum class HomeCrewAccountAction { SIGN_OUT, DELETE_ACCOUNT }

internal fun homeCrewAccountUiState(
    session: HomeCrewAuthSession,
    signingIn: Boolean,
    result: GoogleSignInResult?,
    accountAction: HomeCrewAccountAction? = null,
    accountResult: HomeCrewAccountActionResult? = null
): HomeCrewAccountUiState = when (session) {
    HomeCrewAuthSession.Restoring -> HomeCrewAccountUiState(
        status = "계정 상태 확인 중",
        description = "Google 로그인 정보를 확인하고 있습니다."
    )
    is HomeCrewAuthSession.SignedIn -> HomeCrewAccountUiState(
        status = when (accountAction) {
            HomeCrewAccountAction.SIGN_OUT -> "로그아웃 처리 중"
            HomeCrewAccountAction.DELETE_ACCOUNT -> "계정 삭제 처리 중"
            null -> "Google 계정 연결됨"
        },
        description = "Firebase 로그인 계정과 기기 내 집안일은 별도로 보관됩니다.",
        accountName = session.user.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "이름 정보 없음",
        accountEmail = session.user.email?.trim()?.takeIf { it.isNotEmpty() } ?: "이메일 정보 없음",
        showSignOut = true,
        showDeleteAccount = true,
        accountActionsEnabled = accountAction == null && !signingIn,
        feedback = accountResult?.let(::accountActionMessage),
        feedbackIsError = accountResult?.let(::accountActionIsError) ?: false
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
            signInEnabled = !signingIn && accountAction == null,
            signInLabel = if (signingIn) "로그인 중…" else "Google로 로그인",
            feedback = accountResult?.let(::accountActionMessage) ?: feedback,
            feedbackIsError = accountResult?.let(::accountActionIsError) ?: (result in setOf(
                GoogleSignInResult.NETWORK_ERROR,
                GoogleSignInResult.CONFIGURATION_ERROR,
                GoogleSignInResult.INVALID_CREDENTIAL,
                GoogleSignInResult.FAILED
            ))
        )
    }
}

internal fun accountActionMessage(result: HomeCrewAccountActionResult): String = when (result) {
    HomeCrewAccountActionResult.SIGNED_OUT ->
        "로그아웃했습니다. 이 기기의 집안일과 구성원 기록은 유지됩니다."
    HomeCrewAccountActionResult.SIGNED_OUT_CREDENTIAL_WARNING ->
        "Firebase 로그아웃은 완료됐지만 Google 계정 선택 기록을 지우지 못했습니다."
    HomeCrewAccountActionResult.DELETED ->
        "Firebase 로그인 계정이 삭제되었습니다. 기기에 저장한 집안일은 유지됩니다."
    HomeCrewAccountActionResult.DELETED_CREDENTIAL_WARNING ->
        "Firebase 계정은 삭제됐지만 Google 계정 선택 기록을 지우지 못했습니다."
    HomeCrewAccountActionResult.CANCELLED ->
        "Google 계정 확인이 취소되었습니다. 계정은 삭제되지 않았습니다."
    HomeCrewAccountActionResult.IN_PROGRESS ->
        "다른 계정 작업이 진행 중입니다."
    HomeCrewAccountActionResult.NOT_SIGNED_IN ->
        "현재 연결된 Firebase 계정이 없습니다."
    HomeCrewAccountActionResult.NETWORK_ERROR ->
        "네트워크 오류가 발생했습니다. 연결을 확인하고 다시 시도해 주세요."
    HomeCrewAccountActionResult.CONFIGURATION_ERROR ->
        "계정 인증 설정을 확인할 수 없습니다. 지원팀에 문의해 주세요."
    HomeCrewAccountActionResult.INVALID_CREDENTIAL ->
        "Google 계정을 다시 확인하지 못했습니다. 계정은 삭제되지 않았습니다."
    HomeCrewAccountActionResult.RECENT_LOGIN_REQUIRED ->
        "재인증이 필요합니다. Google 계정을 확인한 후 다시 시도해 주세요."
    HomeCrewAccountActionResult.FAILED ->
        "계정 작업을 완료하지 못했습니다. 계정 상태를 확인한 뒤 다시 시도해 주세요."
}

internal fun accountActionIsError(result: HomeCrewAccountActionResult): Boolean = when (result) {
    HomeCrewAccountActionResult.SIGNED_OUT,
    HomeCrewAccountActionResult.DELETED,
    HomeCrewAccountActionResult.CANCELLED,
    HomeCrewAccountActionResult.NOT_SIGNED_IN,
    HomeCrewAccountActionResult.IN_PROGRESS -> false
    else -> true
}
