package com.android.exotest

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsCollector
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.database.ExoDatabaseProvider
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.amr.AmrExtractor
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.cache.*
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.EventLogger
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS

class MainActivity : AppCompatActivity() {

    private var cache: SimpleCache? = null
    private lateinit var player: SimpleExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        val playerView = findViewById<StyledPlayerView>(R.id.player_view)

        val bandwidthMeter: DefaultBandwidthMeter.Builder = DefaultBandwidthMeter.Builder(this)
        val trackSelectionFactory: AdaptiveTrackSelection.Factory = AdaptiveTrackSelection.Factory()
        val trackSelector = DefaultTrackSelector(applicationContext, trackSelectionFactory)
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)

        val minBufMs = SECONDS.toMillis(5)
        val maxBufMs = MINUTES.toMillis(50)
        val beforePlaybackMs = SECONDS.toMillis(5)
        val bufferForPlaybackAfterReBufferMs = MINUTES.toMillis(5)
        val backBufferDurationMs = MINUTES.toMillis(5)

        val loadControl: DefaultLoadControl = DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(
                minBufMs.toInt(),
                maxBufMs.toInt(),
                beforePlaybackMs.toInt(),
                bufferForPlaybackAfterReBufferMs.toInt()
            )
            .setBackBuffer(backBufferDurationMs.toInt(), false)
            .setTargetBufferBytes(200 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()


        val upstreamFactory = DefaultDataSourceFactory(this, getHttpDataSourceFactory(this))
        val dataSourceFactory =
            buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(this))

        val extractorsFactory = DefaultExtractorsFactory()
            .setAmrExtractorFlags(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setAdtsExtractorFlags(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
            .setConstantBitrateSeekingEnabled(true)

        val mediaSourceFactory: MediaSourceFactory =
            DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        val collector = createCollector()
        player = SimpleExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAnalyticsCollector(collector)
            .setBandwidthMeter(bandwidthMeter.build())
            .setLoadControl(loadControl)
            .build()

        val parametersBuilder = ParametersBuilder( /* context= */this)
        val trackSelectorParameters = parametersBuilder.build()

        trackSelector.parameters = trackSelectorParameters

        player.addListener(eventListener)
        player.addAnalyticsListener(EventLogger(trackSelector))

        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)

        // Bind the player to the view.
        playerView.player = player

//        val videoUri = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/gear0/fileSequence0.aac"
//        val videoUri = "https://storage.googleapis.com/exoplayer-test-media-1/gen-3/screens/dash-vod-single-segment/audio-141.mp4"
//        val videoUri = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
//        val videoUri = "https://wpr-ice.streamguys1.com/wpr-ideas-mp3-64"
        val videoUri = "https://sc6.gergosnet.com/puls00HD.mp3"
//        val videoUri = "https://storage.googleapis.com/exoplayer-test-media-1/ogg/play.ogg"

//        val videoUri = "https://streams.kqed.org/kqedradio"
//        val videoUri =
//            "https://a.files.bbci.co.uk/media/live/manifesto/audio/simulcast/hls/nonuk/sbr_low/ak/bbc_radio_fourfm.m3u8"

//        val proxy = HttpProxyCacheServer(this)
//        val proxyUrl: String = proxy.getProxyUrl(videoUri)


        // Build the media item.
        val mediaItem: MediaItem = MediaItem.fromUri(videoUri)
//        val mediaItem: MediaItem = MediaItem.fromUri(proxyUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        handler.postDelayed(update, SECONDS.toMillis(1))
    }

    private val handler = Handler()

    private val update: Runnable = object : Runnable {
        override fun run() {
            Log.d(
                "TEST",
                "update: player.isCurrentWindowSeekable = ${player.isCurrentWindowSeekable}"
            )
            handler.postDelayed(this, SECONDS.toMillis(1))
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(update)
    }

    private fun createCollector(): AnalyticsCollector {
        return object : AnalyticsCollector(Clock.DEFAULT) {

            override fun onLoadingChanged(isLoading: Boolean) {
                super.onLoadingChanged(isLoading)
            }

            override fun addListener(listener: AnalyticsListener) {
                super.addListener(listener)
            }

            override fun removeListener(listener: AnalyticsListener) {
                super.removeListener(listener)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
            }

            override fun onSurfaceSizeChanged(width: Int, height: Int) {
                super.onSurfaceSizeChanged(width, height)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
            }

            override fun setPlayer(player: Player, looper: Looper) {
                super.setPlayer(player, looper)
            }

            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                super.onAvailableCommandsChanged(availableCommands)
            }

            override fun onEvents(player: Player, events: Player.Events) {
                super.onEvents(player, events)

            }

            override fun onPositionDiscontinuity(reason: Int) {
                super.onPositionDiscontinuity(reason)
            }

            override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {
                super.onTimelineChanged(timeline, manifest, reason)
                Log.d(
                    "TEST",
                    "onTimelineChanged() called with: timeline = $timeline, manifest = $manifest, reason = $reason"
                )
            }

            override fun release() {
                super.release()
            }
        }
    }

    @Synchronized
    private fun getDownloadCache(context: Context): Cache {
        if (cache == null) {
            val cacheDir = context.getExternalFilesDir( /* type= */null)
            val downloadContentDirectory = File(cacheDir, DOWNLOAD_CONTENT_DIRECTORY)

//      LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(maxCacheSize);
//      SimpleCache simpleCache = new SimpleCache(new File(context.getCacheDir(), "media"), evictor);
            Log.d("TEST", "getDownloadCache: create SimpleCache")
//            val cacheEvictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val cacheEvictor = NoOpCacheEvictor()
            cache = SimpleCache(
                downloadContentDirectory, cacheEvictor, ExoDatabaseProvider(context)
            )
        }

        return cache!!
    }

    @Synchronized
    fun getHttpDataSourceFactory(context: Context): HttpDataSource.Factory {
        val cookieManager = CookieManager()
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        CookieHandler.setDefault(cookieManager)
        return DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT)
    }

    private fun buildReadOnlyCacheDataSource(
        upstreamFactory: DataSource.Factory, cache: Cache
    ): CacheDataSource.Factory {

//        CacheDataSourceFactory(
//            Injector.provideCache(this),
//            DefaultDataSourceFactory(this,
//                DefaultDataSourceFactory(this, httpDataSourceFactory)),
//            CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
//        )

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }


    private val eventListener: Player.Listener = object : Player.Listener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            super.onPlaybackParametersChanged(playbackParameters)
        }

        override fun onSeekProcessed() {
            super.onSeekProcessed()
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            super.onIsLoadingChanged(isLoading)
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            super.onPlayerError(error)
            Log.d("TEST", "onPlayerError() called with: error = $error")
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            super.onLoadingChanged(isLoading)
            Log.d("TEST", "onLoadingChanged() called with: isLoading = $isLoading")
        }

        override fun onPlaybackStateChanged(state: Int) {
            super.onPlaybackStateChanged(state)
            Log.d("TEST", "onPlaybackStateChanged() called with: state = $state")
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            super.onShuffleModeEnabledChanged(shuffleModeEnabled)
            Log.d(
                "TEST",
                "onShuffleModeEnabledChanged() called with: shuffleModeEnabled = $shuffleModeEnabled"
            )
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            super.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
            Log.d(
                "TEST",
                "onPlaybackSuppressionReasonChanged() called with: playbackSuppressionReason = $playbackSuppressionReason"
            )
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            super.onPlayerStateChanged(playWhenReady, playbackState)
            Log.d(
                "TEST",
                "onPlayerStateChanged() called with: playWhenReady = $playWhenReady, playbackState = $playbackState"
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            Log.d("TEST", "onIsPlayingChanged() called with: isPlaying = $isPlaying")
        }

        override fun onTracksChanged(
            trackGroups: TrackGroupArray,
            trackSelections: TrackSelectionArray
        ) {
            super.onTracksChanged(trackGroups, trackSelections)
            Log.d(
                "TEST",
                "onTracksChanged() called with: trackGroups = $trackGroups, trackSelections = $trackSelections"
            )
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            Log.d(
                "TEST",
                "onPlayWhenReadyChanged() called with: playWhenReady = $playWhenReady, reason = $reason"
            )
        }

        override fun onPositionDiscontinuity(reason: Int) {
            super.onPositionDiscontinuity(reason)
            Log.d("TEST", "onPositionDiscontinuity() called with: reason = $reason")
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            Log.d(
                "TEST",
                "onPositionDiscontinuity() called with: oldPosition = $oldPosition, newPosition = $newPosition, reason = $reason"
            )
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            super.onMediaMetadataChanged(mediaMetadata)
            Log.d("TEST", "onMediaMetadataChanged() called with: mediaMetadata = $mediaMetadata")
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            super.onRepeatModeChanged(repeatMode)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            super.onTimelineChanged(timeline, reason)
            Log.d("TEST", "onTimelineChanged() called with: timeline = $timeline, reason = $reason")
        }

        override fun onTimelineChanged(timeline: Timeline, manifest: Any?, reason: Int) {
            super.onTimelineChanged(timeline, manifest, reason)
        }

        override fun onStaticMetadataChanged(metadataList: MutableList<Metadata>) {
            super.onStaticMetadataChanged(metadataList)
            Log.d("TEST", "onStaticMetadataChanged() called with: metadataList = $metadataList")
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            super.onAvailableCommandsChanged(availableCommands)
            Log.d(
                "TEST",
                "onAvailableCommandsChanged() called with: availableCommands = $availableCommands"
            )
        }

        override fun onEvents(player: Player, events: Player.Events) {
            super.onEvents(player, events)
//            for (i in 0 until events.size()) {
//                Log.d("TEST", "onEvents: ${events[i]}")
//            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
        }
    }
    private val USER_AGENT = ("ExoPlayerDemo/"
            + ExoPlayerLibraryInfo.VERSION
            + " (Linux; Android "
            + Build.VERSION.RELEASE
            + ") "
            + ExoPlayerLibraryInfo.VERSION_SLASHY)


    private val DOWNLOAD_ACTION_FILE = "actions"
    private val DOWNLOAD_TRACKER_ACTION_FILE = "tracked_actions"
    private val DOWNLOAD_CONTENT_DIRECTORY = "downloads"
    private val MAX_CACHE_BYTES = 900 * 1024 * 1024.toLong()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}