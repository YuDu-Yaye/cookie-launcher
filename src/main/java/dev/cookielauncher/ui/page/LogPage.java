package dev.cookielauncher.ui.page;

import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.game.diagnose.CrashAnalyzer;
import dev.cookielauncher.game.diagnose.Diagnosis;
import dev.cookielauncher.ui.AppContext;
import dev.cookielauncher.ui.LauncherWindow;
import dev.cookielauncher.ui.component.MdButton;
import dev.cookielauncher.ui.component.MdTextField;
import dev.cookielauncher.ui.theme.TypeScale;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Live log with filtering, diagnosis and export. */
public final class LogPage extends Page {

    private final JTextArea area = new JTextArea();
    private final List<String> buffer = new ArrayList<>();
    private MdTextField search;
    private JLabel countLabel;
    private JLabel diagnosisLabel;
    private Consumer<String> listener;
    private boolean follow = true;

    public LogPage(AppContext context) {
        super(context);
        setLayout(new BorderLayout(0, 14));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        add(buildHeader(), BorderLayout.NORTH);

        area.setEditable(false);
        area.setBackground(theme.colors.surfaceContainer);
        area.setForeground(theme.colors.onSurface);
        area.setCaretColor(theme.colors.primary);
        area.setFont(TypeScale.mono(12));
        area.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        DefaultCaret caret = (DefaultCaret) area.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().addAdjustmentListener(event -> {
            javax.swing.JScrollBar bar = scroll.getVerticalScrollBar();
            follow = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 40;
        });
        add(scroll, BorderLayout.CENTER);

        diagnosisLabel = new JLabel(" ");
        diagnosisLabel.setFont(TypeScale.bodySmall());
        diagnosisLabel.setForeground(theme.colors.onSurfaceVariant);
        diagnosisLabel.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));
        add(diagnosisLabel, BorderLayout.SOUTH);

        listener = line -> SwingUtilities.invokeLater(() -> append(line));
        context.onLog(listener);
        buffer.addAll(Log.history());
        rerender();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new javax.swing.BoxLayout(titles, javax.swing.BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Logs");
        title.setFont(TypeScale.headlineLarge());
        title.setForeground(theme.colors.onSurface);
        title.setAlignmentX(LEFT_ALIGNMENT);
        countLabel = new JLabel("0 lines");
        countLabel.setFont(TypeScale.bodyMedium());
        countLabel.setForeground(theme.colors.onSurfaceVariant);
        countLabel.setAlignmentX(LEFT_ALIGNMENT);
        titles.add(title);
        titles.add(javax.swing.Box.createVerticalStrut(4));
        titles.add(countLabel);
        header.add(titles, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        search = new MdTextField(theme.colors, "Filter lines");
        search.setPreferredSize(new Dimension(220, 44));
        search.addCaretListener(event -> rerender());
        actions.add(search);
        MdButton analyse = new MdButton(theme.colors, "Analyse", MdButton.Variant.FILLED);
        analyse.addActionListener(event -> analyse());
        actions.add(analyse);
        MdButton copy = new MdButton(theme.colors, "Copy", MdButton.Variant.TONAL);
        copy.addActionListener(event -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(area.getText()), null);
            LauncherWindow.toast("Log copied to the clipboard");
        });
        actions.add(copy);
        MdButton export = new MdButton(theme.colors, "Export", MdButton.Variant.TONAL);
        export.addActionListener(event -> export());
        actions.add(export);
        MdButton clear = new MdButton(theme.colors, "Clear", MdButton.Variant.TEXT);
        clear.addActionListener(event -> {
            buffer.clear();
            area.setText("");
            updateCount();
        });
        actions.add(clear);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    @Override
    public void onShow() {
        for (String line : Log.history()) {
            if (!buffer.contains(line)) {
                buffer.add(line);
            }
        }
        rerender();
    }

    private void append(String line) {
        buffer.add(line);
        if (buffer.size() > 20_000) {
            buffer.subList(0, buffer.size() - 20_000).clear();
        }
        if (matches(line)) {
            area.append(line);
            area.append("\n");
            if (follow) {
                area.setCaretPosition(area.getDocument().getLength());
            }
        }
        updateCount();
    }

    private boolean matches(String line) {
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        return query.isEmpty() || line.toLowerCase(Locale.ROOT).contains(query);
    }

    private void rerender() {
        StringBuilder builder = new StringBuilder();
        for (String line : buffer) {
            if (matches(line)) {
                builder.append(line).append('\n');
            }
        }
        area.setText(builder.toString());
        if (follow) {
            area.setCaretPosition(area.getDocument().getLength());
        }
        updateCount();
    }

    private void updateCount() {
        if (countLabel != null) {
            countLabel.setText(buffer.size() + " lines, showing " + area.getLineCount());
        }
    }

    private void analyse() {
        List<Diagnosis> diagnoses = CrashAnalyzer.analyze(String.join("\n", buffer));
        if (diagnoses.isEmpty()) {
            diagnosisLabel.setText("No known problem signature found in this log.");
            return;
        }
        Diagnosis top = diagnoses.get(0);
        diagnosisLabel.setText(diagnoses.size() + " cause(s) found. Most likely: "
                + top.title() + "  -  " + top.fixes().get(0));
        LauncherWindow.toast(diagnoses.size() + " possible cause(s) identified");
    }

    private void export() {
        Path target = dev.cookielauncher.core.AppPaths.logs()
                .resolve("export-" + System.currentTimeMillis() + ".log");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, String.join("\n", buffer));
            LauncherWindow.toast("Exported to " + target);
        } catch (IOException e) {
            LauncherWindow.toast("Export failed: " + e.getMessage());
        }
    }

    @Override
    protected void rebuild() {
        removeAll();
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        setLayout(new BorderLayout(0, 14));
        add(buildHeader(), BorderLayout.NORTH);
        area.setBackground(theme.colors.surfaceContainer);
        area.setForeground(theme.colors.onSurface);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll, BorderLayout.CENTER);
        add(diagnosisLabel, BorderLayout.SOUTH);
        revalidate();
        repaint();
        rerender();
    }
}
