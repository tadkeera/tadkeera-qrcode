package com.tadkeera.eventtickets.data.dao

import androidx.room.*
import com.tadkeera.eventtickets.data.entities.Event
import com.tadkeera.eventtickets.data.entities.GuestName
import com.tadkeera.eventtickets.data.entities.Ticket
import com.tadkeera.eventtickets.data.entities.TicketDesign
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY createdAt DESC")
    fun getAllEvents(): Flow<List<Event>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event)

    @Query("SELECT * FROM events WHERE eventId = :eventId")
    suspend fun getEventById(eventId: String): Event?

    @Update
    suspend fun updateEvent(event: Event)
}

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets WHERE eventId = :eventId")
    fun getEventTickets(eventId: String): Flow<List<Ticket>>
    
    @Query("SELECT * FROM tickets WHERE qrCodeData = :qrCode LIMIT 1")
    suspend fun getTicketByQR(qrCode: String): Ticket?
    
    @Update
    suspend fun updateTicket(ticket: Ticket)
    
    @Insert
    suspend fun insertTickets(tickets: List<Ticket>)
    
    @Query("SELECT COUNT(*) FROM tickets WHERE eventId = :eventId")
    suspend fun getTotalTickets(eventId: String): Int
    
    @Query("SELECT COUNT(*) FROM tickets WHERE eventId = :eventId AND isScanned = 1")
    suspend fun getScannedTickets(eventId: String): Int

    @Query("DELETE FROM tickets WHERE eventCode = :eventCode")
    suspend fun deleteTicketsByCode(eventCode: String)
}

@Dao
interface TicketDesignDao {
    @Query("SELECT * FROM ticket_designs WHERE eventId = :eventId")
    fun getDesignsForEvent(eventId: String): Flow<List<TicketDesign>>

    @Insert
    suspend fun insertDesign(design: TicketDesign)

    @Query("SELECT * FROM ticket_designs WHERE eventId = :eventId AND isDefault = 1 LIMIT 1")
    suspend fun getDefaultDesign(eventId: String): TicketDesign?
}

@Dao
interface GuestNameDao {
    @Query("SELECT * FROM guest_names WHERE eventId = :eventId")
    fun getGuestNames(eventId: String): Flow<List<GuestName>>

    @Insert
    suspend fun insertGuestNames(names: List<GuestName>)
}
