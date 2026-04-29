package com.example.calmtask

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

data class TaskItem(
    val title: String,
    val status: String = "active"
)

enum class Screen {
    ONBOARDING,
    HOME,
    NIGHT,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private var textToSpeech: TextToSpeech? = null

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        textToSpeech = TextToSpeech(this) {}

        setContent {
            CalmTaskApp(
                speak = { text, languageCode ->
                    speakText(text, languageCode)
                }
            )
        }
    }

    private fun speakText(text: String, languageCode: String) {
        val locale = when (languageCode) {
            "English" -> Locale.ENGLISH
            "Hindi" -> Locale("hi", "IN")
            "Spanish" -> Locale("es", "ES")
            "French" -> Locale.FRENCH
            "Arabic" -> Locale("ar")
            else -> Locale.ENGLISH
        }

        textToSpeech?.language = locale
        textToSpeech?.setSpeechRate(0.92f)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "calmtask_voice")
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun CalmTaskApp(
    speak: (String, String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("calmtask_prefs", Context.MODE_PRIVATE)
    }

    var screen by remember {
        mutableStateOf(
            if (prefs.getBoolean("onboarded", false)) Screen.HOME else Screen.ONBOARDING
        )
    }

    var name by remember { mutableStateOf(prefs.getString("name", "") ?: "") }
    var country by remember { mutableStateOf(prefs.getString("country", "United States") ?: "United States") }
    var language by remember { mutableStateOf(prefs.getString("language", "English") ?: "English") }
    var gender by remember { mutableStateOf(prefs.getString("gender", "Prefer not to say") ?: "Prefer not to say") }
    var mood by remember { mutableStateOf(prefs.getString("mood", "Calm") ?: "Calm") }
    var voiceEnabled by remember { mutableStateOf(prefs.getBoolean("voiceEnabled", true)) }

    val tasks = remember {
        mutableStateListOf<TaskItem>().apply {
            val savedTasks = prefs.getString("tasks", "")
            if (!savedTasks.isNullOrBlank()) {
                savedTasks.split("|||").forEach { raw ->
                    val parts = raw.split(":::")
                    if (parts.size == 2) {
                        add(TaskItem(parts[0], parts[1]))
                    }
                }
            }
        }
    }

    fun saveAll() {
        prefs.edit()
            .putString("name", name)
            .putString("country", country)
            .putString("language", language)
            .putString("gender", gender)
            .putString("mood", mood)
            .putBoolean("voiceEnabled", voiceEnabled)
            .putBoolean("onboarded", true)
            .putString("tasks", tasks.joinToString("|||") { "${it.title}:::${it.status}" })
            .apply()
    }

    fun say(text: String) {
        if (voiceEnabled) {
            speak(text, language)
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = WarmBackground
        ) {
            when (screen) {
                Screen.ONBOARDING -> OnboardingScreen(
                    name = name,
                    onNameChange = { name = it },
                    country = country,
                    onCountryChange = { country = it },
                    language = language,
                    onLanguageChange = { language = it },
                    gender = gender,
                    onGenderChange = { gender = it },
                    mood = mood,
                    onMoodChange = { mood = it },
                    onFinish = {
                        saveAll()
                        say("Welcome. Let's keep today simple.")
                        screen = Screen.HOME
                    }
                )

                Screen.HOME -> HomeScreen(
                    name = name,
                    language = language,
                    mood = mood,
                    tasks = tasks,
                    voiceEnabled = voiceEnabled,
                    onAddTask = { title ->
                        if (title.isNotBlank()) {
                            tasks.add(TaskItem(title.trim()))
                            saveAll()
                            say("Added.")
                        }
                    },
                    onDone = { index ->
                        tasks[index] = tasks[index].copy(status = "completed")
                        saveAll()
                        say("Done. That's one less thing to carry.")
                    },
                    onLater = { index ->
                        saveAll()
                        say("Okay. I'll keep it for later.")
                    },
                    onSkip = { index ->
                        tasks[index] = tasks[index].copy(status = "skipped")
                        saveAll()
                        say("Skipped.")
                    },
                    onMorningGreeting = {
                        val active = tasks.count { it.status == "active" }
                        val phrase = greetingForMood(mood, active)
                        say(phrase)
                    },
                    onVoiceCommand = { command ->
                        handleSimpleCommand(
                            command = command,
                            tasks = tasks,
                            save = { saveAll() },
                            say = { say(it) }
                        )
                    },
                    goNight = { screen = Screen.NIGHT },
                    goSettings = { screen = Screen.SETTINGS }
                )

                Screen.NIGHT -> NightScreen(
                    tasks = tasks,
                    onMoveAllActiveToTomorrow = {
                        saveAll()
                        say("Saved for tomorrow.")
                    },
                    onDeleteSkipped = {
                        tasks.removeAll { it.status == "skipped" }
                        saveAll()
                        say("Cleared skipped tasks.")
                    },
                    onBack = { screen = Screen.HOME }
                )

                Screen.SETTINGS -> SettingsScreen(
                    name = name,
                    country = country,
                    language = language,
                    gender = gender,
                    mood = mood,
                    voiceEnabled = voiceEnabled,
                    onNameChange = {
                        name = it
                        saveAll()
                    },
                    onCountryChange = {
                        country = it
                        saveAll()
                    },
                    onLanguageChange = {
                        language = it
                        saveAll()
                    },
                    onGenderChange = {
                        gender = it
                        saveAll()
                    },
                    onMoodChange = {
                        mood = it
                        saveAll()
                    },
                    onVoiceEnabledChange = {
                        voiceEnabled = it
                        saveAll()
                    },
                    onClearTasks = {
                        tasks.clear()
                        saveAll()
                    },
                    onBack = { screen = Screen.HOME }
                )
            }
        }
    }
}

