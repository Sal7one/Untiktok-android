package com.sal7one.untiktok

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sal7one.untiktok.ui.theme.UntiktokTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: TikTokViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[TikTokViewModel::class.java]
        handleIncomingIntent(intent)
        setContent {
            UntiktokTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        TikTokUrlOpenerApp()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }


    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                val text = when {
                    intent.hasExtra(Intent.EXTRA_PROCESS_TEXT) ->
                        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                    intent.hasExtra(Intent.EXTRA_PROCESS_TEXT_READONLY) ->
                        intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT_READONLY)?.toString()
                    else -> null
                }
                text?.let { handleSharedText(it) }
            }
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                sharedText?.let { handleSharedText(it) }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.toString()?.let { handleSharedText(it) }
            }
        }
    }

    private fun handleSharedText(text: String) {
        val tikTokUrl = extractTikTokUrl(text)
        tikTokUrl?.let { url ->
            viewModel.updateUrl(url)
            // Directly open URL in browser for shared intents
            viewModel.handleUrlOpen(
                context = this,
                onError = { message ->
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            )
            // Finish the activity after handling the intent
        }
    }

    private fun extractTikTokUrl(text: String): String? {
        // Updated regex to match more TikTok URL patterns
        val regex = Regex(
            "https?://(?:(?:vt|vr|vm|www)\\.)?(?:tiktok\\.com)/(?:@[\\w.-]+/video/\\d+|[\\w.-]+/\\d+|v/\\d+|t/\\d+|.*)"
        )
        return regex.find(text)?.value
    }
    override fun onStop() {
        super.onStop()
        finishAndRemoveTask()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TikTokUrlOpenerApp(viewModel: TikTokViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppLogo()
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            UrlInput(
                url = uiState.url,
                onUrlChange = viewModel::updateUrl,
                onSubmit = {
                    viewModel.handleUrlOpen(
                        context = context,
                        onError = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    )
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ResolveOptionCheckbox(
                checked = uiState.resolveOption,
                onCheckedChange = viewModel::updateResolveOption
            )

            Spacer(modifier = Modifier.height(16.dp))

            OpenUrlButton(
                enabled = uiState.url.trim().isNotEmpty(),
                onClick = {
                    viewModel.setChrome()
                    viewModel.handleUrlOpen(
                        context = context,
                        onError = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    )
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            )
        }

        if (uiState.isLoading) {
            LoadingOverlay()
        }
    }
}

@Composable
private fun AppLogo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "UN",
            style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "TIKTOK",
            style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun UrlInput(
    url: String,
    onUrlChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(R.string.url_input_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() })
    )
}

@Composable
private fun ResolveOptionCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = stringResource(R.string.resolve_option_label))
    }
}

@Composable
private fun OpenUrlButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    ) {
        Text(text = stringResource(R.string.open_url_button))
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}