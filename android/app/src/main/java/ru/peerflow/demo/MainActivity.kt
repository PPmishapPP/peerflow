package ru.peerflow.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import ru.peerflow.demo.ui.screens.CloseTaskScreen
import ru.peerflow.demo.ui.screens.LeaderReportScreen
import ru.peerflow.demo.ui.screens.NewProposalScreen
import ru.peerflow.demo.ui.screens.PainMessengerScreen
import ru.peerflow.demo.ui.screens.ProfileScreen
import ru.peerflow.demo.ui.screens.ProposalDetailScreen
import ru.peerflow.demo.ui.screens.ProposalListScreen
import ru.peerflow.demo.ui.theme.PeerflowTheme

/**
 * Прототип для презентации. Навигация простая, на состоянии: экранов мало,
 * а бэкенда нет — данные демонстрационные.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PeerflowTheme {
                PeerflowDemoApp()
            }
        }
    }
}

private sealed interface Screen {
    data object Pain : Screen
    data object List : Screen
    data object NewProposal : Screen
    data class Detail(val proposalId: String) : Screen
    data class Report(val proposalId: String) : Screen
    data class Close(val proposalId: String) : Screen
    data object Profile : Screen
}

@Composable
private fun PeerflowDemoApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Pain) }

    when (val current = screen) {
        is Screen.Pain -> PainMessengerScreen(onContinue = { screen = Screen.List })
        is Screen.List -> ProposalListScreen(
            onOpenProposal = { id -> screen = Screen.Detail(id) },
            onOpenProfile = { screen = Screen.Profile },
            onNewProposal = { screen = Screen.NewProposal },
        )
        is Screen.NewProposal -> NewProposalScreen(
            onBack = { screen = Screen.List },
            onCreated = { screen = Screen.List },
        )
        is Screen.Detail -> ProposalDetailScreen(
            proposalId = current.proposalId,
            onBack = { screen = Screen.List },
            onOpenReport = { screen = Screen.Report(current.proposalId) },
        )
        is Screen.Report -> LeaderReportScreen(
            proposalId = current.proposalId,
            onBack = { screen = Screen.Detail(current.proposalId) },
            onCloseTask = { screen = Screen.Close(current.proposalId) },
        )
        is Screen.Close -> CloseTaskScreen(
            proposalId = current.proposalId,
            onBack = { screen = Screen.Report(current.proposalId) },
            onDone = { screen = Screen.List },
        )
        is Screen.Profile -> ProfileScreen(onBack = { screen = Screen.List })
    }
}
