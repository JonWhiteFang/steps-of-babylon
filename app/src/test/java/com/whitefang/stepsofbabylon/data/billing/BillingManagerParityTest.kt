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
import com.whitefang.stepsofbabylon.data.local.PlayerProfileEntity
import com.whitefang.stepsofbabylon.data.repository.PlayerRepositoryImpl
import com.whitefang.stepsofbabylon.domain.model.BillingProduct
import com.whitefang.stepsofbabylon.domain.model.PurchaseResult
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Shape-level parity check between [StubBillingManager] and [BillingManagerImpl] on the
 * golden path for each of the 3 [BillingProduct] shapes — consumable (Gem pack),
 * non-consumable (Ad Removal), and subscription (Season Pass). The C.5 PR 2 flag swap
 * changes which implementation Hilt binds at runtime based on `BuildConfig.USE_REAL_BILLING`,
 * so we need evidence that the user-visible wallet effect is equivalent for both.
 *
 * **What "parity" means here.**
 *
 * - Both implementations return [PurchaseResult.Success] on a successful flow.
 * - Both mutate the same [PlayerProfileEntity] fields (`gems`/`totalGemsEarned`,
 *   `adRemoved`, `seasonPassActive`/`seasonPassExpiry`) to equivalent values.
 * - The [BillingManagerImpl] side additionally persists a
 *   [com.whitefang.stepsofbabylon.data.local.BillingReceiptEntity] row — a stub parity
 *   test cannot assert that, since the stub has no receipt concept. We assert it
 *   separately on the real side for completeness.
 *
 * **What "parity" does NOT mean.**
 *
 * - We do NOT attempt to run `RealBillingClientAdapter` against Play Services. That is
 *   device-only. The real side mocks [BillingClientAdapter] and asserts the post-state
 *   that a happy-path adapter response produces.
 * - We do NOT compare timings (the stub sleeps 500ms; the real path is mock-driven).
 * - Subscription expiry timestamps cannot be byte-equal (stub uses
 *   `System.currentTimeMillis()` at call-time; real uses the mocked `purchaseTime`).
 *   We assert that both land within a 1-minute window of one another, which is
 *   exhaustive for "30 days from now-ish".
 *
 * C.5 PR 2 / ADR-0005.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BillingManagerParityTest {

    private lateinit var stubDb: AppDatabase
    private lateinit var realDb: AppDatabase

    private lateinit var stub: StubBillingManager
    private lateinit var real: BillingManagerImpl

    private lateinit var adapter: BillingClientAdapter

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Two independent DBs so the two implementations cannot observe each other's writes.
        stubDb = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        realDb = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            stubDb.playerProfileDao().upsert(PlayerProfileEntity(id = 1))
            realDb.playerProfileDao().upsert(PlayerProfileEntity(id = 1))
        }

        stub = StubBillingManager(
            playerRepository = PlayerRepositoryImpl(stubDb.playerProfileDao()),
        )

        adapter = mock()
        val activityProvider = ActivityProvider().apply { set(mock<Activity>()) }
        real = BillingManagerImpl(
            adapter = adapter,
            receiptDao = realDb.billingReceiptDao(),
            playerProfileDao = realDb.playerProfileDao(),
            playerRepository = PlayerRepositoryImpl(realDb.playerProfileDao()),
            activityProvider = activityProvider,
            context = ctx,
        )
    }

    @After
    fun tearDown() {
        stubDb.close()
        realDb.close()
    }

    // --- consumable: Gem pack --------------------------------------------------------------

    @Test
    fun `GEM_PACK_SMALL parity — both credit 50 gems and return Success`() = runTest {
        stubAdapterHappyPath(
            product = BillingProduct.GEM_PACK_SMALL,
            productType = SdkProductType.INAPP,
            purchaseToken = "parity_gems_small",
        )

        val stubResult = stub.purchase(BillingProduct.GEM_PACK_SMALL)
        val realResult = real.purchase(BillingProduct.GEM_PACK_SMALL)

        assertEquals(PurchaseResult.Success, stubResult)
        assertEquals(PurchaseResult.Success, realResult)

        val stubProfile = stubDb.playerProfileDao().get().firstOrNull()!!
        val realProfile = realDb.playerProfileDao().get().firstOrNull()!!

        assertEquals("gems credited equivalently", stubProfile.gems, realProfile.gems)
        assertEquals(
            "totalGemsEarned credited equivalently",
            stubProfile.totalGemsEarned,
            realProfile.totalGemsEarned,
        )
        assertEquals("both credit exactly 50 gems", 50L, stubProfile.gems)
        assertEquals(50L, realProfile.totalGemsEarned)
    }

    // --- non-consumable: Ad Removal --------------------------------------------------------

    @Test
    fun `AD_REMOVAL parity — both set adRemoved flag and return Success`() = runTest {
        stubAdapterHappyPath(
            product = BillingProduct.AD_REMOVAL,
            productType = SdkProductType.INAPP,
            purchaseToken = "parity_ad_removal",
        )

        val stubResult = stub.purchase(BillingProduct.AD_REMOVAL)
        val realResult = real.purchase(BillingProduct.AD_REMOVAL)

        assertEquals(PurchaseResult.Success, stubResult)
        assertEquals(PurchaseResult.Success, realResult)

        val stubProfile = stubDb.playerProfileDao().get().firstOrNull()!!
        val realProfile = realDb.playerProfileDao().get().firstOrNull()!!

        assertEquals(stubProfile.adRemoved, realProfile.adRemoved)
        assertTrue("both flip adRemoved to true", stubProfile.adRemoved)
        // adRemoved is mutually exclusive with a gem credit on this path — neither side
        // should have mutated the gem wallet.
        assertEquals(0L, stubProfile.gems)
        assertEquals(0L, realProfile.gems)
    }

    // --- subscription: Season Pass ---------------------------------------------------------

    @Test
    fun `SEASON_PASS parity — both activate pass with ~30-day expiry and return Success`() = runTest {
        val beforePurchase = System.currentTimeMillis()
        stubAdapterHappyPath(
            product = BillingProduct.SEASON_PASS,
            productType = SdkProductType.SUBS,
            purchaseToken = "parity_season_pass",
            purchaseTime = beforePurchase,
        )

        val stubResult = stub.purchase(BillingProduct.SEASON_PASS)
        val realResult = real.purchase(BillingProduct.SEASON_PASS)

        assertEquals(PurchaseResult.Success, stubResult)
        assertEquals(PurchaseResult.Success, realResult)

        val stubProfile = stubDb.playerProfileDao().get().firstOrNull()!!
        val realProfile = realDb.playerProfileDao().get().firstOrNull()!!

        assertEquals(stubProfile.seasonPassActive, realProfile.seasonPassActive)
        assertTrue("both activate the pass", stubProfile.seasonPassActive)

        // Expiry timestamps cannot be byte-equal (stub uses now-at-call-time, real uses the
        // mocked purchaseTime) but they are computed from the same 30-day window within
        // millisecond-scale test execution — a 60s tolerance is exhaustive.
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        val expectedExpiry = beforePurchase + thirtyDaysMs
        val tolerance = 60_000L
        assertTrue(
            "stub expiry within 1 min of expected: diff=${abs(stubProfile.seasonPassExpiry - expectedExpiry)}",
            abs(stubProfile.seasonPassExpiry - expectedExpiry) < tolerance,
        )
        assertTrue(
            "real expiry within 1 min of expected: diff=${abs(realProfile.seasonPassExpiry - expectedExpiry)}",
            abs(realProfile.seasonPassExpiry - expectedExpiry) < tolerance,
        )
        assertTrue(
            "stub and real expiries agree within tolerance",
            abs(stubProfile.seasonPassExpiry - realProfile.seasonPassExpiry) < tolerance,
        )
    }

    // --- helpers ---------------------------------------------------------------------------

    /**
     * Wires the mocked [BillingClientAdapter] for a happy-path flow: connect OK, product
     * details populated, launchPurchase returns a PURCHASED [SdkPurchase], and
     * consume/acknowledge both return OK.
     */
    private fun stubAdapterHappyPath(
        product: BillingProduct,
        productType: SdkProductType,
        purchaseToken: String,
        purchaseTime: Long = 0L,
    ) {
        val details = SdkProductDetails(product.name, productType, product.priceDisplay)
        val purchase = SdkPurchase(
            productId = product.name,
            orderId = "GPA.$purchaseToken",
            purchaseToken = purchaseToken,
            purchaseTime = purchaseTime,
            purchaseState = SdkPurchaseState.PURCHASED,
            isAcknowledged = false,
            isAutoRenewing = product == BillingProduct.SEASON_PASS,
        )
        adapter.stub {
            onBlocking { connect() } doReturn SdkBillingResult.Ok
            onBlocking { queryProductDetails(any(), any()) } doReturn
                QueryProductDetailsResult.Success(listOf(details))
            onBlocking { launchPurchase(any(), any(), any()) } doReturn
                StartPurchaseResult.Completed(purchase)
            onBlocking { consume(any()) } doReturn SdkBillingResult.Ok
            onBlocking { acknowledge(any()) } doReturn SdkBillingResult.Ok
            // Default for reconcile path (not exercised by purchase() but harmless).
            onBlocking { queryPurchases(any()) } doReturn QueryPurchasesResult.Success(emptyList())
        }
    }
}
