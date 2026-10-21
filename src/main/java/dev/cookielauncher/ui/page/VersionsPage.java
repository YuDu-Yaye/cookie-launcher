package dev.cookielauncher.ui.page;

import dev.cookielauncher.core.config.Instance;
import dev.cookielauncher.core.util.Fmt;
import dev.cookielauncher.core.util.Progress;
import dev.cookielauncher.game.install.GameInstaller;
import dev.cookielauncher.game.loader.LoaderInstaller;
import dev.cookielauncher.game.loader.LoaderType;
import dev.cookielauncher.game.loader.LoaderVersion;
import dev.cookielauncher.game.meta.VersionManifest;
import dev.cookielauncher.ui.AppContext;
import dev.cookielauncher.ui.LauncherWindow;
import dev.cookielauncher.ui.component.MdButton;
import dev.cookielauncher.ui.component.MdCard;
import dev.cookielauncher.ui.component.MdChip;
import dev.cookielauncher.ui.component.MdProgress;
import dev.cookielauncher.ui.component.MdTextField;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Version catalogue: browse Minecraft versions, pick a loader, install. */
public final class VersionsPage extends Page {

    private final JPanel listPanel = new JPanel();
    private VersionManifest manifest;
    private String selectedVersion = "";
    private LoaderType selectedLoader = LoaderType.VANILLA;
    private String selectedLoaderVersion = "";
    private MdTextField search;
    private JComboBox<String> filter;
    private JLabel selectionLabel;
    private JComboBox<String> loaderBox;
    private JComboBox<String> loaderVersionBox;
    private MdButton installButton;
    private MdProgress.Labeled progress;

    public VersionsPage(AppContext context) {
        super(context);
        setLayout(new BorderLayout(20, 0));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        add(buildMain(), BorderLayout.CENTER);
        add(buildSidebar(), BorderLayout.EAST);
    }

    private JPanel buildMain() {
        JPanel main = new JPanel(new BorderLayout(0, 14));
        main.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Versions");
        title.setFont(TypeScale.headlineLarge());
        title.setForeground(theme.colors.onSurface);
        header.add(title, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tools.setOpaque(false);
        search = new MdTextField(theme.colors, "Search versions");
        search.setPreferredSize(new Dimension(230, 44));
        search.addCaretListener(event -> refreshList());
        tools.add(search);
        filter = new JComboBox<>(new String[]{"Releases", "Snapshots", "All"});
        filter.setPreferredSize(new Dimension(130, 44));
        filter.addActionListener(event -> refreshList());
        tools.add(filter);
        header.add(tools, BorderLayout.EAST);
        main.add(header, BorderLayout.NORTH);

        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        main.add(scroll, BorderLayout.CENTER);
        return main;
    }

    private MdCard buildSidebar() {
        MdCard card = new MdCard(theme.colors, MdCard.Variant.ELEVATED);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        card.setPreferredSize(new Dimension(340, 0));

        JLabel title = new JLabel("Install");
        title.setFont(TypeScale.titleLarge());
        title.setForeground(theme.colors.onSurface);
        title.setAlignmentX(LEFT_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(14));

        selectionLabel = new JLabel("Select a version");
        selectionLabel.setFont(TypeScale.bodyLarge());
        selectionLabel.setForeground(theme.colors.onSurfaceVariant);
        selectionLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(selectionLabel);
        card.add(Box.createVerticalStrut(18));

        JLabel loaderTitle = new JLabel("MOD LOADER");
        loaderTitle.setFont(TypeScale.labelSmall());
        loaderTitle.setForeground(theme.colors.onSurfaceVariant);
        loaderTitle.setAlignmentX(LEFT_ALIGNMENT);
        card.add(loaderTitle);
        card.add(Box.createVerticalStrut(6));

        List<String> loaderNames = new ArrayList<>();
        loaderNames.add("Vanilla");
        for (LoaderType type : LoaderType.installable()) {
            loaderNames.add(type.display());
        }
        loaderBox = new JComboBox<>(loaderNames.toArray(new String[0]));
        loaderBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        loaderBox.setAlignmentX(LEFT_ALIGNMENT);
        loaderBox.addActionListener(event -> onLoaderChanged());
        card.add(loaderBox);
        card.add(Box.createVerticalStrut(10));

        loaderVersionBox = new JComboBox<>();
        loaderVersionBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        loaderVersionBox.setAlignmentX(LEFT_ALIGNMENT);
        loaderVersionBox.setVisible(false);
        loaderVersionBox.addActionListener(event -> {
            Object value = loaderVersionBox.getSelectedItem();
            if (value != null) {
                selectedLoaderVersion = String.valueOf(value);
            }
        });
        card.add(loaderVersionBox);
        card.add(Box.createVerticalStrut(20));

        installButton = new MdButton(theme.colors, "Install", MdButton.Variant.FILLED);
        installButton.setAlignmentX(LEFT_ALIGNMENT);
        installButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        installButton.addActionListener(event -> install());
        card.add(installButton);
        card.add(Box.createVerticalStrut(18));

        progress = new MdProgress.Labeled(theme.colors);
        progress.setAlignmentX(LEFT_ALIGNMENT);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        card.add(progress);

        card.add(Box.createVerticalGlue());
        MdCard info = new MdCard(theme.colors, MdCard.Variant.FILLED);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        info.setAlignmentX(LEFT_ALIGNMENT);
        info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        JLabel infoTitle = new JLabel("Where files go");
        infoTitle.setFont(TypeScale.labelMedium());
        infoTitle.setForeground(theme.colors.onSurface);
        infoTitle.setAlignmentX(LEFT_ALIGNMENT);
        info.add(infoTitle);
        JLabel path = new JLabel(dev.cookielauncher.core.AppPaths.versions().toString());
        path.setFont(TypeScale.bodySmall());
        path.setForeground(theme.colors.onSurfaceVariant);
        path.setAlignmentX(LEFT_ALIGNMENT);
        info.add(Box.createVerticalStrut(4));
        info.add(path);
        card.add(info);
        return card;
    }

    @Override
    public void onShow() {
        if (manifest == null) {
            progress.setIndeterminate(true);
            progress.setStatus("Fetching versions");
            background("Version list", () -> {
                VersionManifest fetched = new GameInstaller(context.config().mirrorPolicy(),
                        context.config().downloadWorkers).metaService().manifest(0L);
                ui(() -> {
                    manifest = fetched;
                    progress.setIndeterminate(false);
                    progress.setFraction(0);
                    progress.setStatus(fetched.versions.size() + " versions");
                    if (selectedVersion.isBlank()) {
                        selectedVersion = fetched.latestRelease;
                    }
                    refreshList();
                    refreshSelection();
                });
            }, null, error -> {
                progress.setIndeterminate(false);
                progress.setStatus("Cannot load versions");
                LauncherWindow.toast("Version list failed: " + error.getMessage());
            });
        } else {
            refreshList();
            refreshSelection();
        }
    }

    private void refreshList() {
        listPanel.removeAll();
        if (manifest == null) {
            JLabel loading = new JLabel("Loading...");
            loading.setForeground(theme.colors.onSurfaceVariant);
            listPanel.add(loading);
            listPanel.revalidate();
            return;
        }
        int mode = filter == null ? 0 : filter.getSelectedIndex();
        boolean snapshots = mode == 1;
        boolean all = mode == 2;
        List<VersionManifest.Entry> entries = manifest.filter(snapshots || all, all,
                search == null ? "" : search.getText());
        int shown = 0;
        for (VersionManifest.Entry entry : entries) {
            if (shown++ >= 250) {
                break;
            }
            listPanel.add(row(entry));
            listPanel.add(Box.createVerticalStrut(6));
        }
        if (shown == 0) {
            JLabel empty = new JLabel("No version matches that search");
            empty.setForeground(theme.colors.onSurfaceVariant);
            listPanel.add(empty);
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private MdCard row(VersionManifest.Entry entry) {
        boolean installed = GameInstaller.isInstalled(entry.id());
        boolean selected = entry.id().equals(selectedVersion);
        MdCard card = new MdCard(theme.colors, installed
                ? MdCard.Variant.FILLED : MdCard.Variant.OUTLINED);
        if (selected) {
            card.fill(theme.colors.secondaryContainer);
        }
        card.setLayout(new BorderLayout(12, 0));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(entry.id());
        name.setFont(selected ? TypeScale.titleMedium() : TypeScale.bodyLarge());
        name.setForeground(selected ? theme.colors.onSecondaryContainer : theme.colors.onSurface);
        name.setAlignmentX(LEFT_ALIGNMENT);
        JLabel meta = new JLabel(entry.displayType() + "  ·  " + entry.shortDate());
        meta.setFont(TypeScale.bodySmall());
        meta.setForeground(theme.colors.onSurfaceVariant);
        meta.setAlignmentX(LEFT_ALIGNMENT);
        text.add(name);
        text.add(meta);
        card.add(text, BorderLayout.CENTER);

        if (installed) {
            card.add(MdChip.success(theme.colors, "Installed"), BorderLayout.EAST);
        }
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                selectedVersion = entry.id();
                refreshList();
                refreshSelection();
            }
        });
        return card;
    }

