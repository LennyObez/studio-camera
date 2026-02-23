package com.studiocamera.android.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.studiocamera.core.domain.model.SubscriptionState
import com.studiocamera.core.domain.repository.BillingRepository
import com.studiocamera.core.storage.SecureStorage
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

class GoogleBillingRepository(
    private val context: Context,
    private val secureStorage: SecureStorage
) : BillingRepository {

    companion object {
        const val PRODUCT_MONTHLY = "studio_camera_monthly"
        const val PRODUCT_LIFETIME = "studio_camera_lifetime"
        private const val KEY_TRIAL_START = "trial_start_epoch"
        private const val TRIAL_DAYS = 7
        private const val TAG = "Billing"
    }

    private val _subscriptionState = MutableStateFlow<SubscriptionState>(SubscriptionState.Unknown)
    override val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    private var billingClient: BillingClient? = null
    private var monthlyDetails: ProductDetails? = null
    private var lifetimeDetails: ProductDetails? = null
    private var activityRef: WeakReference<Activity>? = null

    private val purchasesListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun initialize() {
        ensureTrialStarted()

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                com.android.billingclient.api.PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()

        startConnection()
    }

    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    queryExistingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                Logger.w(TAG) { "Billing service disconnected, will retry on next operation" }
            }
        })
    }

    private fun ensureConnected(action: () -> Unit) {
        val client = billingClient ?: return
        if (client.isReady) {
            action()
        } else {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        action()
                    }
                }
                override fun onBillingServiceDisconnected() {
                    Logger.w(TAG) { "Billing reconnection failed" }
                }
            })
        }
    }

    private fun ensureTrialStarted() {
        val existingStr = secureStorage.getString(KEY_TRIAL_START)
        val existing = existingStr?.toLongOrNull() ?: 0L
        if (existing == 0L) {
            val now = System.currentTimeMillis()
            secureStorage.putString(KEY_TRIAL_START, now.toString())
            _subscriptionState.value = SubscriptionState.Trial(TRIAL_DAYS, now)
        } else {
            val elapsed = System.currentTimeMillis() - existing
            val daysElapsed = (elapsed / (1000 * 60 * 60 * 24)).toInt()
            val remaining = TRIAL_DAYS - daysElapsed
            _subscriptionState.value = if (remaining > 0) {
                SubscriptionState.Trial(remaining, existing)
            } else {
                SubscriptionState.TrialExpired
            }
        }
    }

    private fun queryProducts() {
        val subProduct = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_MONTHLY)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val inappProduct = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_LIFETIME)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(subProduct, inappProduct))
                .build()
        ) { _, detailsList ->
            for (details in detailsList) {
                when (details.productId) {
                    PRODUCT_MONTHLY -> monthlyDetails = details
                    PRODUCT_LIFETIME -> lifetimeDetails = details
                }
            }
        }
    }

    private fun queryExistingPurchases() {
        ensureConnected {
            // Check subscriptions
            billingClient?.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { _, purchases ->
                for (purchase in purchases) {
                    if (purchase.products.contains(PRODUCT_MONTHLY) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        // Active subscription — trust queryPurchasesAsync result
                        _subscriptionState.value = SubscriptionState.Monthly(
                            expiresAt = purchase.purchaseTime + 30L * 24 * 60 * 60 * 1000
                        )
                        acknowledgePurchaseIfNeeded(purchase)
                        return@queryPurchasesAsync
                    }
                }

                // Check in-app purchases
                billingClient?.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                ) { _, inappPurchases ->
                    for (purchase in inappPurchases) {
                        if (purchase.products.contains(PRODUCT_LIFETIME) &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        ) {
                            _subscriptionState.value = SubscriptionState.Lifetime(
                                purchasedAt = purchase.purchaseTime
                            )
                            acknowledgePurchaseIfNeeded(purchase)
                            return@queryPurchasesAsync
                        }
                    }
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (purchase.products.contains(PRODUCT_MONTHLY)) {
                    _subscriptionState.value = SubscriptionState.Monthly(
                        expiresAt = purchase.purchaseTime + 30L * 24 * 60 * 60 * 1000
                    )
                } else if (purchase.products.contains(PRODUCT_LIFETIME)) {
                    _subscriptionState.value = SubscriptionState.Lifetime(
                        purchasedAt = purchase.purchaseTime
                    )
                }
                acknowledgePurchaseIfNeeded(purchase)
            }
            Purchase.PurchaseState.PENDING -> {
                Logger.i(TAG) { "Purchase pending for: ${purchase.products}" }
            }
            else -> {
                Logger.w(TAG) { "Unhandled purchase state: ${purchase.purchaseState}" }
            }
        }
    }

    private fun acknowledgePurchaseIfNeeded(purchase: Purchase, attempt: Int = 0) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient?.acknowledgePurchase(params) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Logger.i(TAG) { "Purchase acknowledged: ${purchase.products}" }
                } else {
                    Logger.e(TAG) { "Failed to acknowledge purchase (attempt $attempt): ${billingResult.debugMessage}" }
                    // Retry up to 3 times — unacknowledged purchases are refunded after 3 days
                    if (attempt < 3) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            acknowledgePurchaseIfNeeded(purchase, attempt + 1)
                        }, 5000L * (attempt + 1))
                    }
                }
            }
        }
    }

    override suspend fun refreshState() {
        ensureTrialStarted()
        queryExistingPurchases()
    }

    override suspend fun launchMonthlyPurchase() {
        val details = monthlyDetails ?: return
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val act = activityRef?.get() ?: return

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        billingClient?.launchBillingFlow(act, flowParams)
    }

    override suspend fun launchLifetimePurchase() {
        val details = lifetimeDetails ?: return
        val act = activityRef?.get() ?: return

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()

        billingClient?.launchBillingFlow(act, flowParams)
    }

    override suspend fun restorePurchases() {
        queryExistingPurchases()
    }

    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
        activityRef = null
    }
}
