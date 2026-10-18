package dev.paul.dancepad;

import java.util.ArrayList;
import java.util.List;

/** Timing model uses a monotonic clock supplied by the UI, in milliseconds. */
public final class RhythmEngine {
    public static final long HIT_WINDOW=150, EXCELLENT_WINDOW=70, COUNTDOWN=3000;
    public static final class Note {
        public final int lane;
        public final long time;
        public boolean resolved;
        Note(int lane,long time){this.lane=lane;this.time=time;}
    }
    public final List<Note> notes=new ArrayList<>();
    public boolean started,running,finished;
    public int combo,bestCombo,excellent,good,misses,bpm=80;
    public String feedback="READY";
    public long feedbackAt,startedAt,pausedAt;
    public void start(long now,int tempo){
        bpm=Math.max(40,Math.min(160,tempo));started=true;running=true;finished=false;
        combo=bestCombo=excellent=good=misses=0;startedAt=now;feedback="READY";feedbackAt=now;
        notes.clear();int[] pattern={0,1,2,3,2,1,0,3,0,2,1,3,0,1,2,3,3,2,1,0,2,0,3,1,0,1,0,2,3,2,1,3};
        for(int i=0;i<pattern.length;i++)notes.add(new Note(pattern[i],COUNTDOWN+Math.round(i*60000.0/bpm)));
    }
    public long elapsed(long now){return !started?0:(running?now:pausedAt)-startedAt;}
    public void pause(long now){if(running){pausedAt=now;running=false;}}
    public void resume(long now){if(started&&!running&&!finished){startedAt+=now-pausedAt;running=true;}}
    public void tick(long now){
        if(!running)return;long t=elapsed(now);
        for(Note note:notes)if(!note.resolved&&t-note.time>HIT_WINDOW){note.resolved=true;misses++;combo=0;feedback="MISS";feedbackAt=now;}
        if(!notes.isEmpty()&&t>notes.get(notes.size()-1).time+HIT_WINDOW){finished=true;pausedAt=now;running=false;}
    }
    public boolean press(int lane,long now){
        tick(now);if(!running||elapsed(now)<COUNTDOWN-HIT_WINDOW)return false;
        Note closest=null;long distance=Long.MAX_VALUE,t=elapsed(now);
        for(Note n:notes)if(!n.resolved&&n.lane==lane&&Math.abs(t-n.time)<distance){closest=n;distance=Math.abs(t-n.time);}
        if(closest==null||distance>HIT_WINDOW)return false;
        closest.resolved=true;combo++;bestCombo=Math.max(bestCombo,combo);
        if(distance<=EXCELLENT_WINDOW){excellent++;feedback="EXCELLENT";}else{good++;feedback="GOOD";}
        feedbackAt=now;return true;
    }
    public int score(){return excellent*100+good*60;}
}