    private void refreshSelection() {
        if (selectionLabel == null) {
            return;
        }
        if (selectedVersion.isBlank()) {
            selectionLabel.setText("Select a version");
            installButton.setEnabled(false);
            return;
        }
        boolean installed = GameInstaller.isInstalled(selectedVersion);
        selectionLabel.setText(selectedVersion + (installed ? "  ·  installed" : ""));
        installButton.setEnabled(true);
        installButton.setText(selectedLoader == LoaderType.VANILLA
                ? (installed ? "Repair" : "Install")
                : "Install " + selectedLoader.display());
    }

    private void onLoaderChanged() {
        int index = loaderBox.getSelectedIndex();
        selectedLoader = index <= 0 ? LoaderType.VANILLA : LoaderType.installable()[index - 1];
        loaderVersionBox.setVisible(selectedLoader != LoaderType.VANILLA);
        loaderVersionBox.removeAllItems();
        selectedLoaderVersion = "";
        if (selectedLoader == LoaderType.VANILLA || selectedVersion.isBlank()) {
            refreshSelection();
            revalidate();
            return;
        }
        progress.setIndeterminate(true);
        progress.setStatus("Loading " + selectedLoader.display() + " builds");
        background("Loader versions", () -> {
            List<LoaderVersion> versions = new LoaderInstaller(context.config().mirrorPolicy(),
                    context.config().downloadWorkers).available(selectedLoader, selectedVersion);
            ui(() -> {
                progress.setIndeterminate(false);
                progress.setFraction(0);
                loaderVersionBox.removeAllItems();
                if (versions.isEmpty()) {
                    progress.setStatus("No " + selectedLoader.display() + " for " + selectedVersion);
                    loaderVersionBox.setVisible(false);
                } else {
                    for (LoaderVersion version : versions) {
                        loaderVersionBox.addItem(version.version());
                    }
                    selectedLoaderVersion = versions.get(0).version();
                    progress.setStatus(versions.size() + " builds");
                }
                refreshSelection();
                revalidate();
            });
        }, null, error -> {
            progress.setIndeterminate(false);
            progress.setStatus("Cannot list loader builds");
        });
    }

    private void install() {
        if (selectedVersion.isBlank()) {
            return;
        }
        String targetVersion = selectedVersion;
        LoaderType loader = selectedLoader;
        String loaderVersion = selectedLoaderVersion;
        installButton.setEnabled(false);
        progress.setIndeterminate(true);
        progress.setStatus("Starting install");
        Progress sink = new Progress(new Progress.Sink() {
            @Override
            public void onStatus(String message) {
                ui(() -> progress.setStatus(message));
            }

            @Override
            public void onProgress(long completed, long total, double fraction) {
                ui(() -> {
                    progress.setIndeterminate(false);
                    progress.setFraction(fraction);
                    progress.setDetail(Fmt.bytes(completed) + " / " + Fmt.bytes(total));
                });
            }
        });
        background("Install", () -> {
            int workers = context.config().downloadWorkers;
            String installedId;
            if (loader == LoaderType.VANILLA) {
                new GameInstaller(context.config().mirrorPolicy(), workers)
                        .install(targetVersion, sink);
                installedId = targetVersion;
            } else {
                if (loaderVersion == null || loaderVersion.isBlank()) {
                    throw new IllegalStateException("Pick a " + loader.display() + " build");
                }
                installedId = new LoaderInstaller(context.config().mirrorPolicy(), workers)
                        .install(loader, targetVersion, loaderVersion, sink);
            }
            ui(() -> {
                Instance instance = context.instances().selected();
                if (instance == null) {
                    instance = context.instances().create("My world", installedId);
                } else {
                    Instance updated = instance.copy();
                    updated.version = installedId;
                    updated.loader = loader.id();
                    updated.loaderVersion = loader == LoaderType.VANILLA ? "" : loaderVersion;
                    context.instances().update(updated);
                    context.instances().select(updated.id);
                }
                progress.setIndeterminate(false);
                progress.setFraction(1);
                progress.setStatus("Installed " + installedId);
                progress.setDetail("");
                installButton.setEnabled(true);
                refreshList();
                refreshSelection();
                LauncherWindow.toast("Installed " + installedId);
            });
        }, null, error -> {
            installButton.setEnabled(true);
            progress.setIndeterminate(false);
            progress.setStatus("Install failed");
            progress.setDetail(error.getMessage());
            LauncherWindow.toast("Install failed: " + error.getMessage());
        });
    }

    @Override
    protected void rebuild() {
        removeAll();
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        setLayout(new BorderLayout(20, 0));
        add(buildMain(), BorderLayout.CENTER);
        add(buildSidebar(), BorderLayout.EAST);
        revalidate();
        repaint();
        onShow();
    }
}
