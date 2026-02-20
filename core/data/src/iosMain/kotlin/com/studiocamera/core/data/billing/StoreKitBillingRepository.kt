package com.studiocamera.core.data.billing

import co.touchlab.kermit.Logger
import com.studiocamera.core.common.currentTimeMillis
import com.studiocamera.core.domain.model.SubscriptionState
import com.studiocamera.core.domain.repository.BillingRepository
import com.studiocamera.core.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.StoreKit.SKPayment
import platform.StoreKit.SKPaymentQueue
import platform.StoreKit.SKPaymentTransaction
import platform.StoreKit.SKPaymentTransactionObserverProtocol
import platform.StoreKit.SKPaymentTransactionStatePurchased
import platform.StoreKit.SKPaymentTransactionStateRestored
import platform.StoreKit.SKPaymentTransactionStateFailed
import platform.StoreKit.SKProduct
import platform.StoreKit.SKProductsRequest
import platform.StoreKit.SKProductsRequestDelegateProtocol
import platform.StoreKit.SKProductsResponse
import platform.StoreKit.SKRequest
import platform.Foundation.NSError
import platform.Foundation.NSSet
import platform.darwin.NSObject

private const val TAG = "StoreKitBilling"
private const val PRODUCT_MONTHLY = "studio_camera_monthly"
private const val PRODUCT_LIFETIME = "studio_camera_lifetime"
private const val KEY_TRIAL_START = "trial_start_epoch"
private const val KEY_TRIAL_HIGH_WATER = "trial_high_water"
private const val KEY_PURCHASE_TYPE = "ios_purchase_type"
private const val KEY_PURCHASE_TIMESTAMP = "ios_purchase_timestamp"
private const val TRIAL_DAYS = 7

/**
 * iOS billing implementation using StoreKit 1 (Objective-C-compatible API).
 *
 * StoreKit 2 uses Swift async which isn't directly callable from Kotlin/Native.
 * StoreKit 1's `SKPaymentQueue` / `SKProductsRequest` work via ObjC interop.
 */
