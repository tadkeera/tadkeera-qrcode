package com.tadkeera.eventtickets.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.pdf.PdfContentByte
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfStamper
import com.itextpdf.text.pdf.PdfWriter
import com.opencsv.CSVReader
import com.tadkeera.eventtickets.data.TadkeeraDatabase
import com.tadkeera.eventtickets.data.dao.TicketDao
import com.tadkeera.eventtickets.data.entities.Event
import com.tadkeera.eventtickets.data.entities.GuestName
import com.tadkeera.eventtickets.data.entities.Ticket
import com.tadkeera.eventtickets.data.entities.TicketDesign
import com.tadkeera.eventtickets.data.repository.TicketRepository
import com.tadkeera.eventtickets.util.EventCodeGenerator
import com.tadkeera.eventtickets.util.QRCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

sealed class ScanResult {
    object Idle : ScanResult()
    data class Success(val ticket: Ticket) : ScanResult()
    data class Duplicate(val ticket: Ticket, val lastScannedAt: Long) : ScanResult()
    object Invalid : ScanResult()
}

data class SyncPayload(
    val event: Event,
    val tickets: List<Ticket>
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: TicketRepository,
    private val database: TadkeeraDatabase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val events: StateFlow<List<Event>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanResult = MutableStateFlow<ScanResult>(ScanResult.Idle)
    val scanResult: StateFlow<ScanResult> = _scanResult.asStateFlow()

    init {
        // Run database backup automatically when the app is opened
        backupDatabase()
    }

    fun resetScanResult() {
        _scanResult.value = ScanResult.Idle
    }

    fun createEvent(name: String, date: Long) {
        viewModelScope.launch {
            repository.addEvent(Event(eventName = name, eventDate = date))
            backupDatabase()
            uploadBackupToTelegram()
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            repository.deleteEventFully(event)
            backupDatabase()
            uploadBackupToTelegram()
        }
    }

    fun getEventFlow(eventId: String): Flow<Event?> = flow {
        emit(repository.getEvent(eventId))
    }

    fun getTicketsFlow(eventId: String): Flow<List<Ticket>> = repository.getTickets(eventId)

    fun getDesignsFlow(eventId: String): Flow<List<TicketDesign>> = repository.getDesigns(eventId)

    fun saveDesign(
        eventId: String,
        name: String,
        templatePath: String,
        qrCodeX: Float, qrCodeY: Float, qrCodeWidth: Float, qrCodeHeight: Float,
        qrCodeRotation: Float = 0f,
        eventCodeX: Float, eventCodeY: Float, eventCodeSize: Float,
        guestNameX: Float, guestNameY: Float, guestNameSize: Float,
        showGuestName: Boolean,
        isDefault: Boolean = true,
        eventCodeColor: String = "#C62828",
        guestNameColor: String = "#2E7D32",
        guestNameFont: String = "arial.ttf",
        eventCodeWidth: Float = 0.3f,
        eventCodeHeight: Float = 0.08f,
        guestNameWidth: Float = 0.4f,
        guestNameHeight: Float = 0.08f,
        eventCodeWeight: String = "bold",
        guestNameWeight: String = "bold"
    ) {
        viewModelScope.launch {
            val design = TicketDesign(
                eventId = eventId,
                designName = name,
                pdfTemplatePath = templatePath,
                qrCodeX = qrCodeX,
                qrCodeY = qrCodeY,
                qrCodeWidth = qrCodeWidth,
                qrCodeHeight = qrCodeHeight,
                qrCodeRotation = qrCodeRotation,
                eventCodeX = eventCodeX,
                eventCodeY = eventCodeY,
                eventCodeSize = eventCodeSize,
                guestNameX = guestNameX,
                guestNameY = guestNameY,
                guestNameSize = guestNameSize,
                showGuestName = showGuestName,
                isDefault = isDefault,
                eventCodeColor = eventCodeColor,
                guestNameColor = guestNameColor,
                guestNameFont = guestNameFont,
                eventCodeWidth = eventCodeWidth,
                eventCodeHeight = eventCodeHeight,
                guestNameWidth = guestNameWidth,
                guestNameHeight = guestNameHeight,
                eventCodeWeight = eventCodeWeight,
                guestNameWeight = guestNameWeight
            )
            repository.addDesign(design)
            backupDatabase()
            uploadBackupToTelegram()
        }
    }

    fun deleteOrder(eventCode: String) {
        viewModelScope.launch {
            repository.deleteOrder(eventCode)
            // Delete internal PDF if exists
            try {
                val internalFile = getInternalPdfFile(eventCode)
                if (internalFile.exists()) {
                    internalFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            backupDatabase()
            uploadBackupToTelegram()
        }
    }

    fun uploadCSV(eventId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val reader = CSVReader(InputStreamReader(inputStream))
                val guestNames = mutableListOf<GuestName>()
                var nextLine: Array<String>?
                while (reader.readNext().also { nextLine = it } != null) {
                    val name = nextLine?.firstOrNull()?.trim() ?: continue
                    if (name.isNotEmpty()) {
                        guestNames.add(GuestName(eventId = eventId, name = name))
                    }
                }
                reader.close()
                if (guestNames.isNotEmpty()) {
                    repository.addGuestNames(guestNames)
                    backupDatabase()
                    uploadBackupToTelegram()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Ticket scanner - Isolated by Event ID
    fun scanTicket(eventId: String, qrCodeData: String) {
        viewModelScope.launch {
            val ticket = repository.getTicketByQR(qrCodeData)
            if (ticket == null || ticket.eventId != eventId) {
                _scanResult.value = ScanResult.Invalid
            } else {
                if (ticket.isScanned) {
                    val updated = ticket.copy(
                        scanCount = ticket.scanCount + 1
                    )
                    repository.updateTicket(updated)
                    backupDatabase()
                    uploadBackupToTelegram()
                    _scanResult.value = ScanResult.Duplicate(updated, ticket.scannedAt ?: System.currentTimeMillis())
                } else {
                    val updated = ticket.copy(
                        isScanned = true,
                        scannedAt = System.currentTimeMillis(),
                        scanCount = 1
                    )
                    repository.updateTicket(updated)
                    backupDatabase()
                    uploadBackupToTelegram()
                    _scanResult.value = ScanResult.Success(updated)
                }
            }
        }
    }

    // Helper to get internal PDF path
    fun getInternalPdfFile(eventCode: String): File {
        val dir = File(context.filesDir, "Tadkeera/GeneratedPDFs")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${eventCode}.pdf")
    }

    // Function to download/copy the internal PDF to event shared storage
    fun downloadPdfToSharedStorage(eventCode: String, eventName: String, onResult: (Boolean, File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val internalFile = getInternalPdfFile(eventCode)
                if (!internalFile.exists()) {
                    withContext(Dispatchers.Main) { onResult(false, null) }
                    return@launch
                }
                
                // Shared storage event directory
                val appDir = File(Environment.getExternalStorageDirectory(), "Tadkeera")
                val cleanEventName = eventName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                var eventDir = File(appDir, cleanEventName)
                
                if (!eventDir.exists()) {
                    val created = eventDir.mkdirs()
                    if (!created) {
                        eventDir = File(context.getExternalFilesDir(null), "Tadkeera/$cleanEventName")
                        if (!eventDir.exists()) eventDir.mkdirs()
                    }
                }
                
                val destFile = File(eventDir, "${eventCode}.pdf")
                internalFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    onResult(true, destFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false, null)
                }
            }
        }
    }

    // Ticket generation & PDF rendering
    suspend fun issueTickets(
        eventId: String,
        count: Int,
        design: TicketDesign?,
        guestNamesList: List<String>? = null,
        onComplete: (File?) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val event = repository.getEvent(eventId) ?: return@withContext
                val eventCode = EventCodeGenerator.generateEventCode()
                
                val tickets = mutableListOf<Ticket>()
                val charPool : List<Char> = ('A'..'Z') + ('0'..'9')
                
                val resolvedNames = guestNamesList ?: emptyList()
                
                for (i in 1..count) {
                    val qrCode = (1..24)
                        .map { kotlin.random.Random.nextInt(0, charPool.size).let { charPool[it] } }
                        .joinToString("")
                    val guestName = if (i <= resolvedNames.size) resolvedNames[i - 1] else ""
                    tickets.add(
                        Ticket(
                            eventId = eventId,
                            eventCode = eventCode,
                            ticketNumber = i,
                            qrCodeData = qrCode,
                            guestName = guestName
                        )
                    )
                }
                
                // Insert tickets
                repository.addTickets(tickets)
                
                // Update event with code
                repository.updateEvent(event.copy(eventCode = eventCode))
                backupDatabase()
                
                // We now save the generated PDF internally first as requested!
                val internalDir = File(context.filesDir, "Tadkeera/GeneratedPDFs")
                if (!internalDir.exists()) internalDir.mkdirs()
                val outputFile = File(internalDir, "${eventCode}.pdf")
                
                if (design != null && design.pdfTemplatePath.isNotEmpty()) {
                    val pdfFile = File(design.pdfTemplatePath)
                    if (pdfFile.exists()) {
                        generatePdfTickets(tickets, pdfFile, outputFile, design)
                        uploadPdfToTelegram(outputFile, event.eventName)
                        uploadBackupToTelegram()
                        withContext(Dispatchers.Main) {
                            onComplete(outputFile)
                        }
                        return@withContext
                    }
                }
                
                // Fallback: Generate simple tickets without template if no design uploaded
                generateSimplePdfTickets(tickets, outputFile)
                uploadPdfToTelegram(outputFile, event.eventName)
                uploadBackupToTelegram()
                withContext(Dispatchers.Main) {
                    onComplete(outputFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(null)
                }
            }
        }
    }

    private fun generateQRCodeImage(text: String, width: Int = 150, height: Int = 150): Image {
        val hints = java.util.HashMap<com.google.zxing.EncodeHintType, Any>()
        hints[com.google.zxing.EncodeHintType.MARGIN] = 0 // Remove white quiet zone margins!
        
        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            text,
            com.google.zxing.BarcodeFormat.QR_CODE,
            width,
            height,
            hints
        )
        
        val baos = ByteArrayOutputStream()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT)
            }
        }
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bitmap.recycle() // Recycle immediately to free memory!
        
        return Image.getInstance(baos.toByteArray())
    }

    private fun renderTextToImage(text: String, fontSize: Float, fontColorHex: String, fontFileName: String, pdfWidth: Float, fontWeight: String = "bold"): Image {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        
        val parsedColor = try {
            android.graphics.Color.parseColor(fontColorHex)
        } catch (e: Exception) {
            android.graphics.Color.BLACK
        }
        paint.color = parsedColor
        
        // Load custom typeface from Assets
        val typeface = try {
            val baseTypeface = android.graphics.Typeface.createFromAsset(context.assets, "fonts/$fontFileName")
            val isBold = fontWeight == "bold" || fontWeight == "extrabold"
            if (fontWeight == "extrabold") {
                paint.isFakeBoldText = true
            }
            android.graphics.Typeface.create(baseTypeface, if (isBold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        } catch (e: Exception) {
            try {
                android.graphics.Typeface.createFromAsset(context.assets, "fonts/arial.ttf")
            } catch (ex: Exception) {
                android.graphics.Typeface.DEFAULT_BOLD
            }
        }
        paint.typeface = typeface
        
        // Base Font Size
        val calculatedSize = fontSize * 38f
        paint.textSize = calculatedSize
        
        // Measure exact text bounds
        val textWidth = paint.measureText(text).coerceAtLeast(10f)
        val fontMetrics = paint.fontMetrics
        val textHeight = (fontMetrics.descent - fontMetrics.ascent).coerceAtLeast(10f)
        
        // Create matching transparent bitmap with some padding
        val paddingX = 20
        val paddingY = 10
        val bmpW = (textWidth + paddingX * 2).toInt()
        val bmpH = (textHeight + paddingY * 2).toInt()
        
        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        
        paint.textAlign = android.graphics.Paint.Align.LEFT
        val x = paddingX.toFloat()
        val y = paddingY.toFloat() - fontMetrics.ascent
        
        canvas.drawText(text, x, y, paint)
        
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bitmap.recycle()
        
        val img = Image.getInstance(baos.toByteArray())
        return img
    }

    private fun generatePdfTickets(
        tickets: List<Ticket>,
        templateFile: File,
        outputFile: File,
        design: TicketDesign
    ) {
        var tempMultiPageFile: File? = null
        try {
            // Ensure parent directory exists
            outputFile.parentFile?.let {
                if (!it.exists()) it.mkdirs()
            }

            // 1. Create temporary multi-page PDF containing cloned blank pages of the template
            tempMultiPageFile = File(context.cacheDir, "temp_multi_page_${System.currentTimeMillis()}.pdf")
            val document = Document()
            val copy = com.itextpdf.text.pdf.PdfCopy(document, FileOutputStream(tempMultiPageFile))
            document.open()
            
            val templateReader = PdfReader(templateFile.absolutePath)
            val importedPage = copy.getImportedPage(templateReader, 1)
            for (i in 1..tickets.size) {
                copy.addPage(importedPage)
            }
            templateReader.close()
            document.close()
            copy.close()

            // 2. Open single PdfStamper over the cloned blank pages, injecting QR code and text on each page
            val reader2 = PdfReader(tempMultiPageFile.absolutePath)
            val stamper = PdfStamper(reader2, FileOutputStream(outputFile))
            
            for (i in 0 until tickets.size) {
                val ticket = tickets[i]
                val pageNum = i + 1
                val overContent = stamper.getOverContent(pageNum)
                
                // Draw QR Code using zero margins generator
                val qrImage = generateQRCodeImage(ticket.qrCodeData, 150, 150)
                
                val pageSize = reader2.getPageSize(pageNum)
                val pdfWidth = pageSize.width
                val pdfHeight = pageSize.height
                
                // Exact same pixel-for-pixel coordinate mapping matching the designed preview!
                val qX = design.qrCodeX * pdfWidth
                val qY = (1.0f - design.qrCodeY - design.qrCodeHeight) * pdfHeight
                val qW = design.qrCodeWidth * pdfWidth
                val qH = design.qrCodeHeight * pdfHeight
                
                qrImage.setAbsolutePosition(qX, qY)
                qrImage.scaleAbsolute(qW, qH)
                
                // Set rotation angle matching the design setting!
                qrImage.setRotationDegrees(design.qrCodeRotation)
                
                overContent.addImage(qrImage)
                
                // Draw Event Code & Ticket Number at exactly designed positions
                val ecBoxW = design.eventCodeWidth * pdfWidth
                val ecBoxH = design.eventCodeHeight * pdfHeight
                val ecBoxX = design.eventCodeX * pdfWidth
                val ecBoxY = (1.0f - design.eventCodeY - design.eventCodeHeight) * pdfHeight
                
                val eventCodeText = "${ticket.eventCode} - ${ticket.ticketNumber}"
                val ecImg = renderTextToImage(eventCodeText, design.eventCodeSize, design.eventCodeColor, "arial.ttf", pdfWidth, design.eventCodeWeight)
                
                // Scale proportionally to fit inside ecBox
                val ecImgW = ecImg.width
                val ecImgH = ecImg.height
                val ecScale = (ecBoxW / ecImgW).coerceAtMost(ecBoxH / ecImgH)
                val finalEcW = ecImgW * ecScale
                val finalEcH = ecImgH * ecScale
                
                // Center inside box
                val finalEcX = ecBoxX + (ecBoxW - finalEcW) / 2f
                val finalEcY = ecBoxY + (ecBoxH - finalEcH) / 2f
                
                ecImg.setAbsolutePosition(finalEcX, finalEcY)
                ecImg.scaleAbsolute(finalEcW, finalEcH)
                overContent.addImage(ecImg)
                
                // Draw Guest Name if not empty (always draw to bypass any toggle bugs!)
                if (ticket.guestName.isNotEmpty()) {
                    val gBoxW = design.guestNameWidth * pdfWidth
                    val gBoxH = design.guestNameHeight * pdfHeight
                    val gBoxX = design.guestNameX * pdfWidth
                    val gBoxY = (1.0f - design.guestNameY - design.guestNameHeight) * pdfHeight
                    
                    val gImg = renderTextToImage(ticket.guestName, design.guestNameSize, design.guestNameColor, design.guestNameFont, pdfWidth, design.guestNameWeight)
                    
                    // Scale proportionally to fit inside gBox
                    val gImgW = gImg.width
                    val gImgH = gImg.height
                    val gScale = (gBoxW / gImgW).coerceAtMost(gBoxH / gImgH)
                    val finalGW = gImgW * gScale
                    val finalGH = gImgH * gScale
                    
                    // Center inside box
                    val finalGX = gBoxX + (gBoxW - finalGW) / 2f
                    val finalGY = gBoxY + (gBoxH - finalGH) / 2f
                    
                    gImg.setAbsolutePosition(finalGX, finalGY)
                    gImg.scaleAbsolute(finalGW, finalGH)
                    overContent.addImage(gImg)
                }
            }
            
            stamper.close()
            reader2.close()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to simple PDF generation if template processing fails
            generateSimplePdfTickets(tickets, outputFile)
        } finally {
            try {
                tempMultiPageFile?.let {
                    if (it.exists()) it.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun generateSimplePdfTickets(tickets: List<Ticket>, outputFile: File) {
        try {
            // Ensure parent directory exists
            outputFile.parentFile?.let {
                if (!it.exists()) it.mkdirs()
            }

            val document = Document()
            PdfWriter.getInstance(document, FileOutputStream(outputFile))
            document.open()
            
            for (ticket in tickets) {
                document.newPage()
                
                val fontBase = com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA_BOLD, com.itextpdf.text.pdf.BaseFont.CP1252, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED)
                val p1 = com.itextpdf.text.Paragraph("TADKEERA (تذكرة)", com.itextpdf.text.Font(fontBase, 24f))
                p1.alignment = com.itextpdf.text.Element.ALIGN_CENTER
                document.add(p1)
                
                val p2 = com.itextpdf.text.Paragraph("Event Code: ${ticket.eventCode} | Ticket No: ${ticket.ticketNumber}", com.itextpdf.text.Font(fontBase, 16f))
                p2.alignment = com.itextpdf.text.Element.ALIGN_CENTER
                document.add(p2)
                
                if (ticket.guestName.isNotEmpty()) {
                    val pName = com.itextpdf.text.Paragraph("Guest Name: ${ticket.guestName}", com.itextpdf.text.Font(fontBase, 14f))
                    pName.alignment = com.itextpdf.text.Element.ALIGN_CENTER
                    document.add(pName)
                }
                
                // Add QR Code using native generator
                val qrImage = generateQRCodeImage(ticket.qrCodeData, 150, 150)
                qrImage.scaleAbsolute(150f, 150f)
                qrImage.alignment = com.itextpdf.text.Element.ALIGN_CENTER
                document.add(qrImage)
            }
            
            document.close()
        } catch (e: Exception) {
            e.printStackTrace()
            // Bulletproof fallback to app's safe external internal folder if storage is blocked
            try {
                val fallbackFile = File(context.getExternalFilesDir(null), outputFile.name)
                val document = Document()
                PdfWriter.getInstance(document, FileOutputStream(fallbackFile))
                document.open()
                document.close()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun exportEventData(event: Event, onComplete: (Boolean, File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Query tickets for this event (Fetch ALL tickets, regardless of eventCode!)
                val ticketsFlow = repository.getTickets(event.eventId)
                val tickets = ticketsFlow.first()
                
                val payload = SyncPayload(event = event, tickets = tickets)
                val gson = Gson()
                val jsonString = gson.toJson(payload)
                
                // Determine output directory based on app folder structure inside the event subfolder
                val appDir = File(Environment.getExternalStorageDirectory(), "Tadkeera")
                val cleanEventName = event.eventName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                var eventDir = File(appDir, cleanEventName)
                
                try {
                    if (!eventDir.exists()) {
                        val created = eventDir.mkdirs()
                        if (!created) {
                            eventDir = File(context.getExternalFilesDir(null), "Tadkeera/$cleanEventName")
                            if (!eventDir.exists()) eventDir.mkdirs()
                        }
                    }
                } catch (e: Exception) {
                    eventDir = File(context.getExternalFilesDir(null), "Tadkeera/$cleanEventName")
                    if (!eventDir.exists()) eventDir.mkdirs()
                }
                
                val outputFile = File(eventDir, "${event.eventCode}.json")
                FileOutputStream(outputFile).use { output ->
                    output.write(jsonString.toByteArray())
                }
                
                withContext(Dispatchers.Main) {
                    onComplete(true, outputFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false, null)
                }
            }
        }
    }

    fun importEventData(uri: Uri, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open stream")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                
                val gson = Gson()
                val payload = gson.fromJson(jsonString, SyncPayload::class.java)
                
                if (payload != null && payload.event != null) {
                    // Save Event and Tickets
                    repository.addEvent(payload.event)
                    repository.addTickets(payload.tickets)
                    
                    withContext(Dispatchers.Main) {
                        onComplete(true, payload.event.eventName)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "الملف غير صالح أو فارغ")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message)
                }
            }
        }
    }

    fun backupDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFile = context.getDatabasePath("tadkeera_db")
                if (dbFile.exists()) {
                    val appDir = File(Environment.getExternalStorageDirectory(), "Tadkeera")
                    var backupDir = File(appDir, "BACKUP")
                    try {
                        if (!backupDir.exists()) {
                            val created = backupDir.mkdirs()
                            if (!created) {
                                backupDir = File(context.getExternalFilesDir(null), "Tadkeera/BACKUP")
                                if (!backupDir.exists()) backupDir.mkdirs()
                            }
                        }
                    } catch (e: Exception) {
                        backupDir = File(context.getExternalFilesDir(null), "Tadkeera/BACKUP")
                        if (!backupDir.exists()) backupDir.mkdirs()
                    }
                    
                    val destFile = File(backupDir, "tadkeera_db_backup.db")
                    dbFile.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun triggerManualBackup(onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFile = context.getDatabasePath("tadkeera_db")
                if (dbFile.exists()) {
                    val appDir = File(Environment.getExternalStorageDirectory(), "Tadkeera")
                    var backupDir = File(appDir, "BACKUP")
                    if (!backupDir.exists()) {
                        val created = backupDir.mkdirs()
                        if (!created) {
                            backupDir = File(context.getExternalFilesDir(null), "Tadkeera/BACKUP")
                            if (!backupDir.exists()) backupDir.mkdirs()
                        }
                    }
                    
                    val destFile = File(backupDir, "tadkeera_db_backup.db")
                    dbFile.inputStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onComplete(true, "تم إنشاء النسخة الاحتياطية بنجاح في مجلد BACKUP!")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "قاعدة البيانات فارغة أو غير موجودة")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message)
                }
            }
        }
    }

    fun restoreDatabaseBackup(uri: Uri, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Close database cleanly first!
                database.close()
                
                val dbFile = context.getDatabasePath("tadkeera_db")
                val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open stream")
                
                dbFile.outputStream().use { output ->
                    inputStream.use { input ->
                        input.copyTo(output)
                    }
                }
                
                // CRUCIAL: Delete auxiliary WAL and SHM files to force database reload and prevent Room from wiping out events!
                val walFile = File(dbFile.path + "-wal")
                if (walFile.exists()) walFile.delete()
                val shmFile = File(dbFile.path + "-shm")
                if (shmFile.exists()) shmFile.delete()
                
                withContext(Dispatchers.Main) {
                    onComplete(true, "تم استعادة النسخة الاحتياطية بنجاح! يرجى إعادة تشغيل التطبيق لتحديث القائمة.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message)
                }
            }
        }
    }

    fun uploadBackupToTelegram() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("TadkeeraTelegram", Context.MODE_PRIVATE)
                val token = prefs.getString("bot_token", "8605619071:AAG10sarSfX8G37FsGRcsTzPP2mkaaTii1Y") ?: "8605619071:AAG10sarSfX8G37FsGRcsTzPP2mkaaTii1Y"
                val channelId = prefs.getString("channel_id", "-1004357014151") ?: "-1004357014151"
                
                val dbFile = context.getDatabasePath("tadkeera_db")
                if (!dbFile.exists()) return@launch
                
                // 1. Upload .db file
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
                    out.write(("ملف قاعدة البيانات المحدث الاحتياطي: tadkeera_db_backup.db\r\n").toByteArray())

                    // Write document file
                    out.write(("--$boundary\r\n").toByteArray())
                    out.write(("Content-Disposition: form-data; name=\"document\"; filename=\"tadkeera_db_backup.db\"\r\n").toByteArray())
                    out.write(("Content-Type: application/octet-stream\r\n\r\n").toByteArray())
                    out.write(dbFile.readBytes())
                    out.write(("\r\n").toByteArray())

                    // End boundary
                    out.write(("--$boundary--\r\n").toByteArray())
                }
                conn.responseCode
                conn.disconnect()

                // 2. Save tickets as JSON file and upload via sendDocument API
                val eventsList = repository.allEvents.first()
                val allTickets = mutableListOf<Ticket>()
                for (e in eventsList) {
                    allTickets.addAll(repository.getTickets(e.eventId).first())
                }
                val syncData = SyncPayload(event = Event(eventName = "نسخ احتياطي شامل", eventDate = System.currentTimeMillis()), tickets = allTickets)
                
                val tempFile = File(context.cacheDir, "tadkeera_db_backup.json")
                val gson = Gson()
                val jsonString = gson.toJson(syncData)
                tempFile.writeText(jsonString)

                val boundaryJson = "Boundary-" + UUID.randomUUID().toString()
                val docUrl = URL("https://api.telegram.org/bot$token/sendDocument")
                val docConn = docUrl.openConnection() as HttpURLConnection
                docConn.doOutput = true
                docConn.requestMethod = "POST"
                docConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundaryJson")

                docConn.outputStream.use { out ->
                    // Write chat_id
                    out.write(("--$boundaryJson\r\n").toByteArray())
                    out.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n").toByteArray())
                    out.write(("$channelId\r\n").toByteArray())

                    // Write caption
                    out.write(("--$boundaryJson\r\n").toByteArray())
                    out.write(("Content-Disposition: form-data; name=\"caption\"\r\n\r\n").toByteArray())
                    out.write(("ملف بيانات تذاكر المناسبات الاحتياطي الشامل بصيغة JSON\r\n").toByteArray())

                    // Write document file
                    out.write(("--$boundaryJson\r\n").toByteArray())
                    out.write(("Content-Disposition: form-data; name=\"document\"; filename=\"${tempFile.name}\"\r\n").toByteArray())
                    out.write(("Content-Type: application/json\r\n\r\n").toByteArray())
                    out.write(tempFile.readBytes())
                    out.write(("\r\n").toByteArray())

                    // End boundary
                    out.write(("--$boundaryJson--\r\n").toByteArray())
                }
                docConn.responseCode
                docConn.disconnect()
                tempFile.delete()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uploadPdfToTelegram(pdfFile: File, eventName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("TadkeeraTelegram", Context.MODE_PRIVATE)
                val token = prefs.getString("bot_token", "8605619071:AAG10sarSfX8G37FsGRcsTzPP2mkaaTii1Y") ?: "8605619071:AAG10sarSfX8G37FsGRcsTzPP2mkaaTii1Y"
                val channelId = prefs.getString("channel_id", "-1004357014151") ?: "-1004357014151"

                val boundary = "Boundary-" + UUID.randomUUID().toString()
                val docUrl = URL("https://api.telegram.org/bot$token/sendDocument")
                val docConn = docUrl.openConnection() as HttpURLConnection
                docConn.doOutput = true
                docConn.requestMethod = "POST"
                docConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                docConn.outputStream.use { out ->
                    // Write chat_id
                    out.write(("--$boundary\r\n").toByteArray())
                    out.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n").toByteArray())
                    out.write(("$channelId\r\n").toByteArray())

                    // Write caption
                    out.write(("--$boundary\r\n").toByteArray())
                    out.write(("Content-Disposition: form-data; name=\"caption\"\r\n\r\n").toByteArray())
                    out.write(("تقرير تذاكر مناسبة: $eventName (PDF)\r\n").toByteArray())

                    // Write document file
                    out.write(("--$boundary\r\n").toByteArray())
                    out.write(("Content-Disposition: form-data; name=\"document\"; filename=\"${pdfFile.name}\"\r\n").toByteArray())
                    out.write(("Content-Type: application/pdf\r\n\r\n").toByteArray())
                    out.write(pdfFile.readBytes())
                    out.write(("\r\n").toByteArray())

                    // End boundary
                    out.write(("--$boundary--\r\n").toByteArray())
                }
                docConn.responseCode
                docConn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun restoreBackupFromTelegram(onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("TadkeeraTelegram", Context.MODE_PRIVATE)
                val token = prefs.getString("bot_token", "8605619071:AAG10sarSfX8G37FsGRcsTzPP2mkaaTii1Y") ?: "8605619071:AAG10sarSfX8G37FsGRcsTzPP2mkaaTii1Y"
                val channelId = prefs.getString("channel_id", "-1004357014151") ?: "-1004357014151"
                
                val updatesUrl = URL("https://api.telegram.org/bot$token/getUpdates")
                val connection = updatesUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()
                
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                
                val responseMap = Gson().fromJson(jsonString, Map::class.java)
                val resultList = responseMap["result"] as? List<*> ?: throw Exception("لا توجد ملفات مرفوعة مؤخراً في التحديثات")
                
                var latestFileId: String? = null
                
                for (item in resultList.reversed()) {
                    val update = item as? Map<*, *> ?: continue
                    val message = (update["message"] ?: update["channel_post"]) as? Map<*, *> ?: continue
                    val document = message["document"] as? Map<*, *> ?: continue
                    val fileName = document["file_name"] as? String ?: continue
                    
                    if (fileName.endsWith(".db")) {
                        latestFileId = document["file_id"] as? String
                        break
                    }
                }
                
                if (latestFileId == null) {
                    throw Exception("لم يتم العثور على أي ملف نسخة احتياطية (.db) في تليجرام")
                }
                
                val getFileUrl = URL("https://api.telegram.org/bot$token/getFile?file_id=$latestFileId")
                val getFileConn = getFileUrl.openConnection() as HttpURLConnection
                getFileConn.requestMethod = "GET"
                val fileResString = getFileConn.inputStream.bufferedReader().use { it.readText() }
                getFileConn.disconnect()
                
                val fileResMap = Gson().fromJson(fileResString, Map::class.java)
                val fileResult = fileResMap["result"] as? Map<*, *> ?: throw Exception("فشل الحصول على رابط الملف")
                val filePath = fileResult["file_path"] as? String ?: throw Exception("مسار الملف فارغ")
                
                val downloadUrl = URL("https://api.telegram.org/file/bot$token/$filePath")
                val downloadConn = downloadUrl.openConnection() as HttpURLConnection
                val bytes = downloadConn.inputStream.use { it.readBytes() }
                downloadConn.disconnect()
                
                database.close()
                val dbFile = context.getDatabasePath("tadkeera_db")
                dbFile.writeBytes(bytes)
                
                val walFile = File(dbFile.path + "-wal")
                if (walFile.exists()) walFile.delete()
                val shmFile = File(dbFile.path + "-shm")
                if (shmFile.exists()) shmFile.delete()
                
                withContext(Dispatchers.Main) {
                    onComplete(true, "تم تنزيل واستعادة قاعدة البيانات من تليجرام بنجاح! يرجى إعادة تشغيل التطبيق.")
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message ?: "فشل الاستعادة من تليجرام")
                }
            }
        }
    }
}
