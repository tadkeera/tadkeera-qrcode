package com.tadkeera.eventtickets.di

import android.content.Context
import androidx.room.Room
import com.tadkeera.eventtickets.data.TadkeeraDatabase
import com.tadkeera.eventtickets.data.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TadkeeraDatabase {
        return Room.databaseBuilder(
            context,
            TadkeeraDatabase::class.java,
            "tadkeera_db"
        )
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE)
        .addMigrations(com.tadkeera.eventtickets.data.TadkeeraDatabase.MIGRATION_4_5)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideEventDao(db: TadkeeraDatabase): EventDao = db.eventDao()

    @Provides
    fun provideTicketDao(db: TadkeeraDatabase): TicketDao = db.ticketDao()

    @Provides
    fun provideTicketDesignDao(db: TadkeeraDatabase): TicketDesignDao = db.ticketDesignDao()

    @Provides
    fun provideGuestNameDao(db: TadkeeraDatabase): GuestNameDao = db.guestNameDao()

    @Provides
    fun provideSyncQueueDao(db: TadkeeraDatabase): SyncQueueDao = db.syncQueueDao()
}
