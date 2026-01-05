package com.gobex.smartreadingassistant.core.di

import com.gobex.smartreadingassistant.core.audio.AndroidSpeechToTextManager
import com.gobex.smartreadingassistant.core.audio.AndroidTextToSpeechManager
import com.gobex.smartreadingassistant.core.audio.SpeechToTextManager
import com.gobex.smartreadingassistant.core.audio.TextToSpeechManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindSpeechToTextManager(impl: AndroidSpeechToTextManager): SpeechToTextManager

    @Binds
    @Singleton
    abstract fun bindTextToSpeechManager(impl: AndroidTextToSpeechManager): TextToSpeechManager
}