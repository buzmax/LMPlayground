package com.druk.lmplayground.data

import android.content.Context
import com.druk.lmplayground.models.ModelInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.ContinuationInterceptor

class ModelDownloadManager private constructor(context: Context) {

    private val cronetEngine = CronetEngine.Builder(context)
        .enableHttp2(true)
        .enableQuic(true)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private val appContext = context.applicationContext
    private val downloadDir = File(appContext.filesDir, "models")
    
    init {
        downloadDir.mkdirs()
    }

    data class DownloadStatus(
        val progress: Float,
        val state: State,
        val error: String? = null
    ) {
        enum class State { QUEUED, DOWNLOADING, COMPLETED, ERROR }
    }

    fun download(model: ModelInfo): Flow<DownloadStatus> = flow {
        val url = model.remoteUri?.toString() ?: throw IllegalArgumentException("Invalid URI")
        val filename = model.remoteUri.lastPathSegment ?: throw IllegalArgumentException("Invalid filename")
        
        if (activeDownloads.containsKey(url)) {
            emit(DownloadStatus(0f, DownloadStatus.State.QUEUED))
            return@flow
        }

        val outputFile = File(downloadDir, filename)
        if (outputFile.exists()) {
            emit(DownloadStatus(1f, DownloadStatus.State.COMPLETED))
            return@flow
        }

        val downloadJob = scope.launch {
            try {
                emit(DownloadStatus(0f, DownloadStatus.State.DOWNLOADING))
                
                val channel = Channel<Float>(Channel.BUFFERED)
                val callback = object : UrlRequest.Callback() {
                    private val receivedBytes = AtomicLong(0)
                    private var totalBytes = 0L
                    private val buffer = ByteBuffer.allocateDirect(32 * 1024)
                    private val fileOutputStream = FileOutputStream(outputFile)

                    override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                        request.followRedirect()
                    }

                    override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                        totalBytes = info.allHeaders["Content-Length"]?.firstOrNull()?.toLong() ?: 0
                        request.read(buffer)
                    }

                    override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, buffer: ByteBuffer) {
                        buffer.flip()
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        fileOutputStream.write(bytes)
                        
                        receivedBytes.addAndGet(bytes.size.toLong())
                        val progress = if (totalBytes > 0) {
                            receivedBytes.get().toFloat() / totalBytes
                        } else 0f
                        
                        channel.trySend(progress)
                        
                        buffer.clear()
                        request.read(buffer)
                    }

                    override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                        channel.close()
                        fileOutputStream.close()
                    }

                    override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
                        channel.close(error)
                        fileOutputStream.close()
                        outputFile.delete()
                    }
                }

                val request = cronetEngine.newUrlRequestBuilder(
                    url,
                    callback,
                    (scope.coroutineContext[ContinuationInterceptor] as ExecutorCoroutineDispatcher).asExecutor()
                ).build()
                
                request.start()

                for (progress in channel) {
                    emit(DownloadStatus(progress, DownloadStatus.State.DOWNLOADING))
                }

                emit(DownloadStatus(1f, DownloadStatus.State.COMPLETED))
            } catch (e: Exception) {
                emit(DownloadStatus(0f, DownloadStatus.State.ERROR, e.message))
                outputFile.delete()
            } finally {
                activeDownloads.remove(url)
            }
        }
        
        activeDownloads[url] = downloadJob
        downloadJob.join()
    }

    companion object {
        @Volatile
        private var INSTANCE: ModelDownloadManager? = null

        fun getInstance(context: Context): ModelDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelDownloadManager(context).also { INSTANCE = it }
            }
        }
    }
}