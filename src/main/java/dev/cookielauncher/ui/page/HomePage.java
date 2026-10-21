package dev.cookielauncher.ui.page;

import dev.cookielauncher.core.config.Account;
import dev.cookielauncher.core.config.Instance;
import dev.cookielauncher.core.util.Fmt;
import dev.cookielauncher.core.util.Progress;
import dev.cookielauncher.game.diagnose.CrashAnalyzer;
import dev.cookielauncher.game.diagnose.Diagnosis;
import dev.cookielauncher.game.install.GameInstaller;
import dev.cookielauncher.game.launch.GameProcess;
import dev.cookielauncher.game.mod.ModScanner;
import dev.cookielauncher.ui.AppContext;
import dev.cookielauncher.ui.LauncherWindow;
import dev.cookielauncher.ui.component.MdButton;
import dev.cookielauncher.ui.component.MdCard;
import dev.cookielauncher.ui.component.MdChip;
import dev.cookielauncher.ui.component.MdIcon;
import dev.cookielauncher.ui.component.MdProgress;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dashboard: the selected instance, its play button and the last diagnosis. */
public final class HomePage extends Page {

    private JLabel nameLabel;
    private JLabel subtitleLabel;
    private MdChip stateChip;
    private MdButton playButton;
    private MdProgress.Labeled progress;
    private JPanel statsRow;
    private JTextArea diagnosisArea;

    public HomePage(AppContext context) {
        super(context);
        add(scroll(buildBody()), BorderLayout.CENTER);
    }

    private JScrollPane scroll(JPanel content) {
        JScrollPane pane = new JScrollPane(content);
        pane.setBorder(null);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.getVerticalScrollBar().setUnitIncrement(24);
        return pane;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel();
        body.setBackground(theme.colors.surface);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        JLabel greeting = new JLabel("Ready to play");
        greeting.setFont(TypeScale.headlineLarge());
        greeting.setForeground(theme.colors.onSurface);
        greeting.setAlignmentX(LEFT_ALIGNMENT);
        JLabel hint = new JLabel("Every instance keeps its own world, mods and settings.");
        hint.setFont(TypeScale.bodyMedium());
        hint.setForeground(theme.colors.onSurfaceVariant);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        body.add(greeting);
        body.add(Box.createVerticalStrut(4));
        body.add(hint);
        body.add(Box.createVerticalStrut(24));
        body.add(buildHero());
        body.add(Box.createVerticalStrut(16));
        body.add(buildStats());
        body.add(Box.createVerticalStrut(16));
        body.add(buildDiagnosis());
        return body;
    }

    private MdCard buildHero() {
        MdCard card = new MdCard(theme.colors, MdCard.Variant.ELEVATED);
        card.setLayout(new BorderLayout(28, 0));
        card.setBorder(BorderFactory.createEmptyBorder(26, 28, 26, 28));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new BorderLayout(12, 0));
        titleRow.setOpaque(false);
        nameLabel = new JLabel("No instance yet");
        nameLabel.setFont(TypeScale.displaySmall());
        nameLabel.setForeground(theme.colors.onSurface);
        titleRow.add(nameLabel, BorderLayout.WEST);
        stateChip = MdChip.neutral(theme.colors, "New");
        titleRow.add(stateChip, BorderLayout.EAST);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        left.add(titleRow);

        subtitleLabel = new JLabel(" ");
        subtitleLabel.setFont(TypeScale.bodyLarge());
        subtitleLabel.setForeground(theme.colors.onSurfaceVariant);
        subtitleLabel.setAlignmentX(LEFT_ALIGNMENT);
        left.add(Box.createVerticalStrut(6));
        left.add(subtitleLabel);
        left.add(Box.createVerticalStrut(20));

        progress = new MdProgress.Labeled(theme.colors);
        progress.setAlignmentX(LEFT_ALIGNMENT);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        left.add(progress);
        left.add(Box.createVerticalStrut(20));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        playButton = new MdButton(theme.colors, "Play", MdButton.Variant.FILLED);
        playButton.setPreferredSize(new Dimension(150, 46));
        playButton.addActionListener(event -> onPlay());
        actions.add(playButton);
        MdButton repair = new MdButton(theme.colors, "Repair", MdButton.Variant.TONAL);
        repair.addActionListener(event -> onRepair());
        actions.add(repair);
        MdButton folder = new MdButton(theme.colors, "Folder", MdButton.Variant.TEXT);
        folder.addActionListener(event -> openFolder());
        actions.add(folder);
        left.add(actions);
        card.add(left, BorderLayout.CENTER);

