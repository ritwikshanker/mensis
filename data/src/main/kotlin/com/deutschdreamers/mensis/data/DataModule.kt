package com.deutschdreamers.mensis.data

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt module for the data layer. Bindings are added step by step (database, repositories, keys). */
@Module
@InstallIn(SingletonComponent::class)
object DataModule
