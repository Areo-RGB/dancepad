package dev.paul.dancepad;

public final class BridgeClockTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        BridgeClock clock=new BridgeClock();
        // PC clock is Android clock + 1000 ms, symmetric 5 ms paths.
        check(clock.add(100,1105,1106,111),"first ping accepted");
        check(!clock.ready(),"wait for three samples");
        clock.add(200,1205,1206,211);clock.add(300,1305,1306,311);
        check(clock.ready(),"three valid pings synchronize");
        check(clock.androidTime(1500)==500,"different clock origins align");
        check(clock.rtt()==10,"PC processing time excluded from RTT");
        check(clock.uncertainty()==6,"half RTT plus timer resolution");
        clock.add(400,1450,1451,501); // 100 ms RTT outlier
        check(clock.androidTime(1500)==500,"high-RTT sample does not shift the best estimate");
        clock.observeTransit(1600,603);clock.observeTransit(1700,707);
        check(clock.transit()==7&&clock.jitter()==4,"transport delay and jitter");
        check(!clock.add(10,100,90,20),"invalid PC ordering rejected");
        check(!clock.add(10,100,100,2000),"very high RTT rejected");
        check(!clock.add(10,Double.NaN,100,20),"nonfinite values rejected");
        // Repeated new low-latency samples replace old offsets over time.
        for(int i=0;i<17;i++)clock.add(40000+i*100,42001+i*100,42001+i*100,40002+i*100);
        check(clock.androidTime(42500)==40500,"clock estimate refreshes; old samples expire");
        check(!new BridgeClock().ready(),"reconnection starts unsynchronized");
        System.out.println("PASS: clock origins, RTT, jitter, asymmetric-delay outlier, drift refresh, malformed samples, reconnect");
    }
}
