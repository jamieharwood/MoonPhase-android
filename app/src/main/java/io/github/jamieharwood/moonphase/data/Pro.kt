package io.github.jamieharwood.moonphase.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import io.github.jamieharwood.moonphase.BuildConfig
import io.github.jamieharwood.moonphase.widget.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orbitly Pro: a one-time, non-consumable Google Play in-app product ([PRODUCT_ID]).
 *
 * The unlocked flag is cached in preferences so widgets, the background worker and the
 * watch sync can check it without talking to Play. Every time the app connects to Play the
 * flag is re-checked against the user's purchases, so a refund locks Pro again.
 */
object Pro {
    const val PRODUCT_ID = "orbitly_pro"
    private const val PREFS = "pro"
    private const val TAG = "Pro"

    data class State(
        val owned: Boolean = false,
        /** Localised price from Play ("£2.99"), or null until Play answers. */
        val price: String? = null,
        /** False when Play Billing can't be reached (no Play Store, sideloaded build…). */
        val available: Boolean = true,
        val pending: Boolean = false,
    )

    enum class Event { UNLOCKED, PENDING, NOT_FOUND, RESTORED, UNAVAILABLE, ERROR }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private var client: BillingClient? = null
    private var details: ProductDetails? = null

    fun isPro(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("owned", false)

    /** The stored flag, emitting again whenever it changes (for widgets). */
    fun ownedFlow(ctx: Context): Flow<Boolean> = callbackFlow {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "owned") trySend(p.getBoolean("owned", false))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getBoolean("owned", false))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Call once from the activity; safe to call again. */
    fun init(ctx: Context) {
        if (client != null) return
        app = ctx.applicationContext
        _state.update { it.copy(owned = isPro(app)) }
        client = BillingClient.newBuilder(app)
            .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        connect()
    }

    private fun connect(onReady: (() -> Unit)? = null) {
        val c = client ?: return
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update { it.copy(available = true) }
                    scope.launch {
                        loadDetails()
                        refreshPurchases(reportResult = false)
                        onReady?.invoke()
                    }
                } else {
                    Log.d(TAG, "billing unavailable: ${result.debugMessage}")
                    _state.update { it.copy(available = false) }
                }
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() reconnects on the next call.
            }
        })
    }

    private suspend fun loadDetails() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build(),
            ),
        ).build()
        val result = c.queryProductDetails(params)
        details = result.productDetailsList?.firstOrNull()
        _state.update { it.copy(price = details?.oneTimePurchaseOfferDetails?.formattedPrice) }
    }

    /** Re-reads the user's purchases from Play. [reportResult] is for the Restore button. */
    private suspend fun refreshPurchases(reportResult: Boolean) {
        val c = client ?: return
        val result = c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            if (reportResult) _events.tryEmit(Event.ERROR)
            return
        }
        val mine = result.purchasesList.filter { PRODUCT_ID in it.products }
        val owned = mine.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        mine.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { acknowledge(it) }
        _state.update { it.copy(pending = mine.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING }) }
        if (debugOverride() && !owned) return // keep the test switch on in debug builds
        setOwned(owned)
        if (reportResult) _events.tryEmit(if (owned) Event.RESTORED else Event.NOT_FOUND)
    }

    fun buy(activity: Activity) {
        val c = client
        val d = details
        if (c == null || !_state.value.available || d == null) {
            _events.tryEmit(Event.UNAVAILABLE)
            // Try again in case Play has become reachable since start-up.
            connect()
            return
        }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()),
        ).build()
        c.launchBillingFlow(activity, params)
    }

    fun restore() {
        if (client == null || !_state.value.available) {
            _events.tryEmit(Event.UNAVAILABLE)
            connect()
            return
        }
        scope.launch { refreshPurchases(reportResult = true) }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty()
                .filter { PRODUCT_ID in it.products }
                .forEach { p ->
                    when (p.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> scope.launch {
                            acknowledge(p)
                            setOwned(true)
                            _events.tryEmit(Event.UNLOCKED)
                        }
                        Purchase.PurchaseState.PENDING -> {
                            _state.update { it.copy(pending = true) }
                            _events.tryEmit(Event.PENDING)
                        }
                        else -> Unit
                    }
                }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refreshPurchases(reportResult = true) }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _events.tryEmit(Event.ERROR)
        }
    }

    /** Play refunds purchases that aren't acknowledged within three days. */
    private suspend fun acknowledge(p: Purchase) {
        if (p.isAcknowledged) return
        val r = client?.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build(),
        )
        Log.d(TAG, "acknowledge: ${r?.responseCode}")
    }

    private fun setOwned(owned: Boolean) {
        val changed = owned != isPro(app)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("owned", owned).apply()
        _state.update { it.copy(owned = owned, pending = if (owned) false else it.pending) }
        if (changed) onChanged()
    }

    /** Debug builds only: unlock/lock without Play, for testing sideloaded builds. */
    fun setDebugOverride(on: Boolean) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("debug", on).apply()
        setOwned(on)
    }

    private fun onChanged() {
        WearSync.push(app, Settings.load(app))
        scope.launch { WidgetData.updateAll(app) }
    }

    private fun debugOverride() = BuildConfig.DEBUG &&
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("debug", false)
}
