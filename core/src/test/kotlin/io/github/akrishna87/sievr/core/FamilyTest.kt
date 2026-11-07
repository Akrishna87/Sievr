package io.github.akrishna87.sievr.core

import io.github.akrishna87.sievr.core.mf.MfHolding
import io.github.akrishna87.sievr.core.mf.MutualFunds
import io.github.akrishna87.sievr.core.portfolio.Holding
import io.github.akrishna87.sievr.core.portfolio.PortfolioAnalyzer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FamilyTest {
    @Test fun mergeAcrossMembers() {
        val merged = PortfolioAnalyzer.merge(listOf(Holding("GOOD", 10.0, 100.0, "me"), Holding("GOOD", 30.0, 200.0, "wife"), Holding("MID", 5.0, 50.0, "wife")))
        val good = merged.first { it.symbol == "GOOD" }
        assertEquals(40.0, good.quantity, 1e-9)
        assertEquals(175.0, good.averageCost, 1e-9)  // (10×100 + 30×200) / 40
        assertEquals(2, merged.size)
        val funds = MutualFunds.merge(listOf(MfHolding(1, 10.0, 1000.0, "A", "me"), MfHolding(1, 5.0, 600.0, "A", "wife")))
        assertEquals(MfHolding(1, 15.0, 1600.0, "A", "all"), funds.single())
    }

    @Test fun olderSavedHoldingsBelongToMe() {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals("me", json.decodeFromString(Holding.serializer(), """{"symbol":"GOOD","quantity":1,"averageCost":2}""").owner)
        assertEquals("me", json.decodeFromString(MfHolding.serializer(), """{"code":1,"units":1,"invested":2}""").owner)
    }
}
