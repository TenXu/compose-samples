/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.jetnews.ui.home

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.jetnews.R
import com.example.jetnews.data.Result
import com.example.jetnews.data.posts.impl.BlockingFakePostsRepository
import com.example.jetnews.model.Post
import com.example.jetnews.ui.article.ArticleScreen
import com.example.jetnews.ui.article.PostContent
import com.example.jetnews.ui.components.InsetAwareTopAppBar
import com.example.jetnews.ui.theme.JetnewsTheme
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.insets.systemBarsPadding
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking

/**
 * Displays the Home screen.
 *
 * Note: AAC ViewModels don't work with Compose Previews currently.
 *
 * @param homeViewModel ViewModel that handles the business logic of this screen
 * @param navigateToExpandedArticle (event) request navigation to Article screen
 * @param openDrawer (event) request opening the app drawer
 * @param scaffoldState (state) state for the [Scaffold] component on this screen
 */
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    navigateToExpandedArticle: (String) -> Unit,
    openDrawer: () -> Unit,
    scaffoldState: ScaffoldState = rememberScaffoldState()
) {
    // UiState of the HomeScreen
    val uiState by homeViewModel.uiState.collectAsState()

    HomeScreen(
        uiState = uiState,
        onToggleFavorite = { homeViewModel.toggleFavourite(it) },
        onSelectPost = { homeViewModel.selectArticle(it) },
        onRefreshPosts = { homeViewModel.refreshPosts() },
        onErrorDismiss = { homeViewModel.errorShown(it) },
        onInteractWithList = { homeViewModel.interactedWithList() },
        onInteractWithDetail = homeViewModel::interactedWithDetail,
        navigateToExpandedArticle = navigateToExpandedArticle,
        openDrawer = openDrawer,
        scaffoldState = scaffoldState,
    )
}

