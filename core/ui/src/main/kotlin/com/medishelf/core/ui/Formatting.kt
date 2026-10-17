package com.medishelf.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.medishelf.core.model.Medicine
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT

@Composable
@ReadOnlyComposable
fun LocalDate.formatMedium(): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()).format(this)

@Composable
@ReadOnlyComposable
fun Double.formatQuantity(): String =
    NumberFormat.getNumberInstance(currentLocale()).apply { maximumFractionDigits = 2 }.format(this)

/** "10 tablets" style label, locale-formatted. */
@Composable
fun Medicine.quantityLabel(): String = stringResource(R.string.core_ui_quantity_with_unit, quantity.formatQuantity(), unit.label())

/** Brand plus strength, e.g. "Panadol 500 mg". */
val Medicine.titleWithStrength: String
    get() = listOf(displayName, strength).filter(String::isNotBlank).joinToString(" ")
