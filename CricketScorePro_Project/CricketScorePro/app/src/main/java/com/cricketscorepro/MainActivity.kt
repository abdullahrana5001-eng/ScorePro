package com.cricketscorepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cricketscorepro.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CricketViewModel(private val db: AppDatabase) : ViewModel() {
    val players = db.playerDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val teams = db.teamDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val matches = db.matchDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedMatchId by mutableStateOf<Long?>(null)
        private set

    fun selectMatch(id: Long) { selectedMatchId = id }

    fun addPlayer(name: String, nickname: String) = viewModelScope.launch {
        if (name.isNotBlank()) db.playerDao().insert(Player(name = name.trim(), nickname = nickname.trim()))
    }

    fun addTeam(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) db.teamDao().insert(Team(name = name.trim()))
    }

    fun createMatch(
        a: Team, b: Team, format: String, overs: Int, venue: String, tournament: String, ballType: String
    ) = viewModelScope.launch {
        val id = db.matchDao().insert(
            CricketMatch(
                teamAId = a.id, teamBId = b.id,
                teamAName = a.name, teamBName = b.name,
                format = format, oversLimit = overs,
                venue = venue, tournament = tournament, ballType = ballType
            )
        )
        selectedMatchId = id
    }

    fun addDelivery(
        matchId: Long, striker: String, nonStriker: String, bowler: String,
        runs: Int, extra: Int, extraType: String, wicket: Boolean, wicketType: String
    ) = viewModelScope.launch {
        val existing = db.deliveryDao().observeForMatch(matchId).first()
        val legal = extraType != "Wide" && extraType != "No Ball"
        val legalBalls = existing.count { it.legalBall }
        val over = legalBalls / 6
        val ball = (legalBalls % 6) + 1
        db.deliveryDao().insert(
            Delivery(
                matchId = matchId,
                overNumber = over,
                ballNumber = ball,
                striker = striker,
                nonStriker = nonStriker,
                bowler = bowler,
                batRuns = runs,
                extras = extra,
                extraType = extraType,
                wicket = wicket,
                wicketType = wicketType,
                dismissedPlayer = if (wicket) striker else "",
                legalBall = legal
            )
        )
    }

    fun undo(matchId: Long) = viewModelScope.launch { db.deliveryDao().undoLast(matchId) }

    fun deliveries(matchId: Long): Flow<List<Delivery>> =
        db.deliveryDao().observeForMatch(matchId)

    suspend fun getMatch(id: Long): CricketMatch? = db.matchDao().get(id)
}

@Composable
fun App(vm: CricketViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val players by vm.players.collectAsState()
    val teams by vm.teams.collectAsState()
    val matches by vm.matches.collectAsState()

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("CricketScore Pro") }) },
            bottomBar = {
                NavigationBar {
                    listOf("Home", "Players", "Teams", "Matches").forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(label.take(1)) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(matches, teams, onOpen = { vm.selectMatch(it); tab = 3 })
                    1 -> PlayersScreen(players) { n, nick -> vm.addPlayer(n, nick) }
                    2 -> TeamsScreen(teams) { vm.addTeam(it) }
                    3 -> MatchesScreen(
                        matches = matches,
                        teams = teams,
                        onCreate = { a,b,f,o,v,t,bt -> vm.createMatch(a,b,f,o,v,t,bt) },
                        onOpen = { vm.selectMatch(it) }
                    )
                }
            }
        }
    }

    vm.selectedMatchId?.let { id ->
        MatchDialog(
            matchId = id,
            vm = vm,
            onClose = { vm.selectMatch(-1); },
            allPlayers = players
        )
    }
}

@Composable
fun HomeScreen(matches: List<CricketMatch>, teams: List<Team>, onOpen: (Long) -> Unit) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("Teams: ${teams.size}")
        Text("Matches: ${matches.size}")
        Spacer(Modifier.height(20.dp))
        Text("Recent matches", style = MaterialTheme.typography.titleLarge)
        matches.take(5).forEach {
            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), onClick = { onOpen(it.id) }) {
                Column(Modifier.padding(14.dp)) {
                    Text("${it.teamAName} vs ${it.teamBName}", style = MaterialTheme.typography.titleMedium)
                    Text("${it.format} • ${it.oversLimit} overs • ${it.ballType}")
                    if (it.venue.isNotBlank()) Text(it.venue)
                }
            }
        }
    }
}

@Composable
fun PlayersScreen(players: List<Player>, add: (String,String)->Unit) {
    var name by remember { mutableStateOf("") }
    var nick by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        Text("Players", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nick, { nick = it }, label = { Text("Nickname (optional)") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { add(name, nick); name = ""; nick = "" },
            enabled = name.isNotBlank(),
            modifier = Modifier.padding(vertical = 8.dp)
        ) { Text("Add player") }
        LazyColumn {
            items(players) { Text(it.name + if (it.nickname.isNotBlank()) " (${it.nickname})" else "", Modifier.padding(8.dp)) }
        }
    }
}

@Composable
fun TeamsScreen(teams: List<Team>, add: (String)->Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        Text("Teams", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Team name") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { add(name); name = "" }, enabled = name.isNotBlank(), modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Add team")
        }
        LazyColumn { items(teams) { Text(it.name, Modifier.padding(8.dp)) } }
    }
}

