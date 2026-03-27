package top.niunaijun.blackbox.compat.content;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Project-owned sync status ledger parcel for compatibility bookkeeping.
 */
public final class BzSyncLedger implements Parcelable {

    public final int authorityId;
    public long totalElapsedTime;
    public int numSyncs;
    public int numSourcePoll;
    public int numSourceServer;
    public int numSourceLocal;
    public int numSourceUser;
    public int numSourcePeriodic;
    public long lastSuccessTime;
    public int lastSuccessSource;
    public long lastFailureTime;
    public int lastFailureSource;
    public String lastFailureMesg;
    public long initialFailureTime;
    public boolean pending;
    public boolean initialize;

    private final ArrayList<Long> periodicSyncTimes;

    public BzSyncLedger(int authorityId) {
        this.authorityId = authorityId;
        this.periodicSyncTimes = new ArrayList<>();
    }

    public BzSyncLedger(BzSyncLedger other) {
        this.authorityId = other.authorityId;
        this.totalElapsedTime = other.totalElapsedTime;
        this.numSyncs = other.numSyncs;
        this.numSourcePoll = other.numSourcePoll;
        this.numSourceServer = other.numSourceServer;
        this.numSourceLocal = other.numSourceLocal;
        this.numSourceUser = other.numSourceUser;
        this.numSourcePeriodic = other.numSourcePeriodic;
        this.lastSuccessTime = other.lastSuccessTime;
        this.lastSuccessSource = other.lastSuccessSource;
        this.lastFailureTime = other.lastFailureTime;
        this.lastFailureSource = other.lastFailureSource;
        this.lastFailureMesg = other.lastFailureMesg;
        this.initialFailureTime = other.initialFailureTime;
        this.pending = other.pending;
        this.initialize = other.initialize;
        this.periodicSyncTimes = new ArrayList<>(other.periodicSyncTimes);
    }

    private BzSyncLedger(Parcel in) {
        authorityId = in.readInt();
        totalElapsedTime = in.readLong();
        numSyncs = in.readInt();
        numSourcePoll = in.readInt();
        numSourceServer = in.readInt();
        numSourceLocal = in.readInt();
        numSourceUser = in.readInt();
        numSourcePeriodic = in.readInt();
        lastSuccessTime = in.readLong();
        lastSuccessSource = in.readInt();
        lastFailureTime = in.readLong();
        lastFailureSource = in.readInt();
        lastFailureMesg = in.readString();
        initialFailureTime = in.readLong();
        pending = readBooleanInt(in);
        initialize = readBooleanInt(in);

        int count = in.readInt();
        periodicSyncTimes = new ArrayList<>(Math.max(count, 0));
        for (int i = 0; i < count; i++) {
            periodicSyncTimes.add(in.readLong());
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(authorityId);
        dest.writeLong(totalElapsedTime);
        dest.writeInt(numSyncs);
        dest.writeInt(numSourcePoll);
        dest.writeInt(numSourceServer);
        dest.writeInt(numSourceLocal);
        dest.writeInt(numSourceUser);
        dest.writeInt(numSourcePeriodic);
        dest.writeLong(lastSuccessTime);
        dest.writeInt(lastSuccessSource);
        dest.writeLong(lastFailureTime);
        dest.writeInt(lastFailureSource);
        dest.writeString(lastFailureMesg);
        dest.writeLong(initialFailureTime);
        writeBooleanInt(dest, pending);
        writeBooleanInt(dest, initialize);

        dest.writeInt(periodicSyncTimes.size());
        for (Long value : periodicSyncTimes) {
            dest.writeLong(value == null ? 0L : value);
        }
    }

    public long getPeriodicSyncTime(int index) {
        if (index < 0 || index >= periodicSyncTimes.size()) {
            return 0L;
        }
        Long value = periodicSyncTimes.get(index);
        return value == null ? 0L : value;
    }

    public void setPeriodicSyncTime(int index, long when) {
        ensureCapacity(index + 1);
        periodicSyncTimes.set(index, when);
    }

    public void removePeriodicSyncTime(int index) {
        if (index >= 0 && index < periodicSyncTimes.size()) {
            periodicSyncTimes.remove(index);
        }
    }

    public List<Long> getPeriodicSyncTimesSnapshot() {
        return new ArrayList<>(periodicSyncTimes);
    }

    private void ensureCapacity(int size) {
        while (periodicSyncTimes.size() < size) {
            periodicSyncTimes.add(0L);
        }
    }

    private static boolean readBooleanInt(Parcel in) {
        return in.readInt() != 0;
    }

    private static void writeBooleanInt(Parcel dest, boolean value) {
        dest.writeInt(value ? 1 : 0);
    }

    public static final Creator<BzSyncLedger> CREATOR = new Creator<BzSyncLedger>() {
        @Override
        public BzSyncLedger createFromParcel(Parcel in) {
            return new BzSyncLedger(in);
        }

        @Override
        public BzSyncLedger[] newArray(int size) {
            return new BzSyncLedger[size];
        }
    };
}
