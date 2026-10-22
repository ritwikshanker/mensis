package com.deutschdreamers.mensis.backup

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt module for Google Drive backup. Bindings are added in build step 6. */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule
