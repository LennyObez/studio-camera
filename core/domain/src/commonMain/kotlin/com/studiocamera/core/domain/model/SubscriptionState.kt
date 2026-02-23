package com.studiocamera.core.domain.model

sealed class SubscriptionState {
    data class Trial(val daysRemaining: Int, val startedAt: Long) : SubscriptionState()
    data object TrialExpired : SubscriptionState()
    data class Monthly(val expiresAt: Long) : SubscriptionState()
    data class Lifetime(val purchasedAt: Long) : SubscriptionState()
    data object Unknown : SubscriptionState()
}
