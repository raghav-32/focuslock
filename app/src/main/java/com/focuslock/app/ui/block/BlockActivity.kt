package com.focuslock.app.ui.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.focuslock.app.MainActivity
import com.focuslock.app.container
import com.focuslock.app.domain.AppSettings
import com.focuslock.app.domain.BlockAction
import com.focuslock.app.domain.BlockEvent
import com.focuslock.app.domain.BlockSource
import com.focuslock.app.domain.BypassMode
import com.focuslock.app.domain.BypassPolicy
import com.focuslock.app.domain.RuleKind
import com.focuslock.app.domain.formatDuration
import com.focuslock.app.ui.theme.FocusLockTheme
import com.focuslock.app.ui.theme.Purple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The "This App Is Blocked" screen. It never imitates the blocked app, explains the exact rule
 * that fired, and always offers a way home. Back = home so the user is never bounced into a loop.
 */
class BlockActivity : ComponentActivity() {

    private var args by mutableStateOf(BlockArgs())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        args = BlockArgs.from(intent)
        setContent {
            val settings by container.settings.settings.collectAsState(initial = AppSettings())
            FocusLockTheme(settings.themeMode) {
                BlockScreen(
                    args = args,
                    settings = settings,
                    onHome = { goHome() },
                    onProgress = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(MainActivity.EXTRA_ROUTE, "stats")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        )
                        finish()
                    },
                    onBackToApp = { finish() },
                    onBypass = { grantBypass(settings) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        args = BlockArgs.from(intent)
    }

    @Deprecated("Back always means home on this screen.")
    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finishAndRemoveTask()
    }

