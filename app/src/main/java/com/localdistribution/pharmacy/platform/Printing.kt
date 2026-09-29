package com.localdistribution.pharmacy.platform

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Prints the order statement through Android's print service; "Save as PDF"
 * is one of the printers, which replaces the desktop's "حفظ PDF" button.
 * WebView shapes Arabic text correctly and honours dir="rtl".
 */
object Printing {
    // The WebView must stay referenced until the print job has been handed off.
    private var pending: WebView? = null

    fun print(context: Context, jobName: String, html: String) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                manager.print(jobName, view.createPrintDocumentAdapter(jobName),
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
                pending = null
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        pending = webView
    }
}
