package com.replog.workouts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val Bg = Color(0xFF0D1010)
private val Surface = Color(0xFF181D1C)
private val Green = Color(0xFFB7F36B)
private val Muted = Color(0xFF9BA5A0)

private data class Exercise(val name: String, val sets: Int = 3, val minReps: Int = 8, val maxReps: Int = 12, val rest: Int = 120)
private data class Routine(val name: String, val exercises: List<Exercise>)
private data class LoggedSet(val exercise: String, val kg: Double, val reps: Int, val rir: Int)
private data class Session(val routine: String, val date: Long, val sets: List<LoggedSet>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RepLogApp() }
    }
}

@Composable
private fun RepLogApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("replog_local", 0) }
    val routines = remember { mutableStateListOf<Routine>().apply { addAll(loadRoutines(prefs.getString("routines", null))) } }
    val history = remember { mutableStateListOf<Session>().apply { addAll(loadHistory(prefs.getString("history", null))) } }
    var tab by remember { mutableIntStateOf(0) }
    var activeRoutine by remember { mutableStateOf<Routine?>(null) }
    var activeSets by remember { mutableStateOf(listOf<LoggedSet>()) }
    var timerSeconds by remember { mutableIntStateOf(0) }
    var timerRunning by remember { mutableStateOf(false) }
    var showNewRoutine by remember { mutableStateOf(false) }
    var newRoutineName by remember { mutableStateOf("") }
    var newExerciseName by remember { mutableStateOf("") }
    var selectedRoutineForExercise by remember { mutableIntStateOf(0) }
    var showAddExercise by remember { mutableStateOf(false) }

    fun saveAll() {
        prefs.edit().putString("routines", routinesToJson(routines)).putString("history", historyToJson(history)).apply()
    }

    LaunchedEffect(timerRunning, timerSeconds) {
        if (timerRunning && timerSeconds > 0) {
            kotlinx.coroutines.delay(1000)
            timerSeconds -= 1
        } else if (timerSeconds <= 0) timerRunning = false
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Surface, primary = Green, onPrimary = Bg, onBackground = Color.White, onSurface = Color.White)) {
        Scaffold(containerColor = Bg, bottomBar = {
            NavigationBar(containerColor = Surface, contentColor = Green) {
                val labels = listOf("Home", "Schede", "Workout", "Progressi", "Altro")
                val icons = listOf(Icons.Default.Home, Icons.Default.ViewList, Icons.Default.FitnessCenter, Icons.Default.ShowChart, Icons.Default.Settings)
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], contentDescription = label) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Green, selectedTextColor = Green, indicatorColor = Color(0xFF303D29), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(Green, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.FitnessCenter, null, tint = Bg) }
                    Spacer(Modifier.width(10.dp))
                    Column { Text("REPLOG", fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text("TRAIN WITH INTENTION", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp) }
                }
                when (tab) {
                    0 -> HomeScreen(routines, history, onStart = { r -> activeRoutine = r; activeSets = emptyList(); tab = 2 })
                    1 -> RoutinesScreen(routines, onStart = { r -> activeRoutine = r; activeSets = emptyList(); tab = 2 }, onAdd = { showNewRoutine = true }, onAddExercise = { idx -> selectedRoutineForExercise = idx; showAddExercise = true }, onDelete = { idx -> if (routines.size > 1) { routines.removeAt(idx); saveAll() } })
                    2 -> WorkoutScreen(activeRoutine, routines, activeSets, onChoose = { r -> activeRoutine = r; activeSets = emptyList() }, onLog = { s -> activeSets = activeSets + s }, onFinish = {
                        activeRoutine?.let { r -> if (activeSets.isNotEmpty()) { history.add(0, Session(r.name, System.currentTimeMillis(), activeSets)); saveAll() } }
                        activeRoutine = null; activeSets = emptyList(); tab = 3
                    }, timerSeconds = timerSeconds, timerRunning = timerRunning, onTimer = { seconds -> timerSeconds = seconds; timerRunning = true }, onStopTimer = { timerRunning = false })
                    3 -> ProgressScreen(history)
                    else -> SettingsScreen(history, routines, onReset = { history.clear(); saveAll() }, onExport = { shareBackup(context, routinesToJson(routines), historyToJson(history)) })
                }
            }
        }
        if (showNewRoutine) AlertDialog(onDismissRequest = { showNewRoutine = false }, title = { Text("Nuova scheda") }, text = { OutlinedTextField(newRoutineName, { newRoutineName = it }, label = { Text("Nome scheda") }, singleLine = true) }, confirmButton = { TextButton(onClick = { val name = newRoutineName.trim(); if (name.isNotEmpty()) { routines.add(Routine(name, listOf(Exercise("Panca piana"), Exercise("Rematore"), Exercise("Lat machine")))); saveAll(); newRoutineName = ""; showNewRoutine = false } }) { Text("Crea") } }, dismissButton = { TextButton(onClick = { showNewRoutine = false }) { Text("Annulla") } })
        if (showAddExercise) AlertDialog(onDismissRequest = { showAddExercise = false }, title = { Text("Aggiungi esercizio") }, text = { OutlinedTextField(newExerciseName, { newExerciseName = it }, label = { Text("Nome esercizio") }, singleLine = true) }, confirmButton = { TextButton(onClick = { val name = newExerciseName.trim(); if (name.isNotEmpty() && selectedRoutineForExercise in routines.indices) { val old = routines[selectedRoutineForExercise]; routines[selectedRoutineForExercise] = old.copy(exercises = old.exercises + Exercise(name)); saveAll(); newExerciseName = ""; showAddExercise = false } }) { Text("Aggiungi") } }, dismissButton = { TextButton(onClick = { showAddExercise = false }) { Text("Annulla") } })
    }
}

