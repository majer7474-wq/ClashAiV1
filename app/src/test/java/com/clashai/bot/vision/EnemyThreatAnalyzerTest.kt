package com.clashai.bot.vision

import com.clashai.bot.ai.Threat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EnemyThreatAnalyzerTest {
    @Test
    fun detectsTankThreat() {
        val result = EnemyThreatAnalyzer.assess(
            listOf(ArenaUnit("giant", 0.91f, 0.45f, 0.42f, 0.2f, 0.2f))
        )
        assertEquals(Threat.TANK, result.threat)
        assertNotNull(result.targetX)
    }

    @Test
    fun detectsAirThreat() {
        val result = EnemyThreatAnalyzer.assess(
            listOf(ArenaUnit("balloon", 0.88f, 0.7f, 0.4f, 0.2f, 0.2f))
        )
        assertEquals(Threat.AIR, result.threat)
    }

    @Test
    fun ignoresOurHalfAndTowerDetections() {
        val result = EnemyThreatAnalyzer.assess(
            listOf(
                ArenaUnit("hog-rider", 0.95f, 0.5f, 0.82f, 0.1f, 0.1f),
                ArenaUnit("king-tower", 0.99f, 0.5f, 0.1f, 0.2f, 0.2f)
            )
        )
        assertEquals(Threat.NONE, result.threat)
    }
}
