package dev.aymen.etaye;

import dev.aymen.etaye.infrastructure.transaction.PracticeTransaction;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class TestConnectionLifecycleRecorder implements PracticeTransaction.ConnectionLifecycleListener {

    public record Snapshot(UUID practiceId, String contextBeforeSetting, long backendPid) {}

    private final List<Snapshot> snapshots = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onPracticeContextSetting(Connection connection, UUID practiceId, String contextBeforeSetting, long backendPid) {
        snapshots.add(new Snapshot(practiceId, contextBeforeSetting, backendPid));
    }

    public List<Snapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }

    public void clear() {
        snapshots.clear();
    }
}
