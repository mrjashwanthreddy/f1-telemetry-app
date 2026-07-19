package com.f1telemetry.config;

import com.f1telemetry.F1TelemetryApplication;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Embedded Chromium desktop window — renders the F1 Telemetry dashboard
 * inside a native JFrame with a custom dark title bar, system tray integration,
 * and its own taskbar icon. No external browser is launched.
 */
@Component
public class AppGuiLauncher {

    private static final Logger logger = LoggerFactory.getLogger(AppGuiLauncher.class);

    @Value("${server.port:8080}")
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private com.f1telemetry.update.UpdateManager updateManager;

    private JFrame mainFrame;
    private CefApp cefApp;
    private CefClient cefClient;
    private CefBrowser cefBrowser;

    // Colors matching the F1 dashboard dark theme
    private static final Color BG_DARK = new Color(15, 23, 42);
    private static final Color BG_TITLEBAR = new Color(10, 15, 30);
    private static final Color F1_RED = new Color(229, 9, 20);
    private static final Color TEXT_DIM = new Color(148, 163, 184);
    private static final Color TEXT_BRIGHT = new Color(226, 232, 240);
    private static final Color BTN_HOVER = new Color(30, 41, 59);
    private static final Color BTN_CLOSE_HOVER = new Color(229, 9, 20);

