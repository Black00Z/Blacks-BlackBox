package top.niunaijun.blackbox.compat.location;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Project-owned location parcel used for compatibility paths.
 */
public final class BzRoutePassport implements Parcelable {

    private String provider;

    public BzRoutePassport() {
    }

    public BzRoutePassport(String provider) {
        this.provider = provider;
    }

    public BzRoutePassport(BzRoutePassport other) {
        this.provider = other == null ? null : other.provider;
    }

    private BzRoutePassport(Parcel in) {
        provider = in.readString();
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(provider);
    }

    public static final Creator<BzRoutePassport> CREATOR = new Creator<BzRoutePassport>() {
        @Override
        public BzRoutePassport createFromParcel(Parcel in) {
            return new BzRoutePassport(in);
        }

        @Override
        public BzRoutePassport[] newArray(int size) {
            return new BzRoutePassport[size];
        }
    };
}