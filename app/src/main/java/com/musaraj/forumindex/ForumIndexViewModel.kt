package com.musaraj.forumindex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface TaxonomyState {
    data object Loading : TaxonomyState
    data class Loaded(val subjects: List<Subject>) : TaxonomyState
    data object Empty : TaxonomyState
    data class Error(val message: String) : TaxonomyState
}

sealed interface FeedState {
    val rows: List<Topic>
    val page: Int
    val hasMore: Boolean

    data object Initial : FeedState {
        override val rows = emptyList<Topic>()
        override val page = 0
        override val hasMore = false
    }
    data class Loaded(override val rows: List<Topic>, override val page: Int, override val hasMore: Boolean) : FeedState
    data class Refreshing(override val rows: List<Topic>, override val page: Int, override val hasMore: Boolean) : FeedState
    data object Empty : FeedState {
        override val rows = emptyList<Topic>()
        override val page = 1
        override val hasMore = false
    }
    data class Failed(
        override val rows: List<Topic>,
        val message: String,
        override val page: Int = if (rows.isEmpty()) 0 else 1,
        override val hasMore: Boolean = false,
    ) : FeedState
}

sealed interface ReportState {
    data object Running : ReportState
    data object Succeeded : ReportState
    data object NeedsEnrollment : ReportState
    data class Failed(val message: String) : ReportState
}

data class ContributionState(
    val enrollment: Enrollment? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val reports: Map<Int, ReportState> = emptyMap(),
)

data class UiState(
    val taxonomy: TaxonomyState = TaxonomyState.Loading,
    val allDestinations: List<Destination> = emptyList(),
    val visibleDestinations: List<Destination> = emptyList(),
    val selectedDestination: Destination? = null,
    val feeds: Map<String, FeedState> = emptyMap(),
    val contribution: ContributionState = ContributionState(),
)

interface BlockingCallRunner {
    suspend fun <T> run(call: () -> T): T
}

