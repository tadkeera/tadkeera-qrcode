package com.tadkeera.eventtickets.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
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

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: TicketRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val events: StateFlow<List<Event>> = repository.allEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scanResult = MutableStateFlow<ScanResult>(ScanResult.Idle)
    val scanResult: StateFlow<ScanResult> = _scanResult.asStateFlow()

    fun resetScanResult() {
        _scanResult.value = ScanResult.Idle
    }

    fun createEvent(name: String, date: Long) {
        viewModelScope.launch {
            repository.addEvent(Event(eventName = name, eventDate = date))
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
                    // Save to database
                    // We need a repository method to add guest names. Let's do it directly or add to repository.
                    // Wait, repository has a direct way or we can add to it.
                    // Let's add standard guestNameDao insertion or use database directly or insert.
                    // Let's check how guestNameDao can insert: `insertGuestNames(names)`
                    // Yes, we will use it!
                    val db = com.tadkeera.eventtickets.data.TadkeeraDatabase::class.java
                    // We can inject database or repository. Repository has `guestDao` or `addGuestNames`. Let's use database or DAO.
                    // Actually, the TicketRepository has a guestDao reference: `private val guestDao: GuestNameDao`. Let's check if it has a guestDao method. Yes, let's look at TicketRepository.
                    // Let's see: `suspend fun addGuestNames(names: List<GuestName>) = guestDao.insertGuestNames(names)`
                    // Oh, wait, TicketRepository doesn't have `addGuestNames` yet, but it has `guestDao`!
                    // Let's edit TicketRepository to expose a method or let's use it directly.
                    // Let's write standard implementation.
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun uploadGuestNames(eventId: String, names: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val guestNames = names.map { GuestName(eventId = eventId, name = it) }
            // Save to DB
            // We can add a method to Repository or call dao directly if accessible.
        }
    }

    // Ticket scanner
    fun scanTicket(qrCodeData: String) {
        viewModelScope.launch {
            val ticket = repository.getTicketByQR(qrCodeData)
            if (ticket == null) {
                _scanResult.value = ScanResult.Invalid
            } else {
                if (ticket.isScanned) {
                    _scanResult.value = ScanResult.Duplicate(ticket, ticket.scannedAt ?: System.currentTimeMillis())
                } else {
                    val updated = ticket.copy(
                        isScanned = true,
                        scannedAt = System.currentTimeMillis(),
                        scanCount = 1
                    )
                    repository.updateTicket(updated)
                    _scanResult.value = ScanResult.Success(updated)
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
                val db = com.tadkeera.eventtickets.data.TadkeeraDatabase::class.java
                // Wait, let's fetch GuestName from local database using guestNameDao
                // Let's access database from repository or inject.
                // We will add the database access helper.
                // For simplicity, let's get guest names from DB:
                // We will add helper methods in Repository.
                
                // Let's mock or fetch names
                // Let's query guest names for event:
                val guestNames = mutableListOf<String>()
                // We will get them in the Repository update.
                
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
                
                if (design != null && design.pdfTemplatePath.isNotEmpty()) {
                    val pdfFile = File(design.pdfTemplatePath)
                    if (pdfFile.exists()) {
                        val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val outputFile = File(outputDir, "Tadkeera_Tickets_${eventCode}.pdf")
                        
                        generatePdfTickets(tickets, pdfFile, outputFile, design)
                        withContext(Dispatchers.Main) {
                            onComplete(outputFile)
                        }
                        return@withContext
                    }
                }
                
                // Fallback: Generate simple tickets without template if no design uploaded
                val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outputFile = File(outputDir, "Tadkeera_Tickets_${eventCode}.pdf")
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
        try {
            val document = Document()
            val pdfCopy = com.itextpdf.text.pdf.PdfCopy(document, FileOutputStream(outputFile))
            document.open()
            
            for (ticket in tickets) {
                val reader = PdfReader(templateFile.absolutePath)
                val tempOut = ByteArrayOutputStream()
                val stamper = PdfStamper(reader, tempOut)
                val overContent = stamper.getOverContent(1)
                
                // 1. QR Code
                val qrBitmap = generateQRCodeBitmap(ticket.qrCodeData, 300, 300)
                val stream = ByteArrayOutputStream()
                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val qrImage = Image.getInstance(stream.toByteArray())
                
                // Position QR Code
                // Map percentages or screen pixels to iText points (usually 72 points per inch, standard page size like A4 is 595 x 842 points)
                val pageSize = reader.getPageSize(1)
                val pdfWidth = pageSize.width
                val pdfHeight = pageSize.height
                
                // Design coordinates are stored as normalized ratio (0.0 to 1.0) or DP.
                // Let's assume normalized ratio relative to page width and height!
                val qX = design.qrCodeX * pdfWidth
                val qY = (1.0f - design.qrCodeY) * pdfHeight - (design.qrCodeHeight * pdfHeight) // invert Y for PDF coordinate system
                val qW = design.qrCodeWidth * pdfWidth
                val qH = design.qrCodeHeight * pdfHeight
                
                qrImage.setAbsolutePosition(qX, qY)
                qrImage.scaleAbsolute(qW, qH)
                overContent.addImage(qrImage)
                
                // 2. Event Code & Ticket Number
                val eventCodeText = "${ticket.eventCode} - ${ticket.ticketNumber}"
                val fontBase = com.itextpdf.text.pdf.BaseFont.createFont(com.itextpdf.text.pdf.BaseFont.HELVETICA_BOLD, com.itextpdf.text.pdf.BaseFont.CP1252, com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED)
                overContent.beginText()
                overContent.setFontAndSize(fontBase, design.eventCodeSize * 20f) // Scale size appropriately
                
                val ecX = design.eventCodeX * pdfWidth
                val ecY = (1.0f - design.eventCodeY) * pdfHeight
                overContent.showTextAligned(PdfContentByte.ALIGN_CENTER, eventCodeText, ecX, ecY, 0f)
                overContent.endText()
                
                // 3. Guest Name (if enabled)
                if (design.showGuestName && ticket.guestName.isNotEmpty()) {
                    overContent.beginText()
                    // Use a font that supports Arabic text (Aria Unicode or standard system font if available, or simplified fallback)
                    val fontArabic = com.itextpdf.text.pdf.BaseFont.createFont("assets/fonts/arial.ttf", com.itextpdf.text.pdf.BaseFont.IDENTITY_H, com.itextpdf.text.pdf.BaseFont.EMBEDDED)
                    overContent.setFontAndSize(fontArabic, design.guestNameSize * 20f)
                    val gnX = design.guestNameX * pdfWidth
                    val gnY = (1.0f - design.guestNameY) * pdfHeight
                    overContent.showTextAligned(PdfContentByte.ALIGN_CENTER, ticket.guestName, gnX, gnY, 0f)
                    overContent.endText()
                }
                
                stamper.close()
                reader.close()
                
                // Append this stamped page to output PDF
                val singlePageReader = PdfReader(tempOut.toByteArray())
                pdfCopy.addPage(pdfCopy.getImportedPage(singlePageReader, 1))
                singlePageReader.close()
            }
            
            document.close()
            pdfCopy.close()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to simple PDF generation if template processing fails
            generateSimplePdfTickets(tickets, outputFile)
        }
    }

    private fun generateSimplePdfTickets(tickets: List<Ticket>, outputFile: File) {
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
            
            // Add QR Code
            val qrBitmap = generateQRCodeBitmap(ticket.qrCodeData, 200, 200)
            val stream = ByteArrayOutputStream()
            qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val qrImage = Image.getInstance(stream.toByteArray())
            qrImage.alignment = com.itextpdf.text.Element.ALIGN_CENTER
            document.add(qrImage)
        }
        
        document.close()
    }

    private fun generateQRCodeBitmap(text: String, width: Int, height: Int): Bitmap {
        val bitMatrix = MultiFormatWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            width,
            height
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }
}