/**
 * Displays the Home screen.
 *
 * Stateless composable is not coupled to any specific state management.
 *
 * @param uiState (state) the data to show on the screen
 * @param onToggleFavorite (event) toggles favorite for a post
 * @param onRefreshPosts (event) request a refresh of posts
 * @param onErrorDismiss (event) error message was shown
 * @param navigateToExpandedArticle (event) request navigation to Article screen
 * @param openDrawer (event) request opening the app drawer
 * @param scaffoldState (state) state for the [Scaffold] component on this screen
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onToggleFavorite: (String) -> Unit,
    onSelectPost: (String) -> Unit,
    onRefreshPosts: () -> Unit,
    onErrorDismiss: (Long) -> Unit,
    onInteractWithList: () -> Unit,
    onInteractWithDetail: (String) -> Unit,
    navigateToExpandedArticle: (String) -> Unit,
    openDrawer: () -> Unit,
    scaffoldState: ScaffoldState
) {
    // Construct the lazy list states for the list and the details outside of deciding which one to show.
    // This allows the associated state to survive beyond that decision, and therefore we get to preserve the scroll
    // throughout any changes to the content.
    val listLazyListState = rememberLazyListState()
    val detailLazyListStates = uiState.posts.associate { post ->
        key(post.id) {
            post.id to rememberLazyListState()
        }
    }

    BoxWithConstraints {
        val useListDetail = maxWidth > 624.dp

        if (useListDetail || uiState.lastInteractedWithList) {
            Scaffold(
                scaffoldState = scaffoldState,
                snackbarHost = { SnackbarHost(hostState = it, modifier = Modifier.systemBarsPadding()) },
                topBar = {
                    val title = stringResource(id = R.string.app_name)
                    InsetAwareTopAppBar(
                        title = { Text(text = title) },
                        navigationIcon = {
                            IconButton(onClick = openDrawer) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_jetnews_logo),
                                    contentDescription = stringResource(R.string.cd_open_navigation_drawer)
                                )
                            }
                        }
                    )
                },
            ) { innerPadding ->
                val modifier = Modifier.padding(innerPadding)

                LoadingContent(
                    empty = uiState.initialLoad,
                    emptyContent = { FullScreenLoading() },
                    loading = uiState.loading,
                    onRefresh = onRefreshPosts,
                    content = {
                        HomeScreenErrorAndContent(
                            posts = uiState.posts,
                            selectedPostId = uiState.selectedPostId,
                            useListDetail = useListDetail,
                            lastInteractedWithList = uiState.lastInteractedWithList,
                            isShowingErrors = uiState.errorMessages.isNotEmpty(),
                            onRefresh = {
                                onRefreshPosts()
                            },
                            navigateToExpandedArticle = navigateToExpandedArticle,
                            favorites = uiState.favorites,
                            onToggleFavorite = onToggleFavorite,
                            onSelectPost = onSelectPost,
                            onInteractWithList = onInteractWithList,
                            onInteractWithDetail = onInteractWithDetail,
                            listLazyListState = listLazyListState,
                            detailLazyListStates = detailLazyListStates,
                            modifier = modifier
                        )
                    }
                )
            }

            // Process one error message at a time and show them as Snackbars in the UI
            if (uiState.errorMessages.isNotEmpty()) {
                // Remember the errorMessage to display on the screen
                val errorMessage = remember(uiState) { uiState.errorMessages[0] }

                // Get the text to show on the message from resources
                val errorMessageText: String = stringResource(errorMessage.messageId)
                val retryMessageText = stringResource(id = R.string.retry)

                // If onRefreshPosts or onErrorDismiss change while the LaunchedEffect is running,
                // don't restart the effect and use the latest lambda values.
                val onRefreshPostsState by rememberUpdatedState(onRefreshPosts)
                val onErrorDismissState by rememberUpdatedState(onErrorDismiss)

                // Effect running in a coroutine that displays the Snackbar on the screen
                // If there's a change to errorMessageText, retryMessageText or scaffoldState,
                // the previous effect will be cancelled and a new one will start with the new values
                LaunchedEffect(errorMessageText, retryMessageText, scaffoldState) {
                    val snackbarResult = scaffoldState.snackbarHostState.showSnackbar(
                        message = errorMessageText,
                        actionLabel = retryMessageText
                    )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        onRefreshPostsState()
                    }
                    // Once the message is displayed and dismissed, notify the ViewModel
                    onErrorDismissState(errorMessage.id)
                }
            }
        } else {
            val selectedPost = uiState.posts.find { it.id == uiState.selectedPostId }

            if (selectedPost != null) {
                val lazyListState = detailLazyListStates.getValue(selectedPost.id)

                ArticleScreen(
                    post = selectedPost,
                    onBack = onInteractWithList,
                    isFavorite = uiState.favorites.contains(selectedPost.id),
                    onToggleFavorite = {
                        onToggleFavorite(selectedPost.id)
                    },
                    lazyListState = lazyListState,
                )

                // If we are just showing the detail, have a back press switch to the list.
                if (!useListDetail) {
                    BackHandler {
                        onInteractWithList()
                    }
                }
            } else {
                // TODO: Improve UX
                LaunchedEffect(Unit) {
                    onInteractWithList()
                }
            }
        }
    }
}

/**
 * Display an initial empty state or swipe to refresh content.
 *
 * @param empty (state) when true, display [emptyContent]
 * @param emptyContent (slot) the content to display for the empty state
 * @param loading (state) when true, display a loading spinner over [content]
 * @param onRefresh (event) event to request refresh
 * @param content (slot) the main content to show
 */
@Composable
private fun LoadingContent(
    empty: Boolean,
    emptyContent: @Composable () -> Unit,
    loading: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    if (empty) {
        emptyContent()
    } else {
        SwipeRefresh(
            state = rememberSwipeRefreshState(loading),
            onRefresh = onRefresh,
            content = content,
        )
    }
}

