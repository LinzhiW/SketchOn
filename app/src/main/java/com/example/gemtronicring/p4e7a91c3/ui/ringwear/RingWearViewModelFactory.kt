package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.gemtronicring.p4e7a91c3.data.repository.BundledRingMenuRepository
import com.example.gemtronicring.p4e7a91c3.data.repository.RingMenuRepository
import com.example.gemtronicring.p4e7a91c3.domain.usecase.NavigateMenuUseCase
import com.example.gemtronicring.p4e7a91c3.domain.usecase.RingGestureUseCase

class RingWearViewModelFactory(
    private val repository: RingMenuRepository = BundledRingMenuRepository()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RingWearViewModel(repository.menu(), RingGestureUseCase(), NavigateMenuUseCase()) as T
}