@Composable private fun HomeScreen(routines: List<Routine>, history: List<Session>, onStart: (Routine) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { Text("Il tuo prossimo allenamento", color = Muted, fontSize = 13.sp); Text("Un set alla volta.", fontSize = 28.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); CardBlock { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(routines.firstOrNull()?.name ?: "Crea la tua prima scheda", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("${routines.firstOrNull()?.exercises?.size ?: 0} esercizi · registra ogni serie", color = Muted, fontSize = 12.sp) }; Button(onClick = { routines.firstOrNull()?.let(onStart) }, enabled = routines.isNotEmpty()) { Text("Inizia") } } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatCard("SESSIONI", history.size.toString(), Modifier.weight(1f)); StatCard("SERIE LOGGATE", history.sumOf { it.sets.size }.toString(), Modifier.weight(1f)) } }
        item { SectionTitle("Le tue schede") }
        itemsIndexed(routines.take(4)) { _, r -> CardBlock { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(r.name, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("${r.exercises.size} esercizi", color = Muted, fontSize = 12.sp) }; TextButton(onClick = { onStart(r) }) { Text("Allenati →") } } } }
        item { SectionTitle("Ultima attività") }
        item { if (history.isEmpty()) CardBlock { Text("Nessun allenamento registrato. Avvia una scheda per iniziare a costruire il tuo storico.", color = Muted) } else CardBlock { Text(history.first().routine, fontWeight = FontWeight.Bold); Text("${history.first().sets.size} serie · ${formatDate(history.first().date)}", color = Muted, fontSize = 12.sp) } }
    }
}

