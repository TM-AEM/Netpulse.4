package com.example.model

data class NetworkUsage(
    val downloadBytes: Long,
    val uploadBytes: Long,
    val startTime: Long,
    val endTime: Long,
    val networkType: NetworkType
) {
    val totalBytes: Long = safeAdd(downloadBytes, uploadBytes)

    val downloadRatio: Float
        get() = if (totalBytes > 0) downloadBytes.toFloat() / totalBytes.toFloat() else 0f

    val uploadRatio: Float
        get() = if (totalBytes > 0) uploadBytes.toFloat() / totalBytes.toFloat() else 0f

    operator fun plus(other: NetworkUsage): NetworkUsage {
        return NetworkUsage(
            downloadBytes = safeAdd(this.downloadBytes, other.downloadBytes),
            uploadBytes = safeAdd(this.uploadBytes, other.uploadBytes),
            startTime = if (this.startTime == 0L) other.startTime else if (other.startTime == 0L) this.startTime else minOf(this.startTime, other.startTime),
            endTime = maxOf(this.endTime, other.endTime),
            networkType = if (this.networkType == other.networkType) this.networkType else NetworkType.TOTAL
        )
    }

    companion object {
        fun safeAdd(a: Long, b: Long): Long {
            return try {
                Math.addExact(a, b)
            } catch (e: ArithmeticException) {
                if (a > 0L || b > 0L) Long.MAX_VALUE else Long.MIN_VALUE
            }
        }

        fun zero(networkType: NetworkType, startTime: Long = 0L, endTime: Long = 0L): NetworkUsage {
            return NetworkUsage(
                downloadBytes = 0L,
                uploadBytes = 0L,
                startTime = startTime,
                endTime = endTime,
                networkType = networkType
            )
        }
    }
}
