package com.abdullah.deadzone

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.*
import com.google.android.gms.ads.rewarded.*

class MainActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
        }
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(AdsBridge(this, web), "AndroidAds")
        web.loadUrl("file:///android_asset/index.html")
        hideBars()
    }

    private fun hideBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
}

class AdsBridge(private val act: Activity, private val web: WebView) {
    @Volatile private var inter: InterstitialAd? = null
    @Volatile private var rewarded: RewardedAd? = null

    init {
        MobileAds.initialize(act) {}
        loadInter(); loadReward()
    }

    private fun js(type: String, ok: Boolean) =
        web.post { web.evaluateJavascript("window.dzAdResult('$type',$ok)", null) }

    private fun loadInter() = act.runOnUiThread {
        InterstitialAd.load(act, BuildConfig.INTER_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(a: InterstitialAd) { inter = a }
                override fun onAdFailedToLoad(e: LoadAdError) { inter = null }
            })
    }

    private fun loadReward() = act.runOnUiThread {
        RewardedAd.load(act, BuildConfig.REWARD_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(a: RewardedAd) { rewarded = a }
                override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null }
            })
    }

    @JavascriptInterface fun isRewardedReady(): Boolean = rewarded != null

    @JavascriptInterface fun showInterstitial() = act.runOnUiThread {
        val a = inter
        if (a == null) { js("interstitial", false); loadInter(); return@runOnUiThread }
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { inter = null; loadInter(); js("interstitial", true) }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { inter = null; loadInter(); js("interstitial", false) }
        }
        a.show(act)
    }

    @JavascriptInterface fun showRewarded() = act.runOnUiThread {
        val a = rewarded
        if (a == null) { js("rewarded", false); loadReward(); return@runOnUiThread }
        var earned = false
        a.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { rewarded = null; loadReward(); js("rewarded", earned) }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { rewarded = null; loadReward(); js("rewarded", false) }
        }
        a.show(act) { earned = true }
    }
}