@Composable private fun RoutinesScreen(routines: List<Routine>, onStart: (Routine) -> Unit, onAdd: () -> Unit, onAddExercise: (Int) -> Unit, onDelete: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Le tue schede", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Routine flessibili, create da te", color = Muted, fontSize = 12.sp) }; Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Text(" Crea") } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 20.dp)) { itemsIndexed(routines) { idx, routine -> CardBlock { Text(routine.name, fontSize = 19.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(7.dp)); routine.exercises.forEach { Text("• ${it.name}  ·  ${it.sets} × ${it.minReps}-${it.maxReps}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }; Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Button(onClick = { onStart(routine) }, modifier = Modifier.weight(1f)) { Text("Inizia") }; OutlinedButton(onClick = { onAddExercise(idx) }) { Icon(Icons.Default.Add, null); Text(" Esercizio") }; if (routines.size > 1) IconButton(onClick = { onDelete(idx) }) { Icon(Icons.Default.DeleteOutline, "Elimina scheda", tint = Muted) } } } } }
    }
}

@Composable private fun WorkoutScreen(routine: Routine?, routines: List<Routine>, logged: List<LoggedSet>, onChoose: (Routine) -> Unit, onLog: (LoggedSet) -> Unit, onFinish: () -> Unit, timerSeconds: Int, timerRunning: Boolean, onTimer: (Int) -> Unit, onStopTimer: () -> Unit) {
    var exerciseIndex by remember(routine?.name) { mutableIntStateOf(0) }
    var kg by remember(routine?.name, exerciseIndex) { mutableStateOf("") }
    var reps by remember(routine?.name, exerciseIndex) { mutableStateOf("10") }
    var rir by remember(routine?.name, exerciseIndex) { mutableStateOf("2") }
    var rest by remember { mutableIntStateOf(120) }
    Column(Modifier.fillMaxSize()) {
        Text("Sessione", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        if (routine == null) {
            Text("Scegli una scheda per iniziare.", color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
            routines.forEach { r -> CardBlock { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(r.name, fontWeight = FontWeight.Bold); Text("${r.exercises.size} esercizi", color = Muted, fontSize = 12.sp) }; Button(onClick = { onChoose(r) }) { Text("Seleziona") } } } }
        } else {
            Text(routine.name, color = Green, fontWeight = FontWeight.Bold); Text("${logged.size} serie completate", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { routine.exercises.forEachIndexed { i, e -> FilterChip(selected = exerciseIndex == i, onClick = { exerciseIndex = i }, label = { Text("${i + 1}. ${e.name}") }) } }
            val exercise = routine.exercises.getOrNull(exerciseIndex)
            if (exercise != null) {
                CardBlock { Text("${exercise.name}", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Obiettivo: ${exercise.sets} serie · ${exercise.minReps}-${exercise.maxReps} ripetizioni", color = Muted, fontSize = 12.sp); val previous = logged.lastOrNull { it.exercise == exercise.name }; val lastSession = previous ?: null; if (lastSession != null) Text("Ultimo set registrato: ${lastSession.kg} kg × ${lastSession.reps} · RIR ${lastSession.rir}", color = Green, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { NumberInput("Kg", kg, { kg = it }, Modifier.weight(1f), KeyboardType.Decimal); NumberInput("Reps", reps, { reps = it }, Modifier.weight(1f), KeyboardType.Number); NumberInput("RIR", rir, { rir = it }, Modifier.weight(1f), KeyboardType.Number) }
                    Spacer(Modifier.height(12.dp)); Button(onClick = { val w = kg.replace(',', '.').toDoubleOrNull(); val rp = reps.toIntOrNull(); val rr = rir.toIntOrNull(); if (w != null && rp != null && rr != null && w >= 0 && rp > 0 && rr in 0..10) { onLog(LoggedSet(exercise.name, w, rp, rr)); onTimer(rest) } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Check, null); Text("  Registra serie") }
                }
                CardBlock { Text("Recupero", fontWeight = FontWeight.Bold); Text(if (timerRunning) "${timerSeconds / 60}:${(timerSeconds % 60).toString().padStart(2, '0')}" else "Timer pronto", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Green); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { rest = 60; onTimer(60) }) { Text("60s") }; OutlinedButton(onClick = { rest = 90; onTimer(90) }) { Text("90s") }; OutlinedButton(onClick = { rest = 120; onTimer(120) }) { Text("120s") }; TextButton(onClick = onStopTimer) { Text("Stop") } } }
                Text("Serie registrate", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                if (logged.isEmpty()) Text("Registra la prima serie qui sopra.", color = Muted, fontSize = 12.sp) else logged.takeLast(6).reversed().forEachIndexed { i, s -> CardBlock { Row(verticalAlignment = Alignment.CenterVertically) { Text("${logged.size - i}", color = Green, fontWeight = FontWeight.Bold); Spacer(Modifier.width(12.dp)); Column { Text(s.exercise, fontWeight = FontWeight.Medium); Text("${s.kg} kg × ${s.reps} · RIR ${s.rir}", color = Muted, fontSize = 12.sp) } } } }
                Spacer(Modifier.height(8.dp)); Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Termina allenamento") }
            }
        }
    }
}

@Composable private fun ProgressScreen(history: List<Session>) {
    val allSets = history.flatMap { it.sets }
    val volume = allSets.sumOf { it.kg * it.reps }
    Column { Text("I tuoi progressi", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("Dati raccolti dai tuoi allenamenti", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatCard("SESSIONI", "${history.size}", Modifier.weight(1f)); StatCard("VOLUME", "${volume.toInt()} kg", Modifier.weight(1f)) }; Spacer(Modifier.height(12.dp)); StatCard("SERIE REGISTRATE", "${allSets.size}", Modifier.fillMaxWidth()); Spacer(Modifier.height(18.dp)); Text("Migliori carichi registrati", fontWeight = FontWeight.Bold, fontSize = 17.sp); Spacer(Modifier.height(8.dp)); val best = allSets.groupBy { it.exercise }.mapValues { (_, sets) -> sets.maxOf { it.kg } }.toList().sortedByDescending { it.second }; if (best.isEmpty()) Text("I record appariranno dopo il primo allenamento.", color = Muted, fontSize = 12.sp) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(best) { _, item -> CardBlock { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.first, fontWeight = FontWeight.Bold); Text("Miglior carico registrato", color = Muted, fontSize = 11.sp) }; Text("${item.second} kg", color = Green, fontSize = 20.sp, fontWeight = FontWeight.Bold) } } } }; Spacer(Modifier.height(12.dp)); Text("Storico recente", fontWeight = FontWeight.Bold, fontSize = 17.sp); history.take(8).forEach { s -> CardBlock { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.routine, fontWeight = FontWeight.Bold); Text("${s.sets.size} serie · ${formatDate(s.date)}", color = Muted, fontSize = 12.sp) }; Text("${s.sets.sumOf { it.kg * it.reps }.toInt()} kg", color = Green) } } } }
}

