package com.sal7one.untiktok

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ViewModel to handle business logic
class TikTokViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TikTokUiState())
    val uiState: StateFlow<TikTokUiState> = _uiState

    var installedApps = mutableStateOf<List<ApplicationInfo>>(emptyList())
        private set

    fun fetchInstalledApps(context: Context) {
        val packageManager = context.packageManager
        // Filter out your own app; add other filtering as needed.
        installedApps.value =
            packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName }
    }

    private val urlValidator = UrlValidator()

    fun updateUrl(newUrl: String) {
        _uiState.value = _uiState.value.copy(url = newUrl)
    }

    fun updateResolveOption(newValue: Boolean) {
        _uiState.value = _uiState.value.copy(resolveOption = newValue)
    }

    fun handleUrlOpen(
        context: Context,
        onError: (String) -> Unit,
    ) {
        val currentState = _uiState.value
        val trimmedUrl = currentState.url.trim()

        if (!urlValidator.isValidTikTokUrl(trimmedUrl)) {
            onError(context.getString(R.string.invalid_url_error))
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = currentState.copy(isLoading = true)

                if (currentState.resolveOption) {
                    val finalUrl = urlResolver.resolveFinalUrl(trimmedUrl)
                    if (finalUrl != null) {
                        openUrl(context, finalUrl)
                    } else {
                        onError(context.getString(R.string.url_resolution_error))
                    }
                } else {
                    openUrl(context, trimmedUrl)
                }
            } catch (e: Exception) {
                onError(context.getString(R.string.generic_error))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun openUrl(
        context: Context,
        url: String,
        packageName: String = "com.android.chrome",
    ) {
        try {
            val trimmedUrl = url.trim()
            // Only process if it's a TikTok URL
            if (!UrlValidator().isValidTikTokUrl(trimmedUrl)) {
                return
            }

            // Clean the URL and remove tracking parameters
            val cleanUrl = trimmedUrl.substringBefore("?")

            // Create the intent specifically for browser
            val intent =
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(cleanUrl)
                    // This flag forces the URL to open in a browser
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    // Ensure it opens in a new browser task
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK

                    // Force open in browser by setting the package to a browser
                    // Try to open in Chrome first, fall back to system browser
                    try {
                        val savedPackage = getPackageId(context)?.takeIf { it.isNotEmpty() } ?: packageName
                        setPackage(savedPackage)
                    } catch (e: Exception) {
                        // If Chrome isn't available, remove the package specification
                        setPackage(null)
                    }
                }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // If Chrome-specific intent fails, try again with default browser
                intent.setPackage(null)
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error opening browser", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val PREFS_NAME = "app_shared_pref"
        private const val KEY_PACKAGE_ID = "package_id_key"
    }

    // Function to save the package ID in SharedPreferences
    fun savePackageId(
        context: Context,
        packageId: String,
    ) {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().putString(KEY_PACKAGE_ID, packageId).apply()
    }

    // Optional: Function to retrieve the saved package ID
    fun getPackageId(context: Context): String? {
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPref.getString(KEY_PACKAGE_ID, null)
    }
}

// Data class to represent UI state
data class TikTokUiState(
    val url: String = "",
    val resolveOption: Boolean = true,
    val isLoading: Boolean = false,
)

// URL validation logic
class UrlValidator {
    fun isValidTikTokUrl(input: String): Boolean {
        val regex = Regex("^https?://(vt\\.)?(www\\.)?(m\\.)?tiktok\\.com/.+\$")
        return regex.matches(input)
    }
}

object urlResolver {
    suspend fun resolveFinalUrl(inputUrl: String): String? {
        return withContext(Dispatchers.IO) {
            var currentUrl = inputUrl
            var maxRedirects = 5 // Reduced from 10 to prevent excessive redirects
            var previousUrls = mutableSetOf<String>() // Track previously seen URLs

            try {
                while (maxRedirects > 0) {
                    // Check if we've seen this URL before to prevent loops
                    if (previousUrls.contains(currentUrl)) {
                        return@withContext currentUrl // Return last valid URL if we detect a loop
                    }
                    previousUrls.add(currentUrl)

                    val connection = URL(currentUrl).openConnection() as HttpURLConnection
                    connection.apply {
                        instanceFollowRedirects = false
                        requestMethod = "HEAD" // Changed to HEAD request to be lighter
                        connectTimeout = 3000 // Reduced timeout
                        readTimeout = 3000
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                    }

                    try {
                        connection.connect()

                        when (connection.responseCode) {
                            in 300..399 -> {
                                val location = connection.getHeaderField("Location")
                                if (location != null) {
                                    currentUrl =
                                        if (location.startsWith("http")) {
                                            location
                                        } else {
                                            URL(URL(currentUrl), location).toString()
                                        }
                                    maxRedirects--
                                } else {
                                    break
                                }
                            }
                            HttpURLConnection.HTTP_OK -> {
                                break // Found final URL
                            }
                            else -> {
                                return@withContext null // Invalid response code
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                currentUrl
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
