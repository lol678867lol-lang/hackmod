package de.hackmod;

/** Eine Einstellung eines Moduls: entweder Zahl (mit Min/Max/Schritt) oder An/Aus. */
public class Setting {
    public final String name;
    public final boolean bool;
    public double value, min, max, step;
    public boolean on;

    private Setting(String name, boolean bool) {
        this.name = name;
        this.bool = bool;
    }

    public static Setting number(String name, double def, double min, double max, double step) {
        Setting s = new Setting(name, false);
        s.value = def;
        s.min = min;
        s.max = max;
        s.step = step;
        return s;
    }

    public static Setting toggle(String name, boolean def) {
        Setting s = new Setting(name, true);
        s.on = def;
        return s;
    }

    public void step(int dir) {
        double v = value + dir * step;
        v = Math.max(min, Math.min(max, v));
        value = Math.round(v * 100.0) / 100.0;
    }

    public String display() {
        if (bool) return on ? "AN" : "AUS";
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public float fraction() {
        return (float) ((value - min) / (max - min));
    }
}
