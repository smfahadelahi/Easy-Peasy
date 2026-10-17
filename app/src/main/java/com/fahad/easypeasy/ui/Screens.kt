package com.fahad.easypeasy.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import java.util.Locale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.fahad.easypeasy.R
import com.fahad.easypeasy.ui.theme.EasyPeasyTheme
import java.time.LocalDate

@Composable
fun EasyPeasyApp(vm: AppViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val localizedConfig = remember(baseConfig, state.preferences.bangla) {
        Configuration(baseConfig).apply { setLocale(Locale(if (state.preferences.bangla) "bn" else "en")) }
    }
    val localizedContext = remember(baseContext, localizedConfig) { baseContext.createConfigurationContext(localizedConfig) }
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides localizedConfig) {
    EasyPeasyTheme(state.preferences.dark) {
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.notice) {
            state.notice?.let { snackbar.showSnackbar(it); vm.dismissNotice() }
        }
        val route = nav.currentBackStackEntryAsState().value?.destination?.route
        val mainRoutes = listOf("home", "plan", "progress", "settings")
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            if (route in mainRoutes) NavigationBar {
                mainRoutes.forEachIndexed { i, target ->
                    val labels = listOf(R.string.home, R.string.plan, R.string.progress, R.string.settings)
                    val icons = listOf(Icons.Default.Home, Icons.Default.Map, Icons.Default.Insights, Icons.Default.Settings)
                    NavigationBarItem(selected = route == target, onClick = {
                        nav.navigate(target) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
                    }, icon = { Icon(icons[i], null) }, label = { Text(stringResource(labels[i])) })
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (state.loading) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(); Text(stringResource(R.string.loading))
                    }
                } else if (state.error != null) {
                    Column(Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.error), style = MaterialTheme.typography.headlineMedium)
                        Text(state.error.orEmpty()); Action(R.string.retry, vm::reload)
                    }
                } else {
                    val start = remember { if (state.preferences.onboarded) "home" else "onboarding" }
                    NavHost(nav, startDestination = start) {
                    composable("onboarding") { OnboardingScreen(state, vm::preferences) {
                        vm.preferences(state.preferences.copy(onboarded = true)); nav.navigate("models") { popUpTo("onboarding") { inclusive = true } }
                    } }
                    composable("models") { ModelScreen(state, vm::download, vm::pauseDownload) { nav.navigate("home") { popUpTo(nav.graph.id) { inclusive = true } } } }
                    composable("home") { HomeScreen(state) { if (it == "chat") vm.selectMission(state.selectedMission); nav.navigate(it) } }
                    composable("plan") { PlanScreen(state) { vm.selectMission(it); nav.navigate("chat") } }
                    composable("chat") { VoiceScreen(state, vm::demoReply, vm::replay, vm::mute, vm::startLive) {
                        vm.finishSession(); nav.navigate("summary") { popUpTo("chat") { inclusive = true } }
                    } }
                    composable("summary") { SummaryScreen(state) { nav.navigate("home") { popUpTo("home") { inclusive = true } } } }
                    composable("vocab") { VocabScreen(state, vm::gradeWord, vm::replay) }
                    composable("progress") { ProgressScreen(state) }
                    composable("scenarios") { ScenarioScreen(state) { vm.selectMission(it); nav.navigate("chat") } }
                    composable("settings") { SettingsScreen(state, vm::preferences, {
                        vm.reset()
                        nav.navigate("onboarding") { popUpTo(nav.graph.id) { inclusive = true } }
                    }, vm::exportData) { nav.navigate("models") } }
                }
                }
                if (route !in mainRoutes && route !in listOf("chat", "onboarding", "models", null)) {
                    // System/predictive back remains the primary navigation affordance.
                    IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun Page(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("EASY PEASY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        content()
        Spacer(Modifier.height(24.dp))
    }
}
@Composable
private fun Tile(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable
private fun Action(label: Int, click: () -> Unit, enabled: Boolean = true) {
    Button(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(label)) }
}
@Composable
private fun DemoBanner() {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
        Text(stringResource(R.string.demo), Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge)
    }
}
@Composable
fun OnboardingScreen(state: AppState, update: (Preferences) -> Unit, next: () -> Unit) {
    Page(R.string.welcome) {
        Mascot("IDLE", true)
        Text(stringResource(R.string.promise), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.language))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!state.preferences.bangla, { update(state.preferences.copy(bangla = false)) }, { Text("English") })
            FilterChip(state.preferences.bangla, { update(state.preferences.copy(bangla = true)) }, { Text("বাংলা") })
        }
        Text(stringResource(R.string.level))
        listOf(R.string.beginner, R.string.some_basics, R.string.intermediate).forEachIndexed { index, label ->
            FilterChip(state.preferences.proficiency == index, { update(state.preferences.copy(proficiency = index)) }, { Text(stringResource(label)) })
        }
        Text(stringResource(R.string.goal))
        listOf(R.string.goal_default, R.string.goal_study, R.string.goal_work).forEachIndexed { index, label ->
            FilterChip(state.preferences.goal == index, { update(state.preferences.copy(goal = index)) }, { Text(stringResource(label)) })
        }
        Text("${stringResource(R.string.daily_time)}: ${state.preferences.dailyMinutes}")
        Slider(state.preferences.dailyMinutes.toFloat(), { update(state.preferences.copy(dailyMinutes = it.toInt())) }, valueRange = 5f..30f, steps = 4)
        Tile { Text(stringResource(R.string.mic_explanation)) }
        Action(R.string.begin, next)
    }
}
@Composable
fun ModelScreen(state: AppState, download: () -> Unit, pause: () -> Unit, next: () -> Unit) {
    Page(R.string.model_setup) {
        Text(stringResource(R.string.offline))
        Tile {
            Text("RAM: ${state.memoryGb} GB")
            Text(state.recommendedModel)
            Text("${stringResource(R.string.storage)}: ${state.storageGb} GB")
            if (state.storageGb < 4) Text(stringResource(R.string.low_memory), color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.missing_models))
        DemoBanner()
        Text("Silero VAD • 2.3 MB • MIT")
        Text(state.downloadState, style = MaterialTheme.typography.labelLarge)
        if (state.downloadedBytes > 0) LinearProgressIndicator(progress = { (state.downloadedBytes.toFloat() / com.fahad.easypeasy.engine.ModelCatalog.silero.bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        if (state.downloadError.isNotBlank()) Text(state.downloadError, color = MaterialTheme.colorScheme.error)
        Action(if (state.downloadState in listOf("FAILED", "CANCELLED")) R.string.retry else R.string.download, download,
            enabled = state.downloadState !in listOf("RUNNING", "ENQUEUED", "SUCCEEDED"))
        Action(R.string.pause, pause, enabled = state.downloadState in listOf("RUNNING", "ENQUEUED"))
        Action(R.string.continue_label, next)
    }
}
@Composable
fun HomeScreen(state: AppState, open: (String) -> Unit) {
    Page(R.string.home) {
        Text(stringResource(R.string.promise))
        Tile {
            Text(stringResource(R.string.mission), style = MaterialTheme.typography.labelLarge)
            Text(if (state.preferences.bangla) state.mission?.title_bn.orEmpty() else state.mission?.title_en.orEmpty(), style = MaterialTheme.typography.headlineSmall)
            Text(state.mission?.ko.orEmpty(), fontFamily = FontFamily(Font(R.font.noto_sans_kr)), style = MaterialTheme.typography.titleLarge)
            Action(R.string.start_chat, { open("chat") })
        }
        val today = LocalDate.now().toString()
        val minutes = state.sessions.filter { it.day == today }.sumOf { it.minutes }
        Tile {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(progress = { (minutes.toFloat() / state.preferences.dailyMinutes).coerceIn(0f, 1f) }, modifier = Modifier.size(64.dp).semantics { contentDescription = "$minutes / ${state.preferences.dailyMinutes}" })
                Column { Text("$minutes / ${state.preferences.dailyMinutes} min"); Text("${state.streak} ${stringResource(R.string.streak)}") }
            }
        }
        Action(R.string.review, { open("vocab") }); Action(R.string.scenarios, { open("scenarios") })
        DemoBanner()
    }
}
@Composable
fun Mascot(phase: String, reduceMotion: Boolean) {
    val scale by animateFloatAsState(if (reduceMotion || phase == "IDLE") 1f else 1.05f, label = "orb")
    val label = stringResource(when (phase) { "LISTENING" -> R.string.orb_listening; "THINKING" -> R.string.orb_thinking; "SPEAKING" -> R.string.orb_speaking; else -> R.string.orb_idle })
    val color = when (phase) {
        "LISTENING" -> MaterialTheme.colorScheme.secondaryContainer
        "THINKING" -> MaterialTheme.colorScheme.tertiaryContainer
        "SPEAKING" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.mascot), null, Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.size(144.dp).clip(CircleShape).background(color).padding(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}
@Composable
fun VoiceScreen(state: AppState, send: () -> Unit, replay: (String) -> Unit, mute: () -> Unit, live: () -> Unit, finish: () -> Unit) {
    var exit by rememberSaveable { mutableStateOf(false) }
    var translated by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> denied = !granted; if (granted) live() }
    BackHandler { exit = true }
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.chat), style = MaterialTheme.typography.headlineMedium) }
        item { DemoBanner() }
        item { Mascot(state.phase, state.preferences.reduceMotion) }
        item { Tile {
            Text(stringResource(R.string.show_word), style = MaterialTheme.typography.labelLarge)
            Text(state.mission?.ko.orEmpty(), fontFamily = FontFamily(Font(R.font.noto_sans_kr)), style = MaterialTheme.typography.headlineSmall)
            if (state.preferences.romanization) Text(state.mission?.romanization.orEmpty())
            if (state.preferences.hints || translated || help) Text(state.mission?.bn.orEmpty())
        } }
        if (state.bubbles.isEmpty()) item { Text(stringResource(R.string.empty_chat)) }
        items(state.bubbles) { bubble ->
            Surface(color = if (bubble.learner) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                Text(bubble.text, Modifier.fillMaxWidth().padding(16.dp))
            }
        }
        if (state.hint.isNotBlank()) item { Tile { Text(stringResource(R.string.feedback)); Text(state.hint) } }
        if (denied) item { Text(stringResource(R.string.permission_denied), color = MaterialTheme.colorScheme.error) }
        if (state.memoryGb < 4) item { Text(stringResource(R.string.low_memory)) }
        item { Action(R.string.demo_send, send) }
        item { Action(R.string.start_live, {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) live()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }, enabled = false) }
        item { Action(R.string.translate, { translated = !translated }); Action(R.string.replay, { replay(state.mission?.ko.orEmpty()) }) }
        item { Action(R.string.help, { help = !help }); Action(if (state.muted) R.string.unmute else R.string.mute, mute, enabled = false) }
        item { Action(R.string.finish, { exit = true }) }
    }
    if (exit) AlertDialog(onDismissRequest = { exit = false }, title = { Text(stringResource(R.string.exit_title)) }, text = { Text(stringResource(R.string.exit_body)) }, confirmButton = { TextButton(onClick = { exit = false; finish() }) { Text(stringResource(R.string.finish)) } }, dismissButton = { TextButton(onClick = { exit = false }) { Text(stringResource(R.string.cancel)) } })
}
@Composable
fun SummaryScreen(state: AppState, next: () -> Unit) {
    Page(R.string.summary) {
        Mascot("IDLE", true)
        Text(stringResource(R.string.score), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.no_mistakes))
        Tile { Text(stringResource(R.string.new_words)); Text(state.mission?.ko.orEmpty()); Text(state.mission?.bn.orEmpty()) }
        Text(stringResource(R.string.next_goal)); DemoBanner(); Action(R.string.home, next)
    }
}
@Composable
fun VocabScreen(state: AppState, grade: (String, Boolean) -> Unit, replay: (String) -> Unit) {
    val word = state.words.firstOrNull { it.due }
    var revealed by remember(word?.ko) { mutableStateOf(false) }
    Page(R.string.vocab) {
        if (word == null) Text(stringResource(R.string.empty_vocab)) else {
            Tile {
                Text(word.ko, fontFamily = FontFamily(Font(R.font.noto_sans_kr)), style = MaterialTheme.typography.headlineMedium)
                if (state.preferences.romanization) Text(word.romanization)
                if (revealed) Text(word.bn)
                Action(R.string.reveal, { revealed = true })
                Action(R.string.replay, { replay(word.ko) })
            }
            if (revealed) {
                Action(R.string.again, { grade(word.ko, false); revealed = false })
                Action(R.string.remembered, { grade(word.ko, true); revealed = false })
            }
            Action(R.string.speak_answer, {}, enabled = false)
            Text(stringResource(R.string.missing_models))
        }
    }
}
@Composable
fun PlanScreen(state: AppState, select: (String) -> Unit) {
    Page(R.string.path) {
        state.chapters.forEach { chapter ->
            Text(if (state.preferences.bangla) chapter.title_bn else chapter.title_en, style = MaterialTheme.typography.titleLarge)
            chapter.missions.forEach { mission ->
                val index = state.missions.indexOf(mission)
                val unlocked = index == 0 || state.missions.getOrNull(index - 1)?.id in state.completed
                val status = if (mission.id in state.completed) R.string.done else if (unlocked) R.string.current else R.string.locked
                OutlinedButton(onClick = { select(mission.id) }, enabled = unlocked, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Column { Text(if (state.preferences.bangla) mission.title_bn else mission.title_en); Text(stringResource(status), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}
@Composable
fun ProgressScreen(state: AppState) {
    Page(R.string.progress) {
        Text("${stringResource(R.string.beginner)} • ${state.completed.size} / 20", style = MaterialTheme.typography.titleLarge)
        Text("${state.streak} ${stringResource(R.string.streak)}")
        Tile {
            Text(stringResource(R.string.weekly))
            (6 downTo 0).forEach { offset ->
                val date = LocalDate.now().minusDays(offset.toLong()).toString()
                val min = state.sessions.filter { it.day == date }.sumOf { it.minutes }
                Text("$date • $min min", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { (min / state.preferences.dailyMinutes.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        }
        Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge)
        if (state.sessions.isEmpty()) Text(stringResource(R.string.empty_history))
        state.sessions.take(30).forEach { Tile { Text(it.day); Text(it.summary); Text("${it.minutes} min") } }
    }
}
@Composable
fun ScenarioScreen(state: AppState, select: (String) -> Unit) {
    Page(R.string.scenarios) {
        Text(stringResource(R.string.path))
        state.chapters.forEach { chapter ->
            Tile {
                Text(if (state.preferences.bangla) chapter.title_bn else chapter.title_en, style = MaterialTheme.typography.titleLarge)
                Text(chapter.missions.first().ko)
                // Library previews are available without bypassing chapter progression.
                Action(R.string.show_word, { select(chapter.missions.first().id) })
            }
        }
    }
}
@Composable
fun SettingsScreen(state: AppState, update: (Preferences) -> Unit, reset: () -> Unit, export: (android.net.Uri) -> Unit, models: () -> Unit) {
    val p = state.preferences
    var confirm by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) update(p.copy(reminderEnabled = true)) }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(export) }
    Page(R.string.settings) {
        Toggle(R.string.language, p.bangla) { update(p.copy(bangla = it)) }
        Text("${stringResource(R.string.silence)}: ${"%.1f".format(p.silence)}")
        Slider(p.silence, { update(p.copy(silence = it)) }, valueRange = 1f..6f, steps = 9)
        Text("${stringResource(R.string.speed)}: ${"%.2f".format(p.speed)}")
        Slider(p.speed, { update(p.copy(speed = it)) }, valueRange = 0.5f..1.2f)
        Toggle(R.string.romanization, p.romanization) { update(p.copy(romanization = it)) }
        Toggle(R.string.hints, p.hints) { update(p.copy(hints = it)) }
        Toggle(R.string.dark, p.dark) { update(p.copy(dark = it)) }
        Toggle(R.string.reduce_motion, p.reduceMotion) { update(p.copy(reduceMotion = it)) }
        Toggle(R.string.wifi, p.wifiOnly) { update(p.copy(wifiOnly = it)) }
        Toggle(R.string.reminders_enabled, p.reminderEnabled) { enabled ->
            if (enabled && android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else update(p.copy(reminderEnabled = enabled))
        }
        Text("${stringResource(R.string.reminder)}: ${p.reminderHour}:00")
        Slider(p.reminderHour.toFloat(), { update(p.copy(reminderHour = it.toInt())) }, valueRange = 0f..23f, steps = 22)
        Text(stringResource(R.string.storage) + ": ${state.storageGb} GB")
        Action(R.string.models, models); Action(R.string.export, { exportLauncher.launch("easy-peasy-data.json") })
        Action(R.string.licenses, { licenses = true }); Action(R.string.reset, { confirm = true })
        Text(stringResource(R.string.offline))
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, text = { Text(stringResource(R.string.reset_confirm)) }, confirmButton = { TextButton(onClick = { reset(); confirm = false }) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.back)) } })
    if (licenses) AlertDialog(onDismissRequest = { licenses = false }, title = { Text(stringResource(R.string.licenses)) }, text = { Text("AndroidX, Kotlin, Hilt, ONNX Runtime, MediaPipe. Full notices: THIRD_PARTY_NOTICES.md in the source repository. Models are not bundled. Bundled Noto fonts: SIL OFL 1.1.") }, confirmButton = { TextButton(onClick = { licenses = false }) { Text(stringResource(R.string.back)) } })
}
@Composable
private fun Toggle(label: Int, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1f)); val description = stringResource(label)
        Switch(checked, change, modifier = Modifier.semantics { contentDescription = description })
    }
}

@Preview(showBackground = true) @Composable private fun OnboardingPreview() { EasyPeasyTheme { OnboardingScreen(AppState(), {}, {}) } }
@Preview(showBackground = true) @Composable private fun ModelsPreview() { EasyPeasyTheme { ModelScreen(AppState(), {}, {}, {}) } }
@Preview(showBackground = true) @Composable private fun HomePreview() { EasyPeasyTheme { HomeScreen(AppState(), {}) } }
@Preview(showBackground = true) @Composable private fun ChatPreview() { EasyPeasyTheme { VoiceScreen(AppState(), {}, {}, {}, {}, {}) } }
@Preview(showBackground = true) @Composable private fun SummaryPreview() { EasyPeasyTheme { SummaryScreen(AppState(), {}) } }
@Preview(showBackground = true) @Composable private fun VocabPreview() { EasyPeasyTheme { VocabScreen(AppState(), { _, _ -> }, {}) } }
@Preview(showBackground = true) @Composable private fun PlanPreview() { EasyPeasyTheme { PlanScreen(AppState(), {}) } }
@Preview(showBackground = true) @Composable private fun ProgressPreview() { EasyPeasyTheme { ProgressScreen(AppState(),) } }
@Preview(showBackground = true) @Composable private fun ScenariosPreview() { EasyPeasyTheme { ScenarioScreen(AppState(), {}) } }
@Preview(showBackground = true, fontScale = 2f) @Composable private fun SettingsPreview() { EasyPeasyTheme { SettingsScreen(AppState(), {}, {}, {}, {}) } }
