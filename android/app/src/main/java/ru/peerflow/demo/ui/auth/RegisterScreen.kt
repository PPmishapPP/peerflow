package ru.peerflow.demo.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.peerflow.demo.data.model.CurrentUser
import ru.peerflow.demo.ui.components.AvatarPicker

/**
 * Регистрация: логин, пароль, имя, «о себе» и необязательное фото.
 *
 * После успеха человек ещё не член сообщества — заявку он подаёт на следующем
 * экране. Так требует бизнес-правило: регистрация и приём — разные шаги.
 */
@Composable
fun RegisterScreen(
    onRegistered: (CurrentUser) -> Unit,
    onGoToLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RegisterEvent.Registered -> onRegistered(event.user)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Регистрация",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Аккаунт нужен, чтобы подать заявку в сообщество",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.login,
                onValueChange = viewModel::onLoginChange,
                label = { Text("Логин") },
                singleLine = true,
                enabled = !state.loading,
                isError = state.loginError != null,
                supportingText = {
                    Text(state.loginError ?: "3–64 символа: латиница, цифры, точка, дефис, подчёркивание")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Пароль") },
                singleLine = true,
                enabled = !state.loading,
                isError = state.passwordError != null,
                supportingText = {
                    Text(state.passwordError ?: "не короче ${CredentialRules.PASSWORD_MIN} символов")
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = { Text("Как вас зовут") },
                singleLine = true,
                enabled = !state.loading,
                isError = state.displayNameError != null,
                supportingText = state.displayNameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.about,
                onValueChange = viewModel::onAboutChange,
                label = { Text("О себе") },
                enabled = !state.loading,
                isError = state.aboutError != null,
                supportingText = {
                    Text(
                        state.aboutError
                            ?: "${state.about.length} из ${CredentialRules.ABOUT_MAX}",
                    )
                },
                minLines = 3,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            AvatarPicker(
                avatar = state.avatar,
                enabled = !state.loading,
                onPicked = viewModel::onAvatarChange,
            )

            state.formError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Создать аккаунт")
                }
            }

            TextButton(
                onClick = onGoToLogin,
                enabled = !state.loading,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Уже есть аккаунт? Войти")
            }
        }
    }
}
