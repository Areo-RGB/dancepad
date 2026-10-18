package dev.paul.dancepad;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.SeekBar;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Local controller viewer. Mock signals never leave this app. */
public class PadActivity extends Activity implements InputManager.InputDeviceListener {
    private static final int BG=Color.rgb(16,20,30), TILE=Color.rgb(24,34,50), TEXT=Color.rgb(241,246,255),
        MUTED=Color.rgb(159,177,199), CYAN=Color.rgb(9,189,232), PINK=Color.rgb(229,0,131),
        GREEN=Color.rgb(22,125,86), LIME=Color.rgb(112,255,189);
    private final String[] names={"SELECT","START","CROSS","UP","CIRCLE","LEFT","CENTER","RIGHT","TRIANGLE","DOWN","SQUARE"};
    private final String[] symbols={"SELECT","START","×","↑","○","←","Stay Cool!","→","△","↓","□"};
    private final String[] defaults={"B9","B10","B7","B3","B8","B1","Y+","B4","B6","B2","B5"};
    private final Map<String,Set<String>> mapping=new LinkedHashMap<>();
    private final Map<String,Button> tiles=new LinkedHashMap<>();
    private final Set<String> mock=new HashSet<>(), liveKeys=new HashSet<>(), liveAxes=new HashSet<>(), demo=new HashSet<>(), remote=new HashSet<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final TextView[] rawButtons=new TextView[12];
    private TextView status,hint,activeText,rawText,lastText;
    private Switch mockSwitch,bridgeSwitch;
    private volatile Socket bridgeSocket;
    private volatile int bridgeGeneration;
    private boolean resumed;
    private String bridgeStatus="PC bridge · connecting…";
    private Button demoButton,mapButton;
    private boolean demoRunning,mappingMode;
    private String learning,lastEvent="No presses yet";
    private int demoStep;
    private SharedPreferences prefs;
    private InputManager inputs;
    private View testerRoot;
    private RhythmView rhythm;
    private boolean gameMode;
    private Button pauseGame;
    private TextView gameSource;
    private final Runnable demoTick=new Runnable() {
        @Override public void run() {
            if(!demoRunning)return;
            demo.clear();
            if(demoStep%2==0) {
                String[][] steps={{"B3"},{"B4"},{"B2"},{"B1"},{"B1","B4"},{"B7"},{"B8"},{"B6"},{"B5"},{"Y+"},{"B9"},{"B10"}};
                demo.addAll(Arrays.asList(steps[(demoStep/2)%steps.length]));
                lastEvent="DEMO · pressed "+String.join(", ",demo);
            } else lastEvent="DEMO · released";
            demoStep++;paint();handler.postDelayed(this,demoStep%2==1?650:300);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);prefs=getSharedPreferences("pad_mapping",MODE_PRIVATE);
        for(int i=0;i<names.length;i++)mapping.put(names[i],new HashSet<>(prefs.getStringSet(names[i],Set.of(defaults[i]))));
        inputs=(InputManager)getSystemService(INPUT_SERVICE);buildUi();
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,()->{if(gameMode)showTester();else finish();});
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout box(boolean horizontal){LinearLayout l=new LinearLayout(this);l.setOrientation(horizontal?0:1);l.setMotionEventSplittingEnabled(true);return l;}
    private TextView label(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(4),dp(5),dp(4),dp(5));return t;}
    private GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));d.setStroke(dp(2),stroke);return d;}
    private Button control(String text){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(TEXT);b.setTextSize(14);b.setMinHeight(dp(48));b.setMinimumWidth(0);b.setPadding(dp(10),0,dp(10),0);b.setBackground(shape(TILE,Color.rgb(58,78,102),10));return b;}
    private void buildUi(){
        boolean wide=getResources().getConfiguration().screenWidthDp>=840;
        LinearLayout root=box(false);root.setBackgroundColor(BG);root.setPadding(dp(18),dp(8),dp(18),dp(8));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bars.left+dp(18),bars.top+dp(8),bars.right+dp(18),bars.bottom+dp(8));return insets;
        });
        TextView title=label("DANCE PAD",26,TEXT);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        status=label("",13,MUTED);root.addView(status);
        LinearLayout body=box(wide);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout pad=box(false);pad.setPadding(dp(4),dp(4),dp(4),dp(4));pad.setBackground(shape(Color.rgb(8,11,17),Color.rgb(57,73,95),16));
        body.addView(pad,wide?new LinearLayout.LayoutParams(0,-1,1.15f):new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout top=box(true);pad.addView(top,new LinearLayout.LayoutParams(-1,dp(64)));addTile(top,0);addTile(top,1);
        for(int row=0;row<3;row++){LinearLayout r=box(true);pad.addView(r,new LinearLayout.LayoutParams(-1,0,1));for(int col=0;col<3;col++)addTile(r,2+row*3+col);}
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout.LayoutParams detailParams=wide?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,dp(280));
        detailParams.setMargins(wide?dp(20):0,wide?0:dp(12),0,0);body.addView(scroll,detailParams);
        LinearLayout details=box(false);scroll.addView(details);
        Button play=control("Play arrows · rhythm practice");details.addView(play,new LinearLayout.LayoutParams(-1,dp(52)));play.setOnClickListener(v->showGame());
        mockSwitch=new Switch(this);mockSwitch.setText("Mock pad · touch to simulate");mockSwitch.setTextColor(TEXT);mockSwitch.setTextSize(17);mockSwitch.setMinHeight(dp(52));mockSwitch.setChecked(true);details.addView(mockSwitch);
        mockSwitch.setOnCheckedChangeListener((b,checked)->{mock.clear();if(!checked)stopDemo();paint();});
        bridgeSwitch=new Switch(this);bridgeSwitch.setText("PC bridge · mat → PC → tablet");bridgeSwitch.setTextColor(TEXT);bridgeSwitch.setTextSize(17);bridgeSwitch.setMinHeight(dp(52));details.addView(bridgeSwitch);
        bridgeSwitch.setChecked(getIntent().getBooleanExtra("pc_bridge",prefs.getBoolean("pc_bridge",false)));
        bridgeSwitch.setOnCheckedChangeListener((b,checked)->{
            prefs.edit().putBoolean("pc_bridge",checked).apply();
            if(checked){mockSwitch.setChecked(false);if(resumed)startBridge();}else stopBridge();paint();
        });
        LinearLayout actions=box(true);details.addView(actions);
        demoButton=control("Run demo");actions.addView(demoButton,new LinearLayout.LayoutParams(0,dp(50),1));
        demoButton.setOnClickListener(v->{if(demoRunning){stopDemo();paint();}else{mockSwitch.setChecked(true);demoRunning=true;demoStep=0;demoButton.setText("Stop demo");handler.post(demoTick);}});
        Button clear=control("Clear");LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(50),1);cp.setMargins(dp(8),0,0,0);actions.addView(clear,cp);
        clear.setOnClickListener(v->{stopDemo();mock.clear();liveKeys.clear();liveAxes.clear();learning=null;mappingMode=false;lastEvent="All signals cleared";paint();});
        hint=label("",14,Color.rgb(255,216,119));details.addView(hint);
        TextView rawTitle=label("RAW BUTTONS",12,MUTED);rawTitle.setTypeface(null,Typeface.BOLD);details.addView(rawTitle);
        GridLayout raw=new GridLayout(this);raw.setColumnCount(6);details.addView(raw);
        for(int i=0;i<12;i++){TextView t=label("B"+(i+1),14,TEXT);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(44));GridLayout.LayoutParams rp=new GridLayout.LayoutParams(GridLayout.spec(i/6),GridLayout.spec(i%6,1,1f));rp.width=0;rp.setMargins(dp(2),dp(2),dp(2),dp(2));raw.addView(t,rp);rawButtons[i]=t;}
        activeText=label("",17,LIME);details.addView(activeText);
        rawText=label("Axes: neutral",13,MUTED);rawText.setTypeface(Typeface.MONOSPACE);details.addView(rawText);
        lastText=label(lastEvent,14,TEXT);details.addView(lastText);
        mapButton=control("Map real controller");details.addView(mapButton,new LinearLayout.LayoutParams(-1,dp(50)));
        mapButton.setOnClickListener(v->{stopDemo();mock.clear();mappingMode=!mappingMode;learning=null;paint();});
        details.addView(label("PC bridge: leave the mat and tablet plugged into the PC and run start_pad_bridge.ps1. Or connect the mat directly using USB-C OTG. Mappings save on this tablet.",13,MUTED));
        testerRoot=root;setContentView(root);root.requestApplyInsets();prefs.edit().putBoolean("pc_bridge",bridgeSwitch.isChecked()).apply();if(bridgeSwitch.isChecked())mockSwitch.setChecked(false);paint();
    }
    private void showGame(){
        stopDemo();mock.clear();mappingMode=false;learning=null;gameMode=true;
        LinearLayout root=box(false);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left+dp(12),bars.top+dp(6),bars.right+dp(12),bars.bottom+dp(6));return insets;});
        LinearLayout toolbar=box(true);root.addView(toolbar);
        Button back=control("← Pad viewer");toolbar.addView(back,new LinearLayout.LayoutParams(dp(140),dp(48)));back.setOnClickListener(v->showTester());
        TextView title=label("ARROW PRACTICE",20,TEXT);title.setGravity(Gravity.CENTER);title.setTypeface(null,Typeface.BOLD);toolbar.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button start=control("Start / Restart");toolbar.addView(start,new LinearLayout.LayoutParams(dp(150),dp(48)));
        pauseGame=control("Pause");LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(100),dp(48));pp.setMargins(dp(6),0,0,0);toolbar.addView(pauseGame,pp);
        LinearLayout options=box(true);options.setGravity(Gravity.CENTER_VERTICAL);root.addView(options);
        int initialBpm=prefs.getInt("practice_bpm",80);
        TextView tempo=label(initialBpm+" BPM · 32 steps",14,MUTED);options.addView(tempo,new LinearLayout.LayoutParams(dp(170),dp(48)));
        SeekBar speed=new SeekBar(this);speed.setMax(6);speed.setProgress((initialBpm-60)/10);speed.setContentDescription("Tempo for next round, 60 to 120 beats per minute");options.addView(speed,new LinearLayout.LayoutParams(0,dp(48),1));
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int progress,boolean user){int bpm=60+progress*10;tempo.setText(bpm+" BPM · 32 steps");prefs.edit().putInt("practice_bpm",bpm).apply();}});
        Switch click=new Switch(this);click.setText("Beat click");click.setTextColor(TEXT);click.setMinHeight(dp(48));options.addView(click);
        rhythm=new RhythmView(this);root.addView(rhythm,new LinearLayout.LayoutParams(-1,0,1));
        click.setOnCheckedChangeListener((b,on)->rhythm.setClick(on));
        start.setOnClickListener(v->{rhythm.start(60+speed.getProgress()*10);pauseGame.setText("Pause");});
        pauseGame.setOnClickListener(v->{if(rhythm.engine.running){rhythm.pause();pauseGame.setText("Resume");}else if(rhythm.engine.started&&!rhythm.engine.finished){rhythm.resume();pauseGame.setText("Pause");}});
        root.addView(label("Match the arrows at the top. Use your mat, or tap a lane. Tempo changes apply to the next round.",13,MUTED));
        gameSource=label("",12,MUTED);root.addView(gameSource);
        setContentView(root);root.requestApplyInsets();paint();
    }
    private void showTester(){
        if(rhythm!=null){rhythm.pause();rhythm.release();rhythm=null;}gameMode=false;gameSource=null;setContentView(testerRoot);testerRoot.requestApplyInsets();paint();
    }
    @Override public void onBackPressed(){if(gameMode)showTester();else super.onBackPressed();}
    private void addTile(LinearLayout row,int index){
        String name=names[index];Button b=new Button(this);b.setAllCaps(false);b.setTextColor(TEXT);b.setTextSize(index<2?14:name.equals("CENTER")?20:38);b.setGravity(Gravity.CENTER);b.setPadding(dp(2),dp(2),dp(2),dp(2));
        b.setMinHeight(0);b.setMinimumHeight(0);b.setMinimumWidth(0);b.setMinWidth(0);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(b,p);tiles.put(name,b);
        b.setOnClickListener(v->{if(mappingMode){learning=name;paint();}else if(mockSwitch.isChecked())pulse(defaults[index]);});
        b.setOnTouchListener((v,event)->{
            if(mappingMode||!mockSwitch.isChecked())return false;
            int action=event.getActionMasked();String signal=defaults[index];
            if(action==MotionEvent.ACTION_DOWN){mock.add(signal);lastEvent="MOCK · pressed "+signal+" · "+name;paint();return true;}
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){mock.remove(signal);lastEvent="MOCK · released "+signal;paint();return true;}
            return true;
        });
    }
    private void pulse(String signal){mock.add(signal);lastEvent="MOCK · pressed "+signal;paint();handler.postDelayed(()->{mock.remove(signal);paint();},250);}
    private void stopDemo(){demoRunning=false;handler.removeCallbacks(demoTick);demo.clear();if(demoButton!=null)demoButton.setText("Run demo");}
    private void paint(){
        if(mockSwitch==null)return;
        Set<String> simulated=new HashSet<>(mock);simulated.addAll(demo);Set<String> real=new HashSet<>(liveKeys);real.addAll(liveAxes);
        Set<Integer> lanes=new HashSet<>();
        for(int i=0;i<names.length;i++){
            String name=names[i];Button b=tiles.get(name);boolean pressed=simulated.contains(defaults[i])||remote.contains(defaults[i]);for(String s:mapping.get(name))if(real.contains(s))pressed=true;
            if(pressed){int lane=name.equals("LEFT")?0:name.equals("DOWN")?1:name.equals("UP")?2:name.equals("RIGHT")?3:-1;if(lane>=0)lanes.add(lane);}
            int accent=i<2?(i==0?PINK:CYAN):(name.equals("LEFT")||name.equals("RIGHT"))?PINK:CYAN;
            b.setBackground(shape(pressed?GREEN:TILE,name.equals(learning)?Color.YELLOW:pressed?LIME:accent,name.equals("CENTER")?60:8));
            String binding=String.join(" / ",mapping.get(name));
            SpannableString caption=new SpannableString(symbols[i]+"\n"+(name.equals("CENTER")?"CENTER · ":"")+(binding.isEmpty()?"unmapped":binding));
            caption.setSpan(new AbsoluteSizeSpan(12,true),symbols[i].length()+1,caption.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setText(caption);
            b.setContentDescription(name+(pressed?" active":" idle")+" · "+String.join(", ",mapping.get(name)));
        }
        Set<String> all=new HashSet<>(simulated);all.addAll(real);all.addAll(remote);
        for(int i=0;i<12;i++)rawButtons[i].setBackground(shape(all.contains("B"+(i+1))?GREEN:TILE,all.contains("B"+(i+1))?LIME:TILE,5));
        ArrayList<String> list=new ArrayList<>(all);java.util.Collections.sort(list);activeText.setText(list.isEmpty()?"No active signals":"Active: "+String.join(", ",list));lastText.setText(lastEvent);
        mapButton.setText(mappingMode?"Cancel mapping":"Map real controller");
        hint.setText(mappingMode?learning==null?"Tap the area you want to map.":"Press "+learning+" on the real controller.":demoRunning?"DEMO · automatic simulated presses":mockSwitch.isChecked()?"MOCK · hold pad areas, or try two fingers at once.":bridgeSwitch!=null&&bridgeSwitch.isChecked()?"BRIDGE · step on the mat connected to your PC.":"LIVE · waiting for real controller input.");updateDeviceStatus();
        if(rhythm!=null)rhythm.setExternal(lanes);
    }
    private void updateDeviceStatus(){
        if(bridgeSwitch!=null&&bridgeSwitch.isChecked()){status.setText(bridgeStatus);if(gameSource!=null)gameSource.setText(bridgeStatus);return;}
        ArrayList<String> connected=new ArrayList<>();for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(d==null||d.isVirtual()||!d.isExternal())continue;if(d.supportsSource(InputDevice.SOURCE_GAMEPAD)||d.supportsSource(InputDevice.SOURCE_JOYSTICK))connected.add(d.getName());}
        status.setText(connected.isEmpty()?"No controller connected · mock pad ready":"Controller: "+String.join(", ",connected));
        if(gameSource!=null)gameSource.setText(connected.isEmpty()?"Touch practice · no controller connected":"Controller: "+String.join(", ",connected));
    }
    private String keySignal(KeyEvent e){int code=e.getKeyCode();if(code>=KeyEvent.KEYCODE_BUTTON_1&&code<=KeyEvent.KEYCODE_BUTTON_16)return "B"+(code-KeyEvent.KEYCODE_BUTTON_1+1);return KeyEvent.keyCodeToString(code).replace("KEYCODE_","");}
    private boolean controller(KeyEvent e){return e.isFromSource(InputDevice.SOURCE_GAMEPAD)||e.isFromSource(InputDevice.SOURCE_JOYSTICK)||KeyEvent.isGamepadButton(e.getKeyCode());}
    private void learnSignal(String signal){
        if(!mappingMode||learning==null)return;for(Set<String> mapped:mapping.values())mapped.remove(signal);mapping.put(learning,new HashSet<>(Set.of(signal)));
        SharedPreferences.Editor edit=prefs.edit();for(Map.Entry<String,Set<String>> e:mapping.entrySet())edit.putStringSet(e.getKey(),e.getValue());edit.apply();
        lastEvent="Saved "+learning+" → "+signal;learning=null;mappingMode=false;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(!controller(e))return super.dispatchKeyEvent(e);String signal=keySignal(e);
        if(e.getAction()==KeyEvent.ACTION_DOWN){liveKeys.add(signal);if(e.getRepeatCount()==0){lastEvent="LIVE · pressed "+signal;learnSignal(signal);}}
        else if(e.getAction()==KeyEvent.ACTION_UP){liveKeys.remove(signal);lastEvent="LIVE · released "+signal;}
        rawText.setText("Key: "+e.getKeyCode()+"  scan: "+e.getScanCode()+"\nDevice: "+e.getDeviceId());paint();return true;
    }
    @Override public boolean onGenericMotionEvent(MotionEvent e){
        if(!e.isFromSource(InputDevice.SOURCE_JOYSTICK))return super.onGenericMotionEvent(e);liveAxes.clear();
        int[] axes={MotionEvent.AXIS_X,MotionEvent.AXIS_Y,MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y};String[] axisNames={"X","Y","HAT_X","HAT_Y"};
        for(int i=0;i<axes.length;i++){float v=e.getAxisValue(axes[i]);if(Math.abs(v)>.5f)liveAxes.add(axisNames[i]+(v>0?"+":"-"));}
        if(mappingMode&&learning!=null&&!liveAxes.isEmpty())learnSignal(liveAxes.contains("Y+")?"Y+":liveAxes.iterator().next());
        rawText.setText(String.format(Locale.US,"X %.2f   Y %.2f\nHat X %.2f   Hat Y %.2f",e.getAxisValue(0),e.getAxisValue(1),e.getAxisValue(15),e.getAxisValue(16)));lastEvent="LIVE · axes updated";paint();return true;
    }
    private void startBridge(){
        stopBridge();
        final int generation=bridgeGeneration;
        bridgeStatus="PC bridge · connecting…";updateDeviceStatus();
        Thread worker=new Thread(()->{
            while(generation==bridgeGeneration){
                try(Socket socket=new Socket()){
                    bridgeSocket=socket;socket.setTcpNoDelay(true);socket.connect(new InetSocketAddress("127.0.0.1",8765),2000);socket.setSoTimeout(2000);
                    BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    String line;
                    while(generation==bridgeGeneration&&(line=reader.readLine())!=null){
                        JSONObject packet=new JSONObject(line);JSONArray array=packet.getJSONArray("signals");Set<String> next=new HashSet<>();
                        if(packet.optBoolean("connected"))for(int i=0;i<array.length();i++)next.add(array.getString(i));
                        String source=packet.optString("source","PC USB");
                        String nextStatus=packet.optBoolean("connected")?source+" bridge · connected · "+packet.optString("device"):"PC bridge connected · plug the mat into the PC";
                        String raw=packet.has("x")?"PC X "+packet.optInt("x")+"   Y "+packet.optInt("y")+"\nPOV "+packet.optInt("pov"):source+" signals";
                        handler.post(()->{if(generation!=bridgeGeneration)return;
                            if(!remote.equals(next))lastEvent=source+" · "+(next.isEmpty()?"released": "active "+String.join(", ",next));
                            remote.clear();remote.addAll(next);bridgeStatus=nextStatus;rawText.setText(raw);paint();
                        });
                    }
                }catch(Exception ignored){}
                handler.post(()->{if(generation!=bridgeGeneration)return;remote.clear();bridgeStatus="PC bridge · reconnecting (check USB cable and PC relay)";paint();});
                try{Thread.sleep(1000);}catch(InterruptedException e){return;}
            }
        },"PC-pad-bridge");worker.setDaemon(true);worker.start();
    }
    private void stopBridge(){
        bridgeGeneration++;Socket socket=bridgeSocket;bridgeSocket=null;
        if(socket!=null)try{socket.close();}catch(Exception ignored){}
        remote.clear();
    }
    @Override protected void onResume(){super.onResume();resumed=true;inputs.registerInputDeviceListener(this,handler);if(bridgeSwitch.isChecked())startBridge();updateDeviceStatus();}
    @Override protected void onPause(){super.onPause();resumed=false;if(rhythm!=null){rhythm.pause();pauseGame.setText("Resume");}inputs.unregisterInputDeviceListener(this);stopBridge();stopDemo();mock.clear();liveKeys.clear();liveAxes.clear();paint();}
    @Override protected void onDestroy(){if(rhythm!=null)rhythm.release();super.onDestroy();}
    @Override public void onInputDeviceAdded(int id){updateDeviceStatus();}
    @Override public void onInputDeviceChanged(int id){updateDeviceStatus();}
    @Override public void onInputDeviceRemoved(int id){liveKeys.clear();liveAxes.clear();paint();}
}
