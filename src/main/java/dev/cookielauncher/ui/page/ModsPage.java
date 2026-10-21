package dev.cookielauncher.ui.page;

import dev.cookielauncher.core.config.Instance;
import dev.cookielauncher.game.mod.ModInfo;
import dev.cookielauncher.game.mod.ModScanner;
import dev.cookielauncher.ui.AppContext;
import dev.cookielauncher.ui.LauncherWindow;
import dev.cookielauncher.ui.component.MdButton;
import dev.cookielauncher.ui.component.MdCard;
import dev.cookielauncher.ui.component.MdChip;
import dev.cookielauncher.ui.component.MdTextField;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Mod manager for the selected instance: list, filter, toggle, delete. */
public final class ModsPage extends Page {

    private final JPanel listPanel = new JPanel();
    private MdTextField search;
    private JLabel summary;
    private List<ModInfo> mods = List.of();

    public ModsPage(AppContext context) {
        super(context);
        setLayout(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        add(buildHeader(), BorderLayout.NORTH);

        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Mods");
        title.setFont(TypeScale.headlineLarge());
        title.setForeground(theme.colors.onSurface);
        title.setAlignmentX(LEFT_ALIGNMENT);
        summary = new JLabel(" ");
        summary.setFont(TypeScale.bodyMedium());
        summary.setForeground(theme.colors.onSurfaceVariant);
        summary.setAlignmentX(LEFT_ALIGNMENT);
        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(summary);
        header.add(titles, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        search = new MdTextField(theme.colors, "Filter by name or id");
        search.setPreferredSize(new Dimension(230, 44));
        search.addCaretListener(event -> render());
        actions.add(search);
        MdButton openFolder = new MdButton(theme.colors, "Open folder", MdButton.Variant.TONAL);
        openFolder.addActionListener(event -> openFolder());
        actions.add(openFolder);
        MdButton rescan = new MdButton(theme.colors, "Rescan", MdButton.Variant.TEXT);
        rescan.addActionListener(event -> reloadMods());
        actions.add(rescan);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private Path modsDirectory() {
        Instance instance = context.instances().selected();
        if (instance == null) {
            return null;
        }
        Path base = instance.gameDir == null || instance.gameDir.isBlank()
                ? dev.cookielauncher.core.AppPaths.instance(instance.id)
                : Path.of(instance.gameDir);
        return base.resolve("mods");
    }

    @Override
    public void onShow() {
        reloadMods();
    }

    private void reloadMods() {
        Path directory = modsDirectory();
        if (directory == null) {
            mods = List.of();
            render();
            return;
        }
        dev.cookielauncher.core.util.Fs.mkdirs(directory);
        background("Scan mods", () -> {
            List<ModInfo> scanned = ModScanner.scan(directory);
            ui(() -> {
                mods = scanned;
                render();
            });
        }, null, error -> LauncherWindow.toast("Cannot read mods: " + error.getMessage()));
    }

    private void render() {
        listPanel.removeAll();
        if (mods.isEmpty()) {
            MdCard empty = new MdCard(theme.colors, MdCard.Variant.OUTLINED);
            empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
            empty.setBorder(BorderFactory.createEmptyBorder(28, 26, 28, 26));
            empty.setAlignmentX(LEFT_ALIGNMENT);
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
            JLabel title = new JLabel("No mods installed");
            title.setFont(TypeScale.titleLarge());
            title.setForeground(theme.colors.onSurface);
            title.setAlignmentX(LEFT_ALIGNMENT);
            JLabel hint = new JLabel("Drop .jar files into the instance mods folder, "
                    + "or press Open folder.");
            hint.setFont(TypeScale.bodyMedium());
            hint.setForeground(theme.colors.onSurfaceVariant);
            hint.setAlignmentX(LEFT_ALIGNMENT);
            empty.add(title);
            empty.add(Box.createVerticalStrut(6));
            empty.add(hint);
            listPanel.add(empty);
            summary.setText("0 mods");
            listPanel.revalidate();
            listPanel.repaint();
            return;
        }

        String query = search == null ? "" : search.getText().toLowerCase();
        List<ModInfo> visible = new ArrayList<>();
        for (ModInfo mod : mods) {
            String haystack = (mod.displayName() + " " + mod.id + " " + mod.fileName).toLowerCase();
            if (query.isBlank() || haystack.contains(query)) {
                visible.add(mod);
            }
        }
        List<String> duplicates = ModScanner.duplicateIds(mods);
        StringBuilder text = new StringBuilder();
        text.append(visible.size()).append(" of ").append(mods.size()).append(" mods");
        if (!duplicates.isEmpty()) {
            text.append("  ·  ").append(duplicates.size()).append(" duplicate id(s)");
        }
        summary.setText(text.toString());

        for (ModInfo mod : visible) {
            listPanel.add(row(mod));
            listPanel.add(Box.createVerticalStrut(6));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private MdCard row(ModInfo mod) {
        MdCard card = new MdCard(theme.colors, MdCard.Variant.FILLED);
        card.setLayout(new BorderLayout(14, 0));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        JLabel name = new JLabel(mod.displayName());
        name.setFont(TypeScale.titleMedium());
        name.setForeground(mod.enabled ? theme.colors.onSurface : theme.colors.onSurfaceVariant);
        titleRow.add(name);
        if (!mod.displayVersion().isBlank()) {
            JLabel version = new JLabel(mod.displayVersion());
            version.setFont(TypeScale.bodySmall());
            version.setForeground(theme.colors.onSurfaceVariant);
            titleRow.add(version);
        }
        titleRow.add(MdChip.neutral(theme.colors, mod.loader).compact());
        if (!mod.enabled) {
            titleRow.add(MdChip.warning(theme.colors, "Disabled").compact());
        }
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        text.add(titleRow);

        JLabel detail = new JLabel(mod.summary());
        detail.setFont(TypeScale.bodySmall());
        detail.setForeground(theme.colors.onSurfaceVariant);
        detail.setAlignmentX(LEFT_ALIGNMENT);
        text.add(detail);
        card.add(text, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        MdButton toggle = new MdButton(theme.colors, mod.enabled ? "Disable" : "Enable",
                MdButton.Variant.TONAL);
        toggle.compact();
        toggle.addActionListener(event -> toggle(mod));
        actions.add(toggle);
        MdButton remove = new MdButton(theme.colors, "Delete", MdButton.Variant.TEXT);
        remove.compact();
        remove.addActionListener(event -> delete(mod));
        actions.add(remove);
        card.add(actions, BorderLayout.EAST);
        return card;
    }

    private void toggle(ModInfo mod) {
        try {
            ModScanner.toggle(mod);
            reloadMods();
            LauncherWindow.toast((mod.enabled ? "Disabled " : "Enabled ") + mod.displayName());
        } catch (Exception e) {
            LauncherWindow.toast("Cannot rename: " + e.getMessage());
        }
    }

    private void delete(ModInfo mod) {
        try {
            java.nio.file.Files.deleteIfExists(mod.file);
            reloadMods();
            LauncherWindow.toast("Deleted " + mod.fileName);
        } catch (Exception e) {
            LauncherWindow.toast("Cannot delete: " + e.getMessage());
        }
    }

    private void openFolder() {
        Path directory = modsDirectory();
        if (directory == null) {
            LauncherWindow.toast("Select an instance first");
            return;
        }
        try {
            dev.cookielauncher.core.util.Fs.mkdirs(directory);
            java.awt.Desktop.getDesktop().open(directory.toFile());
        } catch (Exception e) {
            LauncherWindow.toast("Cannot open folder: " + e.getMessage());
        }
    }

    @Override
    protected void rebuild() {
        removeAll();
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        setLayout(new BorderLayout(0, 16));
        add(buildHeader(), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);
        revalidate();
        repaint();
        onShow();
    }
}
