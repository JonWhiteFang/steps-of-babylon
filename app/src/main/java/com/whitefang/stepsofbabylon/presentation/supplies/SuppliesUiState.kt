package com.whitefang.stepsofbabylon.presentation.supplies

import androidx.annotation.StringRes
import com.whitefang.stepsofbabylon.domain.model.SupplyDrop

data class SuppliesUiState(
    val drops: List<SupplyDrop> = emptyList(),
    val isLoading: Boolean = true,
    @StringRes val error: Int? = null,
)
