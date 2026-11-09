package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.gemtronicring.p4e7a91c3.BuildConfig
import com.example.gemtronicring.p4e7a91c3.data.local.SessionStore
import com.example.gemtronicring.p4e7a91c3.data.remote.HttpSketchOnServer
import com.example.gemtronicring.p4e7a91c3.data.remote.SketchOnServer
import com.example.gemtronicring.p4e7a91c3.data.repository.BundledRingMenuRepository
import com.example.gemtronicring.p4e7a91c3.data.repository.RingMenuRepository
import com.example.gemtronicring.p4e7a91c3.domain.usecase.NavigateMenuUseCase
import com.example.gemtronicring.p4e7a91c3.domain.usecase.RingGestureUseCase
import java.io.File

class RingWearViewModelFactory(
    private val modelsDir: File,
    private val sessions: SessionStore,
    private val repository: RingMenuRepository = BundledRingMenuRepository(),
    private val server: SketchOnServer = HttpSketchOnServer(BuildConfig.SERVER_URL),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RingWearViewModel(
            repository.menu(),
            RingGestureUseCase(),
            NavigateMenuUseCase(),
            server,
            modelsDir,
            sessions,
        )
            as T
}
