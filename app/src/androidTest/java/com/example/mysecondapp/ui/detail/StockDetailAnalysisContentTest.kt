package com.example.mysecondapp.ui.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mysecondapp.domain.analysis.signal.TechnicalReasonCode
import com.example.mysecondapp.domain.analysis.signal.TechnicalResult
import com.example.mysecondapp.domain.analysis.signal.TechnicalResultStatus
import com.example.mysecondapp.domain.analysis.rule.AnalysisRule
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.StockIdentity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StockDetailAnalysisContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun analysis_summary_and_rule_template_are_actionable() {
        var selectedRule: AnalysisRule? = null
        val identity = StockIdentity("SH", "600000")

        composeRule.setContent {
            MaterialTheme {
                StockDetailContent(
                    uiState = StockDetailUiState(
                        identity = identity,
                        selectedPeriod = CandlePeriod.DAY,
                        analysis = StockAnalysisUiState(
                            quality = com.example.mysecondapp.domain.analysis.history.HistoricalBarQuality.COMPLETE,
                            issueCodes = emptyList(),
                            cutoffMillis = 0L,
                            providerId = "tencent",
                            adjustment = "QFQ",
                            trend = TechnicalResult(
                                kind = "ema_trend",
                                status = TechnicalResultStatus.MATCHED,
                                reasonCode = TechnicalReasonCode.EMA_BULLISH_RELATION,
                                sourceBarTimestamps = listOf(0L),
                                values = mapOf("fastEma" to 10.5, "slowEma" to 10.2),
                            ),
                        ),
                    ),
                    onBackClick = {},
                    onPeriodSelected = {},
                    onRefresh = {},
                    onRuleTemplateSelected = { selectedRule = it },
                )
            }
        }

        composeRule.onNodeWithText("Historical analysis").assertIsDisplayed()
        composeRule.onNodeWithText("COMPLETE  |  tencent  |  QFQ").assertIsDisplayed()
        composeRule.onNodeWithText("ema_trend: EMA_BULLISH_RELATION  fastEma=10.50, slowEma=10.20").assertIsDisplayed()
        composeRule.onNodeWithText("Rule templates").assertIsDisplayed()
        composeRule.onNodeWithText("Enable").performClick()

        assertEquals("ema_golden_cross", selectedRule?.id)
    }
}
