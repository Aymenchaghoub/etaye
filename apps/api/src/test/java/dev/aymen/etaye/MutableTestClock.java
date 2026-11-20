package dev.aymen.etaye;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableTestClock extends Clock {

    private final AtomicReference<Instant> time;
    private final ZoneId zone;

    public MutableTestClock(Instant initialTime) {
        this(new AtomicReference<>(initialTime), ZoneOffset.UTC);
    }

    private MutableTestClock(AtomicReference<Instant> time, ZoneId zone) {
        this.time = time;
        this.zone = zone;
    }

    public void advanceSeconds(long seconds) {
        time.updateAndGet(instant -> instant.plusSeconds(seconds));
    }

    @Override
    public Instant instant() {
        return time.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableTestClock(time, zone);
    }
}
