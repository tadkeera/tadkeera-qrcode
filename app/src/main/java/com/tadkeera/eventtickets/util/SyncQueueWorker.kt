package com.tadkeera.eventtickets.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.room.Room
import com.tadkeera.eventtickets.data.TadkeeraDatabase
import com.tadkeera.eventtickets.data.entities.SyncQueueItem
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class SyncQueueWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = Room.databaseBuilder(appContext, TadkeeraDatabase::class.java, "tadkeera_db")
                .fallbackToDestructiveMigration()
                .build()
            
            val queueDao = db.syncQueueDao()
            val items = queueDao.getQueueItems()
            
            if (items.isEmpty()) {
                db.close()
                return Result.success()
            }

            val prefs = appContext.getSharedPreferences("TadkeeraTelegram", Context.MODE_PRIVATE)
            val token = prefs.getString("bot_token", "8855448849:AAEOMwTZFNlZ2dRFwbsPdUjsMVVQDwg6_R0") ?: "8855448849:AAEOMwTZFNlZ2dRFwbsPdUjsMVVQDwg6_R0"
            val channelId = prefs.getString("channel_id", "-1004389676098") ?: "-1004389676098"

            for (item in items) {
                val success = when (item.type) {
                    "backup" -> uploadFile(token, channelId, appContext.getDatabasePath("tadkeera_db"), "نسخة احتياطية لقاعدة البيانات الكلية للموقع (.db)\r\n")
                    "pdf" -> uploadFile(token, channelId, File(item.filePath), "تقرير تذاكر مناسبة: ${item.eventName} (PDF)\r\n")
                    else -> true
                }
                if (success) {
                    queueDao.dequeue(item)
                }
            }

            db.close()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun uploadFile(token: String, channelId: String, file: File, caption: String): Boolean {
        if (!file.exists()) return true
        return try {
            val boundary = "Boundary-" + UUID.randomUUID().toString()
            val url = URL("https://api.telegram.org/bot$token/sendDocument")
            val conn = url.openConnection() as HttpURLConnection
            conn.doOutput = true
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            conn.outputStream.use { out ->
                out.write(("--$boundary\r\n").toByteArray())
                out.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n").toByteArray())
                out.write(("$channelId\r\n").toByteArray())

                out.write(("--$boundary\r\n").toByteArray())
                out.write(("Content-Disposition: form-data; name=\"caption\"\r\n\r\n").toByteArray())
                out.write((caption).toByteArray())

                out.write(("--$boundary\r\n").toByteArray())
                out.write(("Content-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"\r\n").toByteArray())
                out.write(("Content-Type: application/octet-stream\r\n\r\n").toByteArray())
                out.write(file.readBytes())
                out.write(("\r\n").toByteArray())

                out.write(("--$boundary--\r\n").toByteArray())
            }
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (e: Exception) {
            false
        }
    }
}
