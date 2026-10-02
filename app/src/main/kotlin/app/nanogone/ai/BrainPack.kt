package app.nanogone.ai

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest

/**
 * The deep brain pack: the big OSOR remover, too big for the APK, fetched once over Wi-Fi from
 * the project's "brains-v1" release and kept in the app's own folder. After that it works offline.
 */
class BrainPack(private val context: Context, val dir: File = File(context.getExternalFilesDir(null), "brains")) {

    /** One part of the deep brain's UNet, as written by tools/convert/convert_osor.py. */
    class Part(val file: String, val pops: Int, val h_is_skip: Boolean, val first: Boolean, val last: Boolean)

    class Manifest(val size: Int, val latent: Int, val parts: List<Part>, val files: List<Triple<String, Long, String>>) {
        val totalBytes: Long get() = files.sumOf { it.second }
    }

    sealed interface State {
        data object Missing : State
        data class Downloading(val done: Long, val total: Long) : State
        data object Checking : State
        data class Ready(val manifest: Manifest) : State
        data class Failed(val why: String) : State
    }

    fun manifest(): Manifest? = runCatching { parse(File(dir, MANIFEST).readText()) }.getOrNull()

    /** Ready when the manifest and every file are there with the right sizes (checked fully after download). */
    fun state(): State {
        val m = manifest() ?: return State.Missing
        val ok = m.files.all { (name, size, _) -> File(dir, name).length() == size } && File(dir, VERIFIED).exists()
        return if (ok) State.Ready(m) else State.Missing
    }

    /** Phones with enough memory for the deep brain (the Galaxy Ultras have 8 or 12 GB). */
    fun deviceCanRun(): Boolean {
        val mi = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return mi.totalMem >= 7_000_000_000L
    }

    /**
     * Fetch the pack (Wi-Fi only, resumes by itself), then check every file's fingerprint.
     * [onState] gets progress about once a second.
     */
    suspend fun download(onState: (State) -> Unit): State {
        dir.mkdirs()
        val manifestText = try {
            URL("$BASE/$MANIFEST").openStream().use { it.readBytes().decodeToString() }
        } catch (t: Throwable) {
            return State.Failed("Could not reach the brain pack page. Check Wi-Fi and try again.").also(onState)
        }
        val m = parse(manifestText)
        File(dir, VERIFIED).delete()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val ids = HashMap<Long, Triple<String, Long, String>>()
        for (f in m.files) {
            val target = File(dir, f.first)
            if (target.length() == f.second) continue
            target.delete()
            val req = DownloadManager.Request(Uri.parse("$BASE/${f.first}"))
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                .setAllowedOverMetered(false)
                .setTitle("NanoGone deep brain")
                .setDescription(f.first)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(File(dir, f.first + ".part")))
            ids[dm.enqueue(req)] = f
        }
        val already = m.files.filter { File(dir, it.first).length() == it.second }.sumOf { it.second }
        while (ids.isNotEmpty()) {
            var done = already
            val finished = ArrayList<Long>()
            dm.query(DownloadManager.Query().setFilterById(*ids.keys.toLongArray())).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    done += c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).coerceAtLeast(0)
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> finished.add(id)
                        DownloadManager.STATUS_FAILED -> {
                            ids.keys.forEach { dm.remove(it) }
                            return State.Failed("The download stopped. Try again on Wi-Fi.").also(onState)
                        }
                    }
                }
            }
            for (id in finished) {
                val f = ids.remove(id) ?: continue
                File(dir, f.first + ".part").renameTo(File(dir, f.first))
            }
            onState(State.Downloading(done, m.totalBytes))
            if (ids.isNotEmpty()) delay(1000)
        }
        return finish(m, manifestText, onState)
    }

    /**
     * Import the pack from files already on the phone (brains.json plus every brain file, for
     * example downloaded from the release page). Copies, then checks every fingerprint.
     */
    fun importFrom(resolver: android.content.ContentResolver, uris: List<Uri>, onState: (State) -> Unit): State {
        dir.mkdirs()
        File(dir, VERIFIED).delete()
        val named = uris.associateBy { u ->
            resolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: u.lastPathSegment.orEmpty().substringAfterLast('/')
        }
        val mUri = named[MANIFEST] ?: return State.Failed("Pick brains.json too, together with the brain files.").also(onState)
        val text = resolver.openInputStream(mUri)?.use { it.readBytes().decodeToString() }
            ?: return State.Failed("Could not read brains.json.").also(onState)
        val m = runCatching { parse(text) }.getOrNull() ?: return State.Failed("That brains.json is not a NanoGone brain list.").also(onState)
        val missing = m.files.filter { it.first !in named && File(dir, it.first).length() != it.second }
        if (missing.isNotEmpty()) return State.Failed("Missing ${missing.size} file(s), for example ${missing.first().first}. Pick them all.").also(onState)
        var done = 0L
        for ((name, size, _) in m.files) {
            val u = named[name]
            if (u == null) { done += size; continue }
            val target = File(dir, name)
            resolver.openInputStream(u)?.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done % (64L shl 20) < n) onState(State.Downloading(done, m.totalBytes))
                    }
                }
            } ?: return State.Failed("Could not read $name.").also(onState)
        }
        return finish(m, text, onState)
    }

    private fun finish(m: Manifest, text: String, onState: (State) -> Unit): State {
        onState(State.Checking)
        for ((name, size, sha) in m.files) {
            val file = File(dir, name)
            if (file.length() != size || sha256(file) != sha) {
                file.delete()
                return State.Failed("A brain file is damaged or from another version ($name). Try again.").also(onState)
            }
        }
        File(dir, MANIFEST).writeText(text)
        File(dir, VERIFIED).writeText("ok")
        Log.i("NanoGone", "brain pack ready: ${m.files.size} files, ${m.totalBytes / 1_000_000} MB")
        return State.Ready(m).also(onState)
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val BASE = "https://github.com/McDc777/NanoGone/releases/download/brains-v1"
        const val MANIFEST = "brains.json"
        private const val VERIFIED = "verified.txt"

        fun parse(text: String): Manifest {
            val j = JSONObject(text)
            val parts = j.getJSONArray("parts").let { a ->
                (0 until a.length()).map { i ->
                    val p = a.getJSONObject(i)
                    Part(p.getString("file"), p.getInt("pops"), p.getBoolean("h_is_skip"), p.getBoolean("first"), p.getBoolean("last"))
                }
            }
            val files = j.getJSONArray("files").let { a ->
                (0 until a.length()).map { i ->
                    val f = a.getJSONObject(i)
                    Triple(f.getString("name"), f.getLong("size"), f.getString("sha256"))
                }
            }
            return Manifest(j.getInt("size"), j.getInt("latent"), parts, files)
        }
    }
}
