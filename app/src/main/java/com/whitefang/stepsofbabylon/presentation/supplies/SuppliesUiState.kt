package com.whitefang.stepsofbabylon.presentation.supplies

import com.whitefang.stepsofbabylon.domain.model.SupplyDrop

data class SuppliesUiState(
    val drops: List<SupplyDrop> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
