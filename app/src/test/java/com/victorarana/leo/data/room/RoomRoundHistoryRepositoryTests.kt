package com.victorarana.leo.data.room

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.victorarana.leo.domain.AgeGroup
import com.victorarana.leo.domain.roundRecordFixture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RoomRoundHistoryRepositoryTests {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val reference: Instant = Instant.parse("2001-01-01T00:00:00Z")

    private fun TestScope.dispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)

    private fun TestScope.repository() = RoomRoundHistoryRepository.inMemory(context, dispatcher())

    private fun TestScope.onDisk(file: java.io.File): RoomRoundHistoryRepository {
        val io = dispatcher()
        return RoomRoundHistoryRepository(
            ioDispatcher = io,
            open = { RoomRoundHistoryRepository.onDiskDatabase(context, file.absolutePath, io) },
            openInMemory = { error("not expected") },
        )
    }

    @Test
    fun saveThenRoundRestoresEveryField() =
        runTest {
            val repository = repository()
            val round =
                roundRecordFixture(ageGroup = AgeGroup.NINE, results = listOf(true, false, true, true, false, true))
            repository.save(round)

            val restored = repository.round(round.id)!!
            assertEquals(round.id, restored.id)
            assertEquals(round.finishedAt, restored.finishedAt)
            assertEquals(round.ageGroup, restored.ageGroup)
            assertEquals(round.answers.map { it.index }, restored.answers.map { it.index })
            assertEquals(round.answers.map { it.selectedOption }, restored.answers.map { it.selectedOption })
            assertEquals(round.answers.map { it.exercise.id }, restored.answers.map { it.exercise.id })
            assertEquals(round.answers.map { it.exercise.topic }, restored.answers.map { it.exercise.topic })
            assertEquals(round.answers.map { it.exercise.skill }, restored.answers.map { it.exercise.skill })
            assertEquals(round.answers.map { it.exercise.title }, restored.answers.map { it.exercise.title })
            assertEquals(round.answers.map { it.exercise.passage }, restored.answers.map { it.exercise.passage })
            assertEquals(round.answers.map { it.exercise.question }, restored.answers.map { it.exercise.question })
            assertEquals(round.answers.map { it.exercise.options }, restored.answers.map { it.exercise.options })
            assertEquals(
                round.answers.map { it.exercise.correctIndex },
                restored.answers.map { it.exercise.correctIndex },
            )
            assertEquals(
                round.answers.map { it.exercise.explanation },
                restored.answers.map { it.exercise.explanation },
            )
        }

    @Test
    fun summariesAreNewestFirstAndPerAgeGroup() =
        runTest {
            val repository = repository()
            val old = roundRecordFixture(ageGroup = AgeGroup.NINE, finishedAt = reference)
            val new = roundRecordFixture(ageGroup = AgeGroup.NINE, finishedAt = reference.plusSeconds(1000))
            val other = roundRecordFixture(ageGroup = AgeGroup.TWELVE, finishedAt = reference.plusSeconds(500))
            repository.save(old)
            repository.save(new)
            repository.save(other)

            assertEquals(listOf(new.id, old.id), repository.summaries(AgeGroup.NINE).map { it.id })
            assertEquals(listOf(other.id), repository.summaries(AgeGroup.TWELVE).map { it.id })
        }

    @Test
    fun summaryResultsMatchAnswers() =
        runTest {
            val repository = repository()
            val round = roundRecordFixture(results = listOf(true, false, true, true, false, true))
            repository.save(round)

            val summary = repository.summaries(round.ageGroup).first()
            assertEquals(round.id, summary.id)
            assertEquals(listOf(true, false, true, true, false, true), summary.results)
            assertEquals(4, summary.correctCount)
        }

    @Test
    fun deleteRemovesTheRoundAndItsAnswers() =
        runTest {
            val repository = repository()
            val round = roundRecordFixture()
            repository.save(round)

            repository.delete(round.id)

            assertNull(repository.round(round.id))
            assertEquals(0, repository.daoForTesting().answerCount())
        }

    /** Deleting a round that was never saved does nothing. */
    @Test
    fun deletingAnUnknownRoundDoesNothing() =
        runTest {
            val repository = repository()
            val kept = roundRecordFixture()
            repository.save(kept)
            repository.delete(UUID.randomUUID())
            assertNotNull(repository.round(kept.id))
        }

    @Test
    fun unreadableRoundIsSkipped() =
        runTest {
            val repository = repository()
            val roundId = UUID.randomUUID()
            val answer =
                StoredAnswer(
                    roundId = roundId,
                    index = 0,
                    exerciseID = UUID.randomUUID(),
                    topicPrompt = "owls",
                    topicName = "Owls",
                    topicKind = "informative",
                    skill = "not-a-real-skill",
                    title = "Owls",
                    passage = "Owls are nocturnal birds.",
                    question = "What are owls?",
                    options = listOf("Birds", "Fish"),
                    correctIndex = 0,
                    selectedOption = 9,
                    explanation = "",
                )
            val round = StoredRound(roundId, Instant.now(), AgeGroup.NINE.rawValue, listOf(true))
            repository.daoForTesting().replaceRound(round, listOf(answer))

            assertNull(repository.round(roundId))
        }

    @Test
    fun roundsSurviveANewDatabase() =
        runTest {
            val file = temporaryFolder.newFile("RoundHistory.db")
            val round = roundRecordFixture()

            val first = onDisk(file)
            first.save(round)
            first.closeForTesting()

            val restored = onDisk(file).round(round.id)
            assertEquals(round.id, restored?.id)
            assertEquals(round.answers.size, restored?.answers?.size)
        }

    @Test
    fun savingTheSameIdTwiceKeepsOneRound() =
        runTest {
            val repository = repository()
            val round = roundRecordFixture(results = listOf(true, true, true))
            repository.save(round)
            repository.save(round)

            assertEquals(1, repository.summaries(round.ageGroup).size)
            assertEquals(3, repository.daoForTesting().answerCount())
            assertEquals(3, repository.round(round.id)?.answers?.size)
        }

    @Test
    fun finishedAtKeepsMillisecondPrecision() =
        runTest {
            val repository = repository()
            val finishedAt = Instant.parse("2001-01-01T00:00:00.123456789Z")
            val round = roundRecordFixture(finishedAt = finishedAt)
            repository.save(round)

            assertEquals(Instant.parse("2001-01-01T00:00:00.123Z"), repository.round(round.id)?.finishedAt)
            assertEquals(
                Instant.parse("2001-01-01T00:00:00.123Z"),
                repository.summaries(round.ageGroup).first().finishedAt,
            )
        }

    @Test
    fun fallsBackToInMemoryWhenTheStoreCannotOpen() =
        runTest {
            val io = dispatcher()
            val repository =
                RoomRoundHistoryRepository(
                    ioDispatcher = io,
                    open = { throw SQLiteException("disk full") },
                    openInMemory = {
                        Room
                            .inMemoryDatabaseBuilder(context, RoundHistoryDatabase::class.java)
                            .setQueryCoroutineContext(io)
                            .build()
                    },
                )
            val round = roundRecordFixture()
            repository.save(round)

            assertEquals(round.id, repository.round(round.id)?.id)
        }

    @Test
    fun aDamagedFileIsKeptAndHistoryFallsBackToMemory() =
        runTest {
            val file = temporaryFolder.newFile("Damaged.db")
            val garbage = ByteArray(4096) { (it * 31).toByte() }
            file.writeBytes(garbage)
            val io = dispatcher()
            val repository =
                RoomRoundHistoryRepository(
                    ioDispatcher = io,
                    open = { RoomRoundHistoryRepository.onDiskDatabase(context, file.absolutePath, io) },
                    openInMemory = {
                        Room
                            .inMemoryDatabaseBuilder(context, RoundHistoryDatabase::class.java)
                            .setQueryCoroutineContext(io)
                            .build()
                    },
                )
            val round = roundRecordFixture()
            repository.save(round)

            assertEquals(round.id, repository.round(round.id)?.id)
            assertTrue(file.exists())
            assertArrayEquals(garbage, file.readBytes())
        }

    @Test
    fun releaseBuildFallsBackToMemoryOnASchemaError() =
        runTest {
            val io = dispatcher()
            val repository =
                RoomRoundHistoryRepository(
                    ioDispatcher = io,
                    open = { throw IllegalStateException("Room cannot verify the data integrity") },
                    openInMemory = {
                        Room
                            .inMemoryDatabaseBuilder(context, RoundHistoryDatabase::class.java)
                            .setQueryCoroutineContext(io)
                            .build()
                    },
                    rethrowSchemaErrors = false,
                )
            val round = roundRecordFixture()
            repository.save(round)
            assertEquals(round.id, repository.round(round.id)?.id)
        }

    @Test
    fun debugBuildRethrowsASchemaError() =
        runTest {
            val repository =
                RoomRoundHistoryRepository(
                    ioDispatcher = dispatcher(),
                    open = { throw IllegalStateException("Room cannot verify the data integrity") },
                    openInMemory = { error("not expected") },
                    rethrowSchemaErrors = true,
                )
            try {
                repository.summaries(AgeGroup.NINE)
                fail("Expected the schema error to be rethrown")
            } catch (expected: IllegalStateException) {
                assertNotNull(expected.message)
            }
        }
}