@Composable
fun MatchesScreen(
    matches: List<CricketMatch>,
    teams: List<Team>,
    onCreate: (Team,Team,String,Int,String,String,String)->Unit,
    onOpen: (Long)->Unit
) {
    var show by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Matches", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { show = true }, enabled = teams.size >= 2) { Text("New Match") }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn {
            items(matches) { m ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), onClick = { onOpen(m.id) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${m.teamAName} vs ${m.teamBName}")
                        Text("${m.format} • ${m.oversLimit} overs • ${m.ballType}")
                    }
                }
            }
        }
    }
    if (show) {
        NewMatchDialog(teams, { show = false }) { a,b,f,o,v,t,bt ->
            onCreate(a,b,f,o,v,t,bt); show = false
        }
    }
}

@Composable
fun NewMatchDialog(teams: List<Team>, close: ()->Unit, create: (Team,Team,String,Int,String,String,String)->Unit) {
    var a by remember { mutableStateOf(teams[0]) }
    var b by remember { mutableStateOf(teams[1]) }
    var format by remember { mutableStateOf("T20") }
    var overs by remember { mutableStateOf("20") }
    var venue by remember { mutableStateOf("") }
    var tournament by remember { mutableStateOf("") }
    var ballType by remember { mutableStateOf("Tennis") }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("Create match") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Team A: ${a.name}")
                Row {
                    teams.forEach { t -> TextButton(onClick = { a = t }) { Text(t.name) } }
                }
                Text("Team B: ${b.name}")
                Row {
                    teams.filter { it.id != a.id }.forEach { t -> TextButton(onClick = { b = t }) { Text(t.name) } }
                }
                OutlinedTextField(format, { format = it }, label = { Text("Format") })
                OutlinedTextField(overs, { overs = it.filter(Char::isDigit) }, label = { Text("Overs") })
                OutlinedTextField(venue, { venue = it }, label = { Text("Venue") })
                OutlinedTextField(tournament, { tournament = it }, label = { Text("Tournament") })
                OutlinedTextField(ballType, { ballType = it }, label = { Text("Ball type") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val o = overs.toIntOrNull()?.coerceIn(1, 200) ?: 20
                create(a,b,format,o,venue,tournament,ballType)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@Composable
fun MatchDialog(matchId: Long, vm: CricketViewModel, onClose: ()->Unit, allPlayers: List<Player>) {
    if (matchId <= 0) return
    val match by produceState<CricketMatch?>(initialValue = null, matchId) {
        value = vm.getMatch(matchId)
    }
    val deliveries by vm.deliveries(matchId).collectAsState(initial = emptyList())

    var striker by remember { mutableStateOf("Striker") }
    var nonStriker by remember { mutableStateOf("Non-striker") }
    var bowler by remember { mutableStateOf("Bowler") }
    var wicket by remember { mutableStateOf(false) }
    var wicketType by remember { mutableStateOf("Bowled") }

    val total = deliveries.sumOf { it.batRuns + it.extras }
    val wickets = deliveries.count { it.wicket }
    val legal = deliveries.count { it.legalBall }
    val overs = "${legal / 6}.${legal % 6}"
    val rr = if (legal == 0) 0.0 else total * 6.0 / legal

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(),
        title = { Text("Live Score") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${match?.teamAName ?: "Team A"} vs ${match?.teamBName ?: "Team B"}", style = MaterialTheme.typography.titleMedium)
                Text("$total/$wickets   ($overs ov)   RR ${"%.2f".format(rr)}", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Striker: $striker")
                Text("Non-striker: $nonStriker")
                Text("Bowler: $bowler")
                Spacer(Modifier.height(8.dp))
                Text("Quick scoring")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..6).forEach { r ->
                        Button(onClick = { vm.addDelivery(matchId,striker,nonStriker,bowler,r,0,"",false,"") }) {
                            Text(r.toString())
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { vm.addDelivery(matchId,striker,nonStriker,bowler,0,1,"Wide",false,"") }) { Text("WD") }
                    Button(onClick = { vm.addDelivery(matchId,striker,nonStriker,bowler,0,1,"No Ball",false,"") }) { Text("NB") }
                    Button(onClick = { vm.addDelivery(matchId,striker,nonStriker,bowler,0,1,"Bye",false,"") }) { Text("B") }
                    Button(onClick = { vm.addDelivery(matchId,striker,nonStriker,bowler,0,1,"Leg Bye",false,"") }) { Text("LB") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(wicket, { wicket = it })
                    Text("Wicket")
                }
                if (wicket) {
                    OutlinedTextField(wicketType, { wicketType = it }, label = { Text("Wicket type") })
                    Button(onClick = {
                        vm.addDelivery(matchId,striker,nonStriker,bowler,0,0,"",true,wicketType)
                        wicket = false
                    }) { Text("Record wicket") }
                }
                Button(onClick = { vm.undo(matchId) }, enabled = deliveries.isNotEmpty()) { Text("Undo last ball") }

                Spacer(Modifier.height(10.dp))
                Text("Ball-by-ball", style = MaterialTheme.typography.titleMedium)
                deliveries.asReversed().take(12).forEach {
                    Text("${it.overNumber}.${it.ballNumber}  ${it.bowler} to ${it.striker}: ${it.batRuns + it.extras}" +
                        (if (it.extraType.isNotBlank()) " (${it.extraType})" else "") +
                        (if (it.wicket) " WICKET ${it.wicketType}" else ""),
                        Modifier.padding(vertical = 3.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

class MainVmFactory(private val db: AppDatabase) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CricketViewModel(db) as T
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.create(applicationContext)
        setContent {
            val vm: CricketViewModel = viewModel(factory = MainVmFactory(db))
            App(vm)
        }
    }
}
