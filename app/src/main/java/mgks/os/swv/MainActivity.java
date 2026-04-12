package mgks.os.swv;

/*
  Smart WebView v8
  https://github.com/mgks/Android-SmartWebView

  A modern, open-source WebView wrapper for building advanced hybrid Android apps.
  Native features, modular plugins, and full customisation—built for developers.

  - Documentation: https://mgks.github.io/Android-SmartWebView/documentation
  - Plugins: https://mgks.github.io/Android-SmartWebView/documentation/plugins
  - Discussions: https://github.com/mgks/Android-SmartWebView/discussions
  - Sponsor the Project: https://github.com/sponsors/mgks

  MIT License — https://opensource.org/licenses/MIT

  Mentioning Smart WebView in your project helps others find it and keeps the dev loop alive.
*/

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;

import mgks.os.swv.plugins.QRScannerPlugin;

/**
 * Main Activity for Smart WebView
 * Handles WebView configuration, lifecycle events and user interactions
 */
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";

    private boolean isPageLoaded = false;
    private boolean singleWebViewMode = false;

    static Functions fns = new Functions();
    private FileProcessing fileProcessing;
    private LinearLayout adContainer;
    private PermissionManager permissionManager;
    private ActivityResultLauncher<Intent> fileUploadLauncher;
    private ActivityResultLauncher<ScanOptions> qrScannerLauncher;

    private SwipeRefreshLayout pullRefresh;
    private BottomNavigationView bottomNav;

    private WebView webChat;
    private WebView webLearn;
    private WebView webTransient;
    private WebView activeWebView;

    private boolean chatLoaded = false;
    private boolean learnLoaded = false;

    private static final String URL_CHAT = "https://bbs.886.best/chats";
    private static final String URL_PARTNERS = "https://bbs.886.best/partners";
    private static final String URL_COMMUNITY = "https://bbs.886.best/categories";
    private static final String URL_FEED = "https://bbs.886.best/category/6/%E9%97%B2%E8%81%8A";
    private static final String URL_LEARN = "https://886.best/";

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        SWVContext.getPluginManager().onActivityResult(requestCode, resultCode, intent);
    }

    @SuppressLint({"SetJavaScriptEnabled", "WrongViewCast", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (SWVContext.ASWP_BLOCK_SCREENSHOTS) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackAction();
            }
        });

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        super.onCreate(savedInstanceState);

        final SplashScreen splashScreen = androidx.core.splashscreen.SplashScreen.installSplashScreen(this);

        final View content = findViewById(android.R.id.content);
        if (SWVContext.ASWP_EXTEND_SPLASH) {
            content.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        if (isPageLoaded) {
                            content.getViewTreeObserver().removeOnPreDrawListener(this);
                            return true;
                        } else {
                            return false;
                        }
                    }
                }
            );
        }

        permissionManager = new PermissionManager(this);

        fileUploadLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Uri[] results = null;
                if (result.getResultCode() == Activity.RESULT_CANCELED) {
                    if (SWVContext.asw_file_path != null) {
                        SWVContext.asw_file_path.onReceiveValue(null);
                        SWVContext.asw_file_path = null;
                    }
                    return;
                }

                if (result.getResultCode() == Activity.RESULT_OK) {
                    if (SWVContext.asw_file_path == null) {
                        return;
                    }

                    Intent data = result.getData();

                    if (data != null && (data.getDataString() != null || data.getClipData() != null)) {
                        ClipData clipData = data.getClipData();
                        if (clipData != null) {
                            final int numSelectedFiles = clipData.getItemCount();
                            results = new Uri[numSelectedFiles];
                            for (int i = 0; i < numSelectedFiles; i++) {
                                results[i] = clipData.getItemAt(i).getUri();
                            }
                        } else if (data.getDataString() != null) {
                            results = new Uri[]{Uri.parse(data.getDataString())};
                        }
                    }

                    if (results == null) {
                        if (SWVContext.asw_pcam_message != null) {
                            results = new Uri[]{Uri.parse(SWVContext.asw_pcam_message)};
                        } else if (SWVContext.asw_vcam_message != null) {
                            results = new Uri[]{Uri.parse(SWVContext.asw_vcam_message)};
                        }
                    }
                }

                if (SWVContext.asw_file_path != null) {
                    SWVContext.asw_file_path.onReceiveValue(results);
                    SWVContext.asw_file_path = null;
                }

                SWVContext.asw_pcam_message = null;
                SWVContext.asw_vcam_message = null;
            }
        );

        qrScannerLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                PluginInterface plugin = SWVContext.getPluginManager().getPluginInstance("QRScannerPlugin");
                if (plugin instanceof QRScannerPlugin) {
                    ((QRScannerPlugin) plugin).handleScanResult(result);
                }
            }
        );

        SWVContext.setAppContext(getApplicationContext());
        fileProcessing = new FileProcessing(this, fileUploadLauncher);

        String cookie_orientation = !SWVContext.ASWP_OFFLINE ? fns.get_cookies("ORIENT") : "";
        fns.set_orientation((!Objects.equals(cookie_orientation, "") ? Integer.parseInt(cookie_orientation) : SWVContext.ASWV_ORIENTATION), false, this);

        setupLayout();
        initializeWebView();

        SWVContext.loadPlugins(this);
        SWVContext.init(this, SWVContext.asw_view, fns);

        PluginInterface qrPlugin = SWVContext.getPluginManager().getPluginInstance("QRScannerPlugin");
        if (qrPlugin instanceof QRScannerPlugin) {
            ((QRScannerPlugin) qrPlugin).setLauncher(qrScannerLauncher);
        }

        if (savedInstanceState == null) {
            setupFeatures();
            handleIncomingIntents();
        }

        if (SWVContext.SWV_DEBUGMODE) {
            Log.d(TAG, "URL: " + SWVContext.CURR_URL + " DEVICE INFO: " + Arrays.toString(fns.get_info(this)));
        }

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
    }

    public void setWindowSecure(boolean secure) {
        runOnUiThread(() -> {
            if (secure) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else if (!SWVContext.ASWP_BLOCK_SCREENSHOTS) {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        });
    }

    private void setupLayout() {
        if (SWVContext.ASWV_LAYOUT == 1) {
            singleWebViewMode = true;
            setContentView(R.layout.drawer_main);

            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            pullRefresh = findViewById(R.id.pullfresh);

            if (SWVContext.ASWP_DRAWER_HEADER) {
                findViewById(R.id.app_bar).setVisibility(View.VISIBLE);
                setSupportActionBar(toolbar);
                Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);

                DrawerLayout drawer = findViewById(R.id.drawer_layout);
                ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.open, R.string.close) {
                    @Override
                    public void onDrawerSlide(View drawerView, float slideOffset) {
                        super.onDrawerSlide(drawerView, slideOffset);
                        if (pullRefresh != null && slideOffset > 0 && pullRefresh.isEnabled()) {
                            pullRefresh.setEnabled(false);
                        }
                    }

                    @Override
                    public void onDrawerClosed(View drawerView) {
                        super.onDrawerClosed(drawerView);
                        updatePullRefreshState();
                    }
                };
                drawer.addDrawerListener(toggle);
                toggle.syncState();
            } else {
                findViewById(R.id.app_bar).setVisibility(View.GONE);
            }

            NavigationView navigationView = findViewById(R.id.nav_view);
            navigationView.setNavigationItemSelectedListener(this);

            activeWebView = findViewById(R.id.msw_view);
            SWVContext.asw_view = activeWebView;
        } else {
            singleWebViewMode = false;
            setContentView(R.layout.activity_main);

            pullRefresh = findViewById(R.id.pullfresh);
            webChat = findViewById(R.id.web_chat);
            webLearn = findViewById(R.id.web_learn);
            webTransient = findViewById(R.id.web_transient);
            bottomNav = findViewById(R.id.bottom_nav);

            activeWebView = webChat;
            SWVContext.asw_view = webChat;
        }

        adContainer = findViewById(R.id.msw_ad_container);
        SWVContext.print_view = findViewById(R.id.print_view);
    }

    private void initializeWebView() {
        if (singleWebViewMode) {
            setupOneWebView(activeWebView);
            SWVContext.init(this, activeWebView, fns);
            Playground playground = new Playground(this, activeWebView, fns);
            SWVContext.getPluginManager().setPlayground(playground);
            return;
        }

        setupOneWebView(webChat);
        setupOneWebView(webLearn);
        setupOneWebView(webTransient);

        SWVContext.init(this, activeWebView, fns);
        Playground playground = new Playground(this, activeWebView, fns);
        SWVContext.getPluginManager().setPlayground(playground);

        setupBottomNav();
    }

    private void setupOneWebView(WebView webView) {
        if (webView == null) return;

        WebSettings webSettings = webView.getSettings();

        if (SWVContext.OVERRIDE_USER_AGENT || SWVContext.POSTFIX_USER_AGENT) {
            String userAgent = webSettings.getUserAgentString();
            if (SWVContext.OVERRIDE_USER_AGENT) {
                userAgent = SWVContext.CUSTOM_USER_AGENT;
            }
            if (SWVContext.POSTFIX_USER_AGENT) {
                userAgent = userAgent + " " + SWVContext.USER_AGENT_POSTFIX;
            }
            webSettings.setUserAgentString(userAgent);
        }

        webSettings.setJavaScriptEnabled(true);
        webSettings.setSaveFormData(SWVContext.ASWP_SFORM);
        webSettings.setSupportZoom(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        if (!SWVContext.ASWP_COPYPASTE) {
            webView.setOnLongClickListener(v -> true);
        }

        webView.setHapticFeedbackEnabled(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setVerticalScrollBarEnabled(false);

        webView.setWebViewClient(new WebViewCallback());
        webView.setWebChromeClient(createWebChromeClient());
        webView.setBackgroundColor(getColor(R.color.colorPrimary));
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidInterface");

        setupDownloadListener(webView);
    }

    private void setupDownloadListener(WebView webView) {
        if (webView == null) return;

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (!permissionManager.isStoragePermissionGranted()) {
                ActivityCompat.requestPermissions(
                    MainActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PermissionManager.STORAGE_REQUEST_CODE
                );
                Toast.makeText(this, "Storage permission is required to download files.", Toast.LENGTH_LONG).show();
            } else {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription(getString(R.string.dl_downloading));
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                request.allowScanningByMediaScanner();
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimeType)
                );

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(this, getString(R.string.dl_downloading2), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private WebChromeClient createWebChromeClient() {
        return new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (SWVContext.SWV_DEBUGMODE) {
                    Log.d("SWV_JS", consoleMessage.message() + " -- From line " +
                        consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
                }
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                return fileProcessing.onShowFileChooser(webView, filePathCallback, fileChooserParams);
            }

            @Override
            public void onProgressChanged(WebView view, int p) {
                if (SWVContext.ASWP_PBAR) {
                    if (SWVContext.asw_progress == null) {
                        SWVContext.asw_progress = findViewById(R.id.msw_progress);
                    }
                    SWVContext.asw_progress.setProgress(p);
                    if (p == 100) {
                        SWVContext.asw_progress.setProgress(0);
                    }
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (permissionManager.isLocationPermissionGranted()) {
                    callback.invoke(origin, true, false);
                } else {
                    permissionManager.requestInitialPermissions();
                }
            }
        };
    }

    private void setupBottomNav() {
        if (bottomNav == null) return;

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_chat) {
                openChatTab();
                return true;
            } else if (id == R.id.nav_partner) {
                openTransientTab(URL_PARTNERS, false);
                return true;
            } else if (id == R.id.nav_community) {
                openTransientTab(URL_COMMUNITY, false);
                return true;
            } else if (id == R.id.nav_feed) {
                openTransientTab(URL_FEED, false);
                return true;
            } else if (id == R.id.nav_learn) {
                openLearnTab();
                return true;
            }
            return false;
        });

        bottomNav.setSelectedItemId(R.id.nav_chat);
    }

    private void showOnly(WebView target) {
        if (singleWebViewMode || target == null) return;

        if (webChat != null) webChat.setVisibility(target == webChat ? View.VISIBLE : View.GONE);
        if (webLearn != null) webLearn.setVisibility(target == webLearn ? View.VISIBLE : View.GONE);
        if (webTransient != null) webTransient.setVisibility(target == webTransient ? View.VISIBLE : View.GONE);

        activeWebView = target;
        SWVContext.asw_view = target;
        updatePullRefreshState();
    }

    private void openChatTab() {
        if (singleWebViewMode) {
            fns.aswm_view(URL_CHAT, false, SWVContext.asw_error_counter, this);
            return;
        }

        showOnly(webChat);
        if (!chatLoaded || webChat.getUrl() == null) {
            webChat.loadUrl(URL_CHAT);
            chatLoaded = true;
        }
        setBottomChecked(R.id.nav_chat);
    }

    private void openLearnTab() {
        if (singleWebViewMode) {
            fns.aswm_view(URL_LEARN, false, SWVContext.asw_error_counter, this);
            return;
        }

        showOnly(webLearn);
        if (!learnLoaded || webLearn.getUrl() == null) {
            webLearn.loadUrl(URL_LEARN);
            learnLoaded = true;
        }
        setBottomChecked(R.id.nav_learn);
    }

    private void openTransientTab(String url, boolean forceReload) {
        if (singleWebViewMode) {
            fns.aswm_view(url, false, SWVContext.asw_error_counter, this);
            return;
        }

        showOnly(webTransient);
        String currentUrl = webTransient.getUrl();
        if (forceReload || currentUrl == null || !currentUrl.equals(url)) {
            webTransient.loadUrl(url);
        }

        if (isPartnerUrl(url)) {
            setBottomChecked(R.id.nav_partner);
        } else if (isCommunityUrl(url)) {
            setBottomChecked(R.id.nav_community);
        } else if (isFeedUrl(url)) {
            setBottomChecked(R.id.nav_feed);
        }
    }

    private void setBottomChecked(int itemId) {
        if (bottomNav == null) return;
        MenuItem item = bottomNav.getMenu().findItem(itemId);
        if (item != null) {
            item.setChecked(true);
        }
    }

    private boolean isChatUrl(String url) {
        return url != null && url.startsWith("https://bbs.886.best/chats");
    }

    private boolean isPartnerUrl(String url) {
        return url != null && url.startsWith("https://bbs.886.best/partners");
    }

    private boolean isCommunityUrl(String url) {
        return url != null && url.startsWith("https://bbs.886.best/categories");
    }

    private boolean isFeedUrl(String url) {
        return url != null && url.startsWith("https://bbs.886.best/category/6");
    }

    private boolean isLearnUrl(String url) {
        return url != null && url.startsWith("https://886.best");
    }

    private void routeUrlToTab(String url, boolean forceReload) {
        if (url == null || url.isEmpty()) {
            openChatTab();
            return;
        }

        if (singleWebViewMode) {
            fns.aswm_view(url, false, SWVContext.asw_error_counter, this);
            return;
        }

        if (isChatUrl(url)) {
            showOnly(webChat);
            if (forceReload || webChat.getUrl() == null || !webChat.getUrl().equals(url)) {
                webChat.loadUrl(url);
            }
            chatLoaded = true;
            setBottomChecked(R.id.nav_chat);
        } else if (isLearnUrl(url)) {
            showOnly(webLearn);
            if (forceReload || webLearn.getUrl() == null || !webLearn.getUrl().equals(url)) {
                webLearn.loadUrl(url);
            }
            learnLoaded = true;
            setBottomChecked(R.id.nav_learn);
        } else {
            openTransientTab(url, forceReload);
        }
    }

    private boolean handleBackAction() {
        if (SWVContext.ASWP_EXIT_ON_BACK) {
            if (SWVContext.ASWP_EXITDIAL) {
                fns.ask_exit(this);
            } else {
                finish();
            }
            return true;
        }

        if (activeWebView != null && activeWebView.canGoBack()) {
            activeWebView.goBack();
            return true;
        }

        if (!singleWebViewMode && bottomNav != null && bottomNav.getSelectedItemId() != R.id.nav_chat) {
            bottomNav.setSelectedItemId(R.id.nav_chat);
            return true;
        }

        if (SWVContext.ASWP_EXITDIAL) {
            fns.ask_exit(this);
        } else {
            finish();
        }
        return true;
    }

    private void setupFeatures() {
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                return null;
            }
        });

        if (!isTaskRoot()) {
            finish();
            return;
        }

        setupNotificationChannel();
        setupSwipeRefresh();

        if (SWVContext.ASWP_PBAR) {
            SWVContext.asw_progress = findViewById(R.id.msw_progress);
        } else {
            View progressView = findViewById(R.id.msw_progress);
            if (progressView != null) {
                progressView.setVisibility(View.GONE);
            }
        }
        SWVContext.asw_loading_text = findViewById(R.id.msw_loading_text);

        fns.get_info(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            permissionManager.requestInitialPermissions();
        }, 1500);

        setupFirebaseMessaging();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        if (searchView != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            searchView.setQueryHint(getString(R.string.search_hint));
            searchView.setIconified(true);
            searchView.setIconifiedByDefault(true);
            searchView.clearFocus();

            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                public boolean onQueryTextSubmit(String query) {
                    searchView.clearFocus();
                    if (singleWebViewMode) {
                        fns.aswm_view(SWVContext.ASWV_SEARCH + query, false, SWVContext.asw_error_counter, MainActivity.this);
                    } else {
                        openTransientTab(SWVContext.ASWV_SEARCH + query, true);
                    }
                    searchView.setQuery(query, false);
                    return false;
                }

                public boolean onQueryTextChange(String query) {
                    return false;
                }
            });
        }
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_exit) {
            fns.exit_app(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        SWVContext.NavItem navItem = SWVContext.ASWV_DRAWER_MENU.get(id);

        if (navItem != null) {
            String action = navItem.action;

            if (action.startsWith("mailto:")) {
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse(action));
                try {
                    startActivity(Intent.createChooser(intent, "Send Email"));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(this, "No email app found.", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (singleWebViewMode) {
                    fns.aswm_view(action, false, 0, this);
                } else {
                    routeUrlToTab(action, true);
                }
            }
        } else {
            Log.w(TAG, "No action configured for menu item ID: " + id);
        }

        if (SWVContext.ASWV_LAYOUT == 1) {
            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            if (drawer != null) {
                drawer.closeDrawer(GravityCompat.START);
            }
        }
        return true;
    }

    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel channel = new NotificationChannel(
                SWVContext.asw_fcm_channel,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);

            channel.setDescription(getString(R.string.notification_channel_desc));
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void setupSwipeRefresh() {
        if (pullRefresh == null) return;

        if (SWVContext.ASWP_PULLFRESH) {
            pullRefresh.setOnRefreshListener(() -> {
                if (activeWebView != null) {
                    activeWebView.reload();
                }
                pullRefresh.setRefreshing(false);
            });

            if (!singleWebViewMode) {
                if (webChat != null) {
                    webChat.getViewTreeObserver().addOnScrollChangedListener(this::updatePullRefreshState);
                }
                if (webLearn != null) {
                    webLearn.getViewTreeObserver().addOnScrollChangedListener(this::updatePullRefreshState);
                }
                if (webTransient != null) {
                    webTransient.getViewTreeObserver().addOnScrollChangedListener(this::updatePullRefreshState);
                }
            } else if (activeWebView != null) {
                activeWebView.getViewTreeObserver().addOnScrollChangedListener(this::updatePullRefreshState);
            }

            updatePullRefreshState();
        } else {
            pullRefresh.setRefreshing(false);
            pullRefresh.setEnabled(false);
        }
    }

    private void updatePullRefreshState() {
        if (pullRefresh == null) return;

        if (!SWVContext.ASWP_PULLFRESH) {
            pullRefresh.setEnabled(false);
            return;
        }

        boolean enabled = activeWebView != null && activeWebView.getScrollY() == 0;

        if (!singleWebViewMode && activeWebView == webLearn) {
            enabled = false;
        }

        pullRefresh.setEnabled(enabled);
    }

    private void setAppTheme(boolean isDarkMode) {
        int mode = isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private void setupFirebaseMessaging() {
        fns.fcm_token(new Functions.TokenCallback() {
            @Override
            public void onTokenReceived(String token) {
                Log.d(TAG, "FCM Token received: " + token);
            }

            @Override
            public void onTokenFailed(Exception e) {
                Log.e(TAG, "Failed to retrieve FCM token", e);
            }
        });
    }

    private void handleIncomingIntents() {
        Intent intent = getIntent();
        Log.d(TAG, "Intent: " + intent.toUri(0));

        String uri = intent.getStringExtra("uri");
        String share = intent.getStringExtra("s_uri");
        String shareImg = intent.getStringExtra("s_img");

        if (share != null) {
            handleSharedText(share);
        } else if (shareImg != null) {
            Log.d(TAG, "Share image intent: " + shareImg);
            Toast.makeText(this, shareImg, Toast.LENGTH_LONG).show();
            openChatTab();
        } else if (uri != null) {
            Log.d(TAG, "Notification intent: " + uri);
            routeUrlToTab(uri, true);
        } else if (intent.getData() != null) {
            String path = intent.getDataString();
            routeUrlToTab(path, true);
        } else {
            openChatTab();
        }
    }

    private void handleSharedText(String share) {
        Log.d(TAG, "Share text intent: " + share);

        Matcher matcher = Functions.url_pattern().matcher(share);
        String urlStr = "";

        if (matcher.find()) {
            urlStr = matcher.group();
            if (urlStr.startsWith("(") && urlStr.endsWith(")")) {
                urlStr = urlStr.substring(1, urlStr.length() - 1);
            }
        }

        String redirectUrl = SWVContext.ASWV_SHARE_URL +
            "?text=" + share +
            "&link=" + urlStr +
            "&image_url=";

        routeUrlToTab(redirectUrl, true);
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void setNativeTheme(String theme) {
            runOnUiThread(() -> {
                int newMode;
                if ("dark".equals(theme)) {
                    newMode = AppCompatDelegate.MODE_NIGHT_YES;
                } else if ("light".equals(theme)) {
                    newMode = AppCompatDelegate.MODE_NIGHT_NO;
                } else {
                    newMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                }
                if (AppCompatDelegate.getDefaultNightMode() != newMode) {
                    AppCompatDelegate.setDefaultNightMode(newMode);
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        if (activeWebView != null) activeWebView.onPause();
        SWVContext.getPluginManager().onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activeWebView != null) activeWebView.onResume();
        SWVContext.getPluginManager().onResume();

        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        ActivityManager.TaskDescription taskDesc = new ActivityManager.TaskDescription(
            getString(R.string.app_name), bm, getColor(R.color.colorPrimary));
        setTaskDescription(taskDesc);
    }

    @Override
    protected void onDestroy() {
        SWVContext.getPluginManager().onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        String theme = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? "dark" : "light";
        String script = "if(typeof setTheme === 'function') { setTheme('" + theme + "', true); }";
        if (activeWebView != null) {
            activeWebView.evaluateJavascript(script, null);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (activeWebView != null) {
            activeWebView.saveState(outState);
            if (activeWebView.getUrl() != null) {
                outState.putString("swv_last_url", activeWebView.getUrl());
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (activeWebView != null) {
            activeWebView.restoreState(savedInstanceState);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
            return handleBackAction();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        SWVContext.getPluginManager().onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionManager.INITIAL_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "Location permission granted.");
                    } else {
                        Log.w(TAG, "Location permission denied.");
                    }
                } else if (permissions[i].equals(Manifest.permission.POST_NOTIFICATIONS)) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "Notification permission granted.");

                        if (SWVContext.SWV_DEBUGMODE) {
                            Firebase firebase = new Firebase();
                            firebase.sendMyNotification(
                                "Yay! Firebase is working",
                                "This is a test notification in action.",
                                "OPEN_URI",
                                SWVContext.ASWV_URL,
                                null,
                                String.valueOf(SWVContext.ASWV_FCM_ID),
                                getApplicationContext());
                        }
                    } else {
                        Log.w(TAG, "Notification permission denied.");
                    }
                }
            }
        }
    }

    private class WebViewCallback extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            SWVContext.getPluginManager().onPageStarted(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);

            SWVContext.getPluginManager().onPageFinished(url);

            View welcome = findViewById(R.id.msw_welcome);
            if (welcome != null) {
                welcome.setVisibility(View.GONE);
            }
            view.setVisibility(View.VISIBLE);
            isPageLoaded = true;
            updatePullRefreshState();

            if (!url.startsWith("file://") && SWVContext.ASWV_GTAG != null && !SWVContext.ASWV_GTAG.isEmpty()) {
                fns.inject_gtag(view, SWVContext.ASWV_GTAG);
            }

            String theme = SWVContext.ASWP_DARK_MODE ? "dark" : "light";
            String script = "if(typeof applyInitialTheme === 'function') { applyInitialTheme('" + theme + "'); }";
            view.evaluateJavascript(script, null);

            if (SWVContext.ASWP_CUSTOM_CSS) {
                try {
                    InputStream inputStream = getAssets().open("web/custom.css");
                    byte[] buffer = new byte[inputStream.available()];
                    inputStream.read(buffer);
                    inputStream.close();
                    String encoded = Base64.encodeToString(buffer, Base64.NO_WRAP);
                    String js = "javascript:(function() {" +
                        "var parent = document.getElementsByTagName('head').item(0);" +
                        "var style = document.createElement('style');" +
                        "style.type = 'text/css';" +
                        "style.innerHTML = window.atob('" + encoded + "');" +
                        "parent.appendChild(style)" +
                        "})()";
                    view.loadUrl(js);
                    Log.d(TAG, "Custom CSS injected.");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to inject custom CSS.", e);
                }
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            if (SWVContext.getPluginManager().shouldOverrideUrlLoading(view, url)) {
                return true;
            }

            if (url.matches("^(https?|file)://.*$")) {
                SWVContext.CURR_URL = url;
            }
            return fns.url_actions(view, url, MainActivity.this);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                int errorCode = error.getErrorCode();
                if (errorCode == ERROR_HOST_LOOKUP ||
                    errorCode == ERROR_TIMEOUT ||
                    errorCode == ERROR_CONNECT ||
                    errorCode == ERROR_UNKNOWN ||
                    errorCode == ERROR_IO) {

                    Log.e(TAG, "Network Error Occurred: " + error.getDescription());

                    view.post(() -> {
                        if (SWVContext.ASWV_OFFLINE_URL != null && !SWVContext.ASWV_OFFLINE_URL.isEmpty()) {
                            view.loadUrl(SWVContext.ASWV_OFFLINE_URL);
                        } else {
                            view.loadUrl("file:///android_asset/error.html");
                        }
                    });
                }
            }
            super.onReceivedError(view, request, error);
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            if (SWVContext.ASWP_CERT_VERI) {
                super.onReceivedSslError(view, handler, error);
            } else {
                handler.proceed();
                if (SWVContext.SWV_DEBUGMODE) {
                    Toast.makeText(MainActivity.this, "SSL Error: " + error.getPrimaryError(),
                        Toast.LENGTH_SHORT).show();
                }
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (SWVContext.SWV_DEBUGMODE) {
                Log.e(TAG, "HTTP Error loading " + request.getUrl().toString() +
                    ": " + errorResponse.getStatusCode());
            }
        }
    }
}
