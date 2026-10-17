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
    val submittingForum: Boolean = false,
    val forumSubmissionMessage: String? = null,
)

data class UiState(
    val taxonomy: TaxonomyState = TaxonomyState.Loading,
    val allDestinations: List<Destination> = emptyList(),
    val visibleDestinations: List<Destination> = emptyList(),
    val selectedDestination: Destination? = null,
    val feeds: Map<String, FeedState> = emptyMap(),
    val contribution: ContributionState = ContributionState(),
    val openedTopicIds: Set<String> = emptySet(),
    val sendingFeedback: Boolean = false,
    val feedbackResult: String? = null,
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
    private val bookmarkReply: (String) -> java.time.Instant? = BookmarkActivity::latestReply,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(UiState(openedTopicIds = preferences.openedTopicIds))
    val uiState: StateFlow<UiState> = mutableUiState.asStateFlow()
    private val mutableStars = MutableStateFlow(preferences.stars.sortedByDescending { it.addedAt }.also { preferences.stars = it })
    val stars: StateFlow<List<StarredTopic>> = mutableStars.asStateFlow()
    private val mutableDetailSession = MutableStateFlow<TopicDetailSession?>(null)
    val detailSession = mutableDetailSession.asStateFlow()
    private val mutableFeedReturnTarget = MutableStateFlow<FeedReturnTarget?>(null)
    val feedReturnTarget = mutableFeedReturnTarget.asStateFlow()

    fun openTopic(topic: Topic) {
        val state = mutableUiState.value
        val destination = state.selectedDestination ?: return
        val session = TopicDetailSession.create(state.feeds[destination.id]?.rows.orEmpty(), topic, destination.id) ?: return
        mutableFeedReturnTarget.value = null
        mutableDetailSession.value = session
        markOpened(topic)
    }

    fun openBookmark(bookmark: StarredTopic) {
        val url = try { validTopicUrl(java.net.URL(bookmark.url)) } catch (_: Exception) { null } ?: return
        val forum = bookmark.forum ?: ForumSummary(bookmark.forumId ?: 0, bookmark.forumName, "", url, null, false)
        val topic = Topic(bookmark.topicId ?: 0, bookmark.title, url, null, forum, bookmark.publishedAt, bookmark.replyCount, bookmark.externalSignals)
        mutableFeedReturnTarget.value = null
        mutableDetailSession.value = TopicDetailSession.create(listOf(topic), topic, "bookmarks")
        markOpened(bookmark)
    }

    private var lastBookmarkCheck: Long? = null
    private var checkingBookmarks = false
    suspend fun refreshBookmarkReplies() {
        val now = System.currentTimeMillis()
        if (checkingBookmarks || lastBookmarkCheck?.let { now - it < 300_000 } == true) return
        checkingBookmarks = true
        lastBookmarkCheck = now
        try {
            for (bookmark in mutableStars.value.take(20).filter { !it.hasUnreadReplies && BookmarkActivity.endpoint(it.url) != null }) {
                try {
                    val latest = calls.run { bookmarkReply(bookmark.url) } ?: continue
                    saveStars(mutableStars.value.map {
                        if (it.url == bookmark.url && it.addedAt == bookmark.addedAt) it.copy(latestReplyAt = latest.toEpochMilli()) else it
                    })
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* One unavailable forum does not stop the others. */ }
            }
        } finally { checkingBookmarks = false }
    }

    fun selectDetailTopic(index: Int) {
        val current = mutableDetailSession.value ?: return
        val selected = current.selecting(index)
        if (selected == current) return
        mutableDetailSession.value = selected
        mutableFeedReturnTarget.value = FeedReturnTarget(selected.destinationId, selected.currentTopic.identity)
        markOpened(selected.currentTopic)
    }

    fun closeTopic() {
        mutableDetailSession.value = null
    }

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

    private var automaticEnrollmentDevice: String? = null
    val defaultDisplayName: String
        get() = preferences.contributorDisplayName ?: "Reader-${java.util.UUID.randomUUID().toString().take(8)}".also {
            preferences.contributorDisplayName = it
        }

    private fun enrollAutomaticallyIfNeeded() {
        val device = automaticEnrollmentDevice ?: return
        val state = mutableUiState.value.contribution
        if (preferences.contributionsOptedOut || state.loading || state.error != null || state.enrollment != null) return
        enroll(defaultDisplayName, preferences.deviceNameOverride ?: device)
    }

    fun launch(defaultDeviceName: String? = null) {
        if (launched) return
        launched = true
        automaticEnrollmentDevice = defaultDeviceName
        enrollAutomaticallyIfNeeded()
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
        val destinations = (if (mutableUiState.value.contribution.enrollment?.installation?.forYouEligible == true) listOf(Destination.ForYou, Destination.Main) else listOf(Destination.Main)) + subjects.map(Destination::Subject)
        val byId = destinations.associateBy(Destination::id)
        val stored = preferences.visibleSubjectOrder.distinct().mapNotNull(byId::get)
        val introduceForYou = Destination.ForYou in destinations && !preferences.forYouIntroduced
        val visible = ((if (Destination.ForYou in destinations && (preferences.visibleSubjectOrder.isEmpty() || introduceForYou)) listOf(Destination.ForYou) else emptyList()) + (if (preferences.visibleSubjectOrder.isEmpty()) {
            DEFAULT_SLUGS.mapNotNull { slug -> if (slug == "main") Destination.Main else byId["parent:$slug"] }
        } else if (introduceForYou) stored.filterNot { it == Destination.ForYou } else stored)).ifEmpty { listOf(Destination.Main) }
        if (introduceForYou) {
            preferences.forYouIntroduced = true
            if (preferences.visibleSubjectOrder.isNotEmpty()) preferences.visibleSubjectOrder = visible.map { it.id }
        }
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
        loadSelectedFeedIfReady()
    }

    private fun loadSelectedFeedIfReady() {
        val state = mutableUiState.value
        val selected = state.selectedDestination ?: return
        if (state.contribution.loading) return
        val feed = state.feeds[selected.id] ?: FeedState.Initial
        if (feed !is FeedState.Loaded && feed !is FeedState.Empty) select(selected, force = true)
        else prefetchNext()
    }

    fun updateVisibleOrder(ids: List<String>) {
        val byId = mutableUiState.value.allDestinations.associateBy(Destination::id)
        val visible = ids.distinct().mapNotNull(byId::get).ifEmpty { listOf(byId[Destination.Main.id] ?: return) }
        val saved = visible.map(Destination::id).toMutableList()
        val oldForYouIndex = preferences.visibleSubjectOrder.indexOf(Destination.ForYou.id)
        if (Destination.ForYou.id !in byId && oldForYouIndex >= 0) saved.add(oldForYouIndex.coerceAtMost(saved.size), Destination.ForYou.id)
        preferences.visibleSubjectOrder = saved
        if (Destination.ForYou.id in byId) preferences.forYouIntroduced = true
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
                val response = fetch(destination, 1).capped()
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
                val response = fetch(destination, nextPage).capped()
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
                val response = fetch(destination, 1).capped()
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
                val enrollment = if (preferences.contributionsOptedOut) null else calls.run(tokenStore::load)
                if (generation == contributionGeneration) {
                    contributionSuccess(enrollment)
                    enrollAutomaticallyIfNeeded()
                    loadSelectedFeedIfReady()
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                if (generation == contributionGeneration) {
                    contributionFailure()
                    loadSelectedFeedIfReady()
                }
            }
        }
    }

    fun enroll(displayName: String, deviceName: String): Boolean {
        val display = validLabel(displayName) ?: return false
        val device = validLabel(deviceName) ?: return false
        if (mutableUiState.value.contribution.loading || mutableUiState.value.contribution.submittingForum) return false
        val generation = ++contributionGeneration
        contributionLoading()
        viewModelScope.launch {
            try {
                val enrollment = calls.run { api.createInstallation(display, device) }
                if (generation != contributionGeneration || currentToken() != null) return@launch
                calls.run { tokenStore.save(enrollment) }
                if (generation != contributionGeneration || currentToken() != null) return@launch
                preferences.deviceNameOverride = device
                preferences.contributorDisplayName = display
                preferences.contributionsOptedOut = false
                contributionSuccess(enrollment)
                reloadMainFeed()
                if (mutableUiState.value.selectedDestination != Destination.Main) loadSelectedFeedIfReady()
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                if (generation == contributionGeneration) {
                    contributionFailure()
                    loadSelectedFeedIfReady()
                }
            }
        }
        return true
    }

    fun refreshEnrollment() {
        if (mutableUiState.value.contribution.loading || mutableUiState.value.contribution.submittingForum) return
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
        if (mutableUiState.value.contribution.loading || mutableUiState.value.contribution.submittingForum) return false
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
                    if (contributionMatches(generation, enrollment.token)) {
                        setReport(topicId, ReportState.Succeeded)
                        if (kind == "read" && active) refreshReadEligibility(generation, enrollment.token)
                    }
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

    fun submitFeedback(submission: FeedbackSubmission, onSuccess: () -> Unit) {
        val token = currentToken() ?: return
        if (!submission.isValid || mutableUiState.value.sendingFeedback) return
        val generation = contributionGeneration
        mutableUiState.value = mutableUiState.value.copy(sendingFeedback = true, feedbackResult = null)
        viewModelScope.launch {
            var result: String
            try {
                calls.run { api.submitFeedback(submission, token) }
                onSuccess()
                result = "Thanks for your feedback. Your message has been sent to the app admins."
            } catch (_: ForumIndexApiException.ExpiredAccess) {
                expireEnrollment(generation, token)
                result = "Your contributor access expired. Enable contributions in Settings. Your draft is saved."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                result = when ((e as? ForumIndexApiException.Http)?.status) {
                    404, 405, 501, 503 -> "Feedback isn’t available yet. Your draft is saved; please try again later."
                    429 -> "Too many messages. Your draft is saved; please try again later."
                    400, 413, 422 -> "The message couldn’t be accepted. Please check it and try again."
                    else -> "Couldn’t send feedback. Your draft is saved; please try again."
                }
            } finally { mutableUiState.value = mutableUiState.value.copy(sendingFeedback = false) }
            mutableUiState.value = mutableUiState.value.copy(feedbackResult = result)
        }
    }

    fun submitForum(url: String): Boolean {
        val enrollment = mutableUiState.value.contribution.enrollment ?: return false
        if (!enrollment.installation.trusted || mutableUiState.value.contribution.loading ||
            mutableUiState.value.contribution.submittingForum) return false
        val value = url.trim()
        val valid = try { validTopicUrl(java.net.URL(value)) != null } catch (_: Exception) { false }
        if (!valid || value.toByteArray().size > 1_000) return false
        val generation = contributionGeneration
        mutableUiState.value = mutableUiState.value.copy(
            contribution = mutableUiState.value.contribution.copy(submittingForum = true, forumSubmissionMessage = null),
        )
        viewModelScope.launch {
            try {
                calls.run { api.submitForum(value, enrollment.token) }
                if (contributionMatches(generation, enrollment.token)) {
                    mutableUiState.value = mutableUiState.value.copy(
                        contribution = mutableUiState.value.contribution.copy(
                            submittingForum = false, forumSubmissionMessage = "Forum submitted for review.",
                        ),
                    )
                }
            } catch (_: ForumIndexApiException.ExpiredAccess) {
                expireEnrollment(generation, enrollment.token)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                if (contributionMatches(generation, enrollment.token)) {
                    mutableUiState.value = mutableUiState.value.copy(
                        contribution = mutableUiState.value.contribution.copy(
                            submittingForum = false, forumSubmissionMessage = "Couldn’t submit this forum. Try again.",
                        ),
                    )
                }
            }
        }
        return true
    }

    fun markOpened(topic: Topic) {
        saveStars(mutableStars.value.map { if (it.url == topic.url?.toString()) it.copy(lastReadAt = System.currentTimeMillis()) else it })
        markOpened(topic.forum.id, topic.id)
    }

    fun markOpened(forumId: Int, topicId: Int) {
        preferences.markOpened(forumId, topicId)
        mutableUiState.value = mutableUiState.value.copy(openedTopicIds = preferences.openedTopicIds)
        if (mutableUiState.value.contribution.enrollment != null) report(topicId, "read", true)
    }

    fun isOpened(topic: Topic) = isOpened(topic.forum.id, topic.id)
    fun isOpened(forumId: Int, topicId: Int) = preferences.isOpened(forumId, topicId)

    fun isStarred(topic: Topic) = mutableStars.value.any { it.matches(topic) }
    fun isStarred(star: StarredTopic) = mutableStars.value.any { it.sameIdentity(star) }

    fun toggleStar(star: StarredTopic) {
        val url = try { validTopicUrl(java.net.URL(star.url)) } catch (_: Exception) { null } ?: return
        val active = !isStarred(star)
        val now = maxOf(System.currentTimeMillis(), (mutableStars.value.maxOfOrNull { it.addedAt } ?: 0) + 1)
        saveStars(if (active) mutableStars.value + star.copy(url = url.toString(), addedAt = now, lastReadAt = now, latestReplyAt = null)
            else mutableStars.value.filterNot { it.sameIdentity(star) })
        if (mutableUiState.value.contribution.enrollment != null) star.topicId?.let { report(it, "starred", active) }
    }

    fun toggleStar(topic: Topic) {
        val url = validTopicUrl(topic.url) ?: return
        val current = mutableStars.value
        val active = current.none { it.matches(topic) }
        val updated = if (active) {
            val now = maxOf(System.currentTimeMillis(), (current.maxOfOrNull { it.addedAt } ?: 0) + 1)
            current + topic.bookmark().copy(addedAt = now, lastReadAt = now)
        } else current.filterNot { it.matches(topic) }
        saveStars(updated)
        if (mutableUiState.value.contribution.enrollment != null) report(topic.id, "starred", active)
    }

    fun removeStar(star: StarredTopic) {
        val updated = mutableStars.value.filterNot { it.sameIdentity(star) }
        if (updated.size == mutableStars.value.size) return
        saveStars(updated)
        if (mutableUiState.value.contribution.enrollment != null) star.topicId?.let { report(it, "starred", false) }
    }

    fun markOpened(star: StarredTopic) {
        saveStars(mutableStars.value.map { if (it.sameIdentity(star)) it.copy(lastReadAt = System.currentTimeMillis()) else it })
        if (star.forumId != null && star.topicId != null) preferences.markOpened(star.forumId, star.topicId)
        mutableUiState.value = mutableUiState.value.copy(openedTopicIds = preferences.openedTopicIds)
        if (mutableUiState.value.contribution.enrollment != null) star.topicId?.let { report(it, "read", true) }
    }

    private fun saveStars(updated: List<StarredTopic>) {
        preferences.stars = updated
        mutableStars.value = updated.sortedByDescending { it.addedAt }
    }

    fun optOut() {
        if (mutableUiState.value.contribution.loading || mutableUiState.value.contribution.submittingForum) return
        val enrollment = mutableUiState.value.contribution.enrollment
        val generation = ++contributionGeneration
        contributionLoading()
        try {
            // ponytail: synchronous delete survives ViewModel cancellation; use a durable worker if measured jank warrants it.
            tokenStore.delete()
            preferences.contributionsOptedOut = true
        } catch (_: Exception) {
            if (generation == contributionGeneration) contributionFailure()
            return
        }
        if (generation != contributionGeneration || currentToken() != enrollment?.token) return
        contributionSuccess(null)
        reloadMainFeed()
        if (enrollment == null) return
        viewModelScope.launch {
            try {
                calls.run { api.deleteInstallation(enrollment.token) }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                // Local opt-out is complete; server revocation is best effort.
            }
        }
    }

    private fun contributionLoading() {
        mutableUiState.value = mutableUiState.value.copy(
            contribution = mutableUiState.value.contribution.copy(
                loading = true, error = null, submittingForum = false, forumSubmissionMessage = null,
            ),
        )
    }

    private var eligibilityRefresh = 0
    private fun refreshReadEligibility(generation: Int, token: String) {
        val refresh = ++eligibilityRefresh
        viewModelScope.launch {
            try {
                val installation = calls.run { api.fetchInstallation(token) }
                if (refresh == eligibilityRefresh && contributionMatches(generation, token)) {
                    val updated = Enrollment(token, installation)
                    tokenStore.save(updated)
                    val old = mutableUiState.value.contribution.enrollment
                    mutableUiState.value = mutableUiState.value.copy(contribution = mutableUiState.value.contribution.copy(enrollment = updated))
                    syncEligibility(old, updated)
                }
            } catch (_: ForumIndexApiException.ExpiredAccess) { expireEnrollment(generation, token) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Retry on a later read. */ }
        }
    }

    private fun contributionSuccess(enrollment: Enrollment?) {
        val old = mutableUiState.value.contribution.enrollment

        mutableUiState.value = mutableUiState.value.copy(
            contribution = mutableUiState.value.contribution.copy(
                enrollment = enrollment, loading = false, error = null, submittingForum = false,
            ),
        )
        syncEligibility(old, enrollment)
    }

    private fun syncEligibility(old: Enrollment?, enrollment: Enrollment?) {
        if ((old?.installation?.forYouEligible == true) != (enrollment?.installation?.forYouEligible == true) ||
            (old?.token != enrollment?.token && old?.installation?.forYouEligible == true)) {
            if (mutableUiState.value.selectedDestination == Destination.ForYou) cancelFeed()
            prefetchJob?.cancel()
            prefetchGeneration++
            setFeed(Destination.ForYou.id, FeedState.Initial)
            (mutableUiState.value.taxonomy as? TaxonomyState.Loaded)?.let { applyTaxonomy(it.subjects) }
        }
    }

    private fun contributionFailure() {
        mutableUiState.value = mutableUiState.value.copy(
            contribution = mutableUiState.value.contribution.copy(
                loading = false, error = REQUEST_FAILED, submittingForum = false,
            ),
        )
    }

    private suspend fun expireEnrollment(expectedGeneration: Int, expectedToken: String?): Boolean {
        if (expectedGeneration != contributionGeneration || currentToken() != expectedToken) return false
        val clearingGeneration = ++contributionGeneration
        calls.run(tokenStore::delete)
        if (clearingGeneration != contributionGeneration || currentToken() != expectedToken) return false
        contributionSuccess(null)
        reloadMainFeed()
        return true
    }

    private fun reloadMainFeed() {
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchGeneration++
        if (mutableUiState.value.selectedDestination == Destination.Main) {
            cancelFeed()
            setFeed(Destination.Main.id, FeedState.Initial)
            loadFirstPage(Destination.Main, emptyList())
        } else {
            setFeed(Destination.Main.id, FeedState.Initial)
        }
    }

    private fun setReport(topicId: Int, report: ReportState) {
        val contribution = mutableUiState.value.contribution
        mutableUiState.value = mutableUiState.value.copy(contribution = contribution.copy(reports = contribution.reports + (topicId to report)))
    }

    private suspend fun fetch(destination: Destination, page: Int) = when (destination) {
        Destination.Main -> calls.run { api.fetchMainTopics(page) }
        Destination.ForYou -> {
            val token = currentToken() ?: throw CancellationException()
            val generation = contributionGeneration
            try { calls.run { api.fetchPersonalizedTopics(page, token) } }
            catch (_: ForumIndexApiException.ExpiredAccess) {
                viewModelScope.launch { expireEnrollment(generation, token) }
                throw CancellationException()
            }
        }
        is Destination.Subject -> calls.run { api.fetchSubjectTopics(destination.subject.slug, page) }
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
    private fun StarredTopic.sameIdentity(other: StarredTopic) = if (forumId != null && other.forumId != null && topicId != null && other.topicId != null) {
        forumId == other.forumId && topicId == other.topicId
    } else url.isNotEmpty() && url == other.url

    private companion object {
        const val MIN_TOPIC_COUNT = 5
        const val PAGE_SIZE = 30
        const val MAX_PAGE = 3
        const val MAX_LABEL_CODE_POINTS = 100
        const val REQUEST_FAILED = "Request failed. Please try again."
        val DEFAULT_SLUGS = listOf(
            "main", "ai", "technology", "creative", "markets", "opensource", "jobs", "gaming",
            "audio", "community", "web-dev", "sport", "lifestyle", "science",
        )
        val ACTIONS = setOf("starred", "read", "low_quality", "inappropriate", "wrong_subject")
    }
}
