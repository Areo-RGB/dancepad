package dev.paul.dancepad;

/** Run without Android: javac -d OUTPUT RhythmEngine.java RhythmEngineTest.java. */
public final class RhythmEngineTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        RhythmEngine e=new RhythmEngine();e.start(1000,80);
        check(e.notes.size()==32,"32-note chart");
        check(!e.press(0,3800),"200 ms early must not count");
        check(!e.press(1,4000),"wrong lane must not count");
        check(e.press(0,4070)&&e.excellent==1,"70 ms excellent boundary");
        check(!e.press(0,4071)&&e.combo==1,"resolved note cannot score twice");
        check(e.press(1,4900)&&e.good==1,"150 ms good boundary");
        e.tick(5651);check(e.misses==1&&e.combo==0,"overdue note breaks combo");
        int misses=e.misses;e.tick(5652);check(e.misses==misses,"miss counted once");
        e.start(0,80);e.pause(2500);e.tick(10000);check(e.elapsed(10000)==2500&&e.misses==0,"pause freezes time");
        e.resume(10000);check(e.press(0,10500),"resume preserves timing");
        e.start(0,120);check(e.notes.get(1).time==3500,"tempo changes note spacing");
        e.tick(20000);check(e.finished&&e.misses==32&&!e.running,"all missed run finishes");
        e.start(20000,60);check(e.score()==0&&e.combo==0&&!e.finished,"restart resets scores");
        check(e.notes.get(1).time==4000,"60 BPM spacing");
        System.out.println("PASS: timing boundaries, wrong lanes, duplicate hits, misses, pause/resume, tempo, finish, restart");
    }
}
