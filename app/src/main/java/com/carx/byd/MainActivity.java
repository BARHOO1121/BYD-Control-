package com.carx.byd;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SecurePrefs securePrefs;
    private ThemeSpec theme;
    private BydApiClient client;
    private BydConfig config;
    private JSONArray vehicles = new JSONArray();
    private JSONObject currentVehicle;
    private JSONObject realtime = new JSONObject();

    private TextView globalStatus;
    private ProgressBar globalProgress;
    private TextView batteryValue, rangeValue, odometerValue, lockValue, tempValue, chargeValue, vinValue, modelValue, cloudStateValue;
    private Spinner vehicleSpinner;
    private ImageView heroCarArt;
    private String lastQrScanId = "";
    private String lastQrVin = "";
    private static final int CAMERA_PERMISSION_REQUEST = 7401;
    private DecoratedBarcodeView qrScannerView;
    private boolean qrScannerOpen = false;
    private boolean pendingQrAfterPermission = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        securePrefs = new SecurePrefs(this);
        theme = ThemeSpec.fromKey(securePrefs.get("theme", "red"));
        applySystemBars();
        getWindow().setSoftInputMode(Window.FEATURE_NO_TITLE);
        showLogin();
    }

    @Override protected void onDestroy() {
        if (qrScannerView != null) {
            try { qrScannerView.pause(); } catch (Exception ignored) {}
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onPause() {
        if (qrScannerOpen && qrScannerView != null) {
            try { qrScannerView.pause(); } catch (Exception ignored) {}
        }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (qrScannerOpen && qrScannerView != null && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try { qrScannerView.resume(); } catch (Exception ignored) {}
        }
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(theme.bg);
        getWindow().setNavigationBarColor(theme.bg);
        if (theme.light) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
    }

    // ---------------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------------

    private void showLogin() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(theme.bg);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(22), dp(26), dp(22), dp(32));
        scroll.addView(root, matchWrap());

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_carx_logo);
        brandRow.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        TextView brand = label("CAR X • BYD CLOUD", 20, theme.accent, true);
        TextView sub = label("سيارتك دائماً معك", 11, theme.muted, false);
        sub.setGravity(Gravity.LEFT);
        brandText.addView(brand);
        brandText.addView(sub);
        LinearLayout.LayoutParams btp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btp.setMarginStart(dp(10));
        brandRow.addView(brandText, btp);
        root.addView(brandRow, lpMatch(0, dp(30)));

        TextView headline = label("دخول حساب BYD China", 28, theme.text, true);
        headline.setGravity(Gravity.RIGHT);
        root.addView(headline);
        TextView desc = label("نفس محرك تسجيل الدخول الصيني الذي تم اختباره بنجاح، مع واجهة Car X الجديدة.", 13, theme.muted, false);
        desc.setGravity(Gravity.RIGHT);
        desc.setLineSpacing(dp(3), 1f);
        root.addView(desc, lpMatch(dp(7), dp(22)));

        LinearLayout card = column();
        card.setPadding(dp(18), dp(19), dp(18), dp(18));
        card.setBackground(gradient(new int[]{mix(theme.accent, theme.bg, 0.16f), theme.surface}, 26, theme.accentFaint, 1));
        card.setElevation(dp(7));
        root.addView(card, lpMatch(0, dp(16)));

        Spinner country = new Spinner(this);
        ArrayAdapter<BydRegion> regionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BydRegion.SUPPORTED);
        country.setAdapter(regionAdapter);
        country.setBackground(rounded(theme.surface2, 16, theme.accentFaint, 1));
        country.setPadding(dp(12),0,dp(12),0);
        String savedUser = securePrefs.get("username", "");
        String savedCode = securePrefs.get("region", "CN");
        if (savedUser.startsWith("+86") || savedUser.startsWith("0086")) savedCode = "CN";
        country.setSelection(findRegionIndex(savedCode));
        card.addView(country, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        EditText username = input("رقم الهاتف الصيني (+86 أو 11 رقم)", false);
        username.setInputType(InputType.TYPE_CLASS_PHONE);
        username.setText(savedUser);
        card.addView(username, lpMatch(dp(12),0));

        EditText password = input("كلمة مرور حساب BYD", true);
        password.setText(securePrefs.get("password", ""));
        card.addView(password, lpMatch(dp(10),0));

        EditText pin = input("PIN التحكم — 6 أرقام", true);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setText(securePrefs.get("controlPin", ""));
        card.addView(pin, lpMatch(dp(10),0));

        TextView security = label("🔐 بيانات الدخول محفوظة محلياً بتشفير Android Keystore", 10, theme.muted, false);
        security.setGravity(Gravity.RIGHT);
        card.addView(security, lpMatch(dp(12),dp(13)));

        Button login = primaryButton("تسجيل الدخول إلى BYD China");
        card.addView(login, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        globalProgress = new ProgressBar(this);
        globalProgress.setIndeterminate(true);
        globalProgress.setVisibility(View.GONE);
        statusRow.addView(globalProgress, new LinearLayout.LayoutParams(dp(24), dp(24)));
        globalStatus = label("جاهز للاتصال", 11, theme.muted2, false);
        globalStatus.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sp.setMarginStart(dp(8));
        statusRow.addView(globalStatus, sp);
        card.addView(statusRow, lpMatch(dp(13),0));

        TextView themeHint = label("الثيم الافتراضي: الأحمر الرياضي • يمكن تغييره بعد الدخول", 10, theme.muted2, false);
        themeHint.setGravity(Gravity.CENTER);
        root.addView(themeHint, lpMatch(dp(10),0));

        login.setOnClickListener(v -> {
            String u = username.getText().toString().trim();
            String p = password.getText().toString();
            String cp = pin.getText().toString().trim();
            BydRegion region = (BydRegion) country.getSelectedItem();
            if (u.startsWith("+86") || u.startsWith("0086")) {
                region = BydRegion.SUPPORTED.get(0);
                country.setSelection(0);
            }
            if (u.isEmpty() || p.isEmpty()) { toast("أدخل حساب BYD وكلمة المرور"); return; }
            String normalizedPhone = u.replace(" ", "");
            if (normalizedPhone.startsWith("+86")) normalizedPhone = normalizedPhone.substring(3);
            if (normalizedPhone.startsWith("0086")) normalizedPhone = normalizedPhone.substring(4);
            if (region.china && !normalizedPhone.matches("1\\d{10}")) { toast("رقم حساب BYD الصيني يجب أن يكون 11 رقماً"); return; }
            if (!cp.isEmpty() && !cp.matches("\\d{6}")) { toast("PIN التحكم يجب أن يكون 6 أرقام"); return; }

            securePrefs.put("username", u);
            securePrefs.put("password", p);
            securePrefs.put("controlPin", cp);
            securePrefs.put("region", region.countryCode);
            config = new BydConfig(u,p,cp,region);
            client = new BydApiClient(this, config);
            setBusy(true, "تهيئة الاتصال المشفّر…");
            worker.execute(() -> {
                try {
                    client.prepare(text -> runOnUiThread(() -> setStatus(text)));
                    runOnUiThread(() -> setStatus("تسجيل الدخول إلى BYD China…"));
                    client.login();
                    runOnUiThread(() -> setStatus("جلب السيارات المرتبطة بالحساب…"));
                    vehicles = client.getVehicles();
                    if (vehicles.length() == 0) throw new BydApiClient.BydException("لم يعثر BYD على سيارات مرتبطة بهذا الحساب");
                    currentVehicle = vehicles.getJSONObject(0);
                    runOnUiThread(() -> {
                        setBusy(false, "تم الاتصال بنجاح");
                        showDashboard();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> { setBusy(false, "فشل الاتصال"); showError("تعذر تسجيل الدخول", readableError(e)); });
                }
            });
        });
        setContentView(scroll);
    }

    // ---------------------------------------------------------------------
    // Premium dashboard
    // ---------------------------------------------------------------------

    private void showDashboard() {
        applySystemBars();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(theme.bg);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(15), dp(16), dp(28));
        scroll.addView(root, matchWrap());

        // Header: menu/settings, brand, QR scanner.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView menu = circleIcon("☰", false);
        menu.setOnClickListener(v -> showSettings());
        header.addView(menu, new LinearLayout.LayoutParams(dp(48),dp(48)));

        LinearLayout titleBox = column();
        TextView title = label("CAR X • BYD CLOUD", 20, theme.accent, true);
        title.setGravity(Gravity.CENTER);
        TextView tag = label("سيارتك دائماً معك", 10, theme.muted, false);
        tag.setGravity(Gravity.CENTER);
        titleBox.addView(title);
        titleBox.addView(tag);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        tp.setMargins(dp(8),0,dp(8),0);
        header.addView(titleBox,tp);

        TextView qr = circleIcon("▣", true);
        qr.setContentDescription("مسح QR السيارة");
        qr.setOnClickListener(v -> startQrScan());
        header.addView(qr,new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(header, lpMatch(0,dp(14)));

        // Selector for multiple vehicles.
        vehicleSpinner = new Spinner(this);
        List<String> vehicleNames = new ArrayList<>();
        for(int i=0;i<vehicles.length();i++){
            JSONObject car=vehicles.optJSONObject(i);
            vehicleNames.add(VehicleCatalog.arabicName(car)+"  •  "+maskVin(BydApiClient.value(car,"vin")));
        }
        vehicleSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, vehicleNames));
        vehicleSpinner.setBackground(rounded(theme.surface,16,theme.accentFaint,1));
        vehicleSpinner.setPadding(dp(12),0,dp(12),0);
        root.addView(vehicleSpinner,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        vehicleSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                JSONObject car=vehicles.optJSONObject(pos); if(car==null)return;
                currentVehicle=car; renderVehicleHeader(); loadVehicleArt(); refreshRealtime();
            }
        });

        // Hero card inspired by the selected red/black concept.
        LinearLayout hero = column();
        hero.setPadding(dp(16),dp(15),dp(16),dp(15));
        hero.setBackground(gradient(new int[]{mix(theme.accent,theme.bg,0.15f),theme.surface},26,theme.accent,1));
        hero.setElevation(dp(6));
        root.addView(hero,lpMatch(dp(12),dp(12)));

        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        heroTop.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout nameBox=column();
        modelValue=label("BYD",21,theme.text,true); modelValue.setGravity(Gravity.RIGHT);
        vinValue=label("VIN —",10,theme.muted,false); vinValue.setGravity(Gravity.RIGHT);
        nameBox.addView(modelValue);nameBox.addView(vinValue,lpMatch(dp(4),0));
        heroTop.addView(nameBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView connected=chip("✓  الحساب مسجّل",mix(theme.green,theme.bg,0.12f),theme.green);
        heroTop.addView(connected);
        hero.addView(heroTop);

        heroCarArt = new ImageView(this);
        heroCarArt.setImageResource(theme.heroRes);
        heroCarArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroCarArt.setBackground(rounded(theme.surface2,18,0,0));
        hero.addView(heroCarArt, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(178)));

        LinearLayout metricRow=new LinearLayout(this);
        metricRow.setOrientation(LinearLayout.HORIZONTAL);
        metricRow.setGravity(Gravity.CENTER);
        metricRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        rangeValue=metric("— km");
        batteryValue=metric("—%");
        odometerValue=metric("— km");
        metricRow.addView(heroMetric("المدى المتبقي",rangeValue),new LinearLayout.LayoutParams(0,dp(86),1f));
        metricRow.addView(heroMetric("البطارية",batteryValue),new LinearLayout.LayoutParams(0,dp(86),1f));
        metricRow.addView(heroMetric("عداد المسافات",odometerValue),new LinearLayout.LayoutParams(0,dp(86),1f));
        hero.addView(metricRow,lpMatch(dp(4),0));

        // Four status cards.
        GridLayout info=new GridLayout(this);info.setColumnCount(2);info.setUseDefaultMargins(false);
        lockValue=smallMetric("القفل","—");
        tempValue=smallMetric("حرارة المقصورة","—");
        chargeValue=smallMetric("الشحن","—");
        cloudStateValue=smallMetric("اتصال السيارة","غير معروف");
        info.addView(infoCard("⌖",lockValue),gridLp());
        info.addView(infoCard("♨",tempValue),gridLp());
        info.addView(infoCard("⚡",chargeValue),gridLp());
        info.addView(infoCard("☁",cloudStateValue),gridLp());
        root.addView(info,lpMatch(0,dp(14)));

        // QR completion card.
        LinearLayout qrCard=featureRow("▣","مسح QR السيارة","أكمل تسجيل الدخول من QR الظاهر على شاشة السيارة");
        qrCard.setOnClickListener(v->startQrScan());
        root.addView(qrCard,lpMatch(0,dp(14)));

        sectionTitle(root,"التحكم السريع","أوامر سريعة لسيارتك BYD");
        GridLayout actions=new GridLayout(this);actions.setColumnCount(2);
        addAction(actions,"🔒","قفل السيارة","LOCKDOOR",false);
        addAction(actions,"🔓","فتح السيارة","OPENDOOR",true);
        addClimateAction(actions,"✣","تشغيل المكيف",true);
        addClimateAction(actions,"❄","إيقاف المكيف",false);
        addAction(actions,"◉","إضاءة الأنوار","FLASHLIGHTNOWHISTLE",false);
        addAction(actions,"⌖","العثور على السيارة","FINDCAR",false);
        addAction(actions,"↑","فتح الشنطة","OPENTRUNK",true);
        addAction(actions,"↓","إغلاق الشنطة","CLOSETRUNK",true);
        root.addView(actions,lpMatch(0,dp(14)));

        // Proximity feature entry from the original BYD app design.
        LinearLayout proximity=featureRow("◉","الدخول عند الاقتراب","إعداد فتح عند الاقتراب وقفل عند الابتعاد");
        proximity.setOnClickListener(v->showProximityInfo());
        root.addView(proximity,lpMatch(0,dp(10)));

        LinearLayout location=featureRow("📍","موقع السيارة","عرض موقع السيارة على الخريطة — يكتمل عند توفر GPS Cloud");
        location.setOnClickListener(v->toast("سيتم ربط الخريطة مع بيانات GPS بعد تثبيت قراءة البيانات الحية"));
        root.addView(location,lpMatch(0,dp(12)));

        Button refresh=primaryButton("تحديث بيانات السيارة");
        refresh.setOnClickListener(v->refreshRealtime());
        root.addView(refresh,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)));

        // Bottom navigation look.
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);
        nav.setBackground(rounded(theme.surface,18,theme.accentFaint,1));nav.setPadding(dp(4),dp(6),dp(4),dp(6));
        nav.addView(navItem("👤","حسابي",false,()->showSettings()),new LinearLayout.LayoutParams(0,dp(64),1));
        nav.addView(navItem("☆","الخدمات",false,()->toast("الخدمات — قريباً")),new LinearLayout.LayoutParams(0,dp(64),1));
        nav.addView(navItem("▥","حالة السيارة",false,()->refreshRealtime()),new LinearLayout.LayoutParams(0,dp(64),1));
        nav.addView(navItem("🚗","الرئيسية",true,()->{}),new LinearLayout.LayoutParams(0,dp(64),1));
        nav.addView(navItem("▦","المزيد",false,()->showSettings()),new LinearLayout.LayoutParams(0,dp(64),1));
        root.addView(nav,lpMatch(dp(14),dp(8)));

        TextView legal=label("Car X BYD • Alpha 4.1 • China Cloud • Dev _ Ibraheem",9,theme.muted2,false);
        legal.setGravity(Gravity.CENTER);root.addView(legal);

        setContentView(scroll);
        renderVehicleHeader();
        loadVehicleArt();
    }

    private void renderVehicleHeader(){
        if(currentVehicle==null||modelValue==null)return;
        modelValue.setText(VehicleCatalog.arabicName(currentVehicle));
        vinValue.setText("VIN  "+BydApiClient.value(currentVehicle,"vin"));
    }

    private void loadVehicleArt(){
        if(heroCarArt==null)return;
        heroCarArt.setImageResource(theme.heroRes);
        String url=VehicleCatalog.findImageUrl(currentVehicle);
        if(url.isEmpty() || !url.startsWith("https://")) return;
        worker.execute(()->{
            try(InputStream in=new URL(url).openStream()){
                Bitmap bmp=BitmapFactory.decodeStream(in);
                if(bmp!=null)runOnUiThread(()->{if(heroCarArt!=null)heroCarArt.setImageBitmap(bmp);});
            }catch(Exception ignored){}
        });
    }

    // ---------------------------------------------------------------------
    // Realtime and remote controls
    // ---------------------------------------------------------------------

    private void refreshRealtime(){
        if(client==null||currentVehicle==null)return;
        String vin=BydApiClient.value(currentVehicle,"vin"); int energy=BydApiClient.energyType(currentVehicle);
        setBusy(true,"طلب البيانات الحية من السيارة…");
        if (cloudStateValue != null) setSmallMetricValue(cloudStateValue,"جارٍ التحقق");
        worker.execute(()->{
            try{
                realtime=client.getRealtime(vin,energy);
                runOnUiThread(()->{renderRealtime();setBusy(false,"تم التحديث الآن");});
            }catch(Exception e){runOnUiThread(()->{
                if (cloudStateValue != null) setSmallMetricValue(cloudStateValue,"تعذر الاتصال");
                setBusy(false,"تعذر تحديث البيانات");
                toast(readableError(e));
            });}
        });
    }

    private void renderRealtime(){
        if(realtime==null)return;
        String battery=BydApiClient.value(realtime,"elecPercent","elec_percent","powerBattery","power_battery");
        String range=BydApiClient.value(realtime,"enduranceMileage","endurance_mileage","evEndurance","ev_endurance");
        String odo=BydApiClient.value(realtime,"totalMileage","total_mileage");
        String temp=BydApiClient.value(realtime,"tempInCar","temp_in_car");
        String locked=BydApiClient.value(realtime,"isLocked","is_locked");
        String charging=BydApiClient.value(realtime,"isCharging","is_charging","chargingState","charging_state");
        String online=BydApiClient.value(realtime,"onlineState","online_state","connectState","connect_state");
        batteryValue.setText(suffix(battery,"%")); rangeValue.setText(suffix(range," km")); odometerValue.setText(suffix(odo," km"));
        setSmallMetricValue(tempValue,suffix(temp,"°C"));
        setSmallMetricValue(lockValue,truthText(locked,"مقفلة","مفتوحة"));
        setSmallMetricValue(chargeValue,truthText(charging,"يشحن","غير مشحونة"));
        if (cloudStateValue != null) setSmallMetricValue(cloudStateValue, vehicleConnectionText(online, realtime));
    }

    private void addAction(GridLayout grid,String icon,String title,String command,boolean danger){
        LinearLayout tile=actionTile(icon,title,danger);grid.addView(tile,actionGridLp());
        tile.setOnClickListener(v->{Runnable run=()->executeCommand(title,command);if(danger)confirm("تأكيد الأمر",title+"؟",run);else run.run();});
    }

    private void addClimateAction(GridLayout grid,String icon,String title,boolean on){
        LinearLayout tile=actionTile(icon,title,false);grid.addView(tile,actionGridLp());
        tile.setOnClickListener(v->confirm("تأكيد المكيف",on?"تشغيل المكيف على 21°C لمدة 20 دقيقة؟":"إيقاف المكيف؟",()->executeClimate(on)));
    }

    private void executeCommand(String label,String command){
        if(currentVehicle==null)return;String vin=BydApiClient.value(currentVehicle,"vin");setBusy(true,"إرسال: "+label);
        worker.execute(()->{try{JSONObject result=client.remoteCommand(vin,command);runOnUiThread(()->{setBusy(false,"تم تنفيذ الأمر");toast(controlResultText(result));refreshRealtime();});}
            catch(Exception e){runOnUiThread(()->{setBusy(false,"فشل الأمر");showError("تعذر تنفيذ الأمر",readableError(e));});}});
    }

    private void executeClimate(boolean on){
        if(currentVehicle==null)return;String vin=BydApiClient.value(currentVehicle,"vin");setBusy(true,on?"تشغيل المكيف…":"إيقاف المكيف…");
        worker.execute(()->{try{JSONObject result=client.remoteClimate(vin,on,21.0,20);runOnUiThread(()->{setBusy(false,"تم إرسال أمر المكيف");toast(controlResultText(result));refreshRealtime();});}
            catch(Exception e){runOnUiThread(()->{setBusy(false,"فشل أمر المكيف");showError("المكيف",readableError(e));});}});
    }

    // ---------------------------------------------------------------------
    // QR scanner + BYD China scan-login endpoints
    // ---------------------------------------------------------------------

    private void startQrScan(){
        if(client==null){toast("سجل الدخول بحساب BYD أولاً");return;}
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingQrAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        openInlineQrScanner();
    }

    private void openInlineQrScanner(){
        try {
            qrScannerOpen = true;
            LinearLayout root = column();
            root.setBackgroundColor(Color.BLACK);
            root.setPadding(dp(14), dp(18), dp(14), dp(18));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView back = label("✕", 28, Color.WHITE, true);
            back.setGravity(Gravity.CENTER);
            back.setOnClickListener(v -> closeQrScanner());
            top.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
            TextView title = label("مسح QR السيارة", 20, Color.WHITE, true);
            title.setGravity(Gravity.RIGHT);
            top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            root.addView(top, lpMatch(0, dp(10)));

            TextView hint = label("وجّه الكاميرا إلى QR الظاهر على شاشة السيارة", 13, Color.LTGRAY, false);
            hint.setGravity(Gravity.RIGHT);
            root.addView(hint, lpMatch(0, dp(12)));

            qrScannerView = new DecoratedBarcodeView(this);
            qrScannerView.setStatusText("Car X • QR السيارة");
            root.addView(qrScannerView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            qrScannerView.decodeSingle(new BarcodeCallback() {
                @Override public void barcodeResult(BarcodeResult result) {
                    if (result == null || result.getText() == null || result.getText().trim().isEmpty()) return;
                    final String raw = result.getText();
                    runOnUiThread(() -> {
                        if (qrScannerView != null) {
                            try { qrScannerView.pause(); } catch (Exception ignored) {}
                        }
                        qrScannerOpen = false;
                        qrScannerView = null;
                        showDashboard();
                        handleScannedQr(raw);
                    });
                }
                @Override public void possibleResultPoints(List<ResultPoint> resultPoints) {}
            });

            setContentView(root);
            qrScannerView.resume();
        } catch (Throwable t) {
            qrScannerOpen = false;
            qrScannerView = null;
            showDashboard();
            showError("ماسح QR", "تعذر فتح الكاميرا: " + readableError(t));
        }
    }

    private void closeQrScanner(){
        if (qrScannerView != null) {
            try { qrScannerView.pause(); } catch (Exception ignored) {}
        }
        qrScannerOpen = false;
        qrScannerView = null;
        showDashboard();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingQrAfterPermission) {
                pendingQrAfterPermission = false;
                openInlineQrScanner();
            } else {
                pendingQrAfterPermission = false;
                showError("صلاحية الكاميرا", "لا يمكن مسح QR بدون السماح للكاميرا.");
            }
        }
    }

    private void handleScannedQr(String raw){
        QrPayload parsed=QrPayload.parse(raw);
        setBusy(true,"التحقق من QR مع BYD China…");
        worker.execute(()->{
            try{
                JSONObject auth=client.scanLoginByAuth(raw);
                String scanId=firstValue(auth,"scanId","scan_id","id");
                if(scanId.isEmpty())scanId=parsed.scanId;
                String vin=firstValue(auth,"vin","carVin","vehicleVin","scanVin");
                if(vin.isEmpty())vin=parsed.vin;
                final String finalScanId=scanId;
                final String finalVin=vin;
                runOnUiThread(()->{
                    setBusy(false,"QR صالح — بانتظار التأكيد");
                    lastQrScanId=finalScanId;lastQrVin=finalVin;
                    String msg="تمت قراءة QR والتحقق منه مع BYD.";
                    if(!finalVin.isEmpty())msg+="\n\nVIN: "+finalVin;
                    if(!finalScanId.isEmpty())msg+="\nجلسة QR: "+maskSecret(finalScanId);
                    msg+="\n\nهل تريد تأكيد تسجيل الدخول على شاشة السيارة؟";
                    new AlertDialog.Builder(this).setTitle("تأكيد QR السيارة").setMessage(msg)
                            .setNegativeButton("إلغاء",(d,w)->cancelQrQuietly(finalScanId))
                            .setPositiveButton("تأكيد",(d,w)->confirmQr(finalScanId,finalVin)).show();
                });
            }catch(Exception e){
                runOnUiThread(()->{setBusy(false,"فشل التحقق من QR");showError("QR السيارة",readableError(e)+"\n\nتمت إضافة مسارات BYD الأصلية: scanLoginByAuth / scanLoginByAction. إذا ظهر كود من السيرفر صوّره لي فقط.");});
            }
        });
    }

    private void confirmQr(String scanId,String vin){
        setBusy(true,"تأكيد تسجيل الدخول على السيارة…");
        worker.execute(()->{
            try{
                JSONObject result=client.scanLoginByAction(scanId,vin);
                securePrefs.put("qrLinked","1");
                runOnUiThread(()->{setBusy(false,"تم تأكيد QR");showError("تم الربط","تم إرسال تأكيد تسجيل الدخول إلى BYD بنجاح.\n"+serverMessage(result));});
            }catch(Exception e){runOnUiThread(()->{setBusy(false,"تعذر تأكيد QR");showError("تأكيد QR",readableError(e));});}
        });
    }

    private void cancelQrQuietly(String scanId){
        if(scanId==null||scanId.isEmpty())return;
        worker.execute(()->{try{client.scanLoginCancel(scanId);}catch(Exception ignored){}});
    }

    // ---------------------------------------------------------------------
    // Settings / themes / proximity feature
    // ---------------------------------------------------------------------

    private void showSettings(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(theme.bg);
        LinearLayout root=column();root.setPadding(dp(18),dp(18),dp(18),dp(28));scroll.addView(root,matchWrap());
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView t=label("الإعدادات",25,theme.text,true);t.setGravity(Gravity.RIGHT);top.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button back=ghostButton("رجوع");back.setOnClickListener(v->showDashboard());top.addView(back,new LinearLayout.LayoutParams(dp(80),dp(44)));root.addView(top,lpMatch(0,dp(22)));

        sectionTitle(root,"الثيمات","غيّر شكل التطبيق بدون تغيير محرك BYD China");
        GridLayout themes=new GridLayout(this);themes.setColumnCount(2);
        for(ThemeSpec spec:ThemeSpec.ALL){
            LinearLayout card=column();card.setGravity(Gravity.CENTER);card.setPadding(dp(12),dp(15),dp(12),dp(15));
            boolean active=spec.key.equals(theme.key);card.setBackground(rounded(spec.surface,18,active?spec.accent:spec.accentFaint,active?2:1));
            TextView swatch=label("●",28,spec.accent,true);swatch.setGravity(Gravity.CENTER);TextView name=label(spec.title,13,spec.text,true);name.setGravity(Gravity.CENTER);
            card.addView(swatch);card.addView(name);if(active){TextView a=label("مفعّل",9,spec.green,true);a.setGravity(Gravity.CENTER);card.addView(a);}
            GridLayout.LayoutParams lp=gridLp();lp.height=dp(105);themes.addView(card,lp);
            card.setOnClickListener(v->{securePrefs.put("theme",spec.key);theme=spec;applySystemBars();showSettings();});
        }
        root.addView(themes,lpMatch(0,dp(18)));

        sectionTitle(root,"ربط السيارة","استخدم QR الظاهر على شاشة السيارة بعد تسجيل الدخول");
        LinearLayout qr=featureRow("▣","مسح QR السيارة",securePrefs.get("qrLinked","").equals("1")?"تم تأكيد QR سابقاً":"جاهز للمسح");
        qr.setOnClickListener(v->startQrScan());root.addView(qr,lpMatch(0,dp(12)));

        LinearLayout prox=featureRow("◉","الدخول عند الاقتراب","فتح عند الاقتراب وقفل عند الابتعاد");prox.setOnClickListener(v->showProximityInfo());root.addView(prox,lpMatch(0,dp(12)));

        Button logout=ghostButton("تسجيل الخروج");logout.setOnClickListener(v->{client=null;showLogin();});root.addView(logout,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));
        setContentView(scroll);
    }

    private void showProximityInfo(){
        new AlertDialog.Builder(this).setTitle("الدخول عند الاقتراب")
                .setMessage("جهزنا مكان الميزة داخل Car X بنفس فكرة تطبيق BYD الأصلي. التشغيل الحقيقي يحتاج Bluetooth Digital Key وصلاحيات الخلفية الخاصة بالسيارة؛ لن أفعّل فتح السيارة تلقائياً قبل التأكد من بروتوكول المفتاح على سيارتك حتى لا نرسل أوامر غير آمنة.")
                .setPositiveButton("حسناً",null).show();
    }

    // ---------------------------------------------------------------------
    // UI helpers
    // ---------------------------------------------------------------------

    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return l;}
    private TextView label(String t,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(color);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private EditText input(String hint,boolean password){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(theme.muted2);e.setTextColor(theme.text);e.setTextSize(14);e.setSingleLine(true);e.setPadding(dp(15),0,dp(15),0);e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);e.setBackground(rounded(theme.surface2,16,theme.accentFaint,1));if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    private Button primaryButton(String text){Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(theme.light?Color.WHITE:Color.WHITE);b.setAllCaps(false);b.setBackground(gradient(new int[]{theme.accent,mix(theme.accent,Color.BLACK,0.25f)},18,0,0));b.setElevation(dp(3));return b;}
    private Button ghostButton(String text){Button b=new Button(this);b.setText(text);b.setTextColor(theme.accent);b.setTextSize(12);b.setAllCaps(false);b.setBackground(rounded(theme.surface,16,theme.accentFaint,1));return b;}
    private TextView chip(String text,int bg,int fg){TextView t=label(text,9,fg,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(10),dp(6),dp(10),dp(6));t.setBackground(rounded(bg,20,0,0));return t;}
    private TextView circleIcon(String text,boolean accent){TextView t=label(text,22,accent?theme.accent:theme.text,true);t.setGravity(Gravity.CENTER);t.setBackground(rounded(theme.surface,24,theme.accentFaint,1));return t;}
    private GradientDrawable rounded(int color,int radiusDp,int strokeColor,int strokeDp){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radiusDp));if(strokeDp>0)g.setStroke(dp(strokeDp),strokeColor);return g;}
    private GradientDrawable gradient(int[] colors,int radiusDp,int strokeColor,int strokeDp){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);g.setCornerRadius(dp(radiusDp));if(strokeDp>0)g.setStroke(dp(strokeDp),strokeColor);return g;}
    private int mix(int a,int b,float ratio){ratio=Math.max(0f,Math.min(1f,ratio));return Color.rgb((int)(Color.red(a)*(1-ratio)+Color.red(b)*ratio),(int)(Color.green(a)*(1-ratio)+Color.green(b)*ratio),(int)(Color.blue(a)*(1-ratio)+Color.blue(b)*ratio));}
    private TextView metric(String val){TextView v=label(val,25,theme.accent,true);v.setGravity(Gravity.CENTER);return v;}
    private LinearLayout heroMetric(String name,TextView value){LinearLayout l=column();l.setGravity(Gravity.CENTER);TextView n=label(name,10,theme.muted,false);n.setGravity(Gravity.CENTER);l.addView(value);l.addView(n);return l;}
    private TextView smallMetric(String title,String value){TextView v=label(title+"\n"+value,13,theme.text,true);v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);v.setTag(title);v.setPadding(dp(7),0,dp(7),0);return v;}
    private View infoCard(String icon,TextView v){LinearLayout c=column();c.setPadding(dp(12),dp(10),dp(12),dp(10));c.setBackground(rounded(theme.surface,18,theme.accentFaint,1));LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);row.addView(v,new LinearLayout.LayoutParams(0,dp(64),1));TextView i=label(icon,22,theme.accent,true);i.setGravity(Gravity.CENTER);row.addView(i,new LinearLayout.LayoutParams(dp(44),dp(44)));c.addView(row);return c;}
    private void setSmallMetricValue(TextView v,String value){String title=String.valueOf(v.getTag());v.setText(title+"\n"+value);}
    private void sectionTitle(LinearLayout root,String title,String subtitle){TextView t=label(title,19,theme.text,true);t.setGravity(Gravity.RIGHT);root.addView(t);TextView s=label(subtitle,10,theme.muted,false);s.setGravity(Gravity.RIGHT);root.addView(s,lpMatch(dp(3),dp(9)));}
    private LinearLayout actionTile(String icon,String title,boolean danger){LinearLayout tile=column();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(8),dp(10),dp(8),dp(10));tile.setBackground(rounded(theme.surface,18,danger?mix(theme.danger,theme.bg,0.35f):theme.accentFaint,1));TextView i=label(icon,23,danger?theme.danger:theme.accent,true);i.setGravity(Gravity.CENTER);TextView t=label(title,12,theme.text,true);t.setGravity(Gravity.CENTER);tile.addView(i);tile.addView(t,lpMatch(dp(3),0));return tile;}
    private LinearLayout featureRow(String icon,String title,String subtitle){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);row.setPadding(dp(15),dp(12),dp(15),dp(12));row.setBackground(rounded(theme.surface,18,theme.accentFaint,1));TextView ic=label(icon,24,theme.accent,true);ic.setGravity(Gravity.CENTER);row.addView(ic,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout text=column();TextView tt=label(title,14,theme.text,true);TextView ss=label(subtitle,10,theme.muted,false);text.addView(tt);text.addView(ss,lpMatch(dp(3),0));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMarginStart(dp(10));row.addView(text,p);TextView arrow=label("‹",28,theme.accent,false);row.addView(arrow,new LinearLayout.LayoutParams(dp(30),dp(46)));return row;}
    private LinearLayout navItem(String icon,String text,boolean active,Runnable action){LinearLayout box=column();box.setGravity(Gravity.CENTER);TextView i=label(icon,20,active?theme.accent:theme.muted,true);i.setGravity(Gravity.CENTER);TextView t=label(text,9,active?theme.accent:theme.muted,false);t.setGravity(Gravity.CENTER);box.addView(i);box.addView(t);box.setOnClickListener(v->action.run());return box;}
    private GridLayout.LayoutParams gridLp(){GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=dp(86);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(dp(4),dp(4),dp(4),dp(4));return lp;}
    private GridLayout.LayoutParams actionGridLp(){GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=dp(88);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(dp(4),dp(4),dp(4),dp(4));return lp;}
    private LinearLayout.LayoutParams lpMatch(int top,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.topMargin=top;lp.bottomMargin=bottom;return lp;}
    private ViewGroup.LayoutParams matchWrap(){return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private void setBusy(boolean busy,String status){if(globalProgress!=null)globalProgress.setVisibility(busy?View.VISIBLE:View.GONE);setStatus(status);}
    private void setStatus(String status){if(globalStatus!=null)globalStatus.setText(status);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void showError(String title,String msg){new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("حسناً",null).show();}
    private void confirm(String title,String msg,Runnable yes){new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setNegativeButton("إلغاء",null).setPositiveButton("تنفيذ",(d,w)->yes.run()).show();}
    private String readableError(Throwable e){String m=e.getMessage();if(m==null||m.trim().isEmpty())m=e.getClass().getSimpleName();return m;}
    private int findRegionIndex(String code){for(int i=0;i<BydRegion.SUPPORTED.size();i++)if(BydRegion.SUPPORTED.get(i).countryCode.equals(code))return i;return 0;}
    private String maskVin(String vin){if(vin==null||vin.length()<8)return vin;return vin.substring(0,4)+"••••"+vin.substring(vin.length()-4);}
    private String suffix(String v,String suffix){return v==null||v.isEmpty()||"—".equals(v)?"—":v+suffix;}
    private String truthText(String v,String yes,String no){if(v==null||"—".equals(v))return"—";String x=v.toLowerCase();return("true".equals(x)||"1".equals(x)||"on".equals(x)||"locked".equals(x))?yes:no;}
    private String vehicleConnectionText(String raw, JSONObject payload){
        String x = raw == null ? "" : raw.trim().toLowerCase();
        if ("1".equals(x) || "online".equals(x) || "connected".equals(x)) return "متصلة";
        if ("0".equals(x) || "offline".equals(x) || "disconnected".equals(x)) return "غير متصلة";
        if (payload != null) {
            boolean meaningful = payload.has("time") || payload.has("speed") || payload.has("elecPercent") || payload.has("enduranceMileage") || payload.has("totalMileage");
            if (meaningful) return "بيانات متاحة";
        }
        return "غير معروف";
    }

    private String controlResultText(JSONObject o){if(o==null)return"تم الإرسال";int state=o.optInt("controlState",-1),res=o.optInt("res",-1);if(state==1||res==2)return"تم التنفيذ بنجاح";if(state==2)return"رفضت السيارة الأمر";return o.optString("message","تم إرسال الأمر");}
    private String firstValue(JSONObject o,String...keys){if(o==null)return"";for(String k:keys){Object v=o.opt(k);if(v!=null&&!JSONObject.NULL.equals(v)){String s=String.valueOf(v);if(!s.isEmpty())return s;}}JSONObject data=o.optJSONObject("data");if(data!=null)return firstValue(data,keys);return"";}
    private String maskSecret(String s){if(s==null||s.length()<8)return"••••";return s.substring(0,3)+"••••"+s.substring(s.length()-3);}
    private String serverMessage(JSONObject o){if(o==null)return"";String m=firstValue(o,"message","msg","result");return m.isEmpty()?"تم تنفيذ الطلب":m;}
}