@Composable private fun SettingsScreen(history: List<Session>, routines: List<Routine>, onReset: () -> Unit, onExport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Impostazioni", fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("RepLog salva i dati in locale sul dispositivo.", color = Muted, fontSize = 13.sp); CardBlock { Text("Backup dati", fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("Esporta schede e storico in un file JSON da conservare o trasferire.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)); Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Text("  Esporta backup") } }; CardBlock { Text("Riepilogo", fontWeight = FontWeight.Bold); Text("${routines.size} schede · ${history.size} sessioni salvate", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }; CardBlock { Text("Cancella storico", fontWeight = FontWeight.Bold); Text("Rimuove tutte le sessioni registrate, senza eliminare le schede.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)); var confirm by remember { mutableStateOf(false) }; OutlinedButton(onClick = { confirm = true }) { Text("Cancella sessioni") }; if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Confermi?") }, text = { Text("Lo storico degli allenamenti verrà cancellato.") }, confirmButton = { TextButton(onClick = { onReset(); confirm = false }) { Text("Cancella") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla") } }) } }
}

@Composable private fun CardBlock(content: @Composable ColumnScope.() -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), content = content) } }
@Composable private fun SectionTitle(text: String) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
@Composable private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) { Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(14.dp)) { Text(label, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp); Spacer(Modifier.height(6.dp)); Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Green) } } }
@Composable private fun NumberInput(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, type: KeyboardType) { OutlinedTextField(value, onChange, modifier = modifier, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type)) }

