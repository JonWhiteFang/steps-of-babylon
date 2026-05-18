package com.whitefang.stepsofbabylon.data.billing

import android.app.Activity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.whitefang.stepsofbabylon.data.billing.internal.ActivityProvider
import com.whitefang.stepsofbabylon.data.billing.internal.BillingClientAdapter
import com.whitefang.stepsofbabylon.data.billing.internal.QueryProductDetailsResult
import com.whitefang.stepsofbabylon.data.billing.internal.QueryPurchasesResult
import com.whitefang.stepsofbabylon.data.billing.internal.SdkBillingResult
import com.whitefang.stepsofbabylon.data.billing.internal.SdkProductDetails
import com.whitefang.stepsofbabylon.data.billing.internal.SdkProductType
import com.whitefang.stepsofbabylon.data.billing.internal.SdkPurchase
import com.whitefang.stepsofbabylon.data.billing.internal.SdkPurchaseState
import com.whitefang.stepsofbabylon.data.billing.internal.StartPurchaseResult
import com.whitefang.stepsofbabylon.data.local.AppDatabase
import com.whitefang.stepsofbabylon.data.local.BillingReceiptDao
import com.whitefang.stepsofbabylon.data.local.PlayerProfileDao
import com.whitefang.stepsofbabylon.data.local.PlayerProfileEntity
import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.PlayerProfile
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult
import com.whitefang.stepsofbabylon.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit coverage for [BillingManagerImpl] against a mocked [BillingClientAdapter]. The adapter
 * boundary is plain-Kotlin sealed classes, so mockito-kotlin's default subclass mock-maker
 * handles it without `mockito-inline`. All SQLite writes land in a real in-memory
 * [AppDatabase] so the `@Transaction` semantics of `BillingReceiptDao.grantOnceAtomic` are
 * exercised end-to-end.
 *
 * Robolectric is only needed for [android.content.Context.getSharedPreferences] (the
 * obfuscatedAccountId UUID store); [Activity] is mockito-mocked since the impl only uses it
 * as a pass-through argument to the (mocked) adapter.
 *
 * C.5 PR 1 / ADR-0005.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BillingManagerImplTest {

    private lateinit var db: AppDatabase
    private lateinit var playerDao: PlayerProfileDao
    private lateinit var receiptDao: BillingReceiptDao

    private lateinit var adapter: BillingClientAdapter
    private lateinit var playerRepository: PlayerRepository
    private lateinit var activityProvider: ActivityProvider
    private lateinit var activity: Activity

    private lateinit var profileFlow: MutableStateFlow<PlayerProfile>

    private lateinit var impl: BillingManagerImpl

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playerDao = db.playerProfileDao()
        receiptDao = db.billingReceiptDao()
        // Seed the single-row profile.
        kotlinx.coroutines.runBlocking { playerDao.upsert(PlayerProfileEntity(id = 1)) }

        adapter = mock()
        playerRepository = mock()
        activityProvider = ActivityProvider()
        activity = mock()
        activityProvider.set(activity)

        profileFlow = MutableStateFlow(blankProfile())
        playerRepository.stub { on { observeProfile() } doReturn profileFlow }

        impl = BillingManagerImpl(
            adapter = adapter,
            receiptDao = receiptDao,
            playerProfileDao = playerDao,
            playerRepository = playerRepository,
            activityProvider = activityProvider,
            context = ctx,
        )
    }

    @After
    fun tearDown() { db.close() }

    // --- happy paths --------------------------------------------------------------------

    @Test
    fun `purchase GEM_PACK_SMALL succeeds, credits 50 gems, consumes purchase`() = runTest {
        stubHappyPath(
            product = BillingProduct.GEM_PACK_SMALL,
            productType = SdkProductType.INAPP,
            purchaseToken = "tok_gems_small",
            state = SdkPurchaseState.PURCHASED,
            consumeResult = SdkBillingResult.Ok,
        )

        val result = impl.purchase(BillingProduct.GEM_PACK_SMALL)

        assertEquals(PurchaseResult.Success, result)
        val profile = playerDao.get().firstOrNull()!!
        assertEquals(50L, profile.gems)
        assertEquals(50L, profile.totalGemsEarned)

        val receipt = receiptDao.getByToken("tok_gems_small")!!
        assertTrue("granted flipped inside grantOnceAtomic transaction", receipt.granted)
        assertNotNull(receipt.grantedAt)
        assertTrue("consumed marked after successful consume RPC", receipt.consumed)
        assertFalse("acknowledged not touched for consumables", receipt.acknowledged)

        verify(adapter).consume("tok_gems_small")
    }

    @Test
    fun `purchase AD_REMOVAL succeeds, sets adRemoved, acknowledges purchase`() = runTest {
        stubHappyPath(
            product = BillingProduct.AD_REMOVAL,
            productType = SdkProductType.INAPP,
            purchaseToken = "tok_ad",
            state = SdkPurchaseState.PURCHASED,
            acknowledgeResult = SdkBillingResult.Ok,
        )

        val result = impl.purchase(BillingProduct.AD_REMOVAL)

        assertEquals(PurchaseResult.Success, result)
        val profile = playerDao.get().firstOrNull()!!
        assertTrue(profile.adRemoved)

        val receipt = receiptDao.getByToken("tok_ad")!!
        assertTrue(receipt.granted)
        assertTrue(receipt.acknowledged)
        assertFalse(receipt.consumed)

        verify(adapter).acknowledge("tok_ad")
    }

    @Test
    fun `purchase SEASON_PASS succeeds, sets seasonPass active with 30-day expiry`() = runTest {
        val purchaseTime = 1_700_000_000_000L
        stubHappyPath(
            product = BillingProduct.SEASON_PASS,
            productType = SdkProductType.SUBS,
            purchaseToken = "tok_sub",
            state = SdkPurchaseState.PURCHASED,
            purchaseTime = purchaseTime,
            acknowledgeResult = SdkBillingResult.Ok,
        )

        val result = impl.purchase(BillingProduct.SEASON_PASS)

        assertEquals(PurchaseResult.Success, result)
        val profile = playerDao.get().firstOrNull()!!
        assertTrue(profile.seasonPassActive)
        assertEquals(purchaseTime + 30L * 24 * 60 * 60 * 1000, profile.seasonPassExpiry)
    }

    // --- failure paths ------------------------------------------------------------------

    @Test
    fun `purchase returns Error when user cancels, does not credit or write receipt`() = runTest {
        adapter.stub {
            onBlocking { connect() } doReturn SdkBillingResult.Ok
            onBlocking { queryProductDetails(any(), any()) } doReturn QueryProductDetailsResult.Success(
                listOf(SdkProductDetails("gem_pack_small", SdkProductType.INAPP, "$0.99")),
            )
            onBlocking { launchPurchase(any(), any(), any()) } doReturn StartPurchaseResult.NotCompleted(
                SdkBillingResult.UserCanceled("user backed out"),
            )
        }

        val result = impl.purchase(BillingProduct.GEM_PACK_SMALL)

        assertTrue(result is PurchaseResult.Error)
        assertEquals("Purchase cancelled", (result as PurchaseResult.Error).message)

        val profile = playerDao.get().firstOrNull()!!
        assertEquals(0L, profile.gems)
        assertTrue("no receipt on user-cancel", receiptDao.getAll().isEmpty())
    }

    @Test
    fun `purchase returns Error with no receipt when product is unavailable`() = runTest {
        adapter.stub {
            onBlocking { connect() } doReturn SdkBillingResult.Ok
            onBlocking { queryProductDetails(any(), any()) } doReturn QueryProductDetailsResult.Success(
                emptyList(),
            )
        }

        val result = impl.purchase(BillingProduct.GEM_PACK_SMALL)

        assertTrue(result is PurchaseResult.Error)
        assertTrue(
            "message surfaces SKU + Play Console hint",
            (result as PurchaseResult.Error).message.contains("gem_pack_small"),
        )
        assertTrue(receiptDao.getAll().isEmpty())
    }

    @Test
    fun `purchase returns Error when no Activity is registered`() = runTest {
        activityProvider.clear()
        adapter.stub {
            onBlocking { connect() } doReturn SdkBillingResult.Ok
        }

        val result = impl.purchase(BillingProduct.GEM_PACK_SMALL)

        assertTrue(result is PurchaseResult.Error)
        assertEquals("No activity available for purchase", (result as PurchaseResult.Error).message)
        // adapter.queryProductDetails must NOT run without an activity; we short-circuit.
        verify(adapter).connect()
        verifyNoMoreInteractions(adapter)
    }

    @Test
    fun `purchase returns Error and halts pipeline when connect fails`() = runTest {
        adapter.stub {
            onBlocking { connect() } doReturn SdkBillingResult.ServiceDisconnected("offline")
        }

        val result = impl.purchase(BillingProduct.GEM_PACK_MEDIUM)

        assertTrue(result is PurchaseResult.Error)
        verify(adapter).connect()
        verifyNoMoreInteractions(adapter)
        assertTrue(receiptDao.getAll().isEmpty())
    }

    @Test
    fun `pending purchase writes granted=false receipt and returns pending Error`() = runTest {
        stubHappyPath(
            product = BillingProduct.GEM_PACK_SMALL,
            productType = SdkProductType.INAPP,
            purchaseToken = "tok_pending",
            state = SdkPurchaseState.PENDING,
        )

        val result = impl.purchase(BillingProduct.GEM_PACK_SMALL)

        assertTrue(result is PurchaseResult.Error)
        assertTrue((result as PurchaseResult.Error).message.contains("pending"))

        val profile = playerDao.get().firstOrNull()!!
        assertEquals("no credit on PENDING", 0L, profile.gems)

        val receipt = receiptDao.getByToken("tok_pending")!!
        assertFalse("receipt granted stays false on PENDING", receipt.granted)
        assertNull(receipt.grantedAt)
    }

    // --- idempotency --------------------------------------------------------------------

    @Test
    fun `second purchase with same token does not double-credit wallet`() = runTest {
        stubHappyPath(
            product = BillingProduct.GEM_PACK_LARGE,
            productType = SdkProductType.INAPP,
            purchaseToken = "tok_dup",
            state = SdkPurchaseState.PURCHASED,
            consumeResult = SdkBillingResult.Ok,
        )

        val first = impl.purchase(BillingProduct.GEM_PACK_LARGE)
        val second = impl.purchase(BillingProduct.GEM_PACK_LARGE)

        assertEquals(PurchaseResult.Success, first)
        assertEquals(
            "second purchase with same token must still surface Success — the receipt was already granted",
            PurchaseResult.Success,
            second,
        )

        val profile = playerDao.get().firstOrNull()!!
        assertEquals("wallet credited exactly once (700 not 1400)", 700L, profile.gems)
    }

    // --- reconciliation -----------------------------------------------------------------

    @Test
    fun `reconcile grants PENDING-then-PURCHASED receipt exactly once`() = runTest {
        // Simulate state after a previous pending purchase: receipt row with granted=false.
        receiptDao.upsert(
            com.whitefang.stepsofbabylon.data.local.BillingReceiptEntity(
                purchaseToken = "tok_promoted",
                productId = "gem_pack_small",
                purchaseTime = 0L,
                granted = false,
            ),
        )
        adapter.stub {
            onBlocking { connect() } doReturn SdkBillingResult.Ok
            onBlocking { queryPurchases(SdkProductType.INAPP) } doReturn QueryPurchasesResult.Success(
                listOf(
                    SdkPurchase(
                        productId = "gem_pack_small",
                        orderId = "GPA.777",
                        purchaseToken = "tok_promoted",
                        purchaseTime = 42L,
                        purchaseState = SdkPurchaseState.PURCHASED,
                        isAcknowledged = false,
                        isAutoRenewing = false,
                    ),
                ),
            )
            onBlocking { queryPurchases(SdkProductType.SUBS) } doReturn QueryPurchasesResult.Success(emptyList())
            onBlocking { consume(any()) } doReturn SdkBillingResult.Ok
        }

        impl.reconcilePendingPurchases()

        val profile = playerDao.get().firstOrNull()!!
        assertEquals("wallet credited by reconciliation sweep", 50L, profile.gems)

        val row = receiptDao.getByToken("tok_promoted")!!
        assertTrue(row.granted)
        assertTrue(row.consumed)

        // Second reconcile is a no-op for the wallet — already granted.
        impl.reconcilePendingPurchases()
        val profileAfter = playerDao.get().firstOrNull()!!
        assertEquals("no double-credit across repeated reconcile calls", 50L, profileAfter.gems)
    }

    @Test
    fun `reconcile retries consume on granted-but-unresolved rows without re-crediting`() = runTest {
        // Simulate: grant landed, consume previously failed. Wallet already has 300 gems.
        playerDao.adjustGems(300)
        playerDao.incrementGemsEarned(300)
        receiptDao.upsert(
            com.whitefang.stepsofbabylon.data.local.BillingReceiptEntity(
                purchaseToken = "tok_retry",
                productId = "gem_pack_medium",
                purchaseTime = 0L,
                granted = true,
                grantedAt = 1L,
                consumed = false,
            ),
        )
        adapter.stub {
            onBlocking { connect() } doReturn SdkBillingResult.Ok
            onBlocking { queryPurchases(any()) } doReturn QueryPurchasesResult.Success(emptyList())
            onBlocking { consume("tok_retry") } doReturn SdkBillingResult.Ok
        }

        impl.reconcilePendingPurchases()

        val row = receiptDao.getByToken("tok_retry")!!
        assertTrue("consume retry marked the row", row.consumed)
        val profile = playerDao.get().firstOrNull()!!
        assertEquals("wallet NOT re-credited by retry", 300L, profile.gems)
    }

    // --- delegation ---------------------------------------------------------------------

    @Test
    fun `isAdRemoved reads from PlayerRepository`() = runTest {
        profileFlow.value = blankProfile(adRemoved = true)
        assertTrue(impl.isAdRemoved())

        profileFlow.value = blankProfile(adRemoved = false)
        assertFalse(impl.isAdRemoved())
    }

    @Test
    fun `isSeasonPassActive returns false and clears flag past expiry`() = runTest {
        profileFlow.value = blankProfile(
            seasonPassActive = true,
            seasonPassExpiry = 1L, // well in the past
        )
        assertFalse(impl.isSeasonPassActive())
        verify(playerRepository).updateSeasonPass(false, 0)
    }

    @Test
    fun `isSeasonPassActive returns true when flag is set and expiry is in the future`() = runTest {
        profileFlow.value = blankProfile(
            seasonPassActive = true,
            seasonPassExpiry = Long.MAX_VALUE,
        )
        assertTrue(impl.isSeasonPassActive())
        // No updateSeasonPass call on the future-expiry path (the expiry-clear branch is
        // only taken when expiry < now).
        org.mockito.kotlin.verify(playerRepository, org.mockito.kotlin.never())
            .updateSeasonPass(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    // --- Plan 31 PR B: getPriceDisplay live-price wiring ---

    @Test
    fun `getPriceDisplay returns adapter priceDisplay on success`() = runTest {
        adapter.stub {
            onBlocking { connect() } doReturn SdkBillingResult.Ok
            onBlocking { queryProductDetails(any(), any()) } doReturn QueryProductDetailsResult.Success(
                listOf(SdkProductDetails("gem_pack_small", SdkProductType.INAPP, "£0.79")),
            )
        }

        val result = impl.getPriceDisplay(BillingProduct.GEM_PACK_SMALL)

        assertEquals(
            "getPriceDisplay must surface ProductDetails.priceDisplay verbatim (locale-formatted by Play Billing)",
            "£0.79",
            result,
        )
    }

    @Test
    fun `getPriceDisplay returns null when product details query fails`() = runTest {
        adapter.stub {
            onBlocking { connect() } doReturn SdkBillingResult.Ok
            onBlocking { queryProductDetails(any(), any()) } doReturn QueryProductDetailsResult.Error(
                SdkBillingResult.NetworkError(null),
            )
        }

        val result = impl.getPriceDisplay(BillingProduct.GEM_PACK_SMALL)

        assertNull(
            "failed product-details query must surface as null so the UI falls back to the static priceDisplay constant",
            result,
        )
    }

    @Test
    fun `getPriceDisplay returns null when adapter cannot connect`() = runTest {
        adapter.stub {
            onBlocking { connect() } doReturn SdkBillingResult.BillingUnavailable(null)
        }

        val result = impl.getPriceDisplay(BillingProduct.GEM_PACK_SMALL)

        assertNull(
            "failed connect must surface as null so the UI falls back to the static priceDisplay constant",
            result,
        )
    }

    // --- helpers ------------------------------------------------------------------------

    /**
     * Wires adapter mocks for a "product available, launchPurchase completes with a purchase
     * in [state], consume/acknowledge returns [consumeResult]/[acknowledgeResult]" flow.
     */
    private fun stubHappyPath(
        product: BillingProduct,
        productType: SdkProductType,
        purchaseToken: String,
        state: SdkPurchaseState,
        purchaseTime: Long = 0L,
        consumeResult: SdkBillingResult = SdkBillingResult.Ok,
        acknowledgeResult: SdkBillingResult = SdkBillingResult.Ok,
    ) {
        val details = SdkProductDetails(product.skuId(), productType, product.priceDisplay)
        val purchase = SdkPurchase(
            productId = product.skuId(),
            orderId = "GPA.$purchaseToken",
            purchaseToken = purchaseToken,
            purchaseTime = purchaseTime,
            purchaseState = state,
            isAcknowledged = false,
            isAutoRenewing = product == BillingProduct.SEASON_PASS,
        )
        adapter.stub {
            onBlocking { connect() } doReturn SdkBillingResult.Ok
            onBlocking { queryProductDetails(any(), any()) } doReturn
                QueryProductDetailsResult.Success(listOf(details))
            onBlocking { launchPurchase(any(), any(), any()) } doReturn
                StartPurchaseResult.Completed(purchase)
            onBlocking { consume(any()) } doReturn consumeResult
            onBlocking { acknowledge(any()) } doReturn acknowledgeResult
        }
    }

    private fun blankProfile(
        adRemoved: Boolean = false,
        seasonPassActive: Boolean = false,
        seasonPassExpiry: Long = 0L,
    ) = PlayerProfile(
        id = 1,
        totalStepsEarned = 0,
        stepBalance = 0,
        gems = 0,
        powerStones = 0,
        cardDust = 0,
        currentTier = 1,
        highestUnlockedTier = 1,
        labSlotCount = 1,
        bestWavePerTier = emptyMap(),
        currentStreak = 0,
        lastLoginDate = "",
        totalGemsEarned = 0,
        totalGemsSpent = 0,
        totalPowerStonesEarned = 0,
        totalPowerStonesSpent = 0,
        totalRoundsPlayed = 0,
        totalEnemiesKilled = 0,
        totalCashEarned = 0,
        adRemoved = adRemoved,
        seasonPassActive = seasonPassActive,
        seasonPassExpiry = seasonPassExpiry,
        freeLabRushUsedToday = "",
        freeCardPackAdUsedToday = "",
        createdAt = 0L,
        lastActiveAt = 0L,
    )
}
