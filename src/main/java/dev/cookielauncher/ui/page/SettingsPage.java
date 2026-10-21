package dev.cookielauncher.ui.page;

import dev.cookielauncher.core.AppInfo;
import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.config.Config;
import dev.cookielauncher.core.net.Mirror;
import dev.cookielauncher.core.util.Os;
import dev.cookielauncher.game.java.JavaLocator;
import dev.cookielauncher.game.java.JavaRuntime;
import dev.cookielauncher.ui.AppContext;
import dev.cookielauncher.ui.LauncherWindow;
import dev.cookielauncher.ui.component.MdButton;
import dev.cookielauncher.ui.component.MdCard;
import dev.cookielauncher.ui.component.MdChip;
import dev.cookielauncher.ui.component.MdTextField;
import dev.cookielauncher.ui.theme.Theme;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;

/** Settings: memory, Java, downloads, appearance, paths and about. */
public final class SettingsPage extends Page {

    private JPanel body;
    private JSlider memorySlider;
    private JLabel memoryValue;
    private MdTextField jvmField;
    private MdTextField gameArgsField;
    private MdTextField proxyField;
    private JComboBox<String> mirrorBox;
    private JComboBox<Integer> workerBox;
    private JComboBox<String> themeBox;
    private JCheckBox fullscreenBox;
    private JCheckBox snapshotsBox;
    private JPanel javaList;

    public SettingsPage(AppContext context) {
        super(context);
        setLayout(new BorderLayout());
        add(scroll(buildBody()), BorderLayout.CENTER);
    }

    private JScrollPane scroll(JPanel content) {
        JScrollPane pane = new JScrollPane(content);
        pane.setBorder(null);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.getVerticalScrollBar().setUnitIncrement(24);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return pane;
    }

    private JPanel buildBody() {
        body = new JPanel();
        body.setBackground(theme.colors.surface);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(24, 28, 28, 28));

        JLabel title = new JLabel("Settings");
        title.setFont(TypeScale.headlineLarge());
        title.setForeground(theme.colors.onSurface);
        title.setAlignmentX(LEFT_ALIGNMENT);
        JLabel hint = new JLabel("Everything lives in one folder, so the whole launcher is portable.");
        hint.setFont(TypeScale.bodyMedium());
        hint.setForeground(theme.colors.onSurfaceVariant);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        body.add(title);
        body.add(Box.createVerticalStrut(4));
        body.add(hint);
        body.add(Box.createVerticalStrut(20));

