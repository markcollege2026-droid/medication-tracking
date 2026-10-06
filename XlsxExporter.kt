package com.campmeds.app.export

import android.content.Context
import android.net.Uri
import com.campmeds.app.data.entity.DoseLog
import com.campmeds.app.data.entity.Medication
import com.campmeds.app.data.entity.Patient
import com.campmeds.app.data.entity.User
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream
import java.time.format.DateTimeFormatter

/**
 * Generates one XLSX workbook, one worksheet per patient, rows = DoseLog entries
 * (spec section 4, screen 7 and section 9 acceptance criteria).
 *
 * No data leaves the device except via this explicit, user-initiated export (spec section 7).
 */
class XlsxExporter {

    private val dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    data class ExportBundle(
        val patient: Patient,
        val medicationsById: Map<String, Medication>,
        val doseLogs: List<DoseLog>,
        val usersById: Map<String, User>
    )

    fun export(context: Context, destination: Uri, bundles: List<ExportBundle>) {
        XSSFWorkbook().use { workbook ->
            val headerStyle = headerStyle(workbook)

            bundles.forEach { bundle ->
                val sheetName = sanitizeSheetName(bundle.patient.name)
                val sheet = workbook.createSheet(sheetName)

                val header = sheet.createRow(0)
                val columns = listOf(
                    "Medication", "Strength", "Form", "NDC", "Scheduled For",
                    "Status", "Logged At", "Logged By", "Override?"
                )
                columns.forEachIndexed { i, title ->
                    header.createCell(i).apply {
                        setCellValue(title)
                        cellStyle = headerStyle
                    }
                }

                bundle.doseLogs
                    .sortedBy { it.scheduledFor }
                    .forEachIndexed { rowIndex, log ->
                        val row = sheet.createRow(rowIndex + 1)
                        val med = bundle.medicationsById[log.medicationId]
                        val user = log.loggedByUserId?.let { bundle.usersById[it] }

                        row.createCell(0).setCellValue(med?.name ?: "Unknown")
                        row.createCell(1).setCellValue(med?.strength ?: "")
                        row.createCell(2).setCellValue(med?.form ?: "")
                        row.createCell(3).setCellValue(med?.ndc ?: "")
                        row.createCell(4).setCellValue(log.scheduledFor.format(dtFmt))
                        row.createCell(5).setCellValue(log.status.name)
                        row.createCell(6).setCellValue(log.loggedAt.format(dtFmt))
                        row.createCell(7).setCellValue(user?.name ?: "Unknown")
                        row.createCell(8).setCellValue(if (log.wasOverride) "YES" else "")
                    }

                // autoSizeColumn() needs java.awt font metrics, which do not exist on Android (it throws at runtime).
                // Fixed widths instead (units are 1/256 of a character).
                val widths = intArrayOf(28, 16, 14, 16, 20, 12, 20, 20, 10)
                for (i in columns.indices) sheet.setColumnWidth(i, widths[i] * 256)
            }

            if (workbook.numberOfSheets == 0) {
                workbook.createSheet("No data")
            }

            val outputStream: OutputStream? = context.contentResolver.openOutputStream(destination)
            outputStream?.use { workbook.write(it) }
        }
    }

    private fun headerStyle(workbook: XSSFWorkbook): CellStyle {
        val font = workbook.createFont().apply { bold = true }
        return workbook.createCellStyle().apply {
            setFont(font)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
        }
    }

    /** Excel sheet names: max 31 chars, no \ / ? * [ ] : */
    private fun sanitizeSheetName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim()
        return cleaned.take(31).ifBlank { "Patient" }
    }
}
