package com.carx.byd;

import android.app.Activity;
import android.app.AlertDialog;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(8,8,8);
    private static final int PANEL = Color.rgb(20,20,20);
    private static final int PANEL_2 = Color.rgb(28,28,28);
    private static final int GOLD = Color.rgb(215,174,84);
    private static final int GOLD_DARK = Color.rgb(143,116,57);
    private static final int WHITE = Color.rgb(247,247,247);
    private static final int MUTED = Color.rgb(168,168,168);
    private static final int GREEN = Color.rgb(96,200,130);
    private static final int RED = Color.rgb(235,95,95);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SecurePrefs securePrefs;
    private BydApiClient client;
    private BydConfig config;
    private JSONArray vehicles = new JSONArray();
    private JSONObject currentVehicle;
    private JSONObject realtime = new JSONObject();

    private TextView globalStatus;
    private ProgressBar globalProgress;
    private TextView batteryValue, rangeValue, odometerValue, lockValue, tempValue, chargeValue, vinValue, modelValue;
    private Spinner vehicleSpinner;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(Window.FEATURE_NO_TITLE);
        securePrefs = new SecurePrefs(this);
        showLogin();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void showLogin() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(24), dp(26), dp(24), dp(30));
        scroll.addView(root, matchWrap());

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER);
        header.setOrientation(LinearLayout.VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(com.carx.byd.R.drawable.ic_carx_logo);
        header.addView(logo, new LinearLayout.LayoutParams(dp(94), dp(94)));
        TextView title = label("CAR X  •  BYD CLOUD", 25, GOLD, true);
        title.setGravity(Gravity.CENTER);
        header.addView(title, lpMatch(dp(6),0));
        TextView sub = label("تحكم ذكي بسيارتك — Dev _ Ibraheem", 13, MUTED, false);
        sub.setGravity(Gravity.CENTER);
        header.addView(sub, lpMatch(dp(5),0));
        root.addView(header, lpMatch(0, dp(18)));

        TextView hero = label("سيارتك. حسابك. تحكمك.", 21, WHITE, true);
        hero.setGravity(Gravity.RIGHT);
        root.addView(hero, lpMatch(dp(20), dp(5)));
        TextView hero2 = label("اتصال مباشر بخدمات BYD Cloud بدون Home Assistant.", 13, MUTED, false);
        hero2.setGravity(Gravity.RIGHT);
        root.addView(hero2, lpMatch(0, dp(18)));

        LinearLayout card = column();
        card.setPadding(dp(16), dp(18), dp(16), dp(18));
        card.setBackground(rounded(PANEL, 22, GOLD_DARK, 1));
        root.addView(card, lpMatch(0, dp(16)));

        TextView countryLabel = label("الدولة / سيرفر BYD", 12, MUTED, true);
        card.addView(countryLabel, lpMatch(0, dp(7)));
        Spinner country = new Spinner(this);
        ArrayAdapter<BydRegion> regionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BydRegion.SUPPORTED);
        country.setAdapter(regionAdapter);
        country.setBackground(rounded(PANEL_2, 14, GOLD_DARK, 1));
        country.setPadding(dp(10),0,dp(10),0);
        String savedUser = securePrefs.get("username", "");
        String savedCode = securePrefs.get("region", "CN");
        if (savedUser.startsWith("+86") || savedUser.startsWith("0086")) savedCode = "CN";
        int savedRegion = findRegionIndex(savedCode);
        country.setSelection(savedRegion);
        card.addView(country, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        EditText username = input("رقم الهاتف الصيني (+86 أو 11 رقم)", false);
        username.setInputType(InputType.TYPE_CLASS_PHONE);
        username.setText(securePrefs.get("username", ""));
        card.addView(username, lpMatch(dp(13),0));
        TextView phoneHint = label("حساب الصين: اكتب +86 عادي؛ Car X يحوله تلقائياً إلى الرقم المحلي المطلوب من BYD.", 10, MUTED, false);
        phoneHint.setGravity(Gravity.RIGHT);
        card.addView(phoneHint, lpMatch(dp(5),0));
        EditText password = input("كلمة مرور حساب BYD", true);
        password.setText(securePrefs.get("password", ""));
        card.addView(password, lpMatch(dp(11),0));
        EditText pin = input("PIN التحكم — 6 أرقام", true);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setText(securePrefs.get("controlPin", ""));
        card.addView(pin, lpMatch(dp(11),0));

        TextView security = label("🔐 يتم حفظ بيانات الدخول محليًا بتشفير Android Keystore.", 11, MUTED, false);
        security.setGravity(Gravity.RIGHT);
        card.addView(security, lpMatch(dp(12), dp(13)));

        Button login = primaryButton("تسجيل الدخول إلى BYD");
        card.addView(login, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        globalProgress = new ProgressBar(this);
        globalProgress.setIndeterminate(true);
        globalProgress.setVisibility(View.GONE);
        statusRow.addView(globalProgress, new LinearLayout.LayoutParams(dp(28), dp(28)));
        globalStatus = label("جاهز للاتصال", 12, MUTED, false);
        globalStatus.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sp.setMarginEnd(dp(10));
        statusRow.addView(globalStatus, sp);
        card.addView(statusRow, lpMatch(dp(13),0));

        TextView foot = label("Car X BYD • Alpha 2 China\nBYD China WBSK + pyBYD interoperability", 11, Color.rgb(105,105,105), false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot, lpMatch(dp(7),0));

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
            if (region.china && !normalizedPhone.matches("1\\d{10}")) { toast("رقم حساب BYD الصيني يجب أن يكون 11 رقماً، ويمكن إدخاله مع +86"); return; }
            if (!cp.isEmpty() && !cp.matches("\\d{6}")) { toast("PIN التحكم يجب أن يكون 6 أرقام"); return; }
            securePrefs.put("username", u);
            securePrefs.put("password", p);
            securePrefs.put("controlPin", cp);
            securePrefs.put("region", region.countryCode);
            config = new BydConfig(u,p,cp,region);
            client = new BydApiClient(this, config);
            setBusy(true, "تهيئة اتصال Car X…");
            worker.execute(() -> {
                try {
                    client.prepare(text -> runOnUiThread(() -> setStatus(text)));
                    runOnUiThread(() -> setStatus("تسجيل الدخول إلى BYD Cloud…"));
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

    private void showDashboard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(root, matchWrap());

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_carx_logo);
        top.addView(logo, new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout brand = column();
        TextView b1 = label("CAR X",21,GOLD,true); brand.addView(b1);
        TextView b2 = label("BYD Remote • Dev _ Ibraheem",11,MUTED,false); brand.addView(b2);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT,1f); bp.setMarginStart(dp(10));
        top.addView(brand,bp);
        Button exit=ghostButton("خروج"); exit.setOnClickListener(v->{client=null;showLogin();});
        top.addView(exit,new LinearLayout.LayoutParams(dp(72),dp(42)));
        root.addView(top,lpMatch(0,dp(14)));

        vehicleSpinner = new Spinner(this);
        List<String> vehicleNames = new ArrayList<>();
        for(int i=0;i<vehicles.length();i++){
            JSONObject car=vehicles.optJSONObject(i);
            String name=BydApiClient.value(car,"autoAlias","auto_alias");
            if("—".equals(name)||name.trim().isEmpty()) name=BydApiClient.value(car,"modelName","model_name");
            vehicleNames.add(name+"  •  "+maskVin(BydApiClient.value(car,"vin")));
        }
        ArrayAdapter<String> va=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,vehicleNames);
        vehicleSpinner.setAdapter(va); vehicleSpinner.setBackground(rounded(PANEL,14,GOLD_DARK,1));
        vehicleSpinner.setPadding(dp(10),0,dp(10),0);
        root.addView(vehicleSpinner,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
        vehicleSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                JSONObject car=vehicles.optJSONObject(pos); if(car==null)return; currentVehicle=car; renderVehicleHeader(); refreshRealtime();
            }
        });

        LinearLayout hero=column(); hero.setPadding(dp(18),dp(18),dp(18),dp(18)); hero.setBackground(rounded(PANEL,24,GOLD_DARK,1));
        root.addView(hero,lpMatch(dp(14),dp(14)));
        modelValue=label("BYD",23,WHITE,true); modelValue.setGravity(Gravity.RIGHT); hero.addView(modelValue);
        vinValue=label("VIN —",11,MUTED,false); vinValue.setGravity(Gravity.RIGHT); hero.addView(vinValue,lpMatch(dp(4),dp(14)));
        LinearLayout energy=new LinearLayout(this); energy.setOrientation(LinearLayout.HORIZONTAL); energy.setGravity(Gravity.CENTER);
        batteryValue=metric("—%","البطارية"); rangeValue=metric("— km","المدى");
        energy.addView(wrapMetric(batteryValue,"البطارية"),new LinearLayout.LayoutParams(0,dp(92),1));
        energy.addView(wrapMetric(rangeValue,"المدى"),new LinearLayout.LayoutParams(0,dp(92),1));
        hero.addView(energy,lpMatch(0,0));

        GridLayout metrics=new GridLayout(this); metrics.setColumnCount(2); metrics.setUseDefaultMargins(false);
        odometerValue=smallMetric("العداد","— km"); lockValue=smallMetric("القفل","—"); tempValue=smallMetric("حرارة المقصورة","—°"); chargeValue=smallMetric("الشحن","—");
        metrics.addView(metricCard(odometerValue),gridLp()); metrics.addView(metricCard(lockValue),gridLp());
        metrics.addView(metricCard(tempValue),gridLp()); metrics.addView(metricCard(chargeValue),gridLp());
        root.addView(metrics,lpMatch(0,dp(14)));

        LinearLayout statusBox=new LinearLayout(this); statusBox.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT); statusBox.setPadding(dp(14),dp(10),dp(14),dp(10)); statusBox.setBackground(rounded(PANEL_2,14,0,0));
        globalProgress=new ProgressBar(this); globalProgress.setIndeterminate(true); globalProgress.setVisibility(View.GONE); statusBox.addView(globalProgress,new LinearLayout.LayoutParams(dp(24),dp(24)));
        globalStatus=label("متصل بـ BYD Cloud",12,MUTED,false); globalStatus.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams gsp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);gsp.setMarginEnd(dp(8)); statusBox.addView(globalStatus,gsp);
        root.addView(statusBox,lpMatch(0,dp(18)));

        TextView actTitle=label("التحكم السريع",18,WHITE,true); actTitle.setGravity(Gravity.RIGHT); root.addView(actTitle,lpMatch(0,dp(9)));
        TextView actHint=label("الأزرار تظهر هنا بشكل موحّد؛ BYD يقرر دعم كل وظيفة حسب السيارة.",11,MUTED,false); actHint.setGravity(Gravity.RIGHT); root.addView(actHint,lpMatch(0,dp(12)));

        GridLayout actions=new GridLayout(this); actions.setColumnCount(2);
        addAction(actions,"🔒  قفل السيارة","LOCKDOOR",false);
        addAction(actions,"🔓  فتح السيارة","OPENDOOR",true);
        addClimateAction(actions,"❄  تشغيل المكيف",true);
        addClimateAction(actions,"⏻  إيقاف المكيف",false);
        addAction(actions,"💡  وميض الأنوار","FLASHLIGHTNOWHISTLE",false);
        addAction(actions,"📍  العثور على السيارة","FINDCAR",false);
        addAction(actions,"⬆  فتح الشنطة","OPENTRUNK",true);
        addAction(actions,"⬇  إغلاق الشنطة","CLOSETRUNK",true);
        root.addView(actions,lpMatch(0,dp(16)));

        Button refresh=primaryButton("تحديث بيانات السيارة"); refresh.setOnClickListener(v->refreshRealtime()); root.addView(refresh,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
        TextView legal=label("Car X BYD Alpha 2 • China + Global Cloud\nBYD واسمها التجاري ملك لأصحابها. لا تُرسل بيانات دخولك إلى Car X أو ChatGPT.",10,Color.rgb(100,100,100),false); legal.setGravity(Gravity.CENTER); root.addView(legal,lpMatch(dp(16),0));

        setContentView(scroll);
        renderVehicleHeader();
    }

    private void renderVehicleHeader(){
        if(currentVehicle==null||modelValue==null)return;
        String alias=BydApiClient.value(currentVehicle,"autoAlias","auto_alias");
        String model=BydApiClient.value(currentVehicle,"modelName","model_name");
        modelValue.setText((!"—".equals(alias)&&!alias.isEmpty()?alias+"  •  ":"")+model);
        vinValue.setText("VIN  " + BydApiClient.value(currentVehicle,"vin"));
    }

    private void refreshRealtime(){
        if(client==null||currentVehicle==null)return;
        String vin=BydApiClient.value(currentVehicle,"vin"); int energy=BydApiClient.energyType(currentVehicle);
        setBusy(true,"طلب البيانات الحية من السيارة…");
        worker.execute(()->{
            try{
                realtime=client.getRealtime(vin,energy);
                runOnUiThread(()->{renderRealtime();setBusy(false,"تم التحديث الآن");});
            }catch(Exception e){runOnUiThread(()->{setBusy(false,"تعذر تحديث البيانات");toast(readableError(e));});}
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
        batteryValue.setText(suffix(battery,"%")); rangeValue.setText(suffix(range," km"));
        setSmallMetricValue(odometerValue,suffix(odo," km")); setSmallMetricValue(tempValue,suffix(temp,"°C"));
        setSmallMetricValue(lockValue,truthText(locked,"مقفلة","مفتوحة")); setSmallMetricValue(chargeValue,truthText(charging,"يشحن","غير مشحونة"));
    }

    private void addAction(GridLayout grid,String title,String command,boolean danger){
        Button b=actionButton(title,danger); GridLayout.LayoutParams lp=gridLp(); lp.height=dp(62); grid.addView(b,lp);
        b.setOnClickListener(v->{
            Runnable run=()->executeCommand(title,command);
            if(danger) confirm("تأكيد الأمر",title+"؟",run); else run.run();
        });
    }

    private void addClimateAction(GridLayout grid,String title,boolean on){
        Button b=actionButton(title,false); GridLayout.LayoutParams lp=gridLp();lp.height=dp(62);grid.addView(b,lp);
        b.setOnClickListener(v->confirm("تأكيد المكيف",on?"تشغيل المكيف على 21°C لمدة 20 دقيقة؟":"إيقاف المكيف؟",()->executeClimate(on)));
    }

    private void executeCommand(String label,String command){
        if(currentVehicle==null)return; String vin=BydApiClient.value(currentVehicle,"vin"); setBusy(true,"إرسال: "+label);
        worker.execute(()->{try{JSONObject result=client.remoteCommand(vin,command);runOnUiThread(()->{setBusy(false,"تم تنفيذ الأمر");toast(controlResultText(result));refreshRealtime();});}
            catch(Exception e){runOnUiThread(()->{setBusy(false,"فشل الأمر");showError("تعذر تنفيذ الأمر",readableError(e));});}});
    }

    private void executeClimate(boolean on){
        if(currentVehicle==null)return;String vin=BydApiClient.value(currentVehicle,"vin");setBusy(true,on?"تشغيل المكيف…":"إيقاف المكيف…");
        worker.execute(()->{try{JSONObject result=client.remoteClimate(vin,on,21.0,20);runOnUiThread(()->{setBusy(false,"تم إرسال أمر المكيف");toast(controlResultText(result));refreshRealtime();});}
            catch(Exception e){runOnUiThread(()->{setBusy(false,"فشل أمر المكيف");showError("المكيف",readableError(e));});}});
    }

    private String controlResultText(JSONObject o){
        if(o==null)return"تم الإرسال"; int state=o.optInt("controlState",-1);int res=o.optInt("res",-1);
        if(state==1||res==2)return"تم التنفيذ بنجاح"; if(state==2)return"رفضت السيارة الأمر";return o.optString("message","تم إرسال الأمر");
    }

    private void confirm(String title,String msg,Runnable yes){
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setNegativeButton("إلغاء",null).setPositiveButton("تنفيذ",(d,w)->yes.run()).show();
    }

    // ---------- UI helpers ----------
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return l;}
    private TextView label(String t,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(t);v.setTextSize(sp);v.setTextColor(color);v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private EditText input(String hint,boolean password){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(115,115,115));e.setTextColor(WHITE);e.setTextSize(14);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);e.setBackground(rounded(PANEL_2,14,GOLD_DARK,1));if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    private Button primaryButton(String text){Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.BLACK);b.setAllCaps(false);b.setBackground(rounded(GOLD,16,0,0));return b;}
    private Button ghostButton(String text){Button b=new Button(this);b.setText(text);b.setTextColor(GOLD);b.setTextSize(12);b.setAllCaps(false);b.setBackground(rounded(PANEL,14,GOLD_DARK,1));return b;}
    private Button actionButton(String text,boolean danger){Button b=new Button(this);b.setText(text);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(danger?Color.rgb(255,205,205):WHITE);b.setGravity(Gravity.CENTER);b.setBackground(rounded(PANEL,16,danger?Color.rgb(120,55,55):GOLD_DARK,1));return b;}
    private GradientDrawable rounded(int color,int radiusDp,int strokeColor,int strokeDp){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radiusDp));if(strokeDp>0)g.setStroke(dp(strokeDp),strokeColor);return g;}
    private LinearLayout wrapMetric(TextView value,String label){LinearLayout c=column();c.setGravity(Gravity.CENTER);c.addView(value);TextView l=MainActivity.this.label(label,11,MUTED,false);l.setGravity(Gravity.CENTER);c.addView(l);return c;}
    private TextView metric(String val,String ignored){TextView v=label(val,27,GOLD,true);v.setGravity(Gravity.CENTER);return v;}
    private TextView smallMetric(String title,String value){TextView v=label(title+"\n"+value,14,WHITE,true);v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);v.setTag(title);v.setPadding(dp(13),dp(10),dp(13),dp(10));return v;}
    private View metricCard(TextView v){LinearLayout c=column();c.setPadding(dp(5),dp(5),dp(5),dp(5));c.setBackground(rounded(PANEL,16,0,0));c.addView(v,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(74)));return c;}
    private void setSmallMetricValue(TextView v,String value){String title=String.valueOf(v.getTag());v.setText(title+"\n"+value);}
    private GridLayout.LayoutParams gridLp(){GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=dp(84);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(dp(5),dp(5),dp(5),dp(5));return lp;}
    private LinearLayout.LayoutParams lpMatch(int top,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.topMargin=top;lp.bottomMargin=bottom;return lp;}
    private ViewGroup.LayoutParams matchWrap(){return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private void setBusy(boolean busy,String status){if(globalProgress!=null)globalProgress.setVisibility(busy?View.VISIBLE:View.GONE);setStatus(status);}
    private void setStatus(String status){if(globalStatus!=null)globalStatus.setText(status);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void showError(String title,String msg){new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("حسناً",null).show();}
    private String readableError(Throwable e){String m=e.getMessage();if(m==null||m.trim().isEmpty())m=e.getClass().getSimpleName();return m;}
    private int findRegionIndex(String code){for(int i=0;i<BydRegion.SUPPORTED.size();i++)if(BydRegion.SUPPORTED.get(i).countryCode.equals(code))return i;return 0;}
    private String maskVin(String vin){if(vin==null||vin.length()<8)return vin;return vin.substring(0,4)+"••••"+vin.substring(vin.length()-4);}
    private String suffix(String v,String suffix){return v==null||v.isEmpty()||"—".equals(v)?"—":v+suffix;}
    private String truthText(String v,String yes,String no){if(v==null||"—".equals(v))return"—";String x=v.toLowerCase();return("true".equals(x)||"1".equals(x)||"on".equals(x)||"locked".equals(x))?yes:no;}
}
