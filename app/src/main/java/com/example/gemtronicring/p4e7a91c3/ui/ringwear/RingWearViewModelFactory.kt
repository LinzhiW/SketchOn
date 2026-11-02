package com.example.gemtronicring.p4e7a91c3.ui.ringwear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.gemtronicring.p4e7a91c3.data.repository.BundledRingPageRepository
import com.example.gemtronicring.p4e7a91c3.data.repository.RingPageRepository
import com.example.gemtronicring.p4e7a91c3.domain.usecase.TiltPageUseCase

class RingWearViewModelFactory(
    private val repository: RingPageRepository = BundledRingPageRepository()
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RingWearViewModel(repository.pages(), TiltPageUseCase()) as T
}
