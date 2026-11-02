package com.lumera.app

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pre-warms Coil's image decoder pool and memory cache during splash/profile selection
 * to eliminate first-scroll lag.
 */
@Singleton
class StartupOptimizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader
) {
    
    companion object {
        private const val TAG = "StartupOptimizer"
    }
    
    private var isWarmedUp = false
    private val warmupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Call this early in app startup (onCreate or during splash/profile screen)
     * Warms up image decoding pipeline without blocking UI
     */
    fun warmup() {
        if (isWarmedUp) return
        isWarmedUp = true
        
        warmupScope.launch {
            try {
                // Initialize the actual image pipeline. Allocating and recycling blank bitmaps
                // does not decode an image or populate Coil's decoder pool.
                // Pre-fill a small portion of memory cache
                // This ensures the cache data structures are allocated
                primeMemoryCache()
                
                if (com.lumera.app.BuildConfig.DEBUG) Log.d(TAG, "Warmup complete - decoder pool primed")
            } catch (e: Exception) {
                if (com.lumera.app.BuildConfig.DEBUG) Log.w(TAG, "Warmup failed (non-critical): ${e.message}")
            }
        }
    }
    
    /**
     * Makes a small allocation in the memory cache to ensure it's initialized
     */
    private suspend fun primeMemoryCache() {
        try {
            // Create a small image request with a data URI to avoid network
            // This forces the memory cache to initialize its data structures
            val dummyRequest = ImageRequest.Builder(context)
                .data("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")
                .size(1, 1)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build()
            
            imageLoader.execute(dummyRequest)
        } catch (e: Exception) {
            // Ignore
        }
    }
    
}
