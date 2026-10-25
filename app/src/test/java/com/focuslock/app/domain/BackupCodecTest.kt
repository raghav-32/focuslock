package com.focuslock.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private fun sample(): BackupData {
        val rule = FeedRule("yt", "com.google.android.youtube", "YouTube Shorts", true, BlockScope.ALWAYS, listOf("reel_player"), listOf("shorts"), false, 10, 99)
        val sched = Schedule(0, "Night", 22 * 60, 6 * 60, Days.ALL, true, setOf("com.instagram.android"), emptySet(), setOf("yt"), setOf("com.bank"), true)
        return BackupData(
            exportedAt = 123L,
            blockedApps = listOf(BlockedApp("com.instagram.android", true, BlockScope.SESSIONS_ONLY)),
            feedRules = listOf(rule),
            groups = listOf(BackupGroup("Study", setOf("com.notes.app"), "social")),
            schedules = listOf(BackupSchedule(sched, setOf("Study"))),
            blockEvents = listOf(BlockEvent(0, 1000, "com.instagram.android", RuleKind.APP, "com.instagram.android", "Instagram", BlockSource.SCHEDULE, BlockAction.SHOWN)),
            dailyUsage = listOf(DailyUsage(20000, "com.instagram.android", 60_000)),
        )
    }

    private fun ok(text: String) = (BackupCodec.parse(text) as BackupResult.Ok).data
    private fun invalid(text: String) = (BackupCodec.parse(text) as BackupResult.Invalid).reason

    @Test fun roundTrip() {
        val original = sample()
        val back = ok(BackupCodec.encode(original))
        assertEquals(original.blockedApps, back.blockedApps)
        assertEquals(original.feedRules, back.feedRules)
        assertEquals(original.groups, back.groups)
        assertEquals(original.schedules, back.schedules)
        assertEquals(original.blockEvents, back.blockEvents)
        assertEquals(original.dailyUsage, back.dailyUsage)
    }

    @Test fun rejectsGarbageAndWrongApp() {
        assertTrue(invalid("not json").isNotBlank())
        assertTrue(invalid("{}").contains("not created by FocusLock"))
        assertTrue(invalid("""{"app":"Other","schemaVersion":1}""").contains("not created by FocusLock"))
    }

    @Test fun rejectsUnknownSchemaVersion() {
        assertTrue(invalid("""{"app":"FocusLock","schemaVersion":99}""").contains("version"))
    }

    @Test fun rejectsBadPackageNames() {
        val json = BackupCodec.encode(sample()).replace("com.instagram.android", "../../etc/passwd")
        assertTrue(invalid(json).contains("app identifier"))
    }

    @Test fun rejectsOutOfRangeScheduleValues() {
        val json = BackupCodec.encode(sample()).replace(Regex("\"startMinute\"\\s*:\\s*1320"), "\"startMinute\": 99999")
        assertTrue(invalid(json).contains("out of range"))
    }

    @Test fun rejectsUnknownEnumValues() {
        val json = BackupCodec.encode(sample()).replace("SESSIONS_ONLY", "EVERYTHING")
        assertTrue(invalid(json).contains("Unknown value"))
    }

    @Test fun rejectsOversizedFile() {
        assertTrue(invalid("x".repeat(BackupCodec.MAX_CHARS + 1)).contains("too large"))
    }

    @Test fun rejectsScheduleReferencingUnknownGroup() {
        val json = BackupCodec.encode(sample().copy(groups = emptyList())) // schedule still names group "Study"
        assertTrue(invalid(json).contains("unknown group"))
    }

    @Test fun csvNeutralizesFormulaInjection() {
        val csv = BackupCodec.statsCsv(
            listOf(BlockEvent(0, 0, "a.b", RuleKind.APP, "x", "=HYPERLINK(\"evil\")", BlockSource.ALWAYS_ON, BlockAction.SHOWN)),
            emptyList(), java.time.ZoneId.of("UTC"),
        )
        assertTrue(csv.contains("'=HYPERLINK"))
        assertTrue(csv.lines().first().startsWith("# Block events"))
    }
}
