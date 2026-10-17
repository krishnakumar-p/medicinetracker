package com.medishelf.core.domain

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.demo.DemoDataset
import com.medishelf.core.domain.export.CsvExporter
import com.medishelf.core.domain.export.CsvLabels
import com.medishelf.core.model.MedicineAssignment
import com.medishelf.core.model.MedicineDetails
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.QuantityUnit
import com.medishelf.core.testing.testMedicine
import com.medishelf.core.testing.testMember
import org.junit.Test
import java.time.LocalDate

class CsvAndDemoTest {
    private val today = LocalDate.of(2026, 1, 15)
    private val labels = CsvLabels(
        headers = (1..CsvExporter.COLUMN_COUNT).map { "H$it" },
        forms = MedicineForm.entries.associateWith { it.name.lowercase() },
        units = QuantityUnit.entries.associateWith { it.name.lowercase() },
        statuses = MedicineStatus.entries.associateWith { it.name.lowercase() },
        yes = "yes",
        no = "no",
    )

    @Test
    fun escaping_andFormulaInjectionGuard() {
        assertThat(CsvExporter.escape("plain")).isEqualTo("plain")
        assertThat(CsvExporter.escape("a,b")).isEqualTo("\"a,b\"")
        assertThat(CsvExporter.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"")
        assertThat(CsvExporter.escape("line1\nline2")).isEqualTo("\"line1\nline2\"")
        assertThat(CsvExporter.escape("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"")
        assertThat(CsvExporter.escape("-5")).isEqualTo("'-5")
    }

    @Test
    fun export_hasBomHeaderRowsAndPersonFilter() {
        val sara = testMember(id = 1, name = "Sara")
        val omar = testMember(id = 2, name = "Omar")
        val details = MedicineDetails(
            testMedicine(brandName = "Ventolin", quantity = 2.5, expiryDate = today.minusDays(1)),
            assignments = listOf(MedicineAssignment(sara, "Asthma"), MedicineAssignment(omar, null)),
        )
        val csv = CsvExporter.export(listOf(details), today, labels, memberId = 1)
        val lines = csv.removePrefix("﻿").split("\r\n")
        assertThat(csv).startsWith("﻿")
        assertThat(lines[0]).startsWith("H1,H2")
        assertThat(lines[1]).contains("Ventolin,Paracetamol,,tablet,2.5,tablets,2026-01-14,expired,Sara: Asthma,")
        assertThat(lines[1]).doesNotContain("Omar")
    }

    @Test
    fun demoDataset_isConsistent() {
        val set = DemoDataset.build(today)
        set.medicines.forEach { demo ->
            demo.locationIndex?.let { assertThat(it).isLessThan(set.locations.size) }
            demo.people.forEach { (index, _) -> assertThat(index).isLessThan(set.members.size) }
        }
        val states = set.medicines.map { it.medicine.effectiveStatus(today) }.toSet()
        assertThat(states).containsExactlyElementsIn(MedicineStatus.entries)
        assertThat(set.medicines.any { it.medicine.isLowStock }).isTrue()
    }
}
