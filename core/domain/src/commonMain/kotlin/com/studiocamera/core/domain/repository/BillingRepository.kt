package com.studiocamera.core.domain.repository

import com.studiocamera.core.domain.model.SubscriptionState
import kotlinx.coroutines.flow.StateFlow

interface BillingRepository {
    val subscriptionState: StateFlow<SubscriptionState>
    suspend fun refreshState()
    suspend fun launchMonthlyPurchase()
    suspend fun launchLifetimePurchase()
    suspend fun restorePurchases()
}
