package com.f1telemetry;

import com.f1telemetry.config.SplashScreen;
import me.friwi.jcefmaven.CefAppBuilder;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefLifeSpanHandlerAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Lightweight desktop launcher that opens the remote F1 Telemetry backend
 * in an embedded Chromium browser (JCEF). Does NOT start Spring Boot —
 * the server runs on Oracle Cloud at the configured URL.
 * <p>
 * This is the main class used by jpackage when building the .exe distribution.
 */
public class DesktopLauncher {

    private static final String REMOTE_URL = "http://f1-telemetry-app.duckdns.org:8080";

    // Colors matching the F1 dashboard dark theme
    private static final Color BG_DARK = new Color(15, 23, 42);
    private static final Color BG_TITLEBAR = new Color(10, 15, 30);
    private static final Color F1_RED = new Color(229, 9, 20);
    private static final Color TEXT_DIM = new Color(148, 163, 184);
    private static final Color TEXT_BRIGHT = new Color(226, 232, 240);
    private static final Color BTN_HOVER = new Color(30, 41, 59);
    private static final Color BTN_CLOSE_HOVER = new Color(229, 9, 20);

    private JFrame mainFrame;
    private CefApp cefApp;
    private CefClient cefClient;
    private CefBrowser cefBrowser;
    private boolean isMaximized = false;
    private Rectangle preMaximizeBounds = null;
    private final com.f1telemetry.update.UpdateManager updateManager = new com.f1telemetry.update.UpdateManager();

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");

        // Show splash screen immediately
        SplashScreen splash = new SplashScreen();
        SwingUtilities.invokeLater(splash::showSplash);

