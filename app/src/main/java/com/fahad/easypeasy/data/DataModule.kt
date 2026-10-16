package com.fahad.easypeasy.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): LearningDatabase =
        Room.databaseBuilder(context, LearningDatabase::class.java, "learning.db").build()
    @Provides fun dao(database: LearningDatabase): LearningDao = database.learningDao()
}
