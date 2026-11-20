package ru.peerflow.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.ui.auth.LoginScreen
import ru.peerflow.demo.ui.auth.RegisterScreen
import ru.peerflow.demo.ui.join.JoinCommunityScreen
import ru.peerflow.demo.ui.join.JoinRequestsScreen
import ru.peerflow.demo.ui.screens.NewProposalScreen
import ru.peerflow.demo.ui.screens.PainMessengerScreen
import ru.peerflow.demo.ui.screens.ProfileScreen
import ru.peerflow.demo.ui.screens.ProposalDetailScreen
import ru.peerflow.demo.ui.screens.ProposalListScreen
import ru.peerflow.demo.ui.session.SessionState
import ru.peerflow.demo.ui.session.SessionViewModel
import ru.peerflow.demo.ui.theme.PeerflowTheme

/**
 * Точка входа приложения.
 *
 * Сначала решается один вопрос: кто перед нами — гость, человек с заявкой или
 * член сообщества. Только член видит демонстрационные экраны продукта.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PeerflowTheme {
                PeerflowApp()
            }
        }
    }
}

/** Экраны неаутентифицированной части. */
private enum class AuthScreen { Login, Register }

/**
 * Гейт сессии (`BR-JOIN-1`, `BR-JOIN-3`, `BR-PRIV-1`).
 *
 * `SessionViewModel` держит единственный источник правды о том, вошёл ли
 * человек и принят ли он в сообщество. Экраны ниже только читают это
 * состояние и просят его изменить.
 */
@Composable
private fun PeerflowApp(
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val session by sessionViewModel.state.collectAsStateWithLifecycle()
    var authScreen by remember { mutableStateOf(AuthScreen.Login) }

    when (val current = session) {
        SessionState.Loading -> CenteredMessage {
            CircularProgressIndicator()
        }

        SessionState.SignedOut -> when (authScreen) {
            AuthScreen.Login -> LoginScreen(
                onLoggedIn = sessionViewModel::onAuthenticated,
                onGoToRegister = { authScreen = AuthScreen.Register },
            )

            AuthScreen.Register -> RegisterScreen(
                onRegistered = sessionViewModel::onAuthenticated,
                onGoToLogin = { authScreen = AuthScreen.Login },
            )
        }

        is SessionState.Failure -> CenteredMessage {
            Text(
                text = "Не удалось проверить вход",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = current.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = sessionViewModel::restore,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Повторить")
            }
            Button(
                onClick = sessionViewModel::logout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Войти заново")
            }
        }

        is SessionState.SignedIn -> {
            if (current.user.member) {
                DemoFlow(
                    currentUser = current.user,
                    onLogout = sessionViewModel::logout,
                )
            } else {
                JoinCommunityScreen(
                    onBecameMember = sessionViewModel::refreshProfile,
                )
            }
        }
    }
}

/** Простой центрированный экран для служебных состояний. */
@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

private sealed interface Screen {
    data object Pain : Screen
    data object List : Screen
    data object NewProposal : Screen
    data class Detail(val proposalId: String) : Screen
    data object Profile : Screen

    /** Очередь заявок на вступление — только для действующего члена. */
    data object JoinRequests : Screen
}

/**
 * Демонстрационная часть продукта для члена сообщества.
 *
 * Навигация на состоянии: экранов мало, а данные пока демонстрационные.
 * Когда появятся настоящие правила и задачи, сюда придёт NavHost.
 */
@Composable
private fun DemoFlow(
    currentUser: CurrentUser,
    onLogout: () -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Pain) }

    when (val current = screen) {
        is Screen.Pain -> PainMessengerScreen(onContinue = { screen = Screen.List })
        is Screen.List -> ProposalListScreen(
            currentUser = currentUser,
            onOpenProposal = { id -> screen = Screen.Detail(id) },
            onOpenProfile = { screen = Screen.Profile },
            onNewProposal = { screen = Screen.NewProposal },
            onOpenJoinRequests = { screen = Screen.JoinRequests },
        )
        is Screen.NewProposal -> NewProposalScreen(
            currentUser = currentUser,
            onBack = { screen = Screen.List },
            onCreated = { screen = Screen.List },
        )
        is Screen.Detail -> ProposalDetailScreen(
            proposalId = current.proposalId,
            onBack = { screen = Screen.List },
        )
        is Screen.Profile -> ProfileScreen(
            onBack = { screen = Screen.List },
            currentUser = currentUser,
            onLogout = onLogout,
        )
        is Screen.JoinRequests -> JoinRequestsScreen(onBack = { screen = Screen.List })
    }
}