        // Initialize GUI on EDT
        DesktopLauncher launcher = new DesktopLauncher();
        EventQueue.invokeLater(() -> {
            try {
                launcher.setupSystemTray();
                launcher.initCef();
                launcher.createMainWindow(REMOTE_URL);
                splash.hideSplash();
                System.out.println("[F1Telemetry] Desktop client launched — connected to " + REMOTE_URL);
                launcher.updateManager.checkForUpdatesAsync(true);
            } catch (Throwable e) {
                splash.hideSplash();
                System.err.println("[F1Telemetry] Failed to initialize: " + e.getMessage());
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "Failed to start F1 Race Engineer:\n" + e.getMessage(),
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private void initCef() throws Exception {
        CefAppBuilder builder = new CefAppBuilder();

        File installDir = new File(System.getProperty("user.home"), ".f1telemetry/jcef");
        builder.setInstallDir(installDir);

        builder.getCefSettings().windowless_rendering_enabled = false;
        builder.getCefSettings().locale = "en-US";

        File cacheDir = new File(installDir, "cache");
        builder.getCefSettings().root_cache_path = cacheDir.getAbsolutePath();
        builder.getCefSettings().cache_path = cacheDir.getAbsolutePath();
        builder.getCefSettings().log_file = new File(installDir, "debug.log").getAbsolutePath();
        builder.getCefSettings().log_severity = org.cef.CefSettings.LogSeverity.LOGSEVERITY_DISABLE;

        File helperExe = new File(installDir, "jcef_helper.exe");
        if (helperExe.exists()) {
            builder.getCefSettings().browser_subprocess_path = helperExe.getAbsolutePath();
        }

        builder.addJcefArgs("--disable-gpu");
        builder.addJcefArgs("--disable-gpu-compositing");
        builder.addJcefArgs("--disable-direct-composition");
        builder.addJcefArgs("--disable-gpu-shader-disk-cache");
        builder.addJcefArgs("--disable-gpu-rasterization");
        builder.addJcefArgs("--disable-gpu-process-crash-limit");
        builder.addJcefArgs("--use-gl=disabled");

        cefApp = builder.build();
        cefClient = cefApp.createClient();

        // Open popups in the user's default system browser
        cefClient.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, org.cef.browser.CefFrame frame,
                    String target_url, String target_frame_name) {
                if (target_url != null && (target_url.startsWith("http://") || target_url.startsWith("https://"))) {
                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().browse(new java.net.URI(target_url));
                        } catch (Exception e) {
                            System.err.println("Failed to open popup URL: " + target_url);
                        }
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void createMainWindow(String url) {
        mainFrame = new JFrame();
        mainFrame.setUndecorated(true);
        mainFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        mainFrame.setSize(1280, 800);
        mainFrame.setMinimumSize(new Dimension(900, 600));
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setBackground(BG_DARK);
        mainFrame.setIconImage(createAppIcon(32));

        mainFrame.setLayout(new BorderLayout(0, 0));
        mainFrame.add(createTitleBar(), BorderLayout.NORTH);

        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                minimizeToTray();
            }
        });

        addResizeBehavior(mainFrame);

        mainFrame.setVisible(true);
        mainFrame.toFront();

        cefBrowser = cefClient.createBrowser(url, false, false);
        Component browserUI = cefBrowser.getUIComponent();
        mainFrame.add(browserUI, BorderLayout.CENTER);
        mainFrame.revalidate();
        mainFrame.repaint();
    }

    // ── Title Bar ─────────────────────────────────────────────────────────────

    private JPanel createTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, 36));
        titleBar.setBackground(BG_TITLEBAR);
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 41, 59)));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        leftPanel.setOpaque(false);

        JLabel iconLabel = new JLabel("F1") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(F1_RED);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth("F1")) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString("F1", x, y);
                g2.dispose();
            }
        };
        iconLabel.setPreferredSize(new Dimension(24, 22));
        leftPanel.add(iconLabel);

        JLabel titleLabel = new JLabel("F1 RACE ENGINEER");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        titleLabel.setForeground(TEXT_BRIGHT);
        leftPanel.add(titleLabel);

        titleBar.add(leftPanel, BorderLayout.WEST);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlPanel.setOpaque(false);
        controlPanel.add(createWindowIconButton("minimize", BTN_HOVER, e -> mainFrame.setExtendedState(Frame.ICONIFIED)));
        controlPanel.add(createWindowIconButton("maximize", BTN_HOVER, e -> toggleMaximize()));
        controlPanel.add(createWindowIconButton("close", BTN_CLOSE_HOVER, e -> minimizeToTray()));
        titleBar.add(controlPanel, BorderLayout.EAST);

        addDragBehavior(titleBar, mainFrame);

        titleBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) toggleMaximize();
            }
        });

        return titleBar;
    }

    private JButton createWindowIconButton(String iconType, Color hoverColor, ActionListener action) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getForeground());
                g2.setStroke(new BasicStroke(1.2f));

                int cx = getWidth() / 2;
                int cy = getHeight() / 2;

                switch (iconType) {
                    case "minimize": g2.drawLine(cx - 5, cy, cx + 5, cy); break;
                    case "maximize": g2.drawRect(cx - 5, cy - 5, 10, 10); break;
                    case "close":
                        g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                        g2.drawLine(cx + 5, cy - 5, cx - 5, cy + 5);
                        break;
                }
                g2.dispose();
            }
        };
        btn.setForeground(TEXT_DIM);
        btn.setBackground(BG_TITLEBAR);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        btn.setPreferredSize(new Dimension(46, 36));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(action);

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(hoverColor);
                if (hoverColor.equals(BTN_CLOSE_HOVER)) btn.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(BG_TITLEBAR);
                btn.setForeground(TEXT_DIM);
            }
        });

        return btn;
    }

    // ── Window behaviors ──────────────────────────────────────────────────────

    private void toggleMaximize() {
        if (isMaximized) {
            if (preMaximizeBounds != null) mainFrame.setBounds(preMaximizeBounds);
            isMaximized = false;
        } else {
            preMaximizeBounds = mainFrame.getBounds();
            GraphicsConfiguration config = mainFrame.getGraphicsConfiguration();
            Rectangle screenBounds = config.getBounds();
            Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(config);
            int x = screenBounds.x + screenInsets.left;
            int y = screenBounds.y + screenInsets.top;
            int w = screenBounds.width - screenInsets.left - screenInsets.right;
            int h = screenBounds.height - screenInsets.top - screenInsets.bottom;
            mainFrame.setBounds(new Rectangle(x, y, w, h));
            isMaximized = true;
        }
    }

    private void minimizeToTray() {
        mainFrame.setVisible(false);
    }

    private void showFromTray() {
        mainFrame.setVisible(true);
        mainFrame.toFront();
        mainFrame.requestFocus();
    }

    private void addDragBehavior(JPanel titleBar, JFrame frame) {
        final Point[] dragOffset = { null };
        titleBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) dragOffset[0] = e.getPoint();
            }
            @Override
            public void mouseReleased(MouseEvent e) { dragOffset[0] = null; }
        });
        titleBar.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset[0] != null) {
                    Point current = e.getLocationOnScreen();
                    frame.setLocation(current.x - dragOffset[0].x, current.y - dragOffset[0].y);
                }
            }
        });
    }

    private void addResizeBehavior(JFrame frame) {
        final int RESIZE_MARGIN = 6;

        frame.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int x = e.getX(), y = e.getY();
                int w = frame.getWidth(), h = frame.getHeight();
                boolean left = x < RESIZE_MARGIN, right = x > w - RESIZE_MARGIN;
                boolean top = y < RESIZE_MARGIN, bottom = y > h - RESIZE_MARGIN;

                if (bottom && right) frame.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                else if (bottom && left) frame.setCursor(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR));
                else if (top && right) frame.setCursor(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR));
                else if (top && left) frame.setCursor(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));
                else if (bottom) frame.setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
                else if (right) frame.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                else if (left) frame.setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
                else if (top) frame.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                else frame.setCursor(Cursor.getDefaultCursor());
            }
        });

        final Point[] resizeStart = { null };
        final Rectangle[] frameBounds = { null };

        frame.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (frame.getCursor().getType() != Cursor.DEFAULT_CURSOR) {
                    resizeStart[0] = e.getLocationOnScreen();
                    frameBounds[0] = frame.getBounds();
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                resizeStart[0] = null;
                frameBounds[0] = null;
            }
        });

        frame.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (resizeStart[0] == null || frameBounds[0] == null) return;
                Point current = e.getLocationOnScreen();
                int dx = current.x - resizeStart[0].x;
                int dy = current.y - resizeStart[0].y;
                Rectangle b = frameBounds[0];
                int cursorType = frame.getCursor().getType();
                Dimension minSize = frame.getMinimumSize();
                int newX = b.x, newY = b.y, newW = b.width, newH = b.height;

                if (cursorType == Cursor.E_RESIZE_CURSOR || cursorType == Cursor.SE_RESIZE_CURSOR || cursorType == Cursor.NE_RESIZE_CURSOR)
                    newW = Math.max(b.width + dx, minSize.width);
                if (cursorType == Cursor.S_RESIZE_CURSOR || cursorType == Cursor.SE_RESIZE_CURSOR || cursorType == Cursor.SW_RESIZE_CURSOR)
                    newH = Math.max(b.height + dy, minSize.height);
                if (cursorType == Cursor.W_RESIZE_CURSOR || cursorType == Cursor.SW_RESIZE_CURSOR || cursorType == Cursor.NW_RESIZE_CURSOR) {
                    newW = Math.max(b.width - dx, minSize.width);
                    newX = b.x + b.width - newW;
                }
                if (cursorType == Cursor.N_RESIZE_CURSOR || cursorType == Cursor.NW_RESIZE_CURSOR || cursorType == Cursor.NE_RESIZE_CURSOR) {
                    newH = Math.max(b.height - dy, minSize.height);
                    newY = b.y + b.height - newH;
                }
                frame.setBounds(newX, newY, newW, newH);
            }
        });
    }

    // ── System Tray ───────────────────────────────────────────────────────────

    private void setupSystemTray() {
        if (!SystemTray.isSupported()) return;
        try {
            SystemTray tray = SystemTray.getSystemTray();
            Image trayImage = createAppIcon(16);

            PopupMenu popup = new PopupMenu();

            MenuItem titleItem = new MenuItem("F1 Race Engineer");
            titleItem.setEnabled(false);
            popup.add(titleItem);
            popup.addSeparator();

            MenuItem openItem = new MenuItem("Open Dashboard");
            openItem.addActionListener(e -> showFromTray());
            popup.add(openItem);

            MenuItem updateItem = new MenuItem("Check for Updates");
            updateItem.addActionListener(e -> updateManager.checkForUpdatesAsync(false));
            popup.add(updateItem);

            popup.addSeparator();

            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> {
                if (cefApp != null) cefApp.dispose();
                System.exit(0);
            });
            popup.add(exitItem);

            TrayIcon trayIcon = new TrayIcon(trayImage, "F1 Telemetry Race Engineer", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> showFromTray());
            tray.add(trayIcon);
        } catch (Exception e) {
            System.err.println("Failed to set up System Tray: " + e.getMessage());
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private BufferedImage createAppIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(F1_RED);
        g2.fillRoundRect(0, 0, size, size, size / 4, size / 4);
        g2.setColor(Color.WHITE);
        int fontSize = (int) (size * 0.55);
        g2.setFont(new Font("Segoe UI", Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        String text = "F1";
        int x = (size - fm.stringWidth(text)) / 2;
        int y = (size + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(text, x, y);
        g2.dispose();
        return img;
    }
}
