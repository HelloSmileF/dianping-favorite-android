package com.example.dianpinghelper.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.dianpinghelper.model.ShopInfo

/**
 * WebView 状态
 */
enum class WebViewState {
    INIT,           // 初始
    LOADING,        // 页面加载中
    READY,          // 页面就绪
    SEARCHING,      // 正在搜索
    FOUND_SHOP,     // 搜索到店铺
    FAVORITING,     // 正在收藏
    SUCCESS,        // 收藏成功
    ERROR,          // 出错
    NEED_LOGIN,     // 需要登录
}

/**
 * 大众点评 WebView 操作封装
 *
 * 职责：
 * 1. 管理 WebView 生命周期
 * 2. 注入 JS 自动搜索 + 收藏
 * 3. 持久化 Cookie
 */
class DianpingWebClient(
    private val onStateChange: (WebViewState) -> Unit = {},
    private val onMessage: (String) -> Unit = {},
) {

    /** 状态 */
    var state by mutableStateOf(WebViewState.INIT)
        private set

    /** 当前店铺 */
    var currentShop by mutableStateOf<ShopInfo?>(null)
        private set

    /** 已登录 */
    var isLoggedIn by mutableStateOf(false)
        private set

    private var webView: WebView? = null
    private var pendingShop: ShopInfo? = null
    private var actionStep = 0  // 操作步骤跟踪
    private var cookieManager: CookieManager? = null

    // ── WebView 创建 ──────────────────────────

    /**
     * 创建并配置 WebView
     */
    fun createWebView(parent: ViewGroup): WebView {
        val context = parent.context

        // 先销毁旧的 WebView
        webView?.destroy()
        webView = null

        val wv = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = (
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/125.0.6422.165 Mobile Safari/537.36"
                )
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    setState(WebViewState.LOADING)
                    onMessage("加载中: ${url?.take(60)}...")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    checkLoginStatus()
                    // 页面加载完成，如果有待处理的店铺操作，执行
                    val shop = pendingShop
                    if (shop != null && isLoggedIn) {
                        executeFavorite(shop)
                    } else if (shop != null) {
                        setState(WebViewState.NEED_LOGIN)
                        onMessage("请先登录大众点评")
                    } else {
                        setState(WebViewState.READY)
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?, handler: SslErrorHandler?, error: SslError?
                ) {
                    handler?.proceed() // 忽略 SSL 警告
                }
            }

            // 注入 JS 接口供页面回调
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onResult(json: String) {
                    android.util.Log.d("DianpingWeb", "JS result: $json")
                }
            }, "Android")
        }

        webView = wv

        cookieManager = CookieManager.getInstance()
        cookieManager?.setAcceptCookie(true)
        CookieManager.setAcceptFileSchemeCookies(true)

        // 加载大众点评移动版
        wv.loadUrl("https://m.dianping.com")
        return wv
    }

    /**
     * 销毁 WebView
     */
    fun destroy() {
        webView?.destroy()
        webView = null
    }

    // ── 公开操作 ──────────────────────────────

    /**
     * 收藏指定店铺
     * 如果已登录，直接执行JS操作；否则先导航到登录页
     */
    fun favorite(shop: ShopInfo) {
        pendingShop = shop
        currentShop = shop

        val wv = webView ?: return

        if (isLoggedIn) {
            executeFavorite(shop)
        } else {
            // 先确保在大众点评首页
            wv.loadUrl("https://m.dianping.com")
            setState(WebViewState.NEED_LOGIN)
            onMessage("请先登录大众点评账号")
        }
    }

    /**
     * 重新检查登录状态（用户手动登录后调用）
     */
    fun recheckLogin() {
        checkLoginStatus()
        val shop = pendingShop
        if (isLoggedIn && shop != null) {
            executeFavorite(shop)
        }
    }

    /**
     * 手动导航到指定 URL
     */
    fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    // ── 内部操作 ──────────────────────────────

    /**
     * 执行收藏操作（通过 JS 注入）
     *
     * 流程：
     * 1. 导航到搜索页，填入店铺名搜索
     * 2. 点击第一个搜索结果
     * 3. 点击收藏按钮
     */
    private fun executeFavorite(shop: ShopInfo) {
        val wv = webView ?: return
        setState(WebViewState.SEARCHING)
        onMessage("正在搜索: ${shop.name}")

        // 编码店铺名用于 URL
        val encodedName = java.net.URLEncoder.encode(shop.name, "UTF-8")
        val searchUrl = "https://m.dianping.com/search/city/1/keyword/$encodedName"

        // 设置 WebViewClient 拦截搜索结果页完成事件
        wv.webViewClient = object : WebViewClient() {
            private var step = 0

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                if (url == null) return

                when (step) {
                    0 -> {
                        // 搜索结果页加载完成，尝试点击第一个结果
                        step = 1
                        onMessage("搜索完成，正在打开店铺页...")
                        view?.postDelayed({
                            injectClickFirstResult(view)
                        }, 1500)
                    }
                    1 -> {
                        // 店铺页加载完成，点击收藏
                        step = 2
                        onMessage("正在点击收藏...")
                        view?.postDelayed({
                            injectClickFavorite(view)
                        }, 2000)
                    }
                    2 -> {
                        // 收藏完成
                        step = 3
                        setState(WebViewState.SUCCESS)
                        onMessage("✅ 已收藏: ${shop.name}")
                    }
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onMessage("加载中...")
            }

            override fun onReceivedSslError(
                view: WebView?, handler: SslErrorHandler?, error: SslError?
            ) {
                handler?.proceed()
            }
        }

        // 开始搜索
        wv.loadUrl(searchUrl)
    }

    /**
     * 注入 JS 点击第一个搜索结果
     */
    private fun injectClickFirstResult(view: WebView) {
        val js = """
            (function() {
                // 尝试多种选择器找到第一个店铺链接
                var selectors = [
                    'a[href*="/shop/"]',
                    '.shop-list a',
                    '.search-result a[href*="/shop/"]',
                    'a.shop-item',
                    '.list-item a',
                    'a[class*=shop]'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var links = document.querySelectorAll(selectors[i]);
                    for (var j = 0; j < links.length; j++) {
                        var href = links[j].getAttribute('href');
                        if (href && href.indexOf('/shop/') >= 0) {
                            links[j].click();
                            return 'clicked: ' + href;
                        }
                    }
                }
                // 再尝试找任何包含 "/shop/" 的链接
                var allLinks = document.querySelectorAll('a');
                for (var i = 0; i < allLinks.length; i++) {
                    var href = allLinks[i].getAttribute('href');
                    if (href && href.indexOf('/shop/') >= 0) {
                        allLinks[i].click();
                        return 'clicked: ' + href;
                    }
                }
                return 'no_shop_found';
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }

    /**
     * 注入 JS 点击收藏按钮
     */
    private fun injectClickFavorite(view: WebView) {
        val js = """
            (function() {
                // 点击收藏按钮
                var selectors = [
                    'span.J-collect',
                    'a.J-collect',
                    '.J-collect',
                    'span:contains("收藏")',
                    'a:contains("收藏")',
                    '[class*=favorite]',
                    '[class*=collect]',
                    '.collect-action',
                    '.favorite-action',
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el) {
                        el.click();
                        return 'clicked_favorite';
                    }
                }
                // 用 XPath 兜底
                var result = document.evaluate(
                    '//span[contains(text(), "收藏")] | //a[contains(text(), "收藏")]',
                    document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null
                );
                if (result.singleNodeValue) {
                    result.singleNodeValue.click();
                    return 'clicked_favorite_xpath';
                }
                return 'no_favorite_btn';
            })();
        """.trimIndent()

        view.evaluateJavascript(js) { result ->
            android.util.Log.d("DianpingWeb", "JS favorite result: $result")
            if (result?.contains("no_favorite") == true) {
                onMessage("未找到收藏按钮，请手动操作")
                setState(WebViewState.ERROR)
            }
        }
    }

    /**
     * 检查登录状态（检测页面元素）
     */
    private fun checkLoginStatus() {
        // 方法：检查 URL 是否包含 login，以及页面是否有用户信息元素
        val wv = webView ?: return
        val url = wv.url ?: ""

        val js = """
            (function() {
                var userEl = document.querySelector(
                    '.user-info, .user-name, [class*=user], [class*=avatar], ' +
                    'a[href*="/member/"], .J-user-info'
                );
                return userEl ? 'logged_in' : 'not_logged_in';
            })();
        """.trimIndent()

        wv.evaluateJavascript(js) { result ->
            val loggedIn = result?.trim('"') == "logged_in"
            isLoggedIn = loggedIn
            android.util.Log.d("DianpingWeb", "Login status: $loggedIn (url=$url)")
        }
    }

    private fun setState(s: WebViewState) {
        state = s
        onStateChange(s)
    }
}
