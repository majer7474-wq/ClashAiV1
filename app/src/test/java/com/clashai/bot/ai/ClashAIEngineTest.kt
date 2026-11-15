package com.clashai.bot.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClashAIEngineTest {
    private val engine = ClashAIEngine()

    private fun state(
        threat: Threat = Threat.NONE,
        units: List<ObservedEnemy> = emptyList(),
        elixir: Float = 5f,
        age: Long = 0L
    ) = BattleState(
        elixir = elixir,
        hand = listOf(
            Card.HOG_RIDER, Card.MUSKETEER, Card.CANNON, Card.LOG
        ),
        enemyThreat = threat,
        handConfidence = 1f,
        observationAgeMs = age,
        enemyUnits = units
    )

    @Test fun hogPressureWhenNoThreatAndAffordable() {
        val action = engine.chooseAction(state(elixir = 4f))
        assertEquals(Card.HOG_RIDER, action.card)
        assertEquals("RAGE: immediate Hog pressure in the weaker lane", action.reason)
    }

    @Test fun airThreatUsesMusketeerNotCannon() {
        val enemy = ObservedEnemy("balloon", 0.72f, 0.42f, 0.95f)
        val action = engine.chooseAction(state(Threat.AIR, listOf(enemy)))
        assertEquals(Card.MUSKETEER, action.card)
        assertEquals(0.72f, action.placementX!!, 0.01f)
    }

    @Test fun groundTankUsesCannon() {
        val enemy = ObservedEnemy("pekka", 0.70f, 0.41f, 0.93f)
        val action = engine.chooseAction(state(Threat.HIGH_DANGER, listOf(enemy)))
        assertEquals(Card.CANNON, action.card)
        assertEquals(0.645f, action.placementY!!, 0.001f)
    }

    @Test fun swarmUsesLog() {
        val enemy = ObservedEnemy("skeleton", 0.30f, 0.44f, 0.91f)
        val action = engine.chooseAction(state(Threat.SWARM, listOf(enemy)))
        assertEquals(Card.LOG, action.card)
    }

    @Test fun staleObservationNeverProducesPlay() {
        val action = engine.chooseAction(state(age = 2000L))
        assertNull(action.card)
    }
}
