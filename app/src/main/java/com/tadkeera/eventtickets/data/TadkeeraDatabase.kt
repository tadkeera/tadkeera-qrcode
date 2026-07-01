package com.tadkeera.eventtickets.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tadkeera.eventtickets.data.dao.*
import com.tadkeera.eventtickets.data.entities.*

@Database(
    entities = [
        Event::class,
        TicketDesign::class,
        Ticket::class,
        GuestName::class,
        SyncQueueItem::class
    ],
    version = 5,
    exportSchema = false
)
abstract class TadkeeraDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun ticketDesignDao(): TicketDesignDao
    abstract fun ticketDao(): TicketDao
    abstract fun guestNameDao(): GuestNameDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        // Manual, elegant Migration from 4 to 5!
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_queue` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`filePath` TEXT NOT NULL, " +
                        "`eventName` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`qrCodeData` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL)"
                )
            }
        }
    }
}
