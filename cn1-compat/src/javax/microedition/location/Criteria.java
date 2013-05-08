package javax.microedition.location;

public class Criteria {
    public static final int NO_REQUIREMENT = 0;
    public static final int POWER_USAGE_LOW = 1;
    public static final int POWER_USAGE_MEDIUM = 2;
    public static final int POWER_USAGE_HIGH = 3;

    private int preferredPowerConsumption;
    private boolean costAllowed;
    private boolean speedAndCourseRequired;
    private boolean altitudeRequired;

    public Criteria() {
    }

    public void setPreferredPowerConsumption(int preferredPowerConsumption) {
        this.preferredPowerConsumption = preferredPowerConsumption;
    }

    public void setCostAllowed(boolean costAllowed) {
        this.costAllowed = costAllowed;
    }

    public void setSpeedAndCourseRequired(boolean speedAndCourseRequired) {
        this.speedAndCourseRequired = speedAndCourseRequired;
    }

    public void setAltitudeRequired(boolean altitudeRequired) {
        this.altitudeRequired = altitudeRequired;
    }
}
