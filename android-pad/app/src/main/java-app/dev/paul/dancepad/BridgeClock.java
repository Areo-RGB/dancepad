package dev.paul.dancepad;

import java.util.ArrayDeque;
import java.util.Deque;

/** Two-way monotonic-clock synchronization. All methods run on the socket thread. */
public final class BridgeClock {
    private static final class Sample {
        final double offset,rtt;final long at;
        Sample(double offset,double rtt,long at){this.offset=offset;this.rtt=rtt;this.at=at;}
    }
    private final Deque<Sample> samples=new ArrayDeque<>();
    private final Deque<Double> delays=new ArrayDeque<>();
    private Sample best;
    public boolean add(long androidSent,double pcReceived,double pcSent,long androidReceived){
        double rtt=(androidReceived-androidSent)-(pcSent-pcReceived);
        if(androidReceived<androidSent||pcSent<pcReceived||rtt<0||rtt>1000||!Double.isFinite(pcReceived)||!Double.isFinite(pcSent))return false;
        double offset=((pcReceived-androidSent)+(pcSent-androidReceived))/2;
        samples.addLast(new Sample(offset,rtt,androidReceived));
        while(samples.size()>16||(!samples.isEmpty()&&androidReceived-samples.peekFirst().at>30000))samples.removeFirst();
        best=null;for(Sample sample:samples)if(best==null||sample.rtt<best.rtt)best=sample;
        return true;
    }
    public boolean ready(){return samples.size()>=3&&best!=null;}
    public int sampleCount(){return samples.size();}
    public long androidTime(double pcMilliseconds){if(!ready())throw new IllegalStateException("Clock not synchronized");return Math.round(pcMilliseconds-best.offset);}
    public double rtt(){return best==null?Double.NaN:best.rtt;}
    public double uncertainty(){return best==null?Double.NaN:best.rtt/2+1;}
    public void observeTransit(double pcSent,long androidReceived){
        if(!ready())return;
        double delay=androidReceived-(pcSent-best.offset);
        if(delay < -uncertainty()||delay>1000)return;
        delays.addLast(Math.max(0,delay));while(delays.size()>32)delays.removeFirst();
    }
    public double transit(){return delays.isEmpty()?Double.NaN:delays.peekLast();}
    public double jitter(){if(delays.isEmpty())return Double.NaN;double min=Double.MAX_VALUE,max=0;for(double d:delays){min=Math.min(min,d);max=Math.max(max,d);}return max-min;}
}
