package com.tadkeera.eventtickets.ui.viewmodel

import android.content.Context
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
        isDefault: Boolean = true
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
                isDefault = isDefault
            )
            repository.addDesign(design)
            backupDatabase()
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
                    _scanResult.value = ScanResult.Duplicate(updated, ticket.scannedAt ?: System.currentTimeMillis())
                } else {
                    val updated = ticket.copy(
                        isScanned = true,
                        scannedAt = System.currentTimeMillis(),
                        scanCount = 1
                    )
                    repository.updateTicket(updated)
                    backupDatabase()
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
        onComplete: (File?) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val event = repository.getEvent(eventId) ?: return@withContext
                val eventCode = EventCodeGenerator.generateEventCode()
                
                // Fetch guest names if enabled
                val guestNames = repository.getGuestNamesList(eventId).map { it.name }
                
                val tickets = mutableListOf<Ticket>()
                val charPool : List<Char> = ('A'..'Z') + ('0'..'9')
                for (i in 1..count) {
                    val qrCode = (1..24)
                        .map { kotlin.random.Random.nextInt(0, charPool.size).let { charPool[it] } }
                        .joinToString("")
                    val guestName = if (i <= guestNames.size) guestNames[i - 1] else ""
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
                        withContext(Dispatchers.Main) {
                            onComplete(outputFile)
                        }
                        return@withContext
                    }
                }
                
                // Fallback: Generate simple tickets without template if no design uploaded
                generateSimplePdfTickets(tickets, outputFile)
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
                
                // Draw QR Code using native iText BarcodeQRCode
                val barcode = com.itextpdf.text.pdf.BarcodeQRCode(ticket.qrCodeData, 1, 1, null)
                val qrImage = barcode.getImage()
                
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
                
                // Draw Event Code & Ticket Number at exactly designed center of the text box
                val textW = 0.3f
                val textH = 0.08f
                val ecX = (design.eventCodeX + textW / 2f) * pdfWidth
                val ecY = (1.0f - (design.eventCodeY + textH / 2f)) * pdfHeight - (design.eventCodeSize * 3f) // small offset for baseline adjustment
                
                val eventCodeText = "${ticket.eventCode} - ${ticket.ticketNumber}"
                val fontBase = com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA_BOLD, com.itextpdf.text.pdf.BaseFont.CP1252, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED)
                overContent.beginText()
                val fontCodeSize = (design.eventCodeSize * 13f) * (pdfWidth / 595f) // perfect size calibration
                overContent.setFontAndSize(fontBase, fontCodeSize)
                overContent.showTextAligned(PdfContentByte.ALIGN_CENTER, eventCodeText, ecX, ecY, 0f)
                overContent.endText()
                
                // Draw Guest Name (if enabled) at exactly designed center of the guest text box
                if (design.showGuestName && ticket.guestName.isNotEmpty()) {
                    val gW = 0.4f
                    val gH = 0.08f
                    val gnX = (design.guestNameX + gW / 2f) * pdfWidth
                    val gnY = (1.0f - (design.guestNameY + gH / 2f)) * pdfHeight - (design.guestNameSize * 3f)
                    
                    overContent.beginText()
                    // Safe Arabic font fallback to avoid crashes if arial.ttf is not packaged
                    val fontArabic = try {
                        com.itextpdf.text.pdf.BaseFont.createFont("assets/fonts/arial.ttf", com.itextpdf.text.pdf.BaseFont.IDENTITY_H, com.itextpdf.text.pdf.BaseFont.EMBEDDED)
                    } catch (e: Exception) {
                        com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA_BOLD, com.itextpdf.text.pdf.BaseFont.CP1252, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED)
                    }
                    val fontGuestSize = (design.guestNameSize * 13f) * (pdfWidth / 595f) // perfect size calibration
                    overContent.setFontAndSize(fontArabic, fontGuestSize)
                    overContent.showTextAligned(PdfContentByte.ALIGN_CENTER, ticket.guestName, gnX, gnY, 0f)
                    overContent.endText()
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
                
                // Add QR Code using native iText BarcodeQRCode
                val barcode = com.itextpdf.text.pdf.BarcodeQRCode(ticket.qrCodeData, 1, 1, null)
                val qrImage = barcode.getImage()
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
                // Query tickets for this event
                val ticketsFlow = repository.getTickets(event.eventId)
                val tickets = ticketsFlow.first().filter { it.eventCode == event.eventCode }
                
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
}