    private boolean isMaximized = false;
    private Rectangle preMaximizeBounds = null;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        EventQueue.invokeLater(this::initGui);
    }

    private void initGui() {
        String url = "http://localhost:" + port;

        try {
            initCef();
            createMainWindow(url);
            setupSystemTray();

            // Dismiss the splash screen now that the main window is visible
            if (F1TelemetryApplication.splashScreen != null) {
                F1TelemetryApplication.splashScreen.hideSplash();
            }

            logger.info("Embedded desktop window launched successfully.");
            
            // Check for updates silently on startup
            updateManager.checkForUpdatesAsync(true);
        } catch (Exception e) {
            logger.error("Failed to initialize embedded browser. Falling back to system browser.", e);
            if (F1TelemetryApplication.splashScreen != null) {
                F1TelemetryApplication.splashScreen.hideSplash();
            }
            fallbackToSystemBrowser(url);
        }
    }

    private void initCef() throws UnsupportedPlatformException, CefInitializationException,
            IOException, InterruptedException {
        CefAppBuilder builder = new CefAppBuilder();

        // Set install directory for JCEF native binaries
        File installDir = new File(System.getProperty("user.home"), ".f1telemetry/jcef");
        builder.setInstallDir(installDir);
        logger.info("JCEF install directory: {}", installDir.getAbsolutePath());

        // Configure CEF settings
        builder.getCefSettings().windowless_rendering_enabled = false;
        builder.getCefSettings().locale = "en-US";

        // Allow popups (e.g. OAuth if ever needed) to open in system browser
        builder.addJcefArgs("--disable-gpu-shader-disk-cache");

        cefApp = builder.build();
        cefClient = cefApp.createClient();

        // Allow popups to open in user's default system browser (useful for Netbanking/3DS redirects)
        cefClient.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(CefBrowser browser, org.cef.browser.CefFrame frame,
                    String target_url, String target_frame_name) {
                if (target_url != null && (target_url.startsWith("http://") || target_url.startsWith("https://"))) {
                    if (java.awt.Desktop.isDesktopSupported()) {
                        try {
                            java.awt.Desktop.getDesktop().browse(new java.net.URI(target_url));
                        } catch (Exception e) {
                            logger.error("Failed to open popup URL in system browser: {}", target_url, e);
                        }
                    }
                    return true; // Return true to prevent JCEF from opening or redirecting the main window
                }
                return false; // Return false to let JCEF handle internal popups (like about:blank)
            }
        });

        logger.info("JCEF/Chromium engine initialized successfully.");
    }

    private void createMainWindow(String url) {
        mainFrame = new JFrame();
        mainFrame.setUndecorated(true); // Remove OS title bar for custom one
        mainFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        mainFrame.setSize(1280, 800);
        mainFrame.setMinimumSize(new Dimension(900, 600));
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setBackground(BG_DARK);

        // Set custom app icon (appears in taskbar)
        mainFrame.setIconImage(createAppIcon(32));

        // Layout: custom title bar at top, browser fills center
        mainFrame.setLayout(new BorderLayout(0, 0));
        mainFrame.add(createTitleBar(), BorderLayout.NORTH);

        // Create the embedded Chromium browser
        cefBrowser = cefClient.createBrowser(url, false, false);
        java.awt.Component browserUI = cefBrowser.getUIComponent();
        mainFrame.add(browserUI, BorderLayout.CENTER);

        // Handle window close: minimize to tray instead of exiting
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                minimizeToTray();
            }
        });

        // Enable window resizing on undecorated frame
        addResizeBehavior(mainFrame);

        mainFrame.setVisible(true);
        mainFrame.toFront();

        logger.info("Main application window created and visible.");
    }

    private JPanel createTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setPreferredSize(new Dimension(0, 36));
        titleBar.setBackground(BG_TITLEBAR);
        titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 41, 59)));

        // Left side: F1 icon + title
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

        // Right side: window control buttons
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controlPanel.setOpaque(false);

        controlPanel
                .add(createWindowIconButton("minimize", BTN_HOVER, e -> mainFrame.setExtendedState(Frame.ICONIFIED)));
        controlPanel.add(createWindowIconButton("maximize", BTN_HOVER, e -> toggleMaximize()));
        controlPanel.add(createWindowIconButton("close", BTN_CLOSE_HOVER, e -> minimizeToTray()));

        titleBar.add(controlPanel, BorderLayout.EAST);

        // Enable dragging the window by the title bar
        addDragBehavior(titleBar, mainFrame);

        // Double-click title bar to maximize/restore
        titleBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2)
                    toggleMaximize();
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
                    case "minimize":
                        // Horizontal line (─)
                        g2.drawLine(cx - 5, cy, cx + 5, cy);
                        break;
                    case "maximize":
                        // Square outline (□)
                        g2.drawRect(cx - 5, cy - 5, 10, 10);
                        break;
                    case "close":
                        // X shape (✕)
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
                if (hoverColor.equals(BTN_CLOSE_HOVER))
                    btn.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(BG_TITLEBAR);
                btn.setForeground(TEXT_DIM);
            }
        });

        return btn;
    }

    private void toggleMaximize() {
        if (isMaximized) {
            // Restore to previous windowed bounds
            if (preMaximizeBounds != null) {
                mainFrame.setBounds(preMaximizeBounds);
            }
            isMaximized = false;
        } else {
            // Save current bounds
            preMaximizeBounds = mainFrame.getBounds();
            
            // Get screen bounds and taskbar insets of the screen where the frame is currently located
            GraphicsConfiguration config = mainFrame.getGraphicsConfiguration();
            Rectangle screenBounds = config.getBounds();
            Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(config);
            
            // Subtract screen insets (taskbar) from bounds
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
        logger.info("Window minimized to system tray.");
    }

    private void showFromTray() {
        mainFrame.setVisible(true);
        mainFrame.toFront();
        mainFrame.requestFocus();
    }

    // ── Drag behavior for custom title bar ────────────────────────────────────

    private void addDragBehavior(JPanel titleBar, JFrame frame) {
        final Point[] dragOffset = { null };

        titleBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0) {
                    dragOffset[0] = e.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragOffset[0] = null;
            }
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

    // ── Resize behavior for undecorated frame ─────────────────────────────────

    private void addResizeBehavior(JFrame frame) {
        final int RESIZE_MARGIN = 6;
        ComponentAdapter resizeAdapter = new ComponentAdapter() {
        };

        frame.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int x = e.getX(), y = e.getY();
                int w = frame.getWidth(), h = frame.getHeight();
                boolean left = x < RESIZE_MARGIN, right = x > w - RESIZE_MARGIN;
                boolean top = y < RESIZE_MARGIN, bottom = y > h - RESIZE_MARGIN;

                if (bottom && right)
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
                else if (bottom && left)
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR));
                else if (top && right)
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR));
                else if (top && left)
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));
                else if (bottom)
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
                else if (right)
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                else if (left)
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
                else if (top)
                    frame.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                else
                    frame.setCursor(Cursor.getDefaultCursor());
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
                if (resizeStart[0] == null || frameBounds[0] == null)
                    return;
                Point current = e.getLocationOnScreen();
                int dx = current.x - resizeStart[0].x;
                int dy = current.y - resizeStart[0].y;
                Rectangle b = frameBounds[0];
                int cursorType = frame.getCursor().getType();
                Dimension minSize = frame.getMinimumSize();

                int newX = b.x, newY = b.y, newW = b.width, newH = b.height;

                if (cursorType == Cursor.E_RESIZE_CURSOR || cursorType == Cursor.SE_RESIZE_CURSOR
                        || cursorType == Cursor.NE_RESIZE_CURSOR)
                    newW = Math.max(b.width + dx, minSize.width);
                if (cursorType == Cursor.S_RESIZE_CURSOR || cursorType == Cursor.SE_RESIZE_CURSOR
                        || cursorType == Cursor.SW_RESIZE_CURSOR)
                    newH = Math.max(b.height + dy, minSize.height);
                if (cursorType == Cursor.W_RESIZE_CURSOR || cursorType == Cursor.SW_RESIZE_CURSOR
                        || cursorType == Cursor.NW_RESIZE_CURSOR) {
                    newW = Math.max(b.width - dx, minSize.width);
                    newX = b.x + b.width - newW;
                }
                if (cursorType == Cursor.N_RESIZE_CURSOR || cursorType == Cursor.NW_RESIZE_CURSOR
                        || cursorType == Cursor.NE_RESIZE_CURSOR) {
                    newH = Math.max(b.height - dy, minSize.height);
                    newY = b.y + b.height - newH;
                }

                frame.setBounds(newX, newY, newW, newH);
            }
        });
    }

    // ── System Tray ───────────────────────────────────────────────────────────

    private void setupSystemTray() {
        if (!SystemTray.isSupported()) {
            logger.warn("System Tray is not supported on this platform.");
            return;
        }

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

            MenuItem logsItem = new MenuItem("View Logs");
            logsItem.addActionListener(e -> openLogFile());
            popup.add(logsItem);

            MenuItem updateItem = new MenuItem("Check for Updates");
            updateItem.addActionListener(e -> updateManager.checkForUpdatesAsync(false));
            popup.add(updateItem);

            popup.addSeparator();

            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> {
                logger.info("Exiting application from system tray...");
                if (cefApp != null)
                    cefApp.dispose();
                System.exit(0);
            });
            popup.add(exitItem);

            TrayIcon trayIcon = new TrayIcon(trayImage, "F1 Telemetry Race Engineer", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> showFromTray());

            tray.add(trayIcon);
            logger.info("System Tray icon registered successfully.");

        } catch (Exception e) {
            logger.error("Failed to set up System Tray: {}", e.getMessage(), e);
        }
    }

    // ── Utility methods ───────────────────────────────────────────────────────

    private BufferedImage createAppIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Red rounded background
        g2.setColor(F1_RED);
        g2.fillRoundRect(0, 0, size, size, size / 4, size / 4);

        // White "F1" text
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

    private void fallbackToSystemBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception e) {
            logger.error("System browser fallback also failed: {}", e.getMessage(), e);
        }
    }

    private void openLogFile() {
        try {
            File logFile = new File("logs/f1-telemetry.log");
            if (logFile.exists() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(logFile);
            } else if (!logFile.exists()) {
                logger.warn("Log file does not exist yet.");
            }
        } catch (IOException e) {
            logger.error("Failed to open log file: {}", e.getMessage(), e);
        }
    }
}