        body.add(memorySection());
        body.add(Box.createVerticalStrut(14));
        body.add(javaSection());
        body.add(Box.createVerticalStrut(14));
        body.add(downloadSection());
        body.add(Box.createVerticalStrut(14));
        body.add(appearanceSection());
        body.add(Box.createVerticalStrut(14));
        body.add(pathSection());
        body.add(Box.createVerticalStrut(14));
        body.add(aboutSection());
        return body;
    }

    private MdCard section(String title, String description) {
        MdCard card = new MdCard(theme.colors, MdCard.Variant.ELEVATED);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        card.setAlignmentX(LEFT_ALIGNMENT);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(TypeScale.titleLarge());
        titleLabel.setForeground(theme.colors.onSurface);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(titleLabel);
        if (description != null && !description.isBlank()) {
            JLabel descriptionLabel = new JLabel(description);
            descriptionLabel.setFont(TypeScale.bodySmall());
            descriptionLabel.setForeground(theme.colors.onSurfaceVariant);
            descriptionLabel.setAlignmentX(LEFT_ALIGNMENT);
            card.add(Box.createVerticalStrut(3));
            card.add(descriptionLabel);
        }
        card.add(Box.createVerticalStrut(16));
        return card;
    }

    private MdCard memorySection() {
        MdCard card = section("Memory", "Leave headroom for the operating system; "
                + "modpacks usually need 4-8 GB.");
        Config config = context.config();

        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JLabel caption = new JLabel("Maximum memory");
        caption.setFont(TypeScale.bodyLarge());
        caption.setForeground(theme.colors.onSurface);
        memoryValue = new JLabel(config.maxMemoryMb + " MB");
        memoryValue.setFont(TypeScale.labelLarge());
        memoryValue.setForeground(theme.colors.primary);
        row.add(caption, BorderLayout.WEST);
        row.add(memoryValue, BorderLayout.EAST);
        card.add(row);

        memorySlider = new JSlider(512, 16384, Math.max(512, config.maxMemoryMb));
        memorySlider.setOpaque(false);
        memorySlider.setFocusable(false);
        memorySlider.setAlignmentX(LEFT_ALIGNMENT);
        memorySlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        memorySlider.addChangeListener(event -> {
            int value = memorySlider.getValue();
            int snapped = Math.round(value / 256f) * 256;
            memoryValue.setText(snapped + " MB");
            if (!memorySlider.getValueIsAdjusting()) {
                context.config().maxMemoryMb = snapped;
            }
        });
        card.add(memorySlider);
        card.add(Box.createVerticalStrut(14));

        jvmField = new MdTextField(theme.colors, "-XX:+UseG1GC ...");
        jvmField.setText(config.jvmArgs);
        jvmField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        jvmField.setAlignmentX(LEFT_ALIGNMENT);
        card.add(captionLabel("JVM arguments"));
        card.add(Box.createVerticalStrut(6));
        card.add(jvmField);
        card.add(Box.createVerticalStrut(12));

        gameArgsField = new MdTextField(theme.colors, "Extra game arguments");
        gameArgsField.setText(config.gameArgs);
        gameArgsField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        gameArgsField.setAlignmentX(LEFT_ALIGNMENT);
        card.add(captionLabel("Game arguments"));
        card.add(Box.createVerticalStrut(6));
        card.add(gameArgsField);
        card.add(Box.createVerticalStrut(12));

        fullscreenBox = checkBox("Start in fullscreen", config.fullscreen);
        fullscreenBox.setAlignmentX(LEFT_ALIGNMENT);
        card.add(fullscreenBox);
        return card;
    }

    private MdCard javaSection() {
        MdCard card = section("Java", "Detected runtimes. The launcher picks the lowest "
                + "sufficient major version and can download one when none fits.");
        javaList = new JPanel();
        javaList.setOpaque(false);
        javaList.setLayout(new BoxLayout(javaList, BoxLayout.Y_AXIS));
        javaList.setAlignmentX(LEFT_ALIGNMENT);
        refreshJavaList();
        card.add(javaList);
        card.add(Box.createVerticalStrut(12));

        MdButton rescan = new MdButton(theme.colors, "Rescan runtimes", MdButton.Variant.TONAL);
        rescan.setAlignmentX(LEFT_ALIGNMENT);
        rescan.addActionListener(event -> {
            background("Java scan", () -> {
                JavaLocator locator = new JavaLocator();
                locator.refresh();
                List<JavaRuntime> runtimes = locator.all();
                ui(() -> {
                    refreshJavaList();
                    LauncherWindow.toast(runtimes.size() + " runtime(s) detected");
                });
            }, null, null);
        });
        card.add(rescan);
        return card;
    }

    private void refreshJavaList() {
        javaList.removeAll();
        List<JavaRuntime> runtimes = new JavaLocator().all();
        if (runtimes.isEmpty()) {
            JLabel none = new JLabel("No Java runtime detected yet.");
            none.setFont(TypeScale.bodyMedium());
            none.setForeground(theme.colors.onSurfaceVariant);
            none.setAlignmentX(LEFT_ALIGNMENT);
            javaList.add(none);
        } else {
            for (JavaRuntime runtime : runtimes) {
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
                row.setOpaque(false);
                row.setAlignmentX(LEFT_ALIGNMENT);
                JLabel label = new JLabel(runtime.label());
                label.setFont(TypeScale.bodyMedium());
                label.setForeground(theme.colors.onSurface);
                row.add(label);
                row.add(MdChip.neutral(theme.colors, runtime.executable().toString()).compact());
                javaList.add(row);
            }
        }
        javaList.revalidate();
        javaList.repaint();
    }

    private MdCard downloadSection() {
        MdCard card = section("Downloads",
                "Official CDN first, with an automatic mirror fallback. Large files are "
                        + "split into parallel range requests.");
        Config config = context.config();

        card.add(captionLabel("Source"));
        card.add(Box.createVerticalStrut(6));
        mirrorBox = new JComboBox<>(new String[]{
                Mirror.label(Mirror.Policy.AUTO),
                Mirror.label(Mirror.Policy.OFFICIAL),
                Mirror.label(Mirror.Policy.MIRROR)});
        mirrorBox.setSelectedIndex(config.mirrorPolicy() == Mirror.Policy.MIRROR ? 2
                : config.mirrorPolicy() == Mirror.Policy.OFFICIAL ? 1 : 0);
        mirrorBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        mirrorBox.setAlignmentX(LEFT_ALIGNMENT);
        card.add(mirrorBox);
        card.add(Box.createVerticalStrut(12));

        JPanel workerRow = new JPanel(new BorderLayout(12, 0));
        workerRow.setOpaque(false);
        workerRow.setAlignmentX(LEFT_ALIGNMENT);
        workerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        JLabel workerLabel = new JLabel("Parallel downloads");
        workerLabel.setFont(TypeScale.bodyLarge());
        workerLabel.setForeground(theme.colors.onSurface);
        workerRow.add(workerLabel, BorderLayout.WEST);
        workerBox = new JComboBox<>(new Integer[]{4, 8, 12, 16, 24, 32});
        workerBox.setSelectedItem(config.downloadWorkers);
        workerBox.setPreferredSize(new Dimension(90, 40));
        workerBox.addActionListener(event -> {
            Object value = workerBox.getSelectedItem();
            if (value instanceof Integer number) {
                context.config().downloadWorkers = number;
            }
        });
        workerRow.add(workerBox, BorderLayout.EAST);
        card.add(workerRow);
        card.add(Box.createVerticalStrut(12));

        card.add(captionLabel("Proxy (optional, host:port)"));
        card.add(Box.createVerticalStrut(6));
        proxyField = new MdTextField(theme.colors, "127.0.0.1:7890");
        proxyField.setText(config.proxy);
        proxyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        proxyField.setAlignmentX(LEFT_ALIGNMENT);
        card.add(proxyField);
        card.add(Box.createVerticalStrut(12));

        snapshotsBox = checkBox("Show snapshots in the version list", config.showSnapshots);
        snapshotsBox.setAlignmentX(LEFT_ALIGNMENT);
        card.add(snapshotsBox);
        return card;
    }

    private MdCard appearanceSection() {
        MdCard card = section("Appearance", "Material Design 3 themes generated from a seed colour.");
        themeBox = new JComboBox<>();
        for (Theme option : Theme.all()) {
            themeBox.addItem(option.name);
        }
        themeBox.setSelectedItem(theme.name);
        themeBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        themeBox.setAlignmentX(LEFT_ALIGNMENT);
        themeBox.addActionListener(event -> {
            Object selected = themeBox.getSelectedItem();
            if (selected != null) {
                context.setTheme(Theme.byId(((Theme) Theme.all().stream()
                        .filter(candidate -> candidate.name.equals(selected))
                        .findFirst().orElse(theme)).id));
            }
        });
        card.add(themeBox);
        return card;
    }

    private MdCard pathSection() {
        MdCard card = section("Data directory",
                "Moving this folder moves every instance, account and setting.");
        JLabel path = new JLabel(AppPaths.root().toString());
        path.setFont(TypeScale.bodyMedium());
        path.setForeground(theme.colors.onSurfaceVariant);
        path.setAlignmentX(LEFT_ALIGNMENT);
        card.add(path);
        card.add(Box.createVerticalStrut(12));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        MdButton open = new MdButton(theme.colors, "Open folder", MdButton.Variant.TONAL);
        open.addActionListener(event -> {
            try {
                java.awt.Desktop.getDesktop().open(AppPaths.root().toFile());
            } catch (Exception e) {
                LauncherWindow.toast("Cannot open folder: " + e.getMessage());
            }
        });
        actions.add(open);
        MdButton log = new MdButton(theme.colors, "Open log", MdButton.Variant.TEXT);
        log.addActionListener(event -> {
            try {
                java.awt.Desktop.getDesktop().open(AppPaths.launcherLog().toFile());
            } catch (Exception e) {
                LauncherWindow.toast("Cannot open log: " + e.getMessage());
            }
        });
        actions.add(log);
        card.add(actions);
        card.add(Box.createVerticalStrut(10));

        JLabel portable = new JLabel(AppPaths.isPortable()
                ? "Portable mode is active."
                : "Tip: create an empty file named .portable next to the executable.");
        portable.setFont(TypeScale.bodySmall());
        portable.setForeground(theme.colors.onSurfaceVariant);
        portable.setAlignmentX(LEFT_ALIGNMENT);
        card.add(portable);
        return card;
    }

    private MdCard aboutSection() {
        MdCard card = section("About", null);
        JPanel grid = new JPanel(new GridLayout(0, 2, 18, 10));
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        stat(grid, "Version", AppInfo.VERSION);
        stat(grid, "Licence", AppInfo.LICENSE);
        stat(grid, "Java", System.getProperty("java.version"));
        stat(grid, "Platform", Os.displayName());
        stat(grid, "CPUs", String.valueOf(Os.cpuCount()));
        stat(grid, "Memory", Os.totalMemoryMb() > 0 ? Os.totalMemoryMb() + " MB" : "unknown");
        card.add(grid);
        card.add(Box.createVerticalStrut(16));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        MdButton save = new MdButton(theme.colors, "Save settings", MdButton.Variant.FILLED);
        save.addActionListener(event -> save());
        actions.add(save);
        MdButton reset = new MdButton(theme.colors, "Reset to defaults", MdButton.Variant.TEXT);
        reset.addActionListener(event -> {
            Config fresh = new Config();
            Config config = context.config();
            config.maxMemoryMb = Config.defaultMaxMemoryMb();
            config.jvmArgs = fresh.jvmArgs;
            config.mirror = Mirror.Policy.AUTO.name();
            config.proxy = "";
            config.downloadWorkers = 12;
            config.fullscreen = false;
            context.saveConfig();
            LauncherWindow.toast("Settings reset");
            rebuild();
        });
        actions.add(reset);
        card.add(actions);
        return card;
    }

    private void stat(JPanel grid, String caption, String value) {
        JPanel cell = new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(TypeScale.labelSmall());
        captionLabel.setForeground(theme.colors.onSurfaceVariant);
        captionLabel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(TypeScale.bodyMedium());
        valueLabel.setForeground(theme.colors.onSurface);
        valueLabel.setAlignmentX(LEFT_ALIGNMENT);
        cell.add(captionLabel);
        cell.add(valueLabel);
        grid.add(cell);
    }

    private JLabel captionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(TypeScale.labelMedium());
        label.setForeground(theme.colors.onSurfaceVariant);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private JCheckBox checkBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setOpaque(false);
        box.setForeground(theme.colors.onSurface);
        box.setFont(TypeScale.bodyLarge());
        box.setFocusPainted(false);
        return box;
    }

    private void save() {
        Config config = context.config();
        config.maxMemoryMb = Math.round(memorySlider.getValue() / 256f) * 256;
        config.jvmArgs = jvmField.getText();
        config.gameArgs = gameArgsField.getText();
        config.proxy = proxyField.getText().trim();
        config.fullscreen = fullscreenBox.isSelected();
        config.showSnapshots = snapshotsBox.isSelected();
        config.mirror = switch (mirrorBox.getSelectedIndex()) {
            case 1 -> Mirror.Policy.OFFICIAL.name();
            case 2 -> Mirror.Policy.MIRROR.name();
            default -> Mirror.Policy.AUTO.name();
        };
        context.saveConfig();
        LauncherWindow.toast("Settings saved");
    }

    @Override
    protected void rebuild() {
        removeAll();
        add(scroll(buildBody()), BorderLayout.CENTER);
        revalidate();
        repaint();
    }
}
