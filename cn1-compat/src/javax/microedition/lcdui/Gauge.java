package javax.microedition.lcdui;

//#define __XAML__

public class Gauge extends Item {
    public static final int CONTINUOUS_IDLE = 0;
    public static final int CONTINUOUS_RUNNING = 2;
    public static final int INCREMENTAL_IDLE = 1;
    public static final int INCREMENTAL_UPDATING = 3;
    public static final int INDEFINITE = -1;

//#ifdef __XAML__
    private int maxValue, initialValue, value;
//#else
    private com.codename1.ui.Slider cn1Slider;
//#endif

    public Gauge(String label, boolean interactive, int maxValue, int initialValue) {
        super(label);
//#ifdef __XAML__
        this.maxValue = maxValue;
        this.initialValue = this.value = initialValue;
//#else
        this.cn1Slider = new com.codename1.ui.Slider();
        this.cn1Slider.setMinValue(0);
        this.cn1Slider.setMaxValue(maxValue);
        this.cn1Slider.setProgress(initialValue);
//#endif
    }

//#ifdef __XAML__
    
    public int MIDP_getMaxValue() {
        return maxValue;
    }

    public int MIDP_getInitialValue() {
        return initialValue;
    }

    public void MIDP_setValue(int value) {
        this.value = value;
    }

//#endif

    public int getValue() {
//#ifdef __XAML__
        return value;
//#else
        return cn1Slider.getProgress();
//#endif
    }
}
