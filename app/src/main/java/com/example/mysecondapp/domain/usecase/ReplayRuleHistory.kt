package com.example.mysecondapp.domain.usecase

import com.example.mysecondapp.domain.analysis.replay.HistoricalReplayResult
import com.example.mysecondapp.domain.analysis.replay.HistoricalRuleReplayer
import com.example.mysecondapp.domain.analysis.rule.AnalysisRule
import com.example.mysecondapp.domain.analysis.history.HistoricalBarSeries
import javax.inject.Inject

/** Use-case boundary for historical replay; callers provide a fixed, validated source series. */
class ReplayRuleHistory @Inject constructor(
    private val replayer: HistoricalRuleReplayer,
) {
    operator fun invoke(rule: AnalysisRule, series: HistoricalBarSeries): HistoricalReplayResult =
        replayer.replay(rule, series)
}