class StoreKitBillingRepository(
    private val secureStorage: SecureStorage
) : BillingRepository {

    private val _subscriptionState = MutableStateFlow<SubscriptionState>(SubscriptionState.Unknown)
    override val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    private var monthlyProduct: SKProduct? = null
    private var lifetimeProduct: SKProduct? = null

    private val transactionObserver = TransactionObserver(
        onPurchased = { transaction -> handlePurchased(transaction) },
        onRestored = { transaction -> handleRestored(transaction) },
        onFailed = { transaction, error ->
            Logger.e(TAG) { "Purchase failed: ${error?.localizedDescription}" }
            SKPaymentQueue.defaultQueue().finishTransaction(transaction)
        }
    )

    fun initialize() {
        restorePersistedPurchase()
        ensureTrialStarted()
        SKPaymentQueue.defaultQueue().addTransactionObserver(transactionObserver)
        fetchProducts()
    }

    private fun restorePersistedPurchase() {
        val purchaseType = secureStorage.getString(KEY_PURCHASE_TYPE) ?: return
        val timestamp = secureStorage.getString(KEY_PURCHASE_TIMESTAMP)?.toLongOrNull() ?: 0L
        when (purchaseType) {
            "lifetime" -> _subscriptionState.value = SubscriptionState.Lifetime(purchasedAt = timestamp)
            "monthly" -> _subscriptionState.value = SubscriptionState.Monthly(expiresAt = 0L)
        }
    }

    private fun persistPurchase(type: String) {
        secureStorage.putString(KEY_PURCHASE_TYPE, type)
        secureStorage.putString(KEY_PURCHASE_TIMESTAMP, currentTimeMillis().toString())
    }

    private fun ensureTrialStarted() {
        val existingStr = secureStorage.getString(KEY_TRIAL_START)
        val existing = existingStr?.toLongOrNull() ?: 0L
        if (existing == 0L) {
            val now = currentTimeMillis()
            secureStorage.putString(KEY_TRIAL_START, now.toString())
            secureStorage.putString(KEY_TRIAL_HIGH_WATER, now.toString())
            _subscriptionState.value = SubscriptionState.Trial(TRIAL_DAYS, now)
        } else {
            val rawNow = currentTimeMillis()
            val previousHighWater = secureStorage.getString(KEY_TRIAL_HIGH_WATER)?.toLongOrNull() ?: existing
            val now = maxOf(rawNow, previousHighWater)
            secureStorage.putString(KEY_TRIAL_HIGH_WATER, now.toString())

            val elapsed = now - existing
            val daysElapsed = (elapsed / (1000 * 60 * 60 * 24)).toInt()
            val remaining = TRIAL_DAYS - daysElapsed
            _subscriptionState.value = if (remaining > 0) {
                SubscriptionState.Trial(remaining, existing)
            } else {
                SubscriptionState.TrialExpired
            }
        }
    }

    private fun fetchProducts() {
        val productIds = NSSet.setWithObjects(PRODUCT_MONTHLY, PRODUCT_LIFETIME)
        val request = SKProductsRequest(productIdentifiers = productIds)
        request.delegate = ProductsRequestDelegate(
            onResponse = { products ->
                for (product in products) {
                    val skProduct = product as SKProduct
                    when (skProduct.productIdentifier) {
                        PRODUCT_MONTHLY -> monthlyProduct = skProduct
                        PRODUCT_LIFETIME -> lifetimeProduct = skProduct
                    }
                }
                Logger.d(TAG) { "Fetched ${products.size} products" }
            },
            onError = { error ->
                Logger.e(TAG) { "Failed to fetch products: ${error?.localizedDescription}" }
            }
        )
        request.start()
    }

    private fun handlePurchased(transaction: SKPaymentTransaction) {
        val productId = transaction.payment.productIdentifier
        Logger.i(TAG) { "Purchase completed: $productId" }

        when (productId) {
            PRODUCT_MONTHLY -> {
                persistPurchase("monthly")
                _subscriptionState.value = SubscriptionState.Monthly(expiresAt = 0L)
            }
            PRODUCT_LIFETIME -> {
                persistPurchase("lifetime")
                _subscriptionState.value = SubscriptionState.Lifetime(purchasedAt = currentTimeMillis())
            }
        }

        SKPaymentQueue.defaultQueue().finishTransaction(transaction)
    }

    private fun handleRestored(transaction: SKPaymentTransaction) {
        val productId = transaction.payment.productIdentifier
        Logger.i(TAG) { "Purchase restored: $productId" }

        when (productId) {
            PRODUCT_MONTHLY -> {
                persistPurchase("monthly")
                _subscriptionState.value = SubscriptionState.Monthly(expiresAt = 0L)
            }
            PRODUCT_LIFETIME -> {
                persistPurchase("lifetime")
                _subscriptionState.value = SubscriptionState.Lifetime(purchasedAt = currentTimeMillis())
            }
        }

        SKPaymentQueue.defaultQueue().finishTransaction(transaction)
    }

    override suspend fun refreshState() {
        ensureTrialStarted()
        // Re-fetch products and observe restored transactions
        fetchProducts()
    }

    override suspend fun launchMonthlyPurchase() {
        val product = monthlyProduct
        if (product == null) {
            Logger.w(TAG) { "Monthly product not loaded yet" }
            return
        }
        val payment = SKPayment.paymentWithProduct(product)
        SKPaymentQueue.defaultQueue().addPayment(payment)
    }

    override suspend fun launchLifetimePurchase() {
        val product = lifetimeProduct
        if (product == null) {
            Logger.w(TAG) { "Lifetime product not loaded yet" }
            return
        }
        val payment = SKPayment.paymentWithProduct(product)
        SKPaymentQueue.defaultQueue().addPayment(payment)
    }

    override suspend fun restorePurchases() {
        Logger.d(TAG) { "Restoring purchases..." }
        SKPaymentQueue.defaultQueue().restoreCompletedTransactions()
    }

    fun destroy() {
        SKPaymentQueue.defaultQueue().removeTransactionObserver(transactionObserver)
    }
}

/**
 * StoreKit 1 transaction observer — receives purchase/restore/failure callbacks.
 */
private class TransactionObserver(
    private val onPurchased: (SKPaymentTransaction) -> Unit,
    private val onRestored: (SKPaymentTransaction) -> Unit,
    private val onFailed: (SKPaymentTransaction, NSError?) -> Unit
) : NSObject(), SKPaymentTransactionObserverProtocol {

    override fun paymentQueue(
        queue: SKPaymentQueue,
        updatedTransactions: List<*>
    ) {
        for (item in updatedTransactions) {
            val transaction = item as SKPaymentTransaction
            when (transaction.transactionState) {
                SKPaymentTransactionStatePurchased -> onPurchased(transaction)
                SKPaymentTransactionStateRestored -> onRestored(transaction)
                SKPaymentTransactionStateFailed -> onFailed(transaction, transaction.error)
                else -> {
                    Logger.d(TAG) { "Unhandled transaction state: ${transaction.transactionState}" }
                }
            }
        }
    }
}

/**
 * StoreKit 1 products request delegate — receives fetched product info.
 */
private class ProductsRequestDelegate(
    private val onResponse: (List<*>) -> Unit,
    private val onError: (NSError?) -> Unit
) : NSObject(), SKProductsRequestDelegateProtocol {

    override fun productsRequest(request: SKProductsRequest, didReceiveResponse: SKProductsResponse) {
        onResponse(didReceiveResponse.products ?: emptyList<Any>())
    }

    override fun request(request: SKRequest, didFailWithError: NSError) {
        onError(didFailWithError)
    }
}
