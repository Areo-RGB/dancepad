package dev.paul.dancepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

/** Four-lane practice field. Touch and physical inputs share edge-triggered scoring. */
public final class RhythmView extends View {
    public final RhythmEngine engine=new RhythmEngine();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Set<Integer> external=new HashSet<>(),touch=new HashSet<>(),previous=new HashSet<>();
    private ToneGenerator tone;
    private boolean click;
    private long lastBeat=-1;
    private float left,laneWidth,targetY,bottom,arrowSize;
    public RhythmView(Context context){
        super(context);setBackgroundColor(Color.rgb(5,8,15));setFocusable(true);
        setContentDescription("Rhythm practice. Left, down, up, right. Press Start to begin.");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    public void start(int bpm){engine.start(SystemClock.uptimeMillis(),bpm);previous.clear();previous.addAll(external);touch.clear();lastBeat=-1;announceState();invalidate();}
    public void pause(){engine.pause(SystemClock.uptimeMillis());touch.clear();previous.clear();previous.addAll(external);announceState();invalidate();}
    public void resume(){engine.resume(SystemClock.uptimeMillis());previous.clear();previous.addAll(external);announceState();invalidate();}
    public void setClick(boolean enabled){click=enabled;if(enabled&&tone==null)try{tone=new ToneGenerator(AudioManager.STREAM_MUSIC,30);}catch(RuntimeException ignored){} }
    public void release(){if(tone!=null){tone.release();tone=null;} }
    public void setExternal(Set<Integer> lanes,long detectedAt){external.clear();external.addAll(lanes);checkEdges(detectedAt);}
    private void checkEdges(long detectedAt){
        Set<Integer> active=new HashSet<>(external);active.addAll(touch);long now=SystemClock.uptimeMillis();
        // A negative timestamp establishes a baseline after connection, or
        // displays an over-age packet without allowing it to score.
        if(detectedAt>=0)for(int lane:active)if(!previous.contains(lane)&&engine.press(lane,Math.min(now,detectedAt),now))announceState();
        previous.clear();previous.addAll(active);invalidate();
    }
    private void announceState(){
        setContentDescription("Rhythm practice: "+(!engine.started?"ready":engine.finished?"finished":engine.running?"running":"paused")+". "+engine.feedback+". Combo "+engine.combo+". Excellent "+engine.excellent+". Good "+engine.good+". Misses "+engine.misses+". Score "+engine.score()+". Timing error "+engine.lastTimingErrorMs+" ms. Delivery delay "+engine.lastArrivalDelayMs+" ms.");
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        float width=Math.min(w-dp(24),dp(560));laneWidth=width/4;left=(w-width)/2;targetY=dp(70);bottom=h-dp(68);arrowSize=Math.min(laneWidth*.72f,dp(86));
    }
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean bold){
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(dp(size));paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);c.drawText(value,x,y,paint);
    }
    private void arrow(Canvas c,int lane,float y,int fill,int stroke){
        float x=left+laneWidth*(lane+.5f),s=arrowSize;c.save();c.translate(x,y);c.rotate(new float[]{-90,180,0,90}[lane]);
        Path p=new Path();p.moveTo(0,-s*.48f);p.lineTo(s*.46f,-s*.02f);p.lineTo(s*.23f,s*.2f);p.lineTo(s*.15f,s*.11f);p.lineTo(s*.15f,s*.43f);p.lineTo(-s*.15f,s*.43f);p.lineTo(-s*.15f,s*.11f);p.lineTo(-s*.23f,s*.2f);p.lineTo(-s*.46f,-s*.02f);p.close();
        paint.setStyle(Paint.Style.FILL);paint.setColor(fill);c.drawPath(p,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setColor(stroke);c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);c.restore();
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);long now=SystemClock.uptimeMillis();int previousMiss=engine.misses;boolean wasFinished=engine.finished;engine.tick(now);if(previousMiss!=engine.misses||wasFinished!=engine.finished)announceState();
        long elapsed=engine.elapsed(now);int w=getWidth(),h=getHeight();
        for(int lane=0;lane<4;lane++){
            paint.setColor(lane%2==0?Color.rgb(14,20,33):Color.rgb(10,15,26));c.drawRect(left+lane*laneWidth,0,left+(lane+1)*laneWidth,h,paint);
            paint.setColor(Color.rgb(34,45,62));paint.setStrokeWidth(dp(1));c.drawLine(left+lane*laneWidth,0,left+lane*laneWidth,h,paint);
        }
        paint.setColor(Color.rgb(67,85,109));paint.setStrokeWidth(dp(2));c.drawLine(left,targetY,left+4*laneWidth,targetY,paint);
        Set<Integer> active=new HashSet<>(external);active.addAll(touch);
        for(int lane=0;lane<4;lane++)arrow(c,lane,targetY,active.contains(lane)?Color.rgb(28,151,107):Color.rgb(43,54,72),active.contains(lane)?Color.rgb(112,255,189):Color.rgb(140,157,179));
        if(engine.started){
            for(RhythmEngine.Note n:engine.notes)if(!n.resolved){float y=targetY+(n.time-elapsed)/2400f*(bottom-targetY);if(y>=targetY-arrowSize&&y<bottom+arrowSize)arrow(c,n.lane,y,Color.rgb(255,191,55),Color.rgb(255,240,170));}
        }
        float middle=left+laneWidth*2;
        if(!engine.started){text(c,"READY",middle,h*.43f,34,Color.WHITE,true);text(c,"Step as arrows reach the targets",middle,h*.5f,16,Color.rgb(164,185,209),false);text(c,"Or tap anywhere in the matching lane",middle,h*.55f,14,Color.rgb(164,185,209),false);}
        else if(elapsed<RhythmEngine.COUNTDOWN&&engine.running){int count=(int)Math.ceil((RhythmEngine.COUNTDOWN-elapsed)/1000.0);text(c,Integer.toString(count),middle,h*.43f,64,Color.WHITE,true);text(c,"GET READY",middle,h*.5f,20,Color.rgb(255,215,93),true);}
        else if(engine.finished){text(c,"FINISHED",middle,h*.37f,32,Color.rgb(112,255,189),true);text(c,"Score  "+engine.score(),middle,h*.44f,25,Color.WHITE,true);text(c,"Best combo  "+engine.bestCombo,middle,h*.5f,20,Color.WHITE,false);text(c,"Excellent "+engine.excellent+"   Good "+engine.good+"   Miss "+engine.misses,middle,h*.56f,14,Color.rgb(164,185,209),false);}
        else if(!engine.running){text(c,"PAUSED",middle,h*.43f,34,Color.WHITE,true);}
        else{
            if(now-engine.feedbackAt<650){int color=engine.feedback.equals("MISS")?Color.rgb(255,109,130):engine.feedback.equals("GOOD")?Color.rgb(112,255,189):Color.rgb(255,226,88);text(c,engine.feedback,middle,h*.34f,32,color,true);}
            text(c,Integer.toString(engine.combo),middle,h*.43f,48,Color.WHITE,true);text(c,"COMBO",middle,h*.48f,12,Color.rgb(150,170,194),true);
            if(engine.combo>0&&now-engine.feedbackAt<650)text(c,String.format(Locale.US,"%+d ms timing · %d ms delivery",engine.lastTimingErrorMs,engine.lastArrivalDelayMs),middle,h*.53f,13,Color.rgb(150,170,194),false);
        }
        text(c,"←                  ↓                  ↑                  →",middle,h-dp(24),16,Color.rgb(150,170,194),true);
        if(engine.started){text(c,"Score "+engine.score()+"    Best "+engine.bestCombo+"    Miss "+engine.misses,middle,h-dp(52),14,Color.rgb(200,213,230),false);}
        if(click&&tone!=null&&engine.running&&elapsed>=RhythmEngine.COUNTDOWN){long beat=(elapsed-RhythmEngine.COUNTDOWN)/(60000/engine.bpm);if(beat!=lastBeat){lastBeat=beat;tone.startTone(ToneGenerator.TONE_PROP_BEEP,35);}}
        if(engine.running)postInvalidateOnAnimation();
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();if(action==MotionEvent.ACTION_CANCEL){touch.clear();checkEdges(SystemClock.uptimeMillis());return true;}
        touch.clear();int excluded=action==MotionEvent.ACTION_POINTER_UP?e.getActionIndex():-1;
        if(action!=MotionEvent.ACTION_UP)for(int i=0;i<e.getPointerCount();i++)if(i!=excluded){float x=e.getX(i);if(x>=left&&x<left+4*laneWidth)touch.add((int)((x-left)/laneWidth));}
        checkEdges(SystemClock.uptimeMillis());if(action==MotionEvent.ACTION_UP)performClick();return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
