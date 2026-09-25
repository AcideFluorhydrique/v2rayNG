package com.v2ray.ang.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R

/**
 * Offers to repeat a request that failed through the proxy without it
 * (handler/FetchRoutePolicy.kt). Focus starts on Cancel: the retry shows the user's real
 * address, so it must be a deliberate choice.
 */
@Composable
fun DirectRetryDialog(
    onRetryDirect: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDialog(
        title = stringResource(R.string.fetch_direct_retry_title),
        message = stringResource(R.string.fetch_direct_retry_message),
        confirmText = stringResource(R.string.fetch_direct_retry_confirm),
        onConfirm = onRetryDirect,
        onDismiss = onDismiss
    )
}
