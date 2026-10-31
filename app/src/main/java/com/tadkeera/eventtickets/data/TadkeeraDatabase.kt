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
        SyncQueueItem::class,
        ScannerUser::class,
        ScanLog::class,
        ForgeryLog::class
    ],
    version = 8,
    exportSchema = false
)
abstract class TadkeeraDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun ticketDesignDao(): TicketDesignDao
    abstract fun ticketDao(): TicketDao
    abstract fun guestNameDao(): GuestNameDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun scannerUserDao(): ScannerUserDao
    abstract fun reportDao(): ReportDao

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

        // Adds the per-design toggle for the QR micro-text security frame.
        // Existing designs keep their data and default to enabled (1).
        // Scan history and forgery log imported from QR-SCANER reports.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scan_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`eventId` TEXT NOT NULL, " +
                        "`qrCodeData` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`operator` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `forgery_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`eventId` TEXT NOT NULL, " +
                        "`preview` TEXT NOT NULL, " +
                        "`reason` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL)"
                )
            }
        }

        // Event end date + activation code, and scanner users per event.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `events` ADD COLUMN `endDate` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `events` ADD COLUMN `activationCode` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scanner_users` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`eventId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`accessCode` TEXT NOT NULL DEFAULT '')"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `ticket_designs` ADD COLUMN `qrSecurityFrameEnabled` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }
    }
}
