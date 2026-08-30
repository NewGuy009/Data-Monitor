package com.example.mysecondapp.domain.model

import kotlinx.serialization.Serializable

/** Actual price-adjustment口径 returned by the K-line provider. */
@Serializable
enum class CandleAdjustment {
    QFQ,
    RAW,
    NONE,
}