class DispatcherCallRunner(private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : BlockingCallRunner {
    override suspend fun <T> run(call: () -> T): T = withContext(dispatcher) { call() }
}

class ForumIndexViewModel(
    private val api: ForumIndexApi,
    private val preferences: PreferencesStore,
    private val tokenStore: EnrollmentStore,
    private val calls: BlockingCallRunner = DispatcherCallRunner(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = mutableUiState.asStateFlow()
    private val mutableStars = MutableStateFlow(preferences.stars)
    val stars: StateFlow<List<StarredTopic>> = mutableStars.asStateFlow()

    private var launched = false
    private var taxonomyGeneration = 0
    private var feedGeneration = 0
    private var prefetchGeneration = 0
    private var paginationGeneration = 0
    private var contributionGeneration = 0
    private var feedJob: Job? = null
    private var feedJobDestination: String? = null
    private var prefetchJob: Job? = null
    private var paginationJob: Job? = null
    private val reportLocks = mutableMapOf<Int, Mutex>()

    init { reloadEnrollment() }

    fun launch() {
        if (launched) return
        launched = true
        loadTaxonomy()
    }

    fun refreshTaxonomy() = loadTaxonomy()

    private fun loadTaxonomy() {
        val generation = ++taxonomyGeneration
        mutableUiState.value = mutableUiState.value.copy(taxonomy = TaxonomyState.Loading)
        viewModelScope.launch {
            try {
                val envelope = calls.run(api::fetchTaxonomy)
                if (generation != taxonomyGeneration) return@launch
                applyTaxonomy(envelope.subjects.filter { it.topicCount >= MIN_TOPIC_COUNT })
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Exception) {
                if (generation == taxonomyGeneration) {
                    mutableUiState.value = mutableUiState.value.copy(taxonomy = TaxonomyState.Error(error.safeMessage()))
                }
            }
        }
    }

    private fun applyTaxonomy(subjects: List<Subject>) {
        val destinations = listOf(Destination.Main) + subjects.map(Destination::Subject)
        val byId = destinations.associateBy(Destination::id)
        val stored = preferences.visibleSubjectOrder.distinct().mapNotNull(byId::get)
        val visible = (if (preferences.visibleSubjectOrder.isEmpty()) {
            DEFAULT_SLUGS.mapNotNull { slug -> if (slug == "main") Destination.Main else byId["parent:$slug"] }
        } else stored).ifEmpty { listOf(Destination.Main) }
        val selected = mutableUiState.value.selectedDestination?.id?.let(byId::get)?.takeIf { candidate -> visible.any { it.id == candidate.id } }
            ?: visible.first()
        val feeds = destinations.associate { it.id to (mutableUiState.value.feeds[it.id] ?: FeedState.Initial) }
        mutableUiState.value = mutableUiState.value.copy(
            taxonomy = TaxonomyState.Loaded(subjects),
            allDestinations = destinations,
            visibleDestinations = visible,
            selectedDestination = selected,
            feeds = feeds,
        )
        if (feeds.getValue(selected.id) !is FeedState.Loaded && feeds.getValue(selected.id) !is FeedState.Empty) select(selected, force = true)
        else prefetchNext()
    }

    fun updateVisibleOrder(ids: List<String>) {
        val byId = mutableUiState.value.allDestinations.associateBy(Destination::id)
        val visible = ids.distinct().mapNotNull(byId::get).ifEmpty { listOf(byId[Destination.Main.id] ?: return) }
        preferences.visibleSubjectOrder = visible.map(Destination::id)
        val current = mutableUiState.value.selectedDestination
        mutableUiState.value = mutableUiState.value.copy(visibleDestinations = visible)
        if (current == null || visible.none { it.id == current.id }) select(visible.first()) else prefetchNext()
    }

    fun select(destination: Destination) = select(destination, force = false)

    private fun select(destination: Destination, force: Boolean) {
        val actual = mutableUiState.value.allDestinations.firstOrNull { it.id == destination.id } ?: return
        cancelFeed()
        prefetchJob?.cancel()
        prefetchJob = null
        mutableUiState.value = mutableUiState.value.copy(selectedDestination = actual)
        val cached = mutableUiState.value.feeds[actual.id] ?: FeedState.Initial
        if (!force && (cached is FeedState.Loaded || cached is FeedState.Empty)) {
            prefetchNext()
            return
        }
        loadFirstPage(actual, cached.rows)
    }

    fun refreshFeed() {
        val destination = mutableUiState.value.selectedDestination ?: return
        refreshFeed(destination)
    }

    fun refreshFeed(destination: Destination) {
        if (mutableUiState.value.selectedDestination?.id != destination.id) return
        val current = mutableUiState.value.feeds[destination.id] ?: FeedState.Initial
        cancelFeed()
        prefetchJob?.cancel()
        prefetchJob = null
        loadFirstPage(destination, current.rows, refreshing = true)
    }

    fun retry() = refreshFeed()
    fun retry(destination: Destination) = refreshFeed(destination)

    private fun loadFirstPage(destination: Destination, staleRows: List<Topic>, refreshing: Boolean = false) {
        val generation = ++feedGeneration
        val previous = stableState(mutableUiState.value.feeds[destination.id] ?: FeedState.Initial)
        feedJobDestination = destination.id
        setFeed(
            destination.id,
            if (refreshing || staleRows.isNotEmpty()) FeedState.Refreshing(staleRows, previous.page, previous.hasMore)
            else FeedState.Initial,
        )
        feedJob = viewModelScope.launch {
            try {
                val response = calls.run { fetch(destination, 1) }.capped()
                if (generation != feedGeneration || mutableUiState.value.selectedDestination?.id != destination.id) return@launch
                setFeed(destination.id, response.toState(1))
                feedJob = null
                feedJobDestination = null
                prefetchNext()
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Exception) {
                if (generation == feedGeneration && mutableUiState.value.selectedDestination?.id == destination.id) {
                    setFeed(destination.id, FeedState.Failed(staleRows, error.safeMessage(), previous.page, previous.hasMore))
                    feedJob = null
                    feedJobDestination = null
                }
            }
        }
    }

    fun loadNextPage() {
        val destination = mutableUiState.value.selectedDestination ?: return
        loadNextPage(destination)
    }

    fun loadNextPage(destination: Destination) {
        if (mutableUiState.value.selectedDestination?.id != destination.id || paginationJob?.isActive == true) return
        val current = mutableUiState.value.feeds[destination.id] as? FeedState.Loaded ?: return
        if (!current.hasMore || current.page >= MAX_PAGE) return
        val generation = feedGeneration
        val pagingGeneration = ++paginationGeneration
        paginationJob = viewModelScope.launch {
            try {
                val nextPage = current.page + 1
                val response = calls.run { fetch(destination, nextPage) }.capped()
                if (generation != feedGeneration || mutableUiState.value.selectedDestination?.id != destination.id) return@launch
                val rows = (current.rows + response.results).distinctBy { it.forum.id to it.id }
                setFeed(destination.id, FeedState.Loaded(rows, nextPage, nextPage < MAX_PAGE && response.results.size == PAGE_SIZE))
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Exception) {
                if (generation == feedGeneration && mutableUiState.value.selectedDestination?.id == destination.id) {
                    setFeed(destination.id, FeedState.Failed(current.rows, error.safeMessage(), current.page, current.hasMore))
                }
            } finally {
                if (pagingGeneration == paginationGeneration) paginationJob = null
            }
        }
    }

    private fun cancelFeed() {
        feedJob?.cancel()
        feedJobDestination?.let { id ->
            val state = mutableUiState.value.feeds[id]
            if (state is FeedState.Refreshing) setFeed(id, stableState(state))
        }
        feedJob = null
        feedJobDestination = null
        paginationJob?.cancel()
        paginationJob = null
        paginationGeneration++
        feedGeneration++
    }

    private fun prefetchNext() {
        prefetchJob?.cancel()
        val preloadGeneration = ++prefetchGeneration
        val state = mutableUiState.value
        val selected = state.selectedDestination ?: return
        val index = state.visibleDestinations.indexOfFirst { it.id == selected.id }
        val destination = state.visibleDestinations.getOrNull(index + 1) ?: return
        val cached = state.feeds[destination.id] ?: FeedState.Initial
        if (cached !is FeedState.Initial && cached !is FeedState.Failed) return
        val generation = feedGeneration
        prefetchJob = viewModelScope.launch {
            try {
                val response = calls.run { fetch(destination, 1) }.capped()
                val current = mutableUiState.value
                val currentIndex = current.visibleDestinations.indexOfFirst { it.id == current.selectedDestination?.id }
                if (preloadGeneration == prefetchGeneration && generation == feedGeneration &&
                    current.visibleDestinations.getOrNull(currentIndex + 1)?.id == destination.id) {
                    setFeed(destination.id, response.toState(1))
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                // Prefetch is deliberately silent; foreground selection owns errors.
            }
        }
    }

    fun reloadEnrollment() {
        val generation = ++contributionGeneration
        contributionLoading()
        viewModelScope.launch {
            try {
                val enrollment = calls.run(tokenStore::load)
                if (generation == contributionGeneration) contributionSuccess(enrollment)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                if (generation == contributionGeneration) contributionFailure()
            }
        }
    }

    fun enroll(displayName: String, deviceName: String): Boolean {
        val display = validLabel(displayName) ?: return false
        val device = validLabel(deviceName) ?: return false
        if (mutableUiState.value.contribution.loading) return false
        val generation = ++contributionGeneration
        contributionLoading()
        viewModelScope.launch {
            try {
                val enrollment = calls.run { api.createInstallation(display, device) }
                if (generation != contributionGeneration || currentToken() != null) return@launch
                calls.run { tokenStore.save(enrollment) }
                if (generation != contributionGeneration || currentToken() != null) return@launch
                preferences.deviceNameOverride = device
                contributionSuccess(enrollment)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                if (generation == contributionGeneration) contributionFailure()
            }
        }
        return true
    }

    fun refreshEnrollment() {
        if (mutableUiState.value.contribution.loading) return
        val enrollment = mutableUiState.value.contribution.enrollment ?: return
        val generation = ++contributionGeneration
        contributionLoading()
        viewModelScope.launch {
            try {
                val installation = calls.run { api.fetchInstallation(enrollment.token) }
                if (!contributionMatches(generation, enrollment.token)) return@launch
                val updated = enrollment.copy(installation = installation)
                calls.run { tokenStore.save(updated) }
                if (contributionMatches(generation, enrollment.token)) contributionSuccess(updated)
            } catch (_: ForumIndexApiException.ExpiredAccess) {
                expireEnrollment(generation, enrollment.token)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                if (contributionMatches(generation, enrollment.token)) contributionFailure()
            }
        }
    }

    fun updateDevice(deviceName: String): Boolean {
        val device = validLabel(deviceName) ?: return false
        if (mutableUiState.value.contribution.loading) return false
        val enrollment = mutableUiState.value.contribution.enrollment ?: return false
        val generation = ++contributionGeneration
        contributionLoading()
        viewModelScope.launch {
            try {
                val installation = calls.run { api.updateInstallation(device, enrollment.token) }
                if (!contributionMatches(generation, enrollment.token)) return@launch
                val updated = enrollment.copy(installation = installation)
                calls.run { tokenStore.save(updated) }
                if (contributionMatches(generation, enrollment.token)) {
                    preferences.deviceNameOverride = device
                    contributionSuccess(updated)
                }
            } catch (_: ForumIndexApiException.ExpiredAccess) {
                expireEnrollment(generation, enrollment.token)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                if (contributionMatches(generation, enrollment.token)) contributionFailure()
            }
        }
        return true
    }

    fun report(topicId: Int, kind: String, active: Boolean): Boolean {
        if (topicId <= 0 || kind !in ACTIONS) return false
        val enrollment = mutableUiState.value.contribution.enrollment
        if (enrollment == null) {
            setReport(topicId, ReportState.NeedsEnrollment)
            return true
        }
        val generation = contributionGeneration
        val lock = reportLocks.getOrPut(topicId) { Mutex() }
        viewModelScope.launch {
            lock.withLock {
                if (!contributionMatches(generation, enrollment.token)) return@withLock
                setReport(topicId, ReportState.Running)
                try {
                    calls.run { api.setTopicAction(topicId, kind, active, enrollment.token) }
                    if (contributionMatches(generation, enrollment.token)) setReport(topicId, ReportState.Succeeded)
                } catch (_: ForumIndexApiException.ExpiredAccess) {
                    if (expireEnrollment(generation, enrollment.token)) setReport(topicId, ReportState.NeedsEnrollment)
                } catch (_: CancellationException) {
                    throw CancellationException()
                } catch (_: Exception) {
                    if (contributionMatches(generation, enrollment.token)) setReport(topicId, ReportState.Failed(REQUEST_FAILED))
                }
            }
        }
        return true
    }

    fun markOpened(topic: Topic) {
        markOpened(topic.forum.id, topic.id)
    }

    fun markOpened(forumId: Int, topicId: Int) {
        preferences.markOpened(forumId, topicId)
        if (mutableUiState.value.contribution.enrollment != null) report(topicId, "read", true)
    }

    fun isOpened(topic: Topic) = isOpened(topic.forum.id, topic.id)
    fun isOpened(forumId: Int, topicId: Int) = preferences.isOpened(forumId, topicId)

    fun toggleStar(topic: Topic) {
        if (topic.url == null) return
        val current = mutableStars.value
        val active = current.none { it.matches(topic) }
        val updated = if (active) {
            current + StarredTopic(topic.title, topic.url.toString(), topic.forum.name, topic.id, topic.forum.id)
        } else current.filterNot { it.matches(topic) }
        preferences.stars = updated
        mutableStars.value = updated
        if (mutableUiState.value.contribution.enrollment != null) report(topic.id, "starred", active)
    }

    fun optOut() {
        if (mutableUiState.value.contribution.loading) return
        val enrollment = mutableUiState.value.contribution.enrollment
        val generation = ++contributionGeneration
        contributionLoading()
        viewModelScope.launch {
            try {
                if (enrollment != null) calls.run { api.deleteInstallation(enrollment.token) }
                if (generation != contributionGeneration || currentToken() != enrollment?.token) return@launch
                calls.run(tokenStore::delete)
                if (generation == contributionGeneration && currentToken() == enrollment?.token) contributionSuccess(null)
            } catch (_: ForumIndexApiException.ExpiredAccess) {
                expireEnrollment(generation, enrollment?.token)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                if (generation == contributionGeneration) contributionFailure()
            }
        }
    }

    private fun contributionLoading() {
        mutableUiState.value = mutableUiState.value.copy(contribution = mutableUiState.value.contribution.copy(loading = true, error = null))
    }

    private fun contributionSuccess(enrollment: Enrollment?) {
        mutableUiState.value = mutableUiState.value.copy(contribution = mutableUiState.value.contribution.copy(enrollment = enrollment, loading = false, error = null))
    }

    private fun contributionFailure() {
        mutableUiState.value = mutableUiState.value.copy(contribution = mutableUiState.value.contribution.copy(loading = false, error = REQUEST_FAILED))
    }

    private suspend fun expireEnrollment(expectedGeneration: Int, expectedToken: String?): Boolean {
        if (expectedGeneration != contributionGeneration || currentToken() != expectedToken) return false
        val clearingGeneration = ++contributionGeneration
        calls.run(tokenStore::delete)
        if (clearingGeneration != contributionGeneration || currentToken() != expectedToken) return false
        contributionSuccess(null)
        return true
    }

    private fun setReport(topicId: Int, report: ReportState) {
        val contribution = mutableUiState.value.contribution
        mutableUiState.value = mutableUiState.value.copy(contribution = contribution.copy(reports = contribution.reports + (topicId to report)))
    }

    private fun fetch(destination: Destination, page: Int) = when (destination) {
        Destination.Main -> api.fetchMainTopics(page)
        is Destination.Subject -> api.fetchSubjectTopics(destination.subject.slug, page)
    }

    private fun FeedEnvelope.capped() = copy(results = results.take(PAGE_SIZE))

    private fun FeedEnvelope.toState(page: Int): FeedState = if (results.isEmpty()) FeedState.Empty
        else FeedState.Loaded(results.distinctBy { it.forum.id to it.id }, page, page < MAX_PAGE && results.size == PAGE_SIZE)

    private fun stableState(state: FeedState): FeedState = when (state) {
        is FeedState.Refreshing -> if (state.rows.isEmpty()) FeedState.Empty else FeedState.Loaded(state.rows, state.page, state.hasMore)
        else -> state
    }

    private fun setFeed(id: String, feed: FeedState) {
        mutableUiState.value = mutableUiState.value.copy(feeds = mutableUiState.value.feeds + (id to feed))
    }

    private fun validLabel(value: String): String? = value.trim().takeIf { it.codePointCount(0, it.length) in 1..MAX_LABEL_CODE_POINTS }
    private fun Exception.safeMessage() = REQUEST_FAILED
    private fun currentToken() = mutableUiState.value.contribution.enrollment?.token
    private fun contributionMatches(generation: Int, token: String) = generation == contributionGeneration && currentToken() == token
    private fun StarredTopic.matches(topic: Topic) = if (forumId != null) forumId == topic.forum.id && topicId == topic.id
        else url.isNotEmpty() && url == topic.url?.toString()

    private companion object {
        const val MIN_TOPIC_COUNT = 5
        const val PAGE_SIZE = 30
        const val MAX_PAGE = 3
        const val MAX_LABEL_CODE_POINTS = 100
        const val REQUEST_FAILED = "Request failed. Please try again."
        val DEFAULT_SLUGS = listOf("main", "ai", "technology", "sport", "creative", "markets", "jobs", "gaming", "community")
        val ACTIONS = setOf("starred", "read", "low_quality", "inappropriate", "wrong_subject")
    }
}
