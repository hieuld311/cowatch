package com.ivi.cid.app

import com.ivi.common.domain.PlaybackController
import com.ivi.common.playback.Media3PlaybackController
import com.ivi.common.playback.PlayerSurfaceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CIDVideoBindings {
    @Binds
    abstract fun bindPlaybackController(
        implementation: Media3PlaybackController
    ): PlaybackController

    @Binds
    abstract fun bindPlayerSurfaceController(
        implementation: Media3PlaybackController
    ): PlayerSurfaceController
}
