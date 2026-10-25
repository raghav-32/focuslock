package com.focuslock.app.data.repo

import com.focuslock.app.data.db.BlockEventEntity
import com.focuslock.app.data.db.BlockedAppEntity
import com.focuslock.app.data.db.DailyUsageEntity
import com.focuslock.app.data.db.FeedRuleEntity
import com.focuslock.app.data.db.FocusSessionEntity
import com.focuslock.app.data.db.ScheduleEntity
import com.focuslock.app.domain.BlockAction
import com.focuslock.app.domain.BlockEvent
import com.focuslock.app.domain.BlockScope
import com.focuslock.app.domain.BlockSource
import com.focuslock.app.domain.BlockedApp
import com.focuslock.app.domain.DailyUsage
import com.focuslock.app.domain.FeedRule
import com.focuslock.app.domain.FocusSession
import com.focuslock.app.domain.RuleKind
import com.focuslock.app.domain.Schedule
import com.focuslock.app.domain.SessionState

internal inline fun <reified E : Enum<E>> parseEnum(value: String, default: E): E =
    try {
        enumValueOf<E>(value)
    } catch (e: IllegalArgumentException) {
        default
    }

internal fun String.splitCsv(): List<String> =
    if (isBlank()) emptyList() else split(',').map { it.trim() }.filter { it.isNotEmpty() }

internal fun String.splitTokens(): List<String> =
    if (isBlank()) emptyList() else split('|').map { it.trim() }.filter { it.isNotEmpty() }

internal fun BlockedAppEntity.toDomain() =
    BlockedApp(packageName, enabled, parseEnum(scope, BlockScope.ALWAYS))

internal fun BlockedApp.toEntity(addedAt: Long) =
    BlockedAppEntity(packageName, enabled, scope.name, addedAt)

internal fun FeedRuleEntity.toDomain() = FeedRule(
    id = id,
    packageName = packageName,
    name = name,
    enabled = enabled,
    scope = parseEnum(scope, BlockScope.ALWAYS),
    idTokens = idTokens.splitTokens(),
    textTokens = textTokens.splitTokens(),
    fallbackWholeApp = fallbackWholeApp,
    minVersion = minVersion,
    maxVersion = maxVersion,
)

internal fun FeedRule.toEntity() = FeedRuleEntity(
    id = id,
    packageName = packageName,
    name = name,
    enabled = enabled,
    scope = scope.name,
    idTokens = idTokens.joinToString("|"),
    textTokens = textTokens.joinToString("|"),
    fallbackWholeApp = fallbackWholeApp,
    minVersion = minVersion,
    maxVersion = maxVersion,
)

internal fun ScheduleEntity.toDomain() = Schedule(
    id = id,
    name = name,
    startMinute = startMinute,
    endMinute = endMinute,
    daysMask = daysMask,
    enabled = enabled,
    packages = packages.splitCsv().toSet(),
    groupIds = groupIds.splitCsv().mapNotNull { it.toLongOrNull() }.toSet(),
    feedRuleIds = feedRuleIds.splitCsv().toSet(),
    exceptionPackages = exceptions.splitCsv().toSet(),
    requireFocusSession = requireFocusSession,
)

internal fun Schedule.toEntity() = ScheduleEntity(
    id = id,
    name = name,
    startMinute = startMinute,
    endMinute = endMinute,
    daysMask = daysMask,
    enabled = enabled,
    packages = packages.joinToString(","),
    groupIds = groupIds.joinToString(","),
    feedRuleIds = feedRuleIds.joinToString(","),
    exceptions = exceptionPackages.joinToString(","),
    requireFocusSession = requireFocusSession,
)

internal fun FocusSessionEntity.toDomain() = FocusSession(
    id = id,
    startWall = startWall,
    startElapsed = startElapsed,
    startBoot = startBoot,
    plannedMillis = plannedMillis,
    strict = strict,
    groupId = groupId,
    state = parseEnum(state, SessionState.CANCELLED),
    endedWall = endedWall,
)

internal fun BlockEventEntity.toDomain() = BlockEvent(
    id = id,
    timestamp = timestamp,
    packageName = packageName,
    kind = parseEnum(kind, RuleKind.APP),
    ruleId = ruleId,
    ruleLabel = ruleLabel,
    source = parseEnum(source, BlockSource.ALWAYS_ON),
    action = parseEnum(action, BlockAction.SHOWN),
)

internal fun BlockEvent.toEntity() = BlockEventEntity(
    timestamp = timestamp,
    packageName = packageName,
    kind = kind.name,
    ruleId = ruleId,
    ruleLabel = ruleLabel,
    source = source.name,
    action = action.name,
)

internal fun DailyUsageEntity.toDomain() = DailyUsage(epochDay, packageName, foregroundMillis)
internal fun DailyUsage.toEntity() = DailyUsageEntity(epochDay, packageName, foregroundMillis)
