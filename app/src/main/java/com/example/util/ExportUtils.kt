package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.Transaction
import com.example.data.model.Wallet
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    fun shareCSV(context: Context, transactions: List<Transaction>, wallets: List<Wallet>, dateRangeText: String) {
        try {
            val file = File(context.cacheDir, "LimitGuard_Transactions.csv")
            val fos = FileOutputStream(file)
            
            // UTF-8 BOM for Arabic support in Excel
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            
            val walletMap = wallets.associate { it.phoneNumber to it.label }
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar", "EG"))
            val formatter = DecimalFormat("#,##0.00")

            val writer = fos.bufferedWriter()
            writer.write("كشف معلومات الحركات والعمليات المالية - LimitGuard\n")
            writer.write("الفترة:,$dateRangeText\n")
            writer.write("تاريخ التصدير:,${sdf.format(Date())}\n\n")
            
            // Headers
            writer.write("رقم المعاملة,التاريخ والوقت,المحفظة المصدر,النوع,المستلم / الرقم الثاني,المبلغ (ج.م),ملاحظات\n")
            
            for ((index, tx) in transactions.withIndex()) {
                val dateStr = sdf.format(Date(tx.timestamp))
                val walletLabel = walletMap[tx.senderWalletNumber] ?: tx.senderWalletNumber
                val typeStr = if (tx.isDeposit) "إيداع / شحن" else "سحب / تحويل"
                val notesSanitized = tx.notes.replace(",", "-").replace("\n", " ")
                
                writer.write("${index + 1},$dateStr,$walletLabel,$typeStr,${tx.recipientNumber},${formatter.format(tx.amount)},$notesSanitized\n")
            }
            
            writer.flush()
            writer.close()
            fos.close()

            // Share File
            val uri: Uri = FileProvider.getUriForFile(context, "com.example.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "تصدير كشف حساب Excel")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة التقرير عبر:"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "فشل تصدير كشف الحساب: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun sharePDF(context: Context, transactions: List<Transaction>, wallets: List<Wallet>, dateRangeText: String) {
        try {
            val pdfDocument = PdfDocument()
            // Page size: A4 size is 595 x 842 points
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.parseColor("#E60000") // Vodafone Red
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            val headerPaint = Paint().apply {
                color = Color.parseColor("#121212") // Dark primary text for table header
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val textPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val smallBoldPaint = Paint().apply {
                color = Color.BLACK
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val borderPaint = Paint().apply {
                color = Color.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            val fillPaint = Paint().apply {
                color = Color.parseColor("#F9F9F9")
                style = Paint.Style.FILL
            }

            val redFillPaint = Paint().apply {
                color = Color.parseColor("#FFF5F5")
                style = Paint.Style.FILL
            }

            val greenFillPaint = Paint().apply {
                color = Color.parseColor("#F5FFF5")
                style = Paint.Style.FILL
            }

            // Draw Header Banner
            val bannerPaint = Paint().apply {
                color = Color.parseColor("#E60000") // Vodafone Red
                style = Paint.Style.FILL
            }
            canvas.drawRect(30f, 30f, 565f, 75f, bannerPaint)

            // Header Banner Text
            val headerTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("LIMITGUARD - كشف حساب العمليات والحركات", 297f, 58f, headerTextPaint)

            // Subtitle metadata
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar", "EG"))
            val formatter = DecimalFormat("#,##0.00")

            canvas.drawText("فترة التقرير: $dateRangeText", 50f, 105f, smallBoldPaint.apply { textAlign = Paint.Align.LEFT })
            canvas.drawText("تاريخ إصدار التقرير: ${sdf.format(Date())}", 50f, 120f, smallBoldPaint)

            // Draw Stats Summary box
            val totalDeposits = transactions.filter { it.isDeposit }.sumOf { it.amount }
            val totalWithdrawals = transactions.filter { !it.isDeposit }.sumOf { it.amount }
            val currentBalance = totalDeposits - totalWithdrawals

            canvas.drawRect(300f, 90f, 565f, 140f, fillPaint)
            canvas.drawRect(300f, 90f, 565f, 140f, borderPaint)

            val summaryTextPaint = Paint().apply {
                color = Color.BLACK
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("إجمالي الإيداعات: +${formatter.format(totalDeposits)} ج.م", 310f, 105f, summaryTextPaint)
            canvas.drawText("إجمالي السحوبات: -${formatter.format(totalWithdrawals)} ج.م", 310f, 120f, summaryTextPaint.apply { color = Color.parseColor("#E60000") })
            canvas.drawText("الرصيد الصافي: ${formatter.format(currentBalance)} ج.م", 310f, 130f, summaryTextPaint.apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK })

            // Draw Table Headers
            var yPosition = 160f
            val tableXStart = 30f
            val tableXEnd = 565f
            val rowHeight = 22f

            // Fill Header Row
            canvas.drawRect(tableXStart, yPosition, tableXEnd, yPosition + rowHeight, bannerPaint.apply { color = Color.parseColor("#2C3E50") })

            val colWidths = floatArrayOf(25f, 105f, 85f, 75f, 80f, 70f, 95f) // Total 535
            val colHeaders = arrayOf("#", "التاريخ والوقت", "المحفظة", "النوع", "المستلم/الرقم الثاني", "المبلغ ج.م", "ملاحظات")

            var currentX = tableXStart
            for (i in colHeaders.indices) {
                val headerTextColorPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 9f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(colHeaders[i], currentX + 5f, yPosition + 15f, headerTextColorPaint)
                currentX += colWidths[i]
            }
            yPosition += rowHeight

            val walletMap = wallets.associate { it.phoneNumber to it.label }
            
            // Draw rows
            var pageNum = 1
            for ((idx, tx) in transactions.withIndex()) {
                if (yPosition > 800f) {
                    pdfDocument.finishPage(page)
                    pageNum++
                    page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                    canvas = page.canvas
                    yPosition = 40f
                    
                    // Re-draw small table header on next page
                    canvas.drawRect(tableXStart, yPosition, tableXEnd, yPosition + rowHeight, bannerPaint.apply { color = Color.parseColor("#2C3E50") })
                    currentX = tableXStart
                    for (i in colHeaders.indices) {
                        val headerTextColorPaint = Paint().apply {
                            color = Color.WHITE
                            textSize = 9f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }
                        canvas.drawText(colHeaders[i], currentX + 5f, yPosition + 15f, headerTextColorPaint)
                        currentX += colWidths[i]
                    }
                    yPosition += rowHeight
                }

                // Row alternate background color
                val rowFill = if (tx.isDeposit) greenFillPaint else redFillPaint
                canvas.drawRect(tableXStart, yPosition, tableXEnd, yPosition + rowHeight, rowFill)
                canvas.drawRect(tableXStart, yPosition, tableXEnd, yPosition + rowHeight, borderPaint)

                val dateStr = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ENGLISH).format(Date(tx.timestamp))
                val walletLabel = walletMap[tx.senderWalletNumber] ?: tx.senderWalletNumber
                val typeStr = if (tx.isDeposit) "وارد / إيداع" else "صادر / سحب"
                val amountStr = (if (tx.isDeposit) "+" else "-") + formatter.format(tx.amount)
                val notesStr = if (tx.notes.length > 20) tx.notes.take(17) + "..." else tx.notes

                val rowData = arrayOf(
                    (idx + 1).toString(),
                    dateStr,
                    walletLabel,
                    typeStr,
                    tx.recipientNumber,
                    amountStr,
                    notesStr
                )

                currentX = tableXStart
                for (i in rowData.indices) {
                    val cellPaint = Paint().apply {
                        textSize = 8.5f
                        color = if (i == 5) {
                            if (tx.isDeposit) Color.parseColor("#27AE60") else Color.parseColor("#C0392B")
                        } else Color.BLACK
                        typeface = if (i == 5 || i == 0) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    }
                    canvas.drawText(rowData[i], currentX + 5f, yPosition + 14f, cellPaint)
                    currentX += colWidths[i]
                }
                
                yPosition += rowHeight
            }

            // Draw bottom footer
            canvas.drawText("صفحة $pageNum - LimitGuard لتتبع وحماية حدود استهلاك المحافظ المالي", 50f, 825f, Paint().apply {
                color = Color.GRAY
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            })

            pdfDocument.finishPage(page)

            val file = File(context.cacheDir, "LimitGuard_Statement.pdf")
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()

            // Share File
            val uri: Uri = FileProvider.getUriForFile(context, "com.example.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, "كشف حساب معاملاتي المالي - LimitGuard")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "مشاركة تقرير PDF عبر:"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "فشل تصدير كشف الحساب بصيغة PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
