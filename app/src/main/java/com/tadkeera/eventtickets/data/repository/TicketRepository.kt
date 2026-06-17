package com.tadkeera.eventtickets.data.repository

import com.tadkeera.eventtickets.data.dao.*
import com.tadkeera.eventtickets.data.entities.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketRepository @Inject constructor(
    private val eventDao: EventDao,
    private val ticketDao: TicketDao,
    private val designDao: TicketDesignDao,
    private val guestDao: GuestNameDao
) {
    val allEvents: Flow<List<Event>> = eventDao.getAllEvents()

    suspend fun addEvent(event: Event) = eventDao.insertEvent(event)
    suspend fun getEvent(id: String) = eventDao.getEventById(id)
    suspend fun updateEvent(event: Event) = eventDao.updateEvent(event)

    fun getTickets(eventId: String) = ticketDao.getEventTickets(eventId)
    suspend fun getTicketByQR(qr: String) = ticketDao.getTicketByQR(qr)
    suspend fun updateTicket(ticket: Ticket) = ticketDao.updateTicket(ticket)
    suspend fun addTickets(tickets: List<Ticket>) = ticketDao.insertTickets(tickets)
    suspend fun deleteOrder(eventCode: String) = ticketDao.deleteTicketsByCode(eventCode)

    fun getDesigns(eventId: String) = designDao.getDesignsForEvent(eventId)
    suspend fun addDesign(design: TicketDesign) = designDao.insertDesign(design)
    suspend fun getDefaultDesign(eventId: String) = designDao.getDefaultDesign(eventId)
    
    fun getGuestNames(eventId: String) = guestDao.getGuestNames(eventId)
    suspend fun getGuestNamesList(eventId: String) = guestDao.getGuestNamesList(eventId)
    suspend fun addGuestNames(names: List<GuestName>) = guestDao.insertGuestNames(names)
    
    suspend fun getStats(eventId: String): Pair<Int, Int> {
        val total = ticketDao.getTotalTickets(eventId)
        val scanned = ticketDao.getScannedTickets(eventId)
        return Pair(total, scanned)
    }
}
