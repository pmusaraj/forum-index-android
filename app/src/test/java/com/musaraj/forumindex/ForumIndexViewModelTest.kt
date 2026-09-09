package com.musaraj.forumindex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.URL
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class ForumIndexViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun coldLaunchFiltersAndUsesDefaultTypedDestinationOrder() = runTest(dispatcher.scheduler) {
        val api = FakeApi(subjects = listOf(subject("sport"), subject("tiny", 4), subject("main"), subject("ai")))
        val vm = viewModel(api)
        vm.launch(); advanceUntilIdle()

        assertEquals(listOf("main-feed", "parent:ai", "parent:sport"), vm.uiState.value.visibleDestinations.map { it.id })
        assertEquals("main-feed", vm.uiState.value.selectedDestination?.id)
        assertEquals(listOf("main-feed", "parent:sport", "parent:main", "parent:ai"), vm.uiState.value.allDestinations.map { it.id })
        assertNotEquals(vm.uiState.value.allDestinations[0].id, vm.uiState.value.allDestinations.first { it is Destination.Subject && it.subject.slug == "main" }.id)
        assertEquals(listOf("sport", "main", "ai"), (vm.uiState.value.taxonomy as TaxonomyState.Loaded).subjects.map { it.slug })
        assertEquals(listOf("main:1", "subject:ai:1"), api.calls)
    }

    @Test fun persistedVisibilityIsOrderedValidatedAndNeverEmpty() = runTest(dispatcher.scheduler) {
        val prefs = MemoryPreferences(visibleSubjectOrder = listOf("parent:sport", "missing"))
        val vm = viewModel(FakeApi(subjects = listOf(subject("ai"), subject("sport"))), prefs)
        vm.launch(); advanceUntilIdle()
        assertEquals(listOf("parent:sport"), vm.uiState.value.visibleDestinations.map { it.id })

        vm.updateVisibleOrder(emptyList())
        assertEquals(listOf("main-feed"), vm.uiState.value.visibleDestinations.map { it.id })
        assertEquals(listOf("main-feed"), prefs.visibleSubjectOrder)
    }

    @Test fun emptySubjectTaxonomyStillLoadsTheAggregateMainFeed() = runTest(dispatcher.scheduler) {
        val vm = viewModel(FakeApi(subjects = emptyList()))
        vm.launch(); advanceUntilIdle()
        assertEquals(listOf("main-feed"), vm.uiState.value.visibleDestinations.map { it.id })
        assertEquals(TaxonomyState.Loaded(emptyList()), vm.uiState.value.taxonomy)
        assertEquals(listOf("main:1"), vmApi(vm)!!.calls)
    }

    @Test fun selectingUsesExactRequestTypeAndCachedRevisit() = runTest(dispatcher.scheduler) {
        val api = FakeApi(subjects = listOf(subject("ai")))
        val vm = viewModel(api)
        vm.launch(); advanceUntilIdle()
        vm.select(vm.uiState.value.allDestinations.first { it.id == "parent:ai" }); advanceUntilIdle()
        vm.select(Destination.Main); advanceUntilIdle()
        assertEquals(listOf("main:1", "subject:ai:1"), api.calls)
    }

    @Test fun staleSelectionResponseCannotReplaceCurrentSelection() = runTest(dispatcher.scheduler) {
        val runner = ControlledRunner()
        val vm = viewModel(FakeApi(subjects = listOf(subject("ai"))), runner = runner)
        vm.launch()
        completeNext(runner) // token load
        completeNext(runner) // taxonomy
        vm.select(vm.uiState.value.allDestinations.first { it.id == "parent:ai" }); runCurrent()
        vm.select(Destination.Main); runCurrent()
        completeNext(runner, feed(topic(99))) // cancelled original main
        completeNext(runner, feed(topic(88))) // cancelled ai
        completeNext(runner, feed(topic(1)))

        assertEquals("main-feed", vm.uiState.value.selectedDestination?.id)
        assertEquals(listOf(1), vm.uiState.value.feeds.getValue("main-feed").rows.map { it.id })
    }

    @Test fun overlappingRefreshKeepsStaleRowsAndLatestResponse() = runTest(dispatcher.scheduler) {
        val runner = ControlledRunner()
        val vm = viewModel(FakeApi(), runner = runner)
        vm.launch()
        completeNext(runner); completeNext(runner); completeNext(runner, feed(topic(1)))

        vm.refreshFeed(); runCurrent()
        assertEquals(listOf(1), (vm.uiState.value.feeds.getValue("main-feed") as FeedState.Refreshing).rows.map { it.id })
        vm.refreshFeed(); runCurrent()
        completeNext(runner, feed(topic(2)))
        completeNext(runner, feed(topic(3)))
        assertEquals(listOf(3), vm.uiState.value.feeds.getValue("main-feed").rows.map { it.id })
    }

    @Test fun cancelledRefreshRestoresStableCache() = runTest(dispatcher.scheduler) {
        val runner = ControlledRunner()
        val vm = viewModel(FakeApi(subjects = listOf(subject("ai"))), runner = runner)
        vm.launch()
        completeNext(runner); completeNext(runner); completeNext(runner, feed(topic(1)))
        completeNext(runner, feed(topic(2))) // next-visible prefetch

        vm.refreshFeed(); runCurrent()
        vm.select(vm.uiState.value.allDestinations.first { it.id == "parent:ai" }); runCurrent()
        assertTrue(vm.uiState.value.feeds.getValue("main-feed") is FeedState.Loaded)
        assertEquals(listOf(1), vm.uiState.value.feeds.getValue("main-feed").rows.map { it.id })
    }

    @Test fun refreshingKnownEmptyFeedIsDistinctFromInitialLoading() = runTest(dispatcher.scheduler) {
        val runner = ControlledRunner()
        val vm = viewModel(FakeApi(), runner = runner)
        vm.launch()
        completeNext(runner); completeNext(runner); completeNext(runner, feed())
        assertTrue(vm.uiState.value.feeds.getValue("main-feed") is FeedState.Empty)

        vm.refreshFeed(); runCurrent()
        val refreshing = vm.uiState.value.feeds.getValue("main-feed") as FeedState.Refreshing
        assertTrue(refreshing.rows.isEmpty())
        completeNext(runner, feed(topic(2)))
        assertEquals(listOf(2), vm.uiState.value.feeds.getValue("main-feed").rows.map { it.id })
    }

    @Test fun paginationDeduplicatesCompositeIdentityAndCapsAtThreePages() = runTest(dispatcher.scheduler) {
        val first = (1..30).map { topic(it, forumId = 1) }
        val second = listOf(topic(1, 1), topic(1, 2)) + (31..58).map { topic(it, 1) }
        val third = (59..88).map { topic(it, 1) }
        val api = FakeApi(mainFeeds = ArrayDeque(listOf(feed(*first.toTypedArray()), feed(*second.toTypedArray()), feed(*third.toTypedArray()))))
        val vm = viewModel(api)
        vm.launch(); advanceUntilIdle(); vm.loadNextPage(); advanceUntilIdle(); vm.loadNextPage(); advanceUntilIdle(); vm.loadNextPage(); advanceUntilIdle()
        val state = vm.uiState.value.feeds.getValue("main-feed") as FeedState.Loaded
        assertEquals(3, state.page)
        assertFalse(state.hasMore)
        assertEquals(89, state.rows.size)
        assertEquals(2, state.rows.count { it.id == 1 })
        assertEquals(listOf("main:1", "main:2", "main:3"), api.calls)
    }

    @Test fun everyFetchedPageIsCappedAtThirtyBeforeStateAndDedup() = runTest(dispatcher.scheduler) {
        val api = FakeApi(mainFeeds = ArrayDeque(listOf(
            feed(*(1..35).map { topic(it) }.toTypedArray()),
            feed(*(31..65).map { topic(it) }.toTypedArray()),
        )))
        val vm = viewModel(api)
        vm.launch(); advanceUntilIdle()
        assertEquals(30, vm.uiState.value.feeds.getValue("main-feed").rows.size)
        vm.loadNextPage(); advanceUntilIdle()
        val state = vm.uiState.value.feeds.getValue("main-feed") as FeedState.Loaded
        assertEquals(60, state.rows.size)
        assertFalse(state.rows.any { it.id > 60 })
    }

    @Test fun inactiveDestinationCannotRefreshOrPaginateTheSelectedFeed() = runTest(dispatcher.scheduler) {
        val first = (1..30).map { topic(it) }
        val api = FakeApi(
            subjects = listOf(subject("ai")),
            mainFeeds = ArrayDeque(listOf(feed(*first.toTypedArray()), feed(topic(99)))),
        )
        val vm = viewModel(api)
        vm.launch(); advanceUntilIdle()
        val ai = vm.uiState.value.allDestinations.first { it.id == "parent:ai" }
        val before = api.calls.toList()

        vm.refreshFeed(ai)
        vm.loadNextPage(ai)
        advanceUntilIdle()
        assertEquals(before, api.calls)

        vm.loadNextPage(Destination.Main)
        advanceUntilIdle()
        assertEquals(before + "main:2", api.calls)
    }

    @Test fun duplicatePaginationRequestSharesTheSingleInFlightPage() = runTest(dispatcher.scheduler) {
        val runner = ControlledRunner()
        val api = FakeApi(mainFeeds = ArrayDeque(listOf(
            feed(*(1..30).map { topic(it) }.toTypedArray()),
            feed(*(31..60).map { topic(it) }.toTypedArray()),
        )))
        val vm = viewModel(api, runner = runner)
        vm.launch()
        completeNext(runner); completeNext(runner); completeNext(runner)

        vm.loadNextPage(); vm.loadNextPage(); runCurrent()
        assertEquals(1, runner.pendingCount)
        completeNext(runner)
        assertEquals(listOf("main:1", "main:2"), api.calls)
    }

    @Test fun cancelledPaginationCompletionCannotClearItsReplacement() = runTest(dispatcher.scheduler) {
        val runner = ControlledRunner()
        val vm = viewModel(FakeApi(), runner = runner)
        vm.launch()
        completeNext(runner); completeNext(runner)
        completeNext(runner, feed(*(1..30).map { topic(it) }.toTypedArray()))

        vm.loadNextPage(); runCurrent() // old page 2
        vm.refreshFeed(); runCurrent() // cancels old and starts new page 1
        runner.completeLast(feed(*(101..130).map { topic(it) }.toTypedArray()))
        runCurrent()
        vm.loadNextPage(); runCurrent() // replacement page 2
        assertEquals(2, runner.pendingCount)
        completeNext(runner, feed(*(31..60).map { topic(it) }.toTypedArray())) // old completion
        vm.loadNextPage(); runCurrent()
        assertEquals(1, runner.pendingCount)
        completeNext(runner, feed(*(131..160).map { topic(it) }.toTypedArray()))
    }

    @Test fun prefetchUsesNextVisibleOnlySkipsCacheAndCancelsWhenHidden() = runTest(dispatcher.scheduler) {
        val runner = ControlledRunner()
        val prefs = MemoryPreferences(listOf("main-feed", "parent:ai", "parent:sport"))
        val vm = viewModel(FakeApi(subjects = listOf(subject("ai"), subject("technology"), subject("sport"))), prefs, runner = runner)
        vm.launch()
        completeNext(runner); completeNext(runner); completeNext(runner, feed(topic(1)))
        assertEquals(1, runner.pendingCount)
        assertFalse("subject:technology:1" in (vmApi(vm)?.calls ?: emptyList<String>()))

        vm.updateVisibleOrder(listOf("main-feed", "parent:sport")); runCurrent()
        completeNext(runner, feed(topic(7))) // obsolete ai prefetch
        assertTrue(vm.uiState.value.feeds.getValue("parent:ai") is FeedState.Initial)
        assertEquals(1, runner.pendingCount)
        completeNext(runner, feed(topic(8)))
        assertEquals(listOf(8), vm.uiState.value.feeds.getValue("parent:sport").rows.map { it.id })
        vm.select(vm.uiState.value.allDestinations.first { it.id == "parent:sport" }); runCurrent()
        vm.select(Destination.Main); runCurrent()
        assertEquals(0, runner.pendingCount)
    }

    @Test fun reorderedVisibleSubjectsFenceAnObsoleteStillVisiblePrefetch() = runTest(dispatcher.scheduler) {
        val runner = ControlledRunner()
        val prefs = MemoryPreferences(listOf("main-feed", "parent:ai", "parent:sport"))
        val vm = viewModel(FakeApi(subjects = listOf(subject("ai"), subject("sport"))), prefs, runner = runner)
        vm.launch()
        completeNext(runner); completeNext(runner); completeNext(runner, feed(topic(1)))
        assertEquals(1, runner.pendingCount) // AI

        vm.updateVisibleOrder(listOf("main-feed", "parent:sport", "parent:ai")); runCurrent()
        assertEquals(2, runner.pendingCount) // cancelled AI plus current Sport
        completeNext(runner, feed(topic(7)))
        assertTrue(vm.uiState.value.feeds.getValue("parent:ai") is FeedState.Initial)
        completeNext(runner, feed(topic(8)))
        assertEquals(listOf(8), vm.uiState.value.feeds.getValue("parent:sport").rows.map { it.id })
    }

    @Test fun validatesUnicodeLabelsAndPersistsEnrollmentOnlyAfterSuccess() = runTest(dispatcher.scheduler) {
        val failedStore = MemoryTokenStore()
        val failedApi = FakeApi(createFailure = true)
        val failed = viewModel(failedApi, tokens = failedStore)
        advanceUntilIdle()
        assertFalse(failed.enroll(" ", "Phone")); assertFalse(failed.enroll("x".repeat(101), "Phone"))
        assertFalse(failed.enroll("Name", "😀".repeat(101)))
        assertTrue(failed.enroll(" Name ", " 📱 ")); advanceUntilIdle()
        assertNull(failedStore.value)
        assertEquals("Request failed. Please try again.", failed.uiState.value.contribution.error)

        val store = MemoryTokenStore()
        val success = viewModel(FakeApi(), tokens = store)
        advanceUntilIdle()
        assertTrue(success.enroll(" Name ", " 📱 ")); advanceUntilIdle()
        assertEquals(enrollment(), store.value)
    }

    @Test fun tokenLoadSaveAndDeleteAllUseBlockingRunner() = runTest(dispatcher.scheduler) {
        val runner = ControlledRunner()
        val store = MemoryTokenStore(enrollment())
        val vm = viewModel(FakeApi(), tokens = store, runner = runner)
        runCurrent()
        assertEquals(0, store.loads)
        completeNext(runner)
        assertEquals(1, store.loads)

        vm.updateDevice("Tablet"); runCurrent()
        completeNext(runner) // API
        assertEquals(0, store.saves)
        completeNext(runner) // Keystore save
        assertEquals(1, store.saves)
        vm.optOut(); runCurrent()
        completeNext(runner) // API
        assertEquals(0, store.deletes)
        completeNext(runner) // Keystore delete
        assertEquals(1, store.deletes)
    }

    @Test fun expiredAccessClearsEnrollmentAndStarsSurviveOptOut() = runTest(dispatcher.scheduler) {
        val stored = enrollment()
        val store = MemoryTokenStore(stored)
        val prefs = MemoryPreferences(stars = listOf(star(7)))
        val api = FakeApi(expireFetch = true, expireDelete = true)
        val vm = viewModel(api, prefs, store)
        advanceUntilIdle(); vm.refreshEnrollment(); advanceUntilIdle()
        assertNull(store.value)
        store.value = stored; vm.reloadEnrollment(); advanceUntilIdle()
        vm.optOut(); advanceUntilIdle()
        assertNull(store.value)
        assertEquals(listOf(star(7)), vm.stars.value)
    }

    @Test fun staleContributionResultCannotOverwriteReloadedEnrollment() = runTest(dispatcher.scheduler) {
        val old = enrollment("old")
        val newer = enrollment("new").copy(installation = enrollment("new").installation.copy(deviceName = "New"))
        val runner = ControlledRunner()
        val store = MemoryTokenStore(old)
        val vm = viewModel(FakeApi(), tokens = store, runner = runner)
        completeNext(runner)

        vm.refreshEnrollment(); runCurrent()
        store.value = newer
        vm.reloadEnrollment(); runCurrent()
        completeNext(runner, enrollment("old").installation.copy(deviceName = "Stale"))
        completeNext(runner)
        assertEquals(newer, vm.uiState.value.contribution.enrollment)
        assertEquals(0, store.saves)
    }

    @Test fun staleExpiredResponseWithSameTokenCannotClearReloadedEnrollment() = runTest(dispatcher.scheduler) {
        val stored = enrollment("same")
        val runner = ControlledRunner()
        val store = MemoryTokenStore(stored)
        val vm = viewModel(FakeApi(expireFetch = true), tokens = store, runner = runner)
        completeNext(runner)

        vm.refreshEnrollment(); runCurrent()
        vm.reloadEnrollment(); runCurrent()
        runner.completeLast() // newer reload of the same token
        runCurrent()
        completeNext(runner) // stale fetch now returns expired
        assertEquals(stored, vm.uiState.value.contribution.enrollment)
        assertEquals(stored, store.value)
        assertEquals(0, store.deletes)
    }

    @Test fun contributionLoadingRejectsDuplicateSubmissions() = runTest(dispatcher.scheduler) {
        val runner = ControlledRunner()
        val api = FakeApi()
        val vm = viewModel(api, tokens = MemoryTokenStore(enrollment()), runner = runner)
        completeNext(runner)

        assertTrue(vm.updateDevice("One")); runCurrent()
        assertFalse(vm.updateDevice("Two"))
        assertFalse(vm.enroll("Name", "Phone"))
        vm.refreshEnrollment(); vm.optOut(); runCurrent()
        assertEquals(1, runner.pendingCount)
        completeNext(runner); completeNext(runner)
        assertEquals(listOf("update:One"), api.calls)
    }

    @Test fun reportsNeedEnrollmentAllowOnlyKnownKindsAndSerializePerTopic() = runTest(dispatcher.scheduler) {
        val signedOut = viewModel(FakeApi(), tokens = MemoryTokenStore())
        advanceUntilIdle()
        signedOut.report(4, "low_quality", true); advanceUntilIdle()
        assertEquals(ReportState.NeedsEnrollment, signedOut.uiState.value.contribution.reports[4])
        assertFalse(signedOut.report(4, "other", true))

        val runner = ControlledRunner()
        val api = FakeApi()
        val enrolled = viewModel(api, tokens = MemoryTokenStore(enrollment()), runner = runner)
        completeNext(runner)
        assertTrue(enrolled.report(9, "inappropriate", true)); assertTrue(enrolled.report(9, "wrong_subject", true)); runCurrent()
        assertEquals(1, runner.pendingCount)
        completeNext(runner); assertEquals(1, runner.pendingCount)
        completeNext(runner)
        assertEquals(listOf("action:9:inappropriate:true", "action:9:wrong_subject:true"), api.calls)
    }

    @Test fun queuedSameTopicReportStopsWhenTheFirstExpiresEnrollment() = runTest(dispatcher.scheduler) {
        val runner = ControlledRunner()
        val api = FakeApi(expireAction = true)
        val vm = viewModel(api, tokens = MemoryTokenStore(enrollment()), runner = runner)
        completeNext(runner)

        vm.report(9, "inappropriate", true)
        vm.report(9, "wrong_subject", true)
        runCurrent()
        assertEquals(1, runner.pendingCount)
        completeNext(runner) // first report expires
        assertEquals(1, runner.pendingCount) // secure token deletion
        completeNext(runner)
        assertEquals(0, runner.pendingCount)
        assertEquals(ReportState.NeedsEnrollment, vm.uiState.value.contribution.reports[9])
        assertEquals(listOf("action:9:inappropriate:true"), api.calls)
    }

    @Test fun sameTopicIdInDifferentForumsDoesNotCollideInStars() = runTest(dispatcher.scheduler) {
        val prefs = MemoryPreferences()
        val vm = viewModel(FakeApi(), prefs)
        advanceUntilIdle()
        vm.toggleStar(topic(2, 1)); vm.toggleStar(topic(2, 2))
        assertEquals(listOf(1, 2), vm.stars.value.map { it.forumId })
        vm.toggleStar(topic(2, 1))
        assertEquals(listOf(2), vm.stars.value.map { it.forumId })
    }

    @Test fun removingAndOpeningAStarPersistAndReportWhileEnrolled() = runTest(dispatcher.scheduler) {
        val saved = star(4, forumId = 7)
        val prefs = MemoryPreferences(stars = listOf(saved))
        val api = FakeApi()
        val vm = viewModel(api, prefs, MemoryTokenStore(enrollment()))
        advanceUntilIdle()

        vm.markOpened(saved)
        vm.removeStar(saved)
        advanceUntilIdle()
        assertTrue(prefs.stars.isEmpty())
        assertTrue(prefs.isOpened(7, 4))
        assertEquals(
            listOf("action:4:read:true", "action:4:starred:false"),
            api.calls,
        )
    }

    @Test fun topicWithoutHttpsUrlCannotBecomeAStar() = runTest(dispatcher.scheduler) {
        val vm = viewModel(FakeApi())
        advanceUntilIdle()
        vm.toggleStar(topic(3).copy(url = null))
        assertTrue(vm.stars.value.isEmpty())
    }

    @Test fun localReadAndStarsAreBestEffortAndSurviveOptOut() = runTest(dispatcher.scheduler) {
        val api = FakeApi(actionFailure = true)
        val prefs = MemoryPreferences()
        val vm = viewModel(api, prefs, MemoryTokenStore(enrollment()))
        advanceUntilIdle()
        val topic = topic(2)
        vm.markOpened(topic); vm.toggleStar(topic); advanceUntilIdle()
        assertTrue(vm.isOpened(topic)); assertEquals(listOf(star(2)), vm.stars.value)
        vm.optOut(); advanceUntilIdle()
        assertEquals(listOf(star(2)), vm.stars.value)
    }

    private suspend fun TestScope.completeNext(runner: ControlledRunner, value: Any? = ControlledRunner.RUN_CALL) {
        runCurrent()
        assertTrue("expected a pending blocking call", runner.pendingCount > 0)
        runner.completeNext(value)
        runCurrent()
    }

    private fun viewModel(
        api: FakeApi,
        prefs: MemoryPreferences = MemoryPreferences(),
        tokens: MemoryTokenStore = MemoryTokenStore(),
        runner: BlockingCallRunner = DispatcherCallRunner(dispatcher),
    ) = ForumIndexViewModel(api, prefs, tokens, runner).also { viewModelApis[it] = api }

    private val viewModelApis = java.util.WeakHashMap<ForumIndexViewModel, FakeApi>()
    private fun vmApi(vm: ForumIndexViewModel) = viewModelApis[vm]

    private class MemoryPreferences(
        override var visibleSubjectOrder: List<String> = emptyList(),
        override var stars: List<StarredTopic> = emptyList(),
        override var openedTopicIds: Set<String> = emptySet(),
        override var deviceNameOverride: String? = null,
    ) : PreferencesStore {
        override fun markOpened(forumId: Int, topicId: Int) { openedTopicIds += "$forumId:$topicId" }
        override fun isOpened(forumId: Int, topicId: Int) = "$forumId:$topicId" in openedTopicIds
    }

    private class MemoryTokenStore(var value: Enrollment? = null) : EnrollmentStore {
        var loads = 0
        var saves = 0
        var deletes = 0
        override fun save(enrollment: Enrollment) { saves++; value = enrollment }
        override fun load(): Enrollment? { loads++; return value }
        override fun delete() { deletes++; value = null }
    }

    private class ControlledRunner : BlockingCallRunner {
        private val pending = ArrayDeque<Pair<() -> Any?, Continuation<Any?>>>()
        val pendingCount get() = pending.size
        override suspend fun <T> run(call: () -> T): T = suspendCoroutine { continuation ->
            @Suppress("UNCHECKED_CAST")
            pending += (call as () -> Any?) to (continuation as Continuation<Any?>)
        }
        fun completeNext(value: Any? = RUN_CALL) {
            val (call, continuation) = pending.removeFirst()
            continuation.resumeWith(runCatching { if (value === RUN_CALL) call() else value })
        }
        fun completeLast(value: Any? = RUN_CALL) {
            val (call, continuation) = pending.removeLast()
            continuation.resumeWith(runCatching { if (value === RUN_CALL) call() else value })
        }
        companion object { val RUN_CALL = Any() }
    }

    private class FakeApi(
        val subjects: List<Subject> = emptyList(),
        val mainFeeds: ArrayDeque<FeedEnvelope> = ArrayDeque(listOf(feed(topic(1)))),
        val createFailure: Boolean = false,
        val expireFetch: Boolean = false,
        val expireDelete: Boolean = false,
        val actionFailure: Boolean = false,
        val expireAction: Boolean = false,
    ) : ForumIndexApi {
        val calls = mutableListOf<String>()
        override fun fetchTaxonomy() = NavigationEnvelope("v2", "5d", subjects)
        override fun fetchMainTopics(page: Int): FeedEnvelope { calls += "main:$page"; return mainFeeds.removeFirstOrNull() ?: feed() }
        override fun fetchSubjectTopics(slug: String, page: Int): FeedEnvelope { calls += "subject:$slug:$page"; return feed(topic(slug.hashCode())) }
        override fun createInstallation(displayName: String, deviceName: String): Enrollment { calls += "create:$displayName:$deviceName"; if (createFailure) throw ForumIndexApiException.Transport(); return enrollment() }
        override fun fetchInstallation(token: String): Installation { calls += "fetch:$token"; if (expireFetch) throw ForumIndexApiException.ExpiredAccess(); return enrollment(token).installation }
        override fun updateInstallation(deviceName: String, token: String): Installation { calls += "update:$deviceName"; return enrollment(token).installation.copy(deviceName = deviceName) }
        override fun deleteInstallation(token: String) { calls += "delete:$token"; if (expireDelete) throw ForumIndexApiException.ExpiredAccess() }
        override fun setTopicAction(topicId: Int, kind: String, active: Boolean, token: String) {
            calls += "action:$topicId:$kind:$active"
            if (expireAction) throw ForumIndexApiException.ExpiredAccess()
            if (actionFailure) throw ForumIndexApiException.Transport()
        }
    }

    companion object {
        private fun subject(slug: String, count: Int = 5) = Subject(slug.hashCode(), slug, slug, "", 0, false, count)
        private fun topic(id: Int, forumId: Int = 1) = Topic(id, "Topic $id", URL("https://example.com/f/$forumId/t/$id"), null, ForumSummary(forumId, "Forum $forumId", "forum-$forumId", null, null), null, 0)
        private fun feed(vararg topics: Topic) = FeedEnvelope(topics.size, topics.toList())
        private fun enrollment(token: String = "token") = Enrollment(token, Installation("id", "Name", "Phone", false, true))
        private fun star(id: Int, forumId: Int = 1) = StarredTopic("Topic $id", "https://example.com/f/$forumId/t/$id", "Forum $forumId", id, forumId)
    }
}
