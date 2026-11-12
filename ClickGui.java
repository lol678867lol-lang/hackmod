package de.hackmod;
// FIX v2: kein net.minecraft.Util mehr, nutzt System.currentTimeMillis()

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Das ClickGUI (Taste C). Linksklick = an/aus, Rechtsklick = Einstellungen auf-/zuklappen. */
public class ClickGui extends Screen {
    private static final int MAXPW = 136, HEAD = 22, ROW = 18, SROW = 16, GAP = 14, TOP = 40;

    /** Klickbare Flaeche vom letzten Frame. dir: -1/+1 = Pfeil, 0 = ganze Zeile. */
    private record Zone(int x, int y, int w, int h, Module m, Setting s, int dir) {
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Zone> zones = new ArrayList<>();
    private final long openedAt = System.currentTimeMillis();

    public ClickGui() {
        super(Component.literal("HackMod"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        zones.clear();
        Font f = this.font;
        float fade = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);

        g.fillGradient(0, 0, width, height, ((int) (fade * 150) << 24) | 0x05050F, ((int) (fade * 215) << 24) | 0x140A28);

        String title = "H A C K M O D";
        g.drawString(f, title, (width - f.width(title)) / 2, 14, Gfx.accent(0));
        String sub = "Linksklick: an/aus   Rechtsklick: Einstellungen   C / ESC: schliessen";
        g.drawString(f, sub, (width - f.width(sub)) / 2, height - 12, 0xFF7A7A90);

        Module.Category[] cats = Module.Category.values();
        final int PW = Math.max(112, Math.min(MAXPW, (width - 20 - (cats.length - 1) * GAP) / cats.length));
        int total = cats.length * PW + (cats.length - 1) * GAP;
        int x0 = (width - total) / 2;
        String tip = null;

        for (int i = 0; i < cats.length; i++) {
            int px = x0 + i * (PW + GAP);
            int py = TOP;
            List<Module> mods = Modules.byCategory(cats[i]);

            int h = HEAD;
            for (Module m : mods) {
                h += ROW;
                if (m.expanded) h += SROW * m.settings.size();
            }

            // Rahmen + Koerper
            g.fill(px - 1, py - 1, px + PW + 1, py + h + 1, Gfx.alpha(Gfx.accent(i * 0.25), 150));
            g.fill(px, py, px + PW, py + h, 0xF0101018);
            g.fillGradient(px, py, px + PW, py + HEAD, Gfx.accent(i * 0.25), Gfx.accent(i * 0.25 + 0.3));
            String label = cats[i].label.toUpperCase();
            g.drawString(f, label, px + (PW - f.width(label)) / 2, py + (HEAD - 8) / 2, 0xFFFFFFFF);

            int y = py + HEAD;
            for (Module m : mods) {
                boolean hov = mx >= px && mx < px + PW && my >= y && my < y + ROW;
                m.anim += ((m.enabled ? 1f : 0f) - m.anim) * 0.25f;
                g.fill(px, y, px + PW, y + ROW, hov ? 0xFF1E1E2C : 0xFF14141E);
                if (m.anim > 0.02f) {
                    int ac = Gfx.accent(i * 0.25 + y * 0.004);
                    g.fill(px, y, px + PW, y + ROW, Gfx.alpha(ac, (int) (m.anim * 70)));
                    g.fill(px, y, px + 2, y + ROW, ac);
                }
                int tc = Gfx.lerp(0xFFA0A0B0, 0xFFFFFFFF, m.anim);
                g.drawString(f, m.name, px + 8, y + (ROW - 8) / 2, tc);
                if (!m.settings.isEmpty()) {
                    String ind = m.expanded ? "-" : "+";
                    g.drawString(f, ind, px + PW - 10, y + (ROW - 8) / 2, 0xFF70708A);
                }
                zones.add(new Zone(px, y, PW, ROW, m, null, 0));
                if (hov) tip = m.desc;
                y += ROW;

                if (m.expanded) {
                    for (Setting s : m.settings) {
                        g.fill(px, y, px + PW, y + SROW, 0xFF0B0B13);
                        g.fill(px, y, px + 1, y + SROW, Gfx.alpha(Gfx.accent(i * 0.25), 160));
                        int ty = y + (SROW - 8) / 2;
                        g.drawString(f, s.name, px + 10, ty, 0xFFB8B8C8);
                        if (s.bool) {
                            String v = s.display();
                            g.drawString(f, v, px + PW - 8 - f.width(v), ty, s.on ? 0xFF5CFF7A : 0xFFFF6A6A);
                            zones.add(new Zone(px, y, PW, SROW, m, s, 0));
                        } else {
                            String v = s.display();
                            int cx = px + PW - 39;
                            g.drawString(f, "<", px + PW - 60, ty, 0xFFFFFFFF);
                            g.drawString(f, v, cx - f.width(v) / 2, ty, 0xFFFFFFFF);
                            g.drawString(f, ">", px + PW - 12, ty, 0xFFFFFFFF);
                            zones.add(new Zone(px + PW - 64, y, 16, SROW, m, s, -1));
                            zones.add(new Zone(px + PW - 16, y, 14, SROW, m, s, 1));
                            int bw = (int) ((PW - 16) * Math.max(0f, Math.min(1f, s.fraction())));
                            g.fill(px + 8, y + SROW - 2, px + 8 + bw, y + SROW - 1, Gfx.accent(i * 0.25 + 0.5));
                        }
                        y += SROW;
                    }
                }
            }
        }

        if (tip != null) {
            int tw = f.width(tip);
            int tx = (width - tw) / 2;
            g.fill(tx - 4, height - 34, tx + tw + 4, height - 21, 0xD0000000);
            g.drawString(f, tip, tx, height - 31, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int btn = event.button();
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            if (!z.hit(mx, my)) continue;
            if (z.s() == null) {
                if (btn == 0) z.m().toggle();
                else if (btn == 1 && !z.m().settings.isEmpty()) z.m().expanded = !z.m().expanded;
            } else if (z.s().bool) {
                z.s().on = !z.s().on;
            } else {
                z.s().step(z.dir());
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_C) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