/**
 * Responsible for displaying any error conditions around [PostList].
 *
 * @param posts (state) list of posts to display
 * @param isShowingErrors (state) whether the screen is showing errors or not
 * @param favorites (state) all favorites
 * @param onRefresh (event) request to refresh data
 * @param navigateToExpandedArticle (event) request navigation to Article screen
 * @param onToggleFavorite (event) request a single favorite be toggled
 * @param modifier modifier for root element
 */
@Composable
private fun HomeScreenErrorAndContent(
    posts: List<Post>,
    selectedPostId: String?,
    useListDetail: Boolean,
    lastInteractedWithList: Boolean,
    isShowingErrors: Boolean,
    favorites: Set<String>,
    onRefresh: () -> Unit,
    navigateToExpandedArticle: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSelectPost: (String) -> Unit,
    onInteractWithList: () -> Unit,
    onInteractWithDetail: (String) -> Unit,
    listLazyListState: LazyListState,
    detailLazyListStates: Map<String, LazyListState>,
    modifier: Modifier = Modifier
) {
    if (posts.isNotEmpty()) {
        val detailPost by derivedStateOf {
            // TODO: Restructure the posts data to remove the magic 3 as a default
            posts.find { it.id == selectedPostId } ?: posts[3]
        }

        Row {
            if (useListDetail || lastInteractedWithList) {
                PostList(
                    posts = posts,
                    onArticleTapped = onSelectPost,
                    favorites = favorites,
                    onToggleFavorite = onToggleFavorite,
                    contentPadding = rememberInsetsPaddingValues(
                        insets = LocalWindowInsets.current.systemBars,
                        applyTop = false,
                        applyEnd = !useListDetail,
                    ),
                    modifier = modifier
                        .then(
                            if (useListDetail) {
                                Modifier.width(334.dp)
                            } else {
                                Modifier.fillMaxSize()
                            }
                        )
                        .pointerInput(Unit) {
                            while (currentCoroutineContext().isActive) {
                                awaitPointerEventScope {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    onInteractWithList()
                                }
                            }
                        },
                    state = listLazyListState
                )
            }
            // Crossfade between different detail posts
            Crossfade(targetState = detailPost) { detailPost ->
                // Get the lazy list state for this detail view
                val detailLazyListState by derivedStateOf {
                    detailLazyListStates.getValue(detailPost.id)
                }

                // Key against the post id to avoid sharing any state between different posts
                key(detailPost.id) {
                    PostContent(
                        post = detailPost,
                        modifier = modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                while (currentCoroutineContext().isActive) {
                                    awaitPointerEventScope {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        onInteractWithDetail(detailPost.id)
                                    }
                                }
                            },
                        contentPadding = rememberInsetsPaddingValues(
                            insets = LocalWindowInsets.current.systemBars,
                            applyTop = false,
                            applyStart = !useListDetail,
                        ),
                        state = detailLazyListState
                    )
                }
            }
        }
    } else if (!isShowingErrors) {
        // if there are no posts, and no error, let the user refresh manually
        TextButton(onClick = onRefresh, modifier.fillMaxSize()) {
            Text(stringResource(id = R.string.home_tap_to_load_content), textAlign = TextAlign.Center)
        }
    } else {
        // there's currently an error showing, don't show any content
        Box(modifier.fillMaxSize()) { /* empty screen */ }
    }
}

/**
 * Display a list of posts.
 *
 * When a post is clicked on, [onArticleTapped] will be called.
 *
 * @param posts (state) the list to display
 * @param onArticleTapped (event) request navigation to Article screen
 * @param modifier modifier for the root element
 */
