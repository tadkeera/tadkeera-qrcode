package com.tadkeera.eventtickets.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.tadkeera.eventtickets.R
import com.tadkeera.eventtickets.data.entities.ForgeryLog
import com.tadkeera.eventtickets.data.entities.ScanLog
import com.tadkeera.eventtickets.data.entities.Ticket
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Attendance report sent to Telegram. Order of sections:
 * 1. Forgery attempts  2. Attended guests  3. Guests who did not attend.
 * Dates are always written with English digits.
 */
object AttendanceReport {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 36f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN
    private const val ROW_H = 18f

    private val dateFmt = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ENGLISH)

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f; isFakeBoldText = true; color = Color.rgb(17, 30, 56)
    }
    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f; isFakeBoldText = true; color = Color.rgb(255, 109, 0)
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; isFakeBoldText = true; color = Color.rgb(17, 30, 56)
    }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f; color = Color.rgb(38, 50, 56)
    }
    private val fillPaint = Paint().apply { color = Color.rgb(241, 243, 245) }
    private val linePaint = Paint().apply { color = Color.rgb(176, 190, 197); strokeWidth = 0.7f }

    private class Pager(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = MARGIN
        private var pageNo = 0

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            page = p
            canvas = p.canvas
            y = MARGIN
        }

        fun ensure(height: Float, onNewPage: () -> Unit = {}) {
            if (canvas == null || y + height > PAGE_H - MARGIN) {
                newPage()
                onNewPage()
            }
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
        }
    }

    fun build(
        context: Context,
        eventName: String,
        eventCode: String,
        tickets: List<Ticket>,
        scans: List<ScanLog>,
        forgeries: List<ForgeryLog>,
        outFile: File
    ): File {
        val doc = PdfDocument()
        val pager = Pager(doc)
        pager.newPage()
        val c = pager.canvas!!

        // ---- Header with logo
        ContextCompat.getDrawable(context, R.drawable.ic_tadkeera_logo)?.let { drawable ->
            val logo = drawable.toBitmap(72, 72)
            c.drawBitmap(logo, MARGIN, pager.y, null)
        }
        c.drawText("تقرير حضور المناسبة", MARGIN + 86f, pager.y + 26f, titlePaint)
        c.drawText("Event: $eventName", MARGIN + 86f, pager.y + 48f, cellPaint)
        c.drawText("Event code: ${eventCode.ifEmpty { "-" }}", MARGIN + 86f, pager.y + 62f, cellPaint)
        c.drawText("Generated: ${dateFmt.format(System.currentTimeMillis())}", MARGIN + 86f, pager.y + 76f, cellPaint)
        pager.y += 92f

        // ---- Derived data
        val inScans = scans.filter { it.type == "IN" }
        val outScans = scans.filter { it.type == "OUT" }
        val inByQr = inScans.groupBy { it.qrCodeData }
        val outByQr = outScans.groupBy { it.qrCodeData }
        val sorted = tickets.sortedBy { it.ticketNumber }
        val attended = sorted.filter { inByQr.containsKey(it.qrCodeData) }
        val absent = sorted.filter { !inByQr.containsKey(it.qrCodeData) }

        // ---- 1. Forgery attempts
        val forgeryRows = forgeries.sortedBy { it.timestamp }.map {
            listOf(dateFmt.format(it.timestamp), it.preview, it.reason)
        }
        table(pager, "1) محاولات التزوير (${forgeries.size})",
            listOf("الوقت", "الكود", "السبب"), listOf(120f, 110f, 293f), forgeryRows)

        // ---- 2. Attended
        val attendedRows = attended.mapIndexed { index, t ->
            val ins = inByQr[t.qrCodeData].orEmpty()
            val outs = outByQr[t.qrCodeData].orEmpty()
            listOf(
                (index + 1).toString(),
                t.ticketNumber.toString(),
                t.guestName.ifEmpty { "-" },
                ins.size.toString(),
                dateFmt.format(ins.minOf { it.timestamp }),
                outs.maxOfOrNull { it.timestamp }?.let { dateFmt.format(it) } ?: "-"
            )
        }
        table(pager, "2) الحاضرون (${attended.size})",
            listOf("#", "رقم التذكرة", "الاسم", "الدخول", "أول دخول", "آخر خروج"),
            listOf(30f, 62f, 150f, 52f, 118f, 111f), attendedRows)

        // ---- 3. Absent
        val absentRows = absent.mapIndexed { index, t ->
            listOf((index + 1).toString(), t.ticketNumber.toString(), t.guestName.ifEmpty { "-" })
        }
        table(pager, "3) الذين لم يحضروا (${absent.size})",
            listOf("#", "رقم التذكرة", "الاسم"), listOf(40f, 90f, 393f), absentRows)

        pager.finish()
        outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        outFile.outputStream().use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    private fun table(
        pager: Pager,
        title: String,
        headers: List<String>,
        widths: List<Float>,
        rows: List<List<String>>
    ) {
        pager.ensure(40f)
        pager.canvas!!.drawText(title, MARGIN, pager.y + 14f, sectionPaint)
        pager.y += 22f

        fun drawHeader() {
            val c = pager.canvas!!
            c.drawRect(MARGIN, pager.y, MARGIN + CONTENT_W, pager.y + ROW_H, fillPaint)
            var x = MARGIN
            headers.forEachIndexed { i, h ->
                c.drawText(fit(h, widths[i] - 6f, headPaint), x + 3f, pager.y + 13f, headPaint)
                x += widths[i]
            }
            pager.y += ROW_H
        }

        pager.ensure(ROW_H * 2) { }
        drawHeader()
        if (rows.isEmpty()) {
            pager.ensure(ROW_H)
            pager.canvas!!.drawText("لا يوجد", MARGIN + 3f, pager.y + 13f, cellPaint)
            pager.y += ROW_H
        }
        rows.forEach { row ->
            pager.ensure(ROW_H) { drawHeader() }
            val c = pager.canvas!!
            var x = MARGIN
            row.forEachIndexed { i, text ->
                c.drawText(fit(text, widths[i] - 6f, cellPaint), x + 3f, pager.y + 13f, cellPaint)
                x += widths[i]
            }
            c.drawLine(MARGIN, pager.y + ROW_H, MARGIN + CONTENT_W, pager.y + ROW_H, linePaint)
            pager.y += ROW_H
        }
        pager.y += 16f
    }

    /** Shortens [text] with an ellipsis so it fits inside [maxWidth]. */
    private fun fit(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
