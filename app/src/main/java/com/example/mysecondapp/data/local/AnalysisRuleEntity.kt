package com.example.mysecondapp.data.local

import androidx.room.Entity
import androidx.room.Index

/** 当前用户规则的持久化表示；condition_json 保留完整的 typed condition tree。 */
@Entity(
    tableName = "analysis_rule",
    indices = [Index(value = ["enabled"])],
)
data class AnalysisRuleEntity(
    @androidx.room.PrimaryKey
    @androidx.room.ColumnInfo(name = "rule_id")
    val ruleId: String,
    val version: Int,
    val name: String,
    val period: String,
    val direction: String,
    val conditionJson: String,
    val enabled: Boolean,
)
