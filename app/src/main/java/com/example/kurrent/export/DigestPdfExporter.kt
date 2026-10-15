package com.example.kurrent.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.kurrent.model.Article
import com.example.kurrent.model.DailyBriefing
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DigestPdfExporter {

    private const val PAGE_WIDTH = 595 // A4 standard width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 standard height in points
    private const val MARGIN_HORIZONTAL = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)

    /**
     * Generates a printable, magazine-grade A4 PDF digest of the provided articles.
     * Automatically handles pagination, syllabus mapping, and revision checkboxes.
     */
    fun exportDigestPdf(
        context: Context,
        articles: List<Article>,
        title: String = "Kurrent Monthly Digest",
        subtitle: String = "Curated High-Yield Current Affairs for UPSC, SSC & IBPS"
    ): File? {
        if (articles.isEmpty()) {
            Toast.makeText(context, "No articles to export", Toast.LENGTH_SHORT).show()
            return null
        }

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Paint definitions
        val paintHeaderBg = Paint().apply { color = Color.parseColor("#080C14") }
        val paintAccent = Paint().apply { color = Color.parseColor("#0D9488") }
        val paintGold = Paint().apply { color = Color.parseColor("#F59E0B") }

        val paintTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFFF")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val paintSub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 10f
        }

        val paintArticleTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val paintCategory = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4F46E5")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val paintBody = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val paintTakeaway = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F766E")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val paintFooter = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 8.5f
        }

        val paintBoxBg = Paint().apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }

        val paintBoxStroke = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val paintCheckbox = Paint().apply {
            color = Color.parseColor("#0D9488")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }

        // Draw initial page header
        fun drawHeader(c: Canvas, isFirstPage: Boolean) {
            if (isFirstPage) {
                // Large cover banner
                c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 95f, paintHeaderBg)
                c.drawRect(0f, 95f, PAGE_WIDTH.toFloat(), 98f, paintAccent)

                c.drawText("🔥 $title", MARGIN_HORIZONTAL, 42f, paintTitle)
                c.drawText(subtitle, MARGIN_HORIZONTAL, 62f, paintSub)

                val dateStr = SimpleDateFormat("MMMM yyyy • Daily Automated Briefing", Locale.US).format(Date())
                c.drawText("Generated: $dateStr • ${articles.size} Total High-Yield Topics", MARGIN_HORIZONTAL, 80f, paintSub)
            } else {
                // Running mini top header on subsequent pages
                c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 35f, paintHeaderBg)
                c.drawRect(0f, 35f, PAGE_WIDTH.toFloat(), 37f, paintAccent)
                c.drawText("KURRENT EXAM COMPILATION • $title", MARGIN_HORIZONTAL, 22f, paintSub)
            }
        }

        // Draw page footer with page number
        fun drawFooter(c: Canvas, pNum: Int) {
            val y = PAGE_HEIGHT - 22f
            c.drawLine(MARGIN_HORIZONTAL, y - 8f, PAGE_WIDTH - MARGIN_HORIZONTAL, y - 8f, paintBoxStroke)
            c.drawText("Kurrent Daily Current Affairs Engine • https://kurrent.in", MARGIN_HORIZONTAL, y + 4f, paintFooter)
            val pageStr = "Page $pNum"
            val pWidth = paintFooter.measureText(pageStr)
            c.drawText(pageStr, PAGE_WIDTH - MARGIN_HORIZONTAL - pWidth, y + 4f, paintFooter)
        }

        var currentY = 115f
        drawHeader(canvas, true)

        // Iterate through all articles
        for ((idx, article) in articles.withIndex()) {
            val titleLayout = createStaticLayout(article.title, paintArticleTitle, (CONTENT_WIDTH - 24).toInt())
            val headlineLayout = createStaticLayout(
                if (article.headlineFact.isNotBlank()) article.headlineFact else article.background,
                paintBody,
                (CONTENT_WIDTH - 24).toInt()
            )
            val takeawayLayout = createStaticLayout(
                "🎯 Takeaway: ${article.revisionLine.ifBlank { "High-yield topic for prelims and mains." }}",
                paintTakeaway,
                (CONTENT_WIDTH - 24).toInt()
            )

            // Approximate block height
            val blockHeight = 22f + titleLayout.height + 14f + headlineLayout.height + 12f + takeawayLayout.height + 26f

            // Check if page overflow
            if (currentY + blockHeight > PAGE_HEIGHT - 45f) {
                drawFooter(canvas, pageNumber)
                pdfDocument.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                drawHeader(canvas, false)
                currentY = 55f
            }

            // Draw Card Container
            val cardRect = RectF(MARGIN_HORIZONTAL, currentY, MARGIN_HORIZONTAL + CONTENT_WIDTH, currentY + blockHeight - 10f)
            canvas.drawRoundRect(cardRect, 10f, 10f, paintBoxBg)
            canvas.drawRoundRect(cardRect, 10f, 10f, paintBoxStroke)

            var innerY = currentY + 16f

            // 1. Category & Index & Syllabus Badge
            val catText = "${idx + 1}. ${article.category.uppercase()} • ${article.exams.joinToString(", ")}"
            canvas.drawText(catText, MARGIN_HORIZONTAL + 12f, innerY, paintCategory)
            innerY += 14f

            // 2. Title
            canvas.save()
            canvas.translate(MARGIN_HORIZONTAL + 12f, innerY)
            titleLayout.draw(canvas)
            canvas.restore()
            innerY += titleLayout.height + 8f

            // 3. Core Headline / Facts
            canvas.save()
            canvas.translate(MARGIN_HORIZONTAL + 12f, innerY)
            headlineLayout.draw(canvas)
            canvas.restore()
            innerY += headlineLayout.height + 8f

            // 4. Takeaway
            canvas.save()
            canvas.translate(MARGIN_HORIZONTAL + 12f, innerY)
            takeawayLayout.draw(canvas)
            canvas.restore()
            innerY += takeawayLayout.height + 12f

            // 5. Revision Checkbox
            val cbSize = 10f
            val cbX = MARGIN_HORIZONTAL + 12f
            canvas.drawRoundRect(RectF(cbX, innerY - 9f, cbX + cbSize, innerY + 1f), 2f, 2f, paintCheckbox)
            canvas.drawText("Marked as Mastered [  ]", cbX + cbSize + 6f, innerY, paintSub)

            currentY += blockHeight
        }

        drawFooter(canvas, pageNumber)
        pdfDocument.finishPage(page)

        // Save PDF to cache exports
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "Kurrent_Digest_${System.currentTimeMillis()}.pdf"
        val pdfFile = File(exportsDir, fileName)

        try {
            FileOutputStream(pdfFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            Toast.makeText(context, "PDF export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    /**
     * Exports today's briefing into a 1-click single/multi-page study digest.
     */
    fun exportDailyBriefingPdf(context: Context, briefing: DailyBriefing): File? {
        val title = "Daily Briefing — ${briefing.date} (Ed. #${briefing.edition})"
        val subtitle = "Official Current Affairs Compilation • ${briefing.articles.size} High-Yield Modules"
        return exportDigestPdf(context, briefing.articles, title, subtitle)
    }

    fun exportDailyBriefingPdf(context: Context, articles: List<Article>): File? {
        val title = "Daily Briefing — Today's Edition"
        val subtitle = "Official Current Affairs Compilation • ${articles.size} High-Yield Modules"
        return exportDigestPdf(context, articles, title, subtitle)
    }

    /**
     * Launches Android's system share sheet for WhatsApp, Telegram, Drive, and Wireless Print.
     */
    fun sharePdf(context: Context, pdfFile: File, subject: String = "Kurrent Current Affairs Digest") {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "Here is your printable Kurrent Current Affairs study digest.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Share / Print Kurrent Digest"))
    }

    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1.15f)
            .setIncludePad(false)
            .build()
    }
}