private fun defaultRoutines() = listOf(
    Routine("Upper A", listOf(Exercise("Panca piana", 3, 6, 10), Exercise("Rematore T-Bar", 3, 8, 12), Exercise("Lat machine", 3, 8, 12), Exercise("Alzate laterali", 3, 12, 20), Exercise("Curl manubri", 3, 8, 12), Exercise("Pushdown cavo", 3, 10, 15))),
    Routine("Lower A", listOf(Exercise("Squat", 3, 6, 10), Exercise("Romanian deadlift", 3, 8, 12), Exercise("Leg press", 3, 10, 15), Exercise("Leg curl", 3, 10, 15))),
    Routine("Upper B", listOf(Exercise("Panca inclinata manubri", 3, 8, 12), Exercise("Rematore macchina", 3, 8, 12), Exercise("Pulldown", 3, 8, 12), Exercise("Reverse cable fly", 3, 12, 20), Exercise("Curl al cavo", 3, 10, 15), Exercise("Estensioni tricipiti", 3, 10, 15))),
    Routine("Lower B", listOf(Exercise("Hack squat", 3, 8, 12), Exercise("Hip hinge", 3, 8, 12), Exercise("Leg extension", 3, 10, 15), Exercise("Calf raise", 3, 10, 20)))
)

private fun loadRoutines(raw: String?): List<Routine> = try { if (raw.isNullOrBlank()) defaultRoutines() else { val a = JSONArray(raw); (0 until a.length()).map { i -> val o = a.getJSONObject(i); val ex = o.getJSONArray("exercises"); Routine(o.getString("name"), (0 until ex.length()).map { j -> val e = ex.getJSONObject(j); Exercise(e.getString("name"), e.optInt("sets", 3), e.optInt("min", 8), e.optInt("max", 12), e.optInt("rest", 120)) }) } } } catch (_: Exception) { defaultRoutines() }
private fun loadHistory(raw: String?): List<Session> = try { if (raw.isNullOrBlank()) emptyList() else { val a = JSONArray(raw); (0 until a.length()).map { i -> val o = a.getJSONObject(i); val sa = o.getJSONArray("sets"); Session(o.getString("routine"), o.getLong("date"), (0 until sa.length()).map { j -> val s = sa.getJSONObject(j); LoggedSet(s.getString("exercise"), s.getDouble("kg"), s.getInt("reps"), s.getInt("rir")) }) } } } catch (_: Exception) { emptyList() }
private fun routinesToJson(routines: List<Routine>): String { val a = JSONArray(); routines.forEach { r -> val o = JSONObject().put("name", r.name); val ex = JSONArray(); r.exercises.forEach { ex.put(JSONObject().put("name", it.name).put("sets", it.sets).put("min", it.minReps).put("max", it.maxReps).put("rest", it.rest)) }; o.put("exercises", ex); a.put(o) }; return a.toString() }
private fun historyToJson(history: List<Session>): String { val a = JSONArray(); history.forEach { s -> val o = JSONObject().put("routine", s.routine).put("date", s.date); val sets = JSONArray(); s.sets.forEach { sets.put(JSONObject().put("exercise", it.exercise).put("kg", it.kg).put("reps", it.reps).put("rir", it.rir)) }; o.put("sets", sets); a.put(o) }; return a.toString() }
private fun formatDate(ms: Long): String = java.text.SimpleDateFormat("dd/MM/yyyy · HH:mm", java.util.Locale.ITALY).format(java.util.Date(ms))
private fun shareBackup(context: android.content.Context, routines: String, history: String) { try { val payload = JSONObject().put("app", "RepLog").put("version", 1).put("routines", JSONArray(routines)).put("history", JSONArray(history)).toString(2); val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_SUBJECT, "Backup RepLog.json").putExtra(android.content.Intent.EXTRA_TEXT, payload); context.startActivity(android.content.Intent.createChooser(intent, "Esporta backup RepLog")) } catch (_: Exception) { android.widget.Toast.makeText(context, "Impossibile creare il backup", android.widget.Toast.LENGTH_LONG).show() } }