        JPanel mark = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                MdIcon.drawLogo(g, theme.colors, Math.min(getWidth(), getHeight()));
                g.dispose();
            }
        };
        mark.setOpaque(false);
        mark.setPreferredSize(new Dimension(112, 112));
        card.add(mark, BorderLayout.EAST);
        return card;
    }

    private JPanel buildStats() {
        statsRow = new JPanel(new GridLayout(1, 4, 12, 0));
        statsRow.setOpaque(false);
        statsRow.setAlignmentX(LEFT_ALIGNMENT);
        statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
        return statsRow;
    }

    private MdCard buildDiagnosis() {
        MdCard card = new MdCard(theme.colors, MdCard.Variant.OUTLINED);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Last crash diagnosis");
        title.setFont(TypeScale.titleMedium());
        title.setForeground(theme.colors.onSurface);
        header.add(title, BorderLayout.WEST);
        MdButton analyse = new MdButton(theme.colors, "Analyse logs", MdButton.Variant.TEXT);
        analyse.addActionListener(event -> analyse());
        header.add(analyse, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        diagnosisArea = new JTextArea("No crash recorded yet. If the game will not start, "
                + "press Analyse logs to get a cause and a fix.");
        diagnosisArea.setEditable(false);
        diagnosisArea.setLineWrap(true);
        diagnosisArea.setWrapStyleWord(true);
        diagnosisArea.setOpaque(false);
        diagnosisArea.setForeground(theme.colors.onSurfaceVariant);
        diagnosisArea.setFont(TypeScale.bodyMedium());
        diagnosisArea.setBorder(null);
        card.add(diagnosisArea, BorderLayout.CENTER);
        return card;
    }

    @Override
    public void onShow() {
        Instance instance = context.instances().selected();
        if (instance == null) {
            nameLabel.setText("Create your first instance");
            subtitleLabel.setText("Choose a Minecraft version to get started");
            playButton.setText("New instance");
            stateChip.text("Empty").tone(theme.colors.onSurfaceVariant);
            refreshStats(null);
            return;
        }
        nameLabel.setText(instance.name);
        subtitleLabel.setText(instance.subtitle());
        renderState(instance);
        refreshStats(instance);
        analyseQuietly(instance);
    }

    private void renderState(Instance instance) {
        boolean installed = GameInstaller.isInstalled(instance.version);
        if (context.launcher().isRunning()) {
            stateChip.text("Running").tone(theme.colors.tertiary);
            playButton.setText("Stop");
            return;
        }
        if (instance.version == null || instance.version.isBlank()) {
            stateChip.text("No version").tone(theme.colors.error);
            playButton.setText("Pick a version");
        } else if (!installed) {
            stateChip.text("Not installed").tone(theme.colors.error);
            playButton.setText("Install");
        } else {
            stateChip.text("Installed").tone(theme.colors.tertiary);
            playButton.setText("Play");
        }
    }

    private void refreshStats(Instance instance) {
        statsRow.removeAll();
        int memory = instance == null || instance.memoryMb <= 0
                ? context.config().maxMemoryMb : instance.memoryMb;
        Account account = context.accounts().selected();
        int mods = instance == null ? 0
                : ModScanner.scan(gameDirectory(instance).resolve("mods")).size();
        statsRow.add(stat("Memory", memory + " MB"));
        statsRow.add(stat("Version", instance == null || instance.version.isBlank()
                ? "-" : instance.version));
        statsRow.add(stat("Mods", String.valueOf(mods)));
        statsRow.add(stat("Account", account == null ? "None" : account.username));
        statsRow.revalidate();
        statsRow.repaint();
    }

    private MdCard stat(String caption, String value) {
        MdCard card = new MdCard(theme.colors, MdCard.Variant.FILLED);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(TypeScale.labelMedium());
        captionLabel.setForeground(theme.colors.onSurfaceVariant);
        captionLabel.setAlignmentX(LEFT_ALIGNMENT);
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(TypeScale.titleLarge());
        valueLabel.setForeground(theme.colors.onSurface);
        valueLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(captionLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(valueLabel);
        return card;
    }

    private Path gameDirectory(Instance instance) {
        return instance.gameDir == null || instance.gameDir.isBlank()
                ? dev.cookielauncher.core.AppPaths.instance(instance.id)
                : Path.of(instance.gameDir);
    }

    private Progress sink() {
        return new Progress(new Progress.Sink() {
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
    }

    private void onPlay() {
        if (context.launcher().isRunning()) {
            context.launcher().stop();
            playButton.setText("Play");
            return;
        }
        Instance instance = context.instances().selected();
        if (instance == null) {
            Instance created = context.instances().create("My world", "");
            LauncherWindow.toast("Created '" + created.name + "'. Pick a version next.");
            if (LauncherWindow.instance() != null) {
                LauncherWindow.instance().showPage("versions");
            }
            onShow();
            return;
        }
        if (instance.version == null || instance.version.isBlank()) {
            if (LauncherWindow.instance() != null) {
                LauncherWindow.instance().showPage("versions");
            }
            return;
        }
        if (!GameInstaller.isInstalled(instance.version)) {
            install(instance);
            return;
        }
        launch(instance);
    }

    private void install(Instance instance) {
        playButton.setEnabled(false);
        progress.setIndeterminate(true);
        progress.setStatus("Installing " + instance.version);
        background("Install", () ->
                        new GameInstaller(context.config().mirrorPolicy(),
                                context.config().downloadWorkers)
                                .install(instance.version, sink()),
                () -> {
                    playButton.setEnabled(true);
                    progress.setFraction(1);
                    progress.setStatus("Installed " + instance.version);
                    progress.setDetail("");
                    onShow();
                },
                error -> {
                    playButton.setEnabled(true);
                    progress.setIndeterminate(false);
                    progress.setStatus("Install failed");
                    progress.setDetail(error.getMessage());
                    LauncherWindow.toast("Install failed: " + error.getMessage());
                });
    }

    private void launch(Instance instance) {
        Account account = context.accounts().selected();
        if (account == null) {
            LauncherWindow.toast("Add an account first");
            if (LauncherWindow.instance() != null) {
                LauncherWindow.instance().showPage("accounts");
            }
            return;
        }
        playButton.setEnabled(false);
        progress.setIndeterminate(true);
        progress.setStatus("Preparing launch");
        background("Launch", () -> {
            GameProcess process = context.launcher().launch(instance, account, sink(), line -> {
            });
            ui(() -> {
                progress.setIndeterminate(false);
                progress.setFraction(0);
                progress.setStatus("Running");
                progress.setDetail("pid " + process.pid());
                playButton.setText("Stop");
                playButton.setEnabled(true);
                stateChip.text("Running").tone(theme.colors.tertiary);
            });
            process.onExit(code -> ui(() -> {
                playButton.setText("Play");
                playButton.setEnabled(true);
                progress.setFraction(0);
                progress.setStatus("Exited with code " + code);
                progress.setDetail("");
                context.status("Minecraft exited with code " + code);
                onShow();
                if (code != 0) {
                    analyse();
                }
            }));
        }, () -> {
        }, error -> {
            playButton.setEnabled(true);
            progress.setIndeterminate(false);
            progress.setStatus("Launch failed");
            progress.setDetail(error.getMessage());
            LauncherWindow.toast("Launch failed: " + error.getMessage());
            onShow();
        });
    }

    private void onRepair() {
        Instance instance = context.instances().selected();
        if (instance == null || instance.version == null || instance.version.isBlank()) {
            return;
        }
        progress.setIndeterminate(true);
        progress.setStatus("Verifying files");
        background("Repair", () -> {
            var meta = dev.cookielauncher.game.meta.VersionMeta.resolve(
                    dev.cookielauncher.core.AppPaths.versions(), instance.version);
            new GameInstaller(context.config().mirrorPolicy(), context.config().downloadWorkers)
                    .installFiles(meta, sink());
        }, () -> {
            progress.setIndeterminate(false);
            progress.setFraction(0);
            progress.setStatus("All files verified");
            progress.setDetail("");
            LauncherWindow.toast("Repair complete");
        }, error -> {
            progress.setIndeterminate(false);
            progress.setStatus("Repair failed");
            progress.setDetail(error.getMessage());
        });
    }

    private void openFolder() {
        Instance instance = context.instances().selected();
        if (instance == null) {
            return;
        }
        try {
            Path directory = gameDirectory(instance);
            Files.createDirectories(directory);
            java.awt.Desktop.getDesktop().open(directory.toFile());
        } catch (Exception e) {
            LauncherWindow.toast("Cannot open folder: " + e.getMessage());
        }
    }

    private void analyse() {
        Instance instance = context.instances().selected();
        if (instance == null) {
            return;
        }
        List<Diagnosis> diagnoses = CrashAnalyzer.analyzeDirectory(gameDirectory(instance));
        diagnosisArea.setText(CrashAnalyzer.render(diagnoses));
        diagnosisArea.setForeground(diagnoses.isEmpty()
                ? theme.colors.onSurfaceVariant : theme.colors.onSurface);
        if (!diagnoses.isEmpty()) {
            LauncherWindow.toast(diagnoses.size() + " possible cause(s) found");
        }
    }

    private void analyseQuietly(Instance instance) {
        List<Diagnosis> diagnoses = CrashAnalyzer.analyzeDirectory(gameDirectory(instance));
        if (!diagnoses.isEmpty()) {
            diagnosisArea.setText(CrashAnalyzer.render(diagnoses));
            diagnosisArea.setForeground(theme.colors.onSurface);
        }
    }

    @Override
    protected void rebuild() {
        removeAll();
        add(scroll(buildBody()), BorderLayout.CENTER);
        revalidate();
        repaint();
        onShow();
    }
}
