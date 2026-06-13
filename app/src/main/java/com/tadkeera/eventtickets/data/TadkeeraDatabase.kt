package com.tadkeera.eventtickets.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tadkeera.eventtickets.data.dao.*
import com.tadkeera.eventtickets.data.entities.*

@Database(
    entities = [
        Event::class,
        TicketDesign::class,
        Ticket::class,
        GuestName::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TadkeeraDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun ticketDesignDao(): TicketDesignDao
    abstract fun ticketDao(): TicketDao
    abstract fun guestNameDao(): GuestNameDao
}
