// WiFiMonitor.kt
import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.google.gson.GsonBuilder
import okhttp3.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit

data class Result(val ping: Double? = null, val download: Double? = null, val upload: Double? = null)

class WiFiMonitor {
    @Parameter(names = ["--ping"], description = "Хост для пинга")
    var pingHost: String? = null

    @Parameter(names = ["--download"], description = "Размер загрузки в МБ")
    var downloadSize: Int? = null

    @Parameter(names = ["--upload"], description = "Размер отдачи в МБ")
    var uploadSize: Int? = null

    @Parameter(names = ["--count"], description = "Количество тестов")
    var count: Int = 1

    @Parameter(names = ["--output"], description = "Файл для сохранения")
    var outputFile: String? = null

    @Parameter(names = ["--color"], description = "Принудительный цвет")
    var color: Boolean = false

    private val results = mutableListOf<Result>()
    private val client = OkHttpClient()

    private fun measurePing(host: String): Double? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "4", host))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains("avg") || line!!.contains("rtt min/avg/max/mdev")) {
                    val parts = line!!.split("=")
                    if (parts.size > 1) {
                        val nums = parts[1].split("/")
                        if (nums.size > 1) {
                            return nums[1].toDoubleOrNull()
                        }
                    }
                }
            }
            null
        } catch (e: Exception) { null }
    }

    private fun measureDownload(): Double? {
        val url = "http://ipv4.download.thinkbroadband.com/5MB.zip"
        val request = Request.Builder().url(url).build()
        return try {
            val start = System.nanoTime()
            val response = client.newCall(request).execute()
            var total = 0L
            response.body?.byteStream()?.use { stream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    total += read
                }
            }
            val elapsed = (System.nanoTime() - start) / 1e9
            (total * 8) / (elapsed * 1_000_000)
        } catch (e: Exception) { null }
    }

    private fun measureUpload(): Double? {
        val url = "https://httpbin.org/post"
        val size = uploadSize!! * 1024 * 1024
        val data = ByteArray(size) { '0'.toByte() }
        val body = RequestBody.create(MediaType.parse("application/octet-stream"), data)
        val request = Request.Builder().url(url).post(body).build()
        return try {
            val start = System.nanoTime()
            client.newCall(request).execute()
            val elapsed = (System.nanoTime() - start) / 1e9
            (size * 8) / (elapsed * 1_000_000)
        } catch (e: Exception) { null }
    }

    private fun runTest(): Result {
        return Result(
            ping = pingHost?.let { measurePing(it) },
            download = if (downloadSize != null && downloadSize!! > 0) measureDownload() else null,
            upload = if (uploadSize != null && uploadSize!! > 0) measureUpload() else null
        )
    }

    private fun printResult(res: Result) {
        if (color) {
            if (res.ping != null) println("\u001B[32m🌐 Ping: ${String.format("%.2f", res.ping)} ms\u001B[0m")
            if (res.download != null) println("\u001B[34m📥 Download: ${String.format("%.2f", res.download)} Mbps\u001B[0m")
            if (res.upload != null) println("\u001B[36m📤 Upload: ${String.format("%.2f", res.upload)} Mbps\u001B[0m")
        } else {
            if (res.ping != null) println("Ping: ${String.format("%.2f", res.ping)} ms")
            if (res.download != null) println("Download: ${String.format("%.2f", res.download)} Mbps")
            if (res.upload != null) println("Upload: ${String.format("%.2f", res.upload)} Mbps")
        }
        println()
    }

    fun run() {
        for (i in 0 until count) {
            println("Тест ${i+1}/$count")
            val res = runTest()
            results.add(res)
            printResult(res)
            if (i < count - 1) Thread.sleep(1000)
        }
    }

    fun save() {
        if (outputFile == null) return
        val ext = outputFile!!.substringAfterLast('.').lowercase()
        val content = when (ext) {
            "json" -> GsonBuilder().setPrettyPrinting().create().toJson(results)
            "csv" -> {
                val sb = StringBuilder("ping,download,upload\n")
                results.forEach { sb.append("${it.ping},${it.download},${it.upload}\n") }
                sb.toString()
            }
            else -> results.joinToString("\n")
        }
        Files.write(Paths.get(outputFile), content.toByteArray())
        println("Результаты сохранены в $outputFile")
    }
}

fun main(args: Array<String>) {
    val monitor = WiFiMonitor()
    JCommander.newBuilder().addObject(monitor).build().parse(*args)
    if (monitor.pingHost == null && monitor.downloadSize == null && monitor.uploadSize == null) {
        monitor.pingHost = "8.8.8.8"
        monitor.downloadSize = 5
        monitor.uploadSize = 2
    }
    monitor.color = monitor.color || System.console() != null
    monitor.run()
    if (monitor.outputFile != null) monitor.save()
}