val WarmBackground = Color(0xFFF8F5EF)
val PrimaryBlue = Color(0xFF4F7DF3)
val CalmGreen = Color(0xFF5CB85C)
val WarmAmber = Color(0xFFF5A623)
val Charcoal = Color(0xFF222222)
val MutedGray = Color(0xFF777777)
val CardWhite = Color(0xFFFFFFFF)

@Composable
fun OnboardingScreen(
    name: String,
    onNameChange: (String) -> Unit,
    country: String,
    onCountryChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    gender: String,
    onGenderChange: (String) -> Unit,
    mood: String,
    onMoodChange: (String) -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Welcome to CalmTask",
            fontWeight = FontWeight.Bold,
            color = Charcoal,
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "I’ll help you choose what matters today without annoying you.",
            color = MutedGray
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth()
        )

        SimpleDropdown(
            label = "Country",
            selected = country,
            options = listOf(
                "United States",
                "India",
                "United Kingdom",
                "Canada",
                "Australia",
                "Spain",
                "France",
                "Saudi Arabia",
                "Other"
            ),
            onSelected = onCountryChange
        )

        SimpleDropdown(
            label = "Language",
            selected = language,
            options = listOf(
                "English",
                "Hindi",
                "Spanish",
                "French",
                "Arabic"
            ),
            onSelected = onLanguageChange
        )

        SimpleDropdown(
            label = "Gender",
            selected = gender,
            options = listOf(
                "Female",
                "Male",
                "Non-binary",
                "Prefer not to say"
            ),
            onSelected = onGenderChange
        )

        Text(
            text = "How are you starting today?",
            fontWeight = FontWeight.Bold,
            color = Charcoal
        )

        MoodButtons(
            selectedMood = mood,
            onMoodChange = onMoodChange
        )

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start")
        }
    }
}

