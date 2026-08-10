package com.jeongmin.honeymoondoctor.core.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * 길찾기는 Google Maps 외부 Intent만 사용하고(API 키 금지 — 스펙 2장),
 * 지도 앱이 없으면 같은 URL을 브라우저로 연다.
 */
fun openGoogleMapsDirections(context: Context, destination: String) {
    val url = "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}"
    val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(mapsIntent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "지도를 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

/** 저장된 Google Maps URL 등 외부 링크를 연다(지도 앱 우선, 없으면 브라우저). */
fun openUrl(context: Context, url: String) {
    val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(mapsIntent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "링크를 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

/** 안드로이드 공유 시트로 텍스트를 넘긴다(카카오톡·문자 등 사용자가 고른 앱으로 전달). */
fun shareText(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13+는 시스템이 자체 복사 확인 UI를 띄우므로 그 아래 버전에서만 토스트를 보여준다.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "복사했습니다: $label", Toast.LENGTH_SHORT).show()
    }
}