    private fun grantBypass(settings: AppSettings) {
        val c = container
        val a = args
        lifecycleScope.launch {
            val wall = c.time.wallMillis()
            val until = BypassPolicy.grantUntil(wall, settings.bypassMinutes)
            c.settings.update {
                it.copy(
                    bypassGrants = it.bypassGrants + (a.packageName to until),
                    lastBypassEndWall = until,
                )
            }
            runCatching {
                c.stats.recordBlock(
                    BlockEvent(
                        timestamp = wall,
                        packageName = a.packageName,
                        kind = a.kind,
                        ruleId = a.packageName,
                        ruleLabel = a.targetLabel,
                        source = a.source,
                        action = BlockAction.BYPASSED,
                    ),
                )
            }
            c.discovery.launchIntentFor(a.packageName)?.let { launch ->
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_TARGET_LABEL = "label"
        const val EXTRA_KIND = "kind"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_SOURCE_NAME = "sourceName"
        const val EXTRA_ENDS_AT = "endsAt"
        const val EXTRA_STRICT = "strict"
    }
}

data class BlockArgs(
    val packageName: String = "",
    val targetLabel: String = "",
    val kind: RuleKind = RuleKind.APP,
    val source: BlockSource = BlockSource.ALWAYS_ON,
    val sourceName: String = "",
    val endsAtWall: Long = 0L,
    val strict: Boolean = false,
) {
    companion object {
        fun from(i: Intent) = BlockArgs(
            packageName = i.getStringExtra(BlockActivity.EXTRA_PACKAGE).orEmpty(),
            targetLabel = i.getStringExtra(BlockActivity.EXTRA_TARGET_LABEL).orEmpty(),
            kind = runCatching { RuleKind.valueOf(i.getStringExtra(BlockActivity.EXTRA_KIND).orEmpty()) }
                .getOrDefault(RuleKind.APP),
            source = runCatching { BlockSource.valueOf(i.getStringExtra(BlockActivity.EXTRA_SOURCE).orEmpty()) }
                .getOrDefault(BlockSource.ALWAYS_ON),
            sourceName = i.getStringExtra(BlockActivity.EXTRA_SOURCE_NAME).orEmpty(),
            endsAtWall = i.getLongExtra(BlockActivity.EXTRA_ENDS_AT, 0L),
            strict = i.getBooleanExtra(BlockActivity.EXTRA_STRICT, false),
        )
    }
}

@Composable
fun BlockScreen(
    args: BlockArgs,
    settings: AppSettings,
    onHome: () -> Unit,
    onProgress: () -> Unit,
    onBackToApp: () -> Unit,
    onBypass: () -> Unit,
) {
    var remaining by remember(args.endsAtWall) { mutableLongStateOf(Long.MAX_VALUE) }
    LaunchedEffect(args.endsAtWall) {
        if (args.endsAtWall <= 0L) return@LaunchedEffect
        while (true) {
            remaining = (args.endsAtWall - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remaining == 0L) break
            delay(1_000)
        }
    }

    var bypassStage by remember { mutableStateOf(0) } // 0 none, 1 confirm, 2 countdown
    var countdown by remember { mutableStateOf(0) }
    LaunchedEffect(bypassStage) {
        if (bypassStage == 2) {
            countdown = settings.countdownSeconds
            while (countdown > 0) {
                delay(1_000)
                countdown--
            }
            onBypass()
        }
    }

    val canBypass = BypassPolicy.canRequest(settings, args.strict, System.currentTimeMillis())
    val cooldown = BypassPolicy.cooldownRemainingMillis(settings, System.currentTimeMillis())

    val pulse = rememberInfiniteTransition(label = "pulse")
    val s by pulse.animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "scale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("FocusLock", style = MaterialTheme.typography.titleMedium, color = Purple, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .size(132.dp)
                    .scale(s)
                    .clip(CircleShape)
                    .background(Purple.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Purple, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "This App Is Blocked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            val what = if (args.kind == RuleKind.FEED) args.targetLabel else args.targetLabel.ifBlank { args.packageName }
            Text(
                "$what is blocked.",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Why", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(reasonText(args), style = MaterialTheme.typography.bodyLarge)
                    if (args.endsAtWall > 0L && remaining != Long.MAX_VALUE) {
                        Text(
                            if (remaining > 0) "Time remaining: ${formatDuration(remaining)}" else "This block has just ended.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (args.strict) {
                        Text(
                            "Strict session: bypass is turned off until it ends.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                settings.blockMessage,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            Button(onClick = onHome, Modifier.fillMaxWidth().height(52.dp)) { Text("Return to Home") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onProgress, Modifier.fillMaxWidth().height(52.dp)) { Text("View My Progress") }
            if (args.kind == RuleKind.FEED) {
                TextButton(onClick = onBackToApp) { Text("Back to ${args.packageName.substringAfterLast('.')}") }
            }

            if (settings.bypassMode != BypassMode.NONE && !args.strict) {
                Spacer(Modifier.height(8.dp))
                when {
                    bypassStage == 1 -> {
                        Text(
                            "Open it for ${settings.bypassMinutes} minutes?",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row2(
                            onYes = {
                                if (settings.bypassMode == BypassMode.COUNTDOWN) bypassStage = 2 else onBypass()
                            },
                            onNo = { bypassStage = 0 },
                        )
                    }
                    bypassStage == 2 -> {
                        Text(
                            "Opening in $countdown s. Change your mind any time.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { bypassStage = 0 }) { Text("Cancel") }
                    }
                    canBypass -> TextButton(onClick = { bypassStage = 1 }) { Text("Emergency access") }
                    cooldown > 0L -> Text(
                        "Emergency access available again in ${formatDuration(cooldown)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(0.9f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Row2(onYes: () -> Unit, onNo: () -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onYes) { Text("Yes, open it") }
        OutlinedButton(onClick = onNo) { Text("No, stay focused") }
    }
}

private fun reasonText(a: BlockArgs): String = when (a.source) {
    BlockSource.FOCUS_SESSION -> "A focus session is running."
    BlockSource.SCHEDULE -> "Your schedule \"${a.sourceName}\" is active."
    BlockSource.ALWAYS_ON -> "It is on your always-blocked list."
}
