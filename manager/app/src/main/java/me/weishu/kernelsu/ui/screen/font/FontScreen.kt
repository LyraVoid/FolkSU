package me.weishu.kernelsu.ui.screen.font

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedRadioItem
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.theme.FontConfig
import me.weishu.kernelsu.ui.theme.FontMode

/**
 * The font setting: the three [FontMode]s, plus the import and restore rows that only make sense
 * once CUSTOM is selected. The modes are mutually exclusive, so they are radio rows in one group.
 */
@Composable
fun FontScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var showRestoreDialog by remember { mutableStateOf(false) }

    val pickFontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { FontConfig.saveFontFile(context, uri) }
                snackbarHost.showSnackbar(
                    context.getString(if (ok) R.string.font_applied else R.string.font_apply_failed)
                )
            }
        }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(stringResource(R.string.font_restore_default)) },
            text = { Text(stringResource(R.string.font_restore_default_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    FontConfig.clearFont(context)
                    scope.launch {
                        snackbarHost.showSnackbar(context.getString(R.string.font_restored))
                    }
                }) {
                    Text(stringResource(R.string.font_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(stringResource(R.string.font_cancel))
                }
            },
        )
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.font_title)) },
                navigationIcon = { TopBarBackButton(onClick = { navigator.pop() }) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackbarHost) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SegmentedColumn(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 13.dp),
                content = buildList {
                    add {
                        SegmentedRadioItem(
                            title = stringResource(R.string.font_mode_app),
                            selected = FontConfig.fontMode == FontMode.APP_DEFAULT,
                            onClick = { FontConfig.setFontMode(context, FontMode.APP_DEFAULT) },
                        )
                    }
                    add {
                        SegmentedRadioItem(
                            title = stringResource(R.string.font_mode_system),
                            selected = FontConfig.fontMode == FontMode.SYSTEM_DEFAULT,
                            onClick = { FontConfig.setFontMode(context, FontMode.SYSTEM_DEFAULT) },
                        )
                    }
                    add {
                        SegmentedRadioItem(
                            title = stringResource(R.string.font_mode_custom),
                            selected = FontConfig.fontMode == FontMode.CUSTOM,
                            onClick = { FontConfig.setFontMode(context, FontMode.CUSTOM) },
                        )
                    }
                },
            )

            if (FontConfig.fontMode == FontMode.CUSTOM) {
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    content = buildList {
                        add {
                            SegmentedListItem(
                                onClick = { pickFontLauncher.launch("*/*") },
                                headlineContent = { Text(stringResource(R.string.font_select_file)) },
                                leadingContent = {
                                    Icon(Icons.Filled.FontDownload, contentDescription = null)
                                },
                            )
                        }
                        if (FontConfig.customFontFilename != null) {
                            add {
                                SegmentedListItem(
                                    onClick = { showRestoreDialog = true },
                                    headlineContent = { Text(stringResource(R.string.font_restore_default)) },
                                    leadingContent = {
                                        Icon(Icons.Filled.Delete, contentDescription = null)
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
