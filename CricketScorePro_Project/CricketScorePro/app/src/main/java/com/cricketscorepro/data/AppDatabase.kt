package com.cricketscorepro.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nickname: String = "",
    val battingStyle: String = "",
    val bowlingStyle: String = "",
    val wicketkeeper: Boolean = false,
    val captain: Boolean = false
)

@Entity(tableName = "teams")
data class Team(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "team_players",
    primaryKeys = ["teamId", "playerId"]
)
data class TeamPlayer(
    val teamId: Long,
    val playerId: Long
)

@Entity(tableName = "matches")
data class CricketMatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teamAId: Long,
    val teamBId: Long,
    val teamAName: String,
    val teamBName: String,
    val venue: String = "",
    val tournament: String = "",
    val format: String = "T20",
    val oversLimit: Int = 20,
    val ballType: String = "Tennis",
    val createdAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false,
    val battingTeam: String = "",
    val target: Int = 0
)

@Entity(tableName = "deliveries")
data class Delivery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val innings: Int = 1,
    val overNumber: Int,
    val ballNumber: Int,
    val striker: String,
    val nonStriker: String,
    val bowler: String,
    val batRuns: Int = 0,
    val extras: Int = 0,
    val extraType: String = "",
    val wicket: Boolean = false,
    val wicketType: String = "",
    val dismissedPlayer: String = "",
    val fielder: String = "",
    val legalBall: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY name")
    fun observeAll(): Flow<List<Player>>

    @Insert
    suspend fun insert(player: Player): Long

    @Delete
    suspend fun delete(player: Player)
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY name")
    fun observeAll(): Flow<List<Team>>

    @Insert
    suspend fun insert(team: Team): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlayerToTeam(row: TeamPlayer)

    @Query("SELECT p.* FROM players p INNER JOIN team_players tp ON p.id = tp.playerId WHERE tp.teamId = :teamId ORDER BY p.name")
    fun playersForTeam(teamId: Long): Flow<List<Player>>
}

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CricketMatch>>

    @Query("SELECT * FROM matches WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): CricketMatch?

    @Insert
    suspend fun insert(match: CricketMatch): Long

    @Update
    suspend fun update(match: CricketMatch)
}

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM deliveries WHERE matchId = :matchId ORDER BY id")
    fun observeForMatch(matchId: Long): Flow<List<Delivery>>

    @Insert
    suspend fun insert(delivery: Delivery): Long

    @Query("DELETE FROM deliveries WHERE id = (SELECT id FROM deliveries WHERE matchId = :matchId ORDER BY id DESC LIMIT 1)")
    suspend fun undoLast(matchId: Long)
}

@Database(
    entities = [Player::class, Team::class, TeamPlayer::class, CricketMatch::class, Delivery::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun teamDao(): TeamDao
    abstract fun matchDao(): MatchDao
    abstract fun deliveryDao(): DeliveryDao

    companion object {
        fun create(context: android.content.Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cricket_score_pro.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
