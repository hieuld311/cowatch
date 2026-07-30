package com.ivi.common.app

import com.ivi.common.data.AssetSeekFrameProvider
import com.ivi.common.data.AssetVideoRepository
import com.ivi.common.data.AssetVideoThumbnailCache
import com.ivi.common.domain.VideoCatalogRepository
import com.ivi.common.media.SeekFrameProvider
import com.ivi.common.media.ThumbnailLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonBindings {
    @Binds
    abstract fun bindVideoCatalogRepository(
        implementation: AssetVideoRepository
    ): VideoCatalogRepository

    @Binds
    abstract fun bindThumbnailLoader(
        implementation: AssetVideoThumbnailCache
    ): ThumbnailLoader

    @Binds
    abstract fun bindSeekFrameProvider(
        implementation: AssetSeekFrameProvider
    ): SeekFrameProvider
}
