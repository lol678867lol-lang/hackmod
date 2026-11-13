package de.hackmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Basisklasse aller Hacks. */
public abstract class Module {
    public enum Category {
        BEWEGUNG("Bewegung"), FLUG("Flug"), KAMPF("Kampf"), COMBO("Speer/Mace"), CRYSTAL("Crystal/Anker"), SCHUTZ("Schutz"),
        BAUEN("Bauen"), WELT("Welt"), SICHT("Sicht"), HUD("HUD");
        public final String label;
        Category(String label) { this.label = label; }
    }

    public final String name;
    public final String desc;
    public final Category category;
    public final List<Setting> settings = new ArrayList<>();
    public boolean enabled;
    public boolean expanded;
    public float anim; // 0..1 fuer die GUI-Animation

    protected Module(String name, String desc, Category category) {
        this.name = name;
        this.desc = desc;
        this.category = category;
    }

    protected Setting add(Setting s) {
        settings.add(s);
        return s;
    }

    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean e) {
        if (e == enabled) return;
        enabled = e;
        if (e) onEnable(); else onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}

    /** Wird jeden Client-Tick aufgerufen, solange das Modul an ist. */
    public void onTick(Minecraft mc) {}

    /** Zeichnet HUD-Infos (immer, auch in der 3. Person). */
    public void onHud(GuiGraphics g, Minecraft mc) {}

    /** Zeichnet ESP-Overlays (2D-Projektion), solange das Modul an ist. */
    public void onEsp(GuiGraphics g, Minecraft mc, Cam cam) {}
}