@Composable
fun HomeScreen(
    name: String,
    language: String,
    mood: String,
    tasks: MutableList<TaskItem>,
    voiceEnabled: Boolean,
    onAddTask: (String) -> Unit,
    onDone: (Int) -> Unit,
    onLater: (Int) -> Unit,
    onSkip: (Int) -> Unit,
    onMorningGreeting: () -> Unit,
    onVoiceCommand: (String) -> Unit,
    goNight: () -> Unit,
    goSettings: () -> Unit
) {
    var newTask by remember { mutableStateOf("") }
    var commandText by remember { mutableStateOf("") }

    val activeTasks = tasks.withIndex().filter { it.value.status == "active" }
    val completedTasks = tasks.withIndex().filter { it.value.status == "completed" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (name.isBlank()) "Good morning" else "Good morning, $name",
                    fontWeight = FontWeight.Bold,
                    color = Charcoal,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "${activeTasks.size} active tasks · Mood: $mood",
                    color = MutedGray
                )
            }

            TextButton(onClick = goSettings) {
                Text("Settings")
            }
        }

        CardBox {
            Text(
                text = "Today’s Focus",
                fontWeight = FontWeight.Bold,
                color = Charcoal
            )

            Spacer(modifier = Modifier.height(8.dp))

            val focus = activeTasks.firstOrNull()?.value?.title ?: "No task yet"
            Text(
                text = focus,
                color = Charcoal,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(onClick = onMorningGreeting) {
                Text("Hear greeting")
            }
        }

        CardBox {
            Text(
                text = "Add a task",
                fontWeight = FontWeight.Bold,
                color = Charcoal
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = newTask,
                onValueChange = { newTask = it },
                label = { Text("Example: Call dentist") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onAddTask(newTask)
                    newTask = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add task")
            }
        }

        CardBox {
            Text(
                text = "Simple voice command",
                fontWeight = FontWeight.Bold,
                color = Charcoal
            )

            Text(
                text = "For this beginner MVP, type a voice command here. Later we can add real microphone listening.",
                color = MutedGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                label = { Text("Say: done, skip, later, add buy milk") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onVoiceCommand(commandText)
                    commandText = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Run command")
            }
        }

        Text(
            text = "Today",
            fontWeight = FontWeight.Bold,
            color = Charcoal
        )

        if (activeTasks.isEmpty()) {
            Text("No active tasks. Add one small thing.", color = MutedGray)
        } else {
            activeTasks.forEach { item ->
                TaskCard(
                    task = item.value,
                    onDone = { onDone(item.index) },
                    onLater = { onLater(item.index) },
                    onSkip = { onSkip(item.index) }
                )
            }
        }

        if (completedTasks.isNotEmpty()) {
            Text(
                text = "Completed",
                fontWeight = FontWeight.Bold,
                color = Charcoal
            )

            completedTasks.forEach { item ->
                Text("✓ ${item.value.title}", color = CalmGreen)
            }
        }

        Button(
            onClick = goNight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Night review")
        }
    }
}

@Composable
fun NightScreen(
    tasks: MutableList<TaskItem>,
    onMoveAllActiveToTomorrow: () -> Unit,
    onDeleteSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val completed = tasks.count { it.status == "completed" }
    val active = tasks.count { it.status == "active" }
    val skipped = tasks.count { it.status == "skipped" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Night reset",
            fontWeight = FontWeight.Bold,
            color = Charcoal,
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "You completed $completed task(s). $active task(s) are still open.",
            color = MutedGray
        )

        CardBox {
            Text(
                text = "Unfinished tasks",
                fontWeight = FontWeight.Bold,
                color = Charcoal
            )

            tasks.filter { it.status == "active" }.forEach {
                Text("○ ${it.title}", color = Charcoal)
            }

            if (active == 0) {
                Text("Nothing waiting. Good reset.", color = MutedGray)
            }
        }

        Button(
            onClick = onMoveAllActiveToTomorrow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Keep unfinished tasks for tomorrow")
   
