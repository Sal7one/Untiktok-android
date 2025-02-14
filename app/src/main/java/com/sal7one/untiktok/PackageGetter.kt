package com.sal7one.untiktok

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PackageGetter(setAsDefaultPackage: (String) -> Unit) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    // State for the full list of installed apps
    var installedApps by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    // State for the search query input
    var searchQuery by remember { mutableStateOf("") }

    // Fetch installed apps when the composable is first launched
    LaunchedEffect(Unit) {
        val allApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        // Exclude your own app package from the list
        installedApps = allApps.filter { it.packageName != context.packageName }
    }

    // Filter the list based on the search query
    val filteredApps =
        installedApps.filter { appInfo ->
            val appLabel = packageManager.getApplicationLabel(appInfo).toString()
            appLabel.contains(searchQuery, ignoreCase = true)
        }

    Column(
        modifier =
            Modifier
                .then(
                    if (filteredApps.isNotEmpty()) {
                        Modifier.fillMaxHeight(0.8f)
                    } else {
                        Modifier.padding(0.dp)
                    },
                ).padding(16.dp),
    ) {
        // Text field to input search queries
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Apps") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        )

        if (filteredApps.isEmpty()) {
            // Display a friendly message if no apps match the filter
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "No apps found", style = MaterialTheme.typography.headlineSmall)
            }
        } else {
            // Display the filtered list using LazyColumn
            LazyColumn {
                items(filteredApps) { appInfo ->
                    // Retrieve the human-readable app label
                    val appLabel = packageManager.getApplicationLabel(appInfo).toString()
                    // Each app is shown inside a Card for a cleaner UI
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { setAsDefaultPackage(appInfo.packageName) },
                        elevation =
                            CardDefaults.cardElevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 4.dp,
                                focusedElevation = 4.dp,
                                hoveredElevation = 4.dp,
                                draggedElevation = 4.dp,
                                disabledElevation = 4.dp,
                            ),
                    ) {
                        Text(
                            text = appLabel,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
