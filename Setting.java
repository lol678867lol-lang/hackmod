package de.hackmod;

/** Eine Einstellung eines Moduls: entweder Zahl (mit Min/Max/Schritt) oder An/Aus. */
public class Setting {
    public final String name;
    public final boolean bool;
    public double value, min, max, step;
    public boolean on;
    public double[] list; // optional: feste Stufen (z.B. 16 .. 10000)

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

    /** Zahl mit festen Stufen, z.B. choice("Radius", 64, 16, 32, 64, 128, ..., 10000). */
    public static Setting choice(String name, double def, double... values) {
        Setting s = new Setting(name, false);
        s.list = values;
        s.min = values[0];
        s.max = values[values.length - 1];
        s.step = 1;
        s.value = def;
        s.value = values[s.nearest()];
        return s;
    }

    private int nearest() {
        int best = 0;
        for (int i = 1; i < list.length; i++) {
            if (Math.abs(list[i] - value) < Math.abs(list[best] - value)) best = i;
        }
        return best;
    }

    public static Setting toggle(String name, boolean def) {
        Setting s = new Setting(name, true);
        s.on = def;
        return s;
    }

    public void step(int dir) {
        if (list != null) {
            int i = Math.max(0, Math.min(list.length - 1, nearest() + dir));
            value = list[i];
            return;
        }
        double v = value + dir * step;
        v = Math.max(min, Math.min(max, v));
        value = Math.round(v * 100.0) / 100.0;
    }

    public String display() {
        if (bool) return on ? "AN" : "AUS";
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public float fraction() {
        if (list != null) return list.length < 2 ? 1f : (float) nearest() / (list.length - 1);
        return (float) ((value - min) / (max - min));
    }
}