@Composable
private fun PostList(
    posts: List<Post>,
    onArticleTapped: (postId: String) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    val postTop = posts[3]
    val postsSimple = posts.subList(0, 2)
    val postsPopular = posts.subList(2, 7)
    val postsHistory = posts.subList(7, 10)

    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding
    ) {
        item { PostListTopSection(postTop, onArticleTapped) }
        item { PostListSimpleSection(postsSimple, onArticleTapped, favorites, onToggleFavorite) }
        item { PostListPopularSection(postsPopular, onArticleTapped) }
        item { PostListHistorySection(postsHistory, onArticleTapped) }
    }
}

/**
 * Full screen circular progress indicator
 */
@Composable
private fun FullScreenLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Top section of [PostList]
 *
 * @param post (state) highlighted post to display
 * @param navigateToArticle (event) request navigation to Article screen
 */
@Composable
private fun PostListTopSection(post: Post, navigateToArticle: (String) -> Unit) {
    Text(
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        text = stringResource(id = R.string.home_top_section_title),
        style = MaterialTheme.typography.subtitle1
    )
    PostCardTop(
        post = post,
        modifier = Modifier.clickable(onClick = { navigateToArticle(post.id) })
    )
    PostListDivider()
}

/**
 * Full-width list items for [PostList]
 *
 * @param posts (state) to display
 * @param navigateToArticle (event) request navigation to Article screen
 */
@Composable
private fun PostListSimpleSection(
    posts: List<Post>,
    navigateToArticle: (String) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit
) {
    Column {
        posts.forEach { post ->
            PostCardSimple(
                post = post,
                navigateToArticle = navigateToArticle,
                isFavorite = favorites.contains(post.id),
                onToggleFavorite = { onToggleFavorite(post.id) }
            )
            PostListDivider()
        }
    }
}

/**
 * Horizontal scrolling cards for [PostList]
 *
 * @param posts (state) to display
 * @param navigateToArticle (event) request navigation to Article screen
 */
@Composable
private fun PostListPopularSection(
    posts: List<Post>,
    navigateToArticle: (String) -> Unit
) {
    Column {
        Text(
            modifier = Modifier.padding(16.dp),
            text = stringResource(id = R.string.home_popular_section_title),
            style = MaterialTheme.typography.subtitle1
        )

        LazyRow(modifier = Modifier.padding(end = 16.dp)) {
            items(posts) { post ->
                PostCardPopular(
                    post,
                    navigateToArticle,
                    Modifier.padding(start = 16.dp, bottom = 16.dp)
                )
            }
        }
        PostListDivider()
    }
}

/**
 * Full-width list items that display "based on your history" for [PostList]
 *
 * @param posts (state) to display
 * @param navigateToArticle (event) request navigation to Article screen
 */
@Composable
private fun PostListHistorySection(
    posts: List<Post>,
    navigateToArticle: (String) -> Unit
) {
    Column {
        posts.forEach { post ->
            PostCardHistory(post, navigateToArticle)
            PostListDivider()
        }
    }
}

/**
 * Full-width divider with padding for [PostList]
 */
@Composable
private fun PostListDivider() {
    Divider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
    )
}

@Preview("Home screen")
@Preview("Home screen (dark)", uiMode = UI_MODE_NIGHT_YES)
@Preview("Home screen (big font)", fontScale = 1.5f)
@Preview("Home screen (large screen)", device = Devices.PIXEL_C)
@Composable
fun PreviewHomeScreen() {
    val posts = runBlocking {
        (BlockingFakePostsRepository().getPosts() as Result.Success).data
    }
    JetnewsTheme {
        HomeScreen(
            uiState = HomeUiState(posts = posts),
            onToggleFavorite = { /*TODO*/ },
            onSelectPost = { /*TODO*/ },
            onRefreshPosts = { /*TODO*/ },
            onErrorDismiss = { /*TODO*/ },
            onInteractWithList = { /*TODO*/ },
            onInteractWithDetail = { /*TODO*/ },
            navigateToExpandedArticle = { /*TODO*/ },
            openDrawer = { /*TODO*/ },
            scaffoldState = rememberScaffoldState()
        )
    }
}
