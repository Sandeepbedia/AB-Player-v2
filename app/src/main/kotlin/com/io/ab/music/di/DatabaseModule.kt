package com.io.ab.music.di

import android.content.Context
import androidx.room.Room
import com.io.ab.music.data.db.MusicDatabase
import com.io.ab.music.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): MusicDatabase =
        Room.databaseBuilder(ctx, MusicDatabase::class.java, "ab_music.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideSongDao(db: MusicDatabase): SongDao = db.songDao()
    @Provides fun providePlaylistDao(db: MusicDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideFavoriteDao(db: MusicDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideRecentlyPlayedDao(db: MusicDatabase): RecentlyPlayedDao = db.recentlyPlayedDao()
    @Provides fun provideLyricsDao(db: MusicDatabase): LyricsDao = db.lyricsDao()
}
