package top.niunaijun.blackbox.fake.device;

public class DeviceSpoofProfile {
    public final String manufacturer;
    public final String brand;
    public final String model;
    public final String device;
    public final String product;
    public final String fingerprint;
    public final String serial;
    public final String androidId;

    public DeviceSpoofProfile(
            String manufacturer,
            String brand,
            String model,
            String device,
            String product,
            String fingerprint,
            String serial,
            String androidId
    ) {
        this.manufacturer = manufacturer;
        this.brand = brand;
        this.model = model;
        this.device = device;
        this.product = product;
        this.fingerprint = fingerprint;
        this.serial = serial;
        this.androidId = androidId;
    }
}
