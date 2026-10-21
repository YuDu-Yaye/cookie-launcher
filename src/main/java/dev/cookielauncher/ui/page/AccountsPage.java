package dev.cookielauncher.ui.page;

import dev.cookielauncher.core.config.Account;
import dev.cookielauncher.game.auth.MicrosoftAuth;
import dev.cookielauncher.game.auth.YggdrasilAuth;
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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

/** Account management: offline, authlib (Yggdrasil) and Microsoft. */
public final class AccountsPage extends Page {

    private final JPanel listPanel = new JPanel();
    private JLabel summary;

    public AccountsPage(AppContext context) {
        super(context);
        setLayout(new BorderLayout(0, 18));
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
        JLabel title = new JLabel("Accounts");
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
        MdButton offline = new MdButton(theme.colors, "Add offline", MdButton.Variant.TONAL);
        offline.addActionListener(event -> addOffline());
        actions.add(offline);
        MdButton authlib = new MdButton(theme.colors, "Add authlib", MdButton.Variant.TONAL);
        authlib.addActionListener(event -> addAuthlib());
        actions.add(authlib);
        MdButton microsoft = new MdButton(theme.colors, "Microsoft", MdButton.Variant.OUTLINED);
        microsoft.addActionListener(event -> signInMicrosoft());
        actions.add(microsoft);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    @Override
    public void onShow() {
        render();
    }

    private void render() {
        listPanel.removeAll();
        List<Account> accounts = context.accounts().all();
        summary.setText(accounts.isEmpty() ? "No accounts yet"
                : accounts.size() + (accounts.size() == 1 ? " account" : " accounts"));

        if (accounts.isEmpty()) {
            MdCard empty = new MdCard(theme.colors, MdCard.Variant.OUTLINED);
            empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
            empty.setBorder(BorderFactory.createEmptyBorder(28, 26, 28, 26));
            empty.setAlignmentX(LEFT_ALIGNMENT);
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
            JLabel title = new JLabel("Add an account to play");
            title.setFont(TypeScale.titleLarge());
            title.setForeground(theme.colors.onSurface);
            title.setAlignmentX(LEFT_ALIGNMENT);
            JLabel hint = new JLabel("<html>Offline accounts play singleplayer and LAN.<br>"
                    + "Authlib accounts join servers that use a third-party login.</html>");
            hint.setFont(TypeScale.bodyMedium());
            hint.setForeground(theme.colors.onSurfaceVariant);
            hint.setAlignmentX(LEFT_ALIGNMENT);
            empty.add(title);
            empty.add(Box.createVerticalStrut(6));
            empty.add(hint);
            listPanel.add(empty);
            listPanel.revalidate();
            listPanel.repaint();
            return;
        }

        Account selected = context.accounts().selected();
        for (Account account : accounts) {
            listPanel.add(row(account, selected != null && selected.id.equals(account.id)));
            listPanel.add(Box.createVerticalStrut(6));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private MdCard row(Account account, boolean selected) {
        MdCard card = new MdCard(theme.colors, selected
                ? MdCard.Variant.FILLED : MdCard.Variant.OUTLINED);
        card.setLayout(new BorderLayout(14, 0));
        card.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        JLabel name = new JLabel(account.username);
        name.setFont(TypeScale.titleMedium());
        name.setForeground(theme.colors.onSurface);
        titleRow.add(name);
        String typeLabel = switch (account.type) {
            case "microsoft" -> "Microsoft";
            case "authlib" -> "Authlib";
            default -> "Offline";
        };
        titleRow.add(new MdChip(theme.colors, typeLabel, account.isOnline()
                ? theme.colors.primary : theme.colors.onSurfaceVariant).compact());
        if (selected) {
            titleRow.add(MdChip.success(theme.colors, "In use").compact());
        }
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        text.add(titleRow);

        String detail = account.uuid == null || account.uuid.isBlank() ? "No UUID" : account.uuid;
        if ("authlib".equals(account.type) && account.serverUrl != null
                && !account.serverUrl.isBlank()) {
            detail = detail + "  ·  " + account.serverUrl;
        }
        JLabel detailLabel = new JLabel(detail);
        detailLabel.setFont(TypeScale.bodySmall());
        detailLabel.setForeground(theme.colors.onSurfaceVariant);
        detailLabel.setAlignmentX(LEFT_ALIGNMENT);
        text.add(detailLabel);
        card.add(text, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        if (!selected) {
            MdButton use = new MdButton(theme.colors, "Use", MdButton.Variant.TONAL);
            use.compact();
            use.addActionListener(event -> {
                context.accounts().select(account.id);
                render();
            });
            actions.add(use);
        }
        MdButton remove = new MdButton(theme.colors, "Remove", MdButton.Variant.TEXT);
        remove.compact();
        remove.addActionListener(event -> {
            context.accounts().remove(account.id);
            render();
            LauncherWindow.toast("Removed " + account.username);
        });
        actions.add(remove);
        card.add(actions, BorderLayout.EAST);
        return card;
    }

    private void addOffline() {
        String name = JOptionPane.showInputDialog(this,
                "Username for the offline account:", "Add offline account",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        context.accounts().add(Account.offline(name.trim()));
        render();
        LauncherWindow.toast("Added offline account " + name.trim());
    }

    private void addAuthlib() {
        MdTextField serverField = new MdTextField(theme.colors, "https://example.com/api/yggdrasil");
        MdTextField userField = new MdTextField(theme.colors, "Email or username");
        dev.cookielauncher.ui.component.MdPasswordField passwordField =
                new dev.cookielauncher.ui.component.MdPasswordField(theme.colors, "Password");

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        form.add(label("Server root URL"));
        form.add(serverField);
        form.add(Box.createVerticalStrut(10));
        form.add(label("Username"));
        form.add(userField);
        form.add(Box.createVerticalStrut(10));
        form.add(label("Password"));
        form.add(passwordField);

        int result = JOptionPane.showConfirmDialog(this, form, "Add authlib account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        String server = serverField.getText().trim();
        String username = userField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (!server.startsWith("http")) {
            LauncherWindow.toast("The server URL must start with http:// or https://");
            return;
        }
        background("Authlib sign-in", () -> {
            Account account = new YggdrasilAuth(server).login(username, password);
            ui(() -> {
                context.accounts().add(account);
                render();
                LauncherWindow.toast("Signed in as " + account.username);
            });
        }, null, error -> LauncherWindow.toast("Sign-in failed: " + error.getMessage()));
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(TypeScale.labelMedium());
        label.setForeground(theme.colors.onSurfaceVariant);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private void signInMicrosoft() {
        MicrosoftAuth auth = new MicrosoftAuth(context.config().msaClientId);
        if (!auth.isConfigured()) {
            JOptionPane.showMessageDialog(this,
                    "<html>Microsoft sign-in needs an Entra application id.<br><br>"
                            + "1. Create a public client app in Microsoft Entra ID.<br>"
                            + "2. Supported accounts: personal Microsoft accounts only.<br>"
                            + "3. Authentication: enable <b>Allow public client flows</b>.<br>"
                            + "4. Put the application id in config.json as "
                            + "<code>msaClientId</code>.<br><br>"
                            + "Offline and authlib accounts work without this step.</html>",
                    "Microsoft sign-in is not configured", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        background("Microsoft sign-in", () -> {
            MicrosoftAuth.DeviceCode code = auth.startDeviceCode();
            int[] choice = {JOptionPane.CANCEL_OPTION};
            ui(() -> choice[0] = JOptionPane.showOptionDialog(this,
                    "<html>Open <b>" + code.verificationUri() + "</b><br><br>"
                            + "and enter the code <b style='font-size:16px'>"
                            + code.userCode() + "</b><br><br>"
                            + "Approve the request, then press Continue.</html>",
                    "Sign in with Microsoft", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.INFORMATION_MESSAGE, null,
                    new Object[]{"Continue", "Open browser"}, "Open browser"));
            if (choice[0] == 1 && Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(java.net.URI.create(code.verificationUri()));
                } catch (Exception e) {
                    dev.cookielauncher.core.log.Log.warn("Cannot open browser: " + e.getMessage());
                }
            }
            MicrosoftAuth.Tokens tokens = auth.poll(code);
            Account account = auth.toAccount(tokens);
            ui(() -> {
                context.accounts().add(account);
                render();
                LauncherWindow.toast("Signed in as " + account.username);
            });
        }, null, error -> LauncherWindow.toast("Microsoft sign-in failed: " + error.getMessage()));
    }

    @Override
    protected void rebuild() {
        removeAll();
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        setLayout(new BorderLayout(0, 18));
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
