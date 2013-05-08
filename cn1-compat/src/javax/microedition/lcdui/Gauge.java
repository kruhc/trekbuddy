package javax.microedition.lcdui;

import com.codename1.ui.Slider;

public class Gauge extends Item {
    public static final int CONTINUOUS_IDLE = 0;
    public static final int CONTINUOUS_RUNNING = 2;
    public static final int INCREMENTAL_IDLE = 1;
    public static final int INCREMENTAL_UPDATING = 3;
    public static final int INDEFINITE = -1;

    private Slider cn1Slider;

    public Gauge(String label, boolean interactive, int maxValue, int initialValue) {
        super(label);
        this.cn1Slider = new Slider();
        this.cn1Slider.setMinValue(0);
        this.cn1Slider.setMaxValue(maxValue);
        this.cn1Slider.setProgress(initialValue);
    }

    public int getValue() {
        return cn1Slider.getProgress();
    }
}
