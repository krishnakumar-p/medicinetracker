package com.medishelf

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/** Empty @AndroidEntryPoint activity so tests can call setContent with hiltViewModel()s. */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
