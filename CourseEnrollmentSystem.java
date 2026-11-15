import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Course Enrollment Management System (Java Swing).
 * Records are kept in memory and saved to enrollments.json after every change.
 */
public class CourseEnrollmentSystem extends JFrame {

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------
    private static final Path DATA_FILE = Paths.get("enrollments.json");
    private static final String[] COURSES = {
        "B.Tech CSE", "B.Tech ECE", "B.Tech Mechanical", "B.Tech Civil",
        "BCA", "MCA", "B.Sc Data Science", "MBA"
    };
    private static final int SEM_MIN = 1;
    private static final int SEM_MAX = 8;

    // Colour pairs: index 0 = light theme, index 1 = dark theme
    private static final Color[] BG     = {new Color(0xF4F5F7), new Color(0x18181B)};
    private static final Color[] CARD   = {Color.WHITE,         new Color(0x27272A)};
    private static final Color[] BORDER = {new Color(0xD9DCE1), new Color(0x3F3F46)};
    private static final Color[] TEXT   = {new Color(0x1F2328), new Color(0xF4F4F5)};
    private static final Color[] MUTED  = {new Color(0x6B7280), new Color(0xA1A1AA)};
    private static final Color[] FIELD  = {Color.WHITE,         new Color(0x18181B)};
    private static final Color[] SELECT = {new Color(0xDBEAFE), new Color(0x1E3A5F)};
    private static final Color[] OK     = {new Color(0x15803D), new Color(0x4ADE80)};
    private static final Color[] ERR    = {new Color(0xB91C1C), new Color(0xF87171)};

    // ------------------------------------------------------------------
    // Data model
    // ------------------------------------------------------------------
    private static final class Enrollment {
        final String name;
        final String course;
        final int semester;
        final String mode;

        Enrollment(String name, String course, int semester, String mode) {
            this.name = name;
            this.course = course;
            this.semester = semester;
            this.mode = mode;
        }
    }

    private static final class Validated {
        final String id;
        final Enrollment record;

        Validated(String id, Enrollment record) {
            this.id = id;
            this.record = record;
        }
    }

    // Records keyed by Student ID (TreeMap keeps them sorted by ID)
    private final Map<String, Enrollment> data = new TreeMap<>();
    // Student IDs in the same order as the rows currently shown in the list
    private final List<String> visibleIds = new ArrayList<>();

    // ------------------------------------------------------------------
    // UI state
    // ------------------------------------------------------------------
    private boolean dark;
    private int statusKind = 0;            // 0 = neutral, 1 = success, 2 = error
    private boolean suppressSelect = false;

    private JTextField txtId, txtName, txtSem, txtSearch;
    private JComboBox<String> cmbCourse;
    private JRadioButton rbRegular, rbOnline;
    private DefaultListModel<String> listModel;
    private JList<String> list;
    private JLabel lblCount, lblStatus;
    private JCheckBox chkDark;

    public CourseEnrollmentSystem() {
        super("Course Enrollment Management System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        dark = systemIsDark();
        load();
        buildUi();
        refreshList(null);
        applyTheme(getContentPane());
        notify("Ready. " + data.size() + " record(s) loaded.", true);

        setSize(960, 580);
        setMinimumSize(new Dimension(880, 520));
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------
    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.putClientProperty("role", "bg");
        root.setBorder(new EmptyBorder(16, 20, 12, 20));
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel body = plain(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weighty = 1;
        g.gridx = 0;
        g.weightx = 0;
        g.insets = new Insets(0, 0, 0, 14);
        body.add(buildForm(), g);
        g.gridx = 1;
        g.weightx = 1;
        g.insets = new Insets(0, 0, 0, 0);
        body.add(buildRecords(), g);
        root.add(body, BorderLayout.CENTER);

        root.add(buildStatus(), BorderLayout.SOUTH);
    }

    private JPanel buildHeader() {
        JPanel header = plain(new BorderLayout());

        JPanel titles = plain(new GridLayout(2, 1, 0, 2));
        JLabel title = new JLabel("Course Enrollment Management System");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("Enroll, update, search and delete student records");
        subtitle.putClientProperty("role", "muted");
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        chkDark = new JCheckBox("Dark mode", dark);
        chkDark.addActionListener(e -> {
            dark = chkDark.isSelected();
            applyTheme(getContentPane());
            getContentPane().repaint();
        });
        header.add(chkDark, BorderLayout.EAST);
        return header;
    }

    private JPanel buildForm() {
        JPanel card = card(new GridBagLayout());
        card.setPreferredSize(new Dimension(340, 400));

        txtId = new JTextField();
        txtName = new JTextField();
        txtSem = new JTextField();
        cmbCourse = new JComboBox<>(COURSES);
        rbRegular = new JRadioButton("Regular", true);
        rbOnline = new JRadioButton("Online");
        ButtonGroup group = new ButtonGroup();
        group.add(rbRegular);
        group.add(rbOnline);

        JPanel modes = plain(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modes.add(rbRegular);
        modes.add(Box.createHorizontalStrut(12));
        modes.add(rbOnline);

        int[] row = {0};

        JLabel heading = new JLabel("Enrollment form");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 15f));
        GridBagConstraints hg = new GridBagConstraints();
        hg.gridx = 0;
        hg.gridy = row[0]++;
        hg.anchor = GridBagConstraints.WEST;
        card.add(heading, hg);

        addField(card, row, "Student ID", txtId);
        addField(card, row, "Student Name", txtName);
        addField(card, row, "Course", cmbCourse);
        addField(card, row, "Semester (" + SEM_MIN + "-" + SEM_MAX + ")", txtSem);
        addField(card, row, "Study Mode", modes);

        JButton btnEnroll = new JButton("Enroll");
        JButton btnUpdate = new JButton("Update");
        JButton btnDelete = new JButton("Delete");
        JButton btnClear = new JButton("Clear");
        btnEnroll.addActionListener(e -> doEnroll());
        btnUpdate.addActionListener(e -> doUpdate());
        btnDelete.addActionListener(e -> doDelete());
        btnClear.addActionListener(e -> {
            clearForm();
            notify("Form cleared.", true);
        });

        JPanel buttons = plain(new GridLayout(2, 2, 8, 8));
        buttons.add(btnEnroll);
        buttons.add(btnUpdate);
        buttons.add(btnDelete);
        buttons.add(btnClear);

        GridBagConstraints bg = new GridBagConstraints();
        bg.gridx = 0;
        bg.gridy = row[0]++;
        bg.fill = GridBagConstraints.HORIZONTAL;
        bg.weightx = 1;
        bg.insets = new Insets(18, 0, 0, 0);
        card.add(buttons, bg);

        // Filler pushes everything to the top
        GridBagConstraints fg = new GridBagConstraints();
        fg.gridx = 0;
        fg.gridy = row[0]++;
        fg.weighty = 1;
        fg.fill = GridBagConstraints.VERTICAL;
        card.add(plain(new FlowLayout()), fg);

        return card;
    }

    private void addField(JPanel panel, int[] row, String label, JComponent field) {
        int top = 10;
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = row[0]++;
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.insets = new Insets(top, 0, 3, 0);
        JLabel lbl = new JLabel(label);
        lbl.putClientProperty("role", "muted");
        panel.add(lbl, g);

        g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = row[0]++;
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        panel.add(field, g);
    }

    private JPanel buildRecords() {
        JPanel card = card(new BorderLayout(0, 10));

        lblCount = new JLabel("Records");
        lblCount.setFont(lblCount.getFont().deriveFont(Font.BOLD, 15f));

        txtSearch = new JTextField();
        txtSearch.setToolTipText("Type a Student ID and press Enter");
        txtSearch.addActionListener(e -> doSearch());
        JButton btnSearch = new JButton("Search");
        JButton btnAll = new JButton("Show all");
        btnSearch.addActionListener(e -> doSearch());
        btnAll.addActionListener(e -> showAll());

        JPanel searchButtons = plain(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        searchButtons.add(btnSearch);
        searchButtons.add(btnAll);

        JLabel searchLabel = new JLabel("Student ID:");
        searchLabel.putClientProperty("role", "muted");

        JPanel searchRow = plain(new BorderLayout(8, 0));
        searchRow.add(searchLabel, BorderLayout.WEST);
        searchRow.add(txtSearch, BorderLayout.CENTER);
        searchRow.add(searchButtons, BorderLayout.EAST);

        JPanel top = plain(new GridLayout(2, 1, 0, 8));
        top.add(lblCount);
        top.add(searchRow);
        card.add(top, BorderLayout.NORTH);

        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);

        JLabel cols = new JLabel(rowText("Student ID", "Name", "Course", "Sem", "Mode"));
        cols.setFont(mono);
        cols.putClientProperty("role", "muted");
        cols.setBorder(new EmptyBorder(0, 3, 0, 0));

        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        list.setFont(mono);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || suppressSelect) return;
            int i = list.getSelectedIndex();
            if (i >= 0 && i < visibleIds.size()) {
                String id = visibleIds.get(i);
                populateForm(id, data.get(id));
            }
        });

        JPanel listArea = plain(new BorderLayout(0, 4));
        listArea.add(cols, BorderLayout.NORTH);
        listArea.add(new JScrollPane(list), BorderLayout.CENTER);
        card.add(listArea, BorderLayout.CENTER);

        return card;
    }

    private JPanel buildStatus() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.putClientProperty("role", "bg");
        bar.setBorder(new EmptyBorder(2, 2, 0, 2));
        lblStatus = new JLabel("Ready.");
        lblStatus.putClientProperty("role", "status");
        bar.add(lblStatus, BorderLayout.WEST);
        return bar;
    }

    private JPanel card(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.putClientProperty("role", "card");
        return p;
    }

    private JPanel plain(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    // ------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------
    private void applyTheme(Component c) {
        int t = dark ? 1 : 0;

        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            Object role = jc.getClientProperty("role");

            if (c instanceof JPanel) {
                if ("card".equals(role)) {
                    jc.setBackground(CARD[t]);
                    jc.setBorder(new CompoundBorder(
                            new LineBorder(BORDER[t], 1, true),
                            new EmptyBorder(14, 16, 14, 16)));
                } else if ("bg".equals(role)) {
                    jc.setBackground(BG[t]);
                }
            } else if (c instanceof JLabel) {
                if ("status".equals(role)) {
                    setStatusColor();
                } else {
                    jc.setForeground("muted".equals(role) ? MUTED[t] : TEXT[t]);
                }
            } else if (c instanceof JTextField) {
                jc.setBackground(FIELD[t]);
                jc.setForeground(TEXT[t]);
                ((JTextField) c).setCaretColor(TEXT[t]);
            } else if (c instanceof JRadioButton || c instanceof JCheckBox) {
                jc.setOpaque(false);
                jc.setForeground(TEXT[t]);
            } else if (c instanceof JComboBox) {
                jc.setBackground(FIELD[t]);
                jc.setForeground(TEXT[t]);
            } else if (c instanceof JList) {
                jc.setBackground(FIELD[t]);
                jc.setForeground(TEXT[t]);
                ((JList<?>) c).setSelectionBackground(SELECT[t]);
                ((JList<?>) c).setSelectionForeground(TEXT[t]);
            } else if (c instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane) c;
                sp.getViewport().setBackground(FIELD[t]);
                sp.setBorder(new LineBorder(BORDER[t]));
            }
        }

        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                applyTheme(child);
            }
        }
    }

    private void setStatusColor() {
        int t = dark ? 1 : 0;
        if (statusKind == 1) lblStatus.setForeground(OK[t]);
        else if (statusKind == 2) lblStatus.setForeground(ERR[t]);
        else lblStatus.setForeground(MUTED[t]);
    }

    /** Detects macOS dark mode; other systems start in light mode (use the toggle). */
    private static boolean systemIsDark() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) return false;
        try {
            Process p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                return line != null && line.trim().equalsIgnoreCase("Dark");
            }
        } catch (Exception ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Status bar
    // ------------------------------------------------------------------
    private void notify(String message, boolean ok) {
        lblStatus.setText(message);
        statusKind = ok ? 1 : 2;
        setStatusColor();
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------
    private Validated fail(String message) {
        JOptionPane.showMessageDialog(this, message, "Validation Error", JOptionPane.ERROR_MESSAGE);
        notify(message, false);
        return null;
    }

    private Validated validate() {
        String id = txtId.getText().trim().toUpperCase();
        if (id.isEmpty()) return fail("Student ID is required.");

        String name = txtName.getText().trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) return fail("Student Name is required.");
        if (!name.matches("[\\p{L} .'\\-]+")) {
            return fail("Name may contain only letters, spaces and . ' -");
        }

        int sem;
        try {
            sem = Integer.parseInt(txtSem.getText().trim());
        } catch (NumberFormatException ex) {
            return fail("Semester must be a whole number from " + SEM_MIN + " to " + SEM_MAX + ".");
        }
        if (sem < SEM_MIN || sem > SEM_MAX) {
            return fail("Semester must be between " + SEM_MIN + " and " + SEM_MAX + ".");
        }

        Enrollment record = new Enrollment(
                toTitle(name),
                (String) cmbCourse.getSelectedItem(),
                sem,
                rbOnline.isSelected() ? "Online" : "Regular");
        return new Validated(id, record);
    }

    private static String toTitle(String s) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) {
                sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upper = false;
            } else {
                sb.append(c);
                upper = true;
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------
    private void doEnroll() {
        Validated v = validate();
        if (v == null) return;

        if (data.containsKey(v.id)) {
            String msg = "Student ID " + v.id + " already exists. Use Update to change it.";
            JOptionPane.showMessageDialog(this, msg, "Duplicate Entry", JOptionPane.WARNING_MESSAGE);
            notify(msg, false);
            return;
        }

        data.put(v.id, v.record);
        if (!save()) return;
        txtSearch.setText("");
        refreshList(null);
        clearForm();
        notify("Enrolled " + v.record.name + " (" + v.id + ").", true);
    }

    private void doUpdate() {
        Validated v = validate();
        if (v == null) return;

        if (!data.containsKey(v.id)) {
            String msg = "Student ID " + v.id + " not found. Enroll the student first.";
            JOptionPane.showMessageDialog(this, msg, "Not Found", JOptionPane.ERROR_MESSAGE);
            notify(msg, false);
            return;
        }

        data.put(v.id, v.record);
        if (!save()) return;
        refreshList(null);
        notify("Updated " + v.record.name + " (" + v.id + ").", true);
    }

    private void doDelete() {
        String id = txtId.getText().trim().toUpperCase();
        if (id.isEmpty()) {
            String msg = "Enter or select a Student ID to delete.";
            JOptionPane.showMessageDialog(this, msg, "Input Required", JOptionPane.WARNING_MESSAGE);
            notify(msg, false);
            return;
        }

        Enrollment existing = data.get(id);
        if (existing == null) {
            String msg = "Student ID " + id + " not found.";
            JOptionPane.showMessageDialog(this, msg, "Not Found", JOptionPane.ERROR_MESSAGE);
            notify(msg, false);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete enrollment for " + existing.name + " (" + id + ")?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        data.remove(id);
        if (!save()) return;
        txtSearch.setText("");
        refreshList(null);
        clearForm();
        notify("Deleted record " + id + ".", true);
    }

    private void doSearch() {
        String id = txtSearch.getText().trim().toUpperCase();
        if (id.isEmpty()) {
            showAll();
            return;
        }

        Enrollment e = data.get(id);
        if (e == null) {
            notify("No record found for ID " + id + ".", false);
            return;
        }

        refreshList(Collections.singletonList(id));
        list.setSelectedIndex(0);
        populateForm(id, e);
        notify("Found " + e.name + " (" + id + ").", true);
    }

    private void showAll() {
        txtSearch.setText("");
        refreshList(null);
        notify("Showing all " + data.size() + " record(s).", true);
    }

    // ------------------------------------------------------------------
    // List and form helpers
    // ------------------------------------------------------------------
    private void refreshList(List<String> ids) {
        suppressSelect = true;
        visibleIds.clear();
        listModel.clear();

        List<String> show = (ids != null) ? ids : new ArrayList<>(data.keySet());
        for (String id : show) {
            Enrollment e = data.get(id);
            if (e == null) continue;
            visibleIds.add(id);
            listModel.addElement(rowText(id, e.name, e.course, String.valueOf(e.semester), e.mode));
        }
        suppressSelect = false;

        if (ids == null) {
            lblCount.setText("Records (" + data.size() + ")");
        } else {
            lblCount.setText("Showing " + visibleIds.size() + " of " + data.size());
        }
    }

    private static String rowText(String id, String name, String course, String sem, String mode) {
        return String.format("%-16s  %-20s  %-18s  %-3s  %-7s",
                fit(id, 16), fit(name, 20), fit(course, 18), fit(sem, 3), fit(mode, 7));
    }

    private static String fit(String s, int width) {
        return s.length() > width ? s.substring(0, width - 1) + "~" : s;
    }

    private void populateForm(String id, Enrollment e) {
        if (e == null) return;
        txtId.setText(id);
        txtName.setText(e.name);
        cmbCourse.setSelectedItem(e.course);
        txtSem.setText(String.valueOf(e.semester));
        if ("Online".equals(e.mode)) rbOnline.setSelected(true);
        else rbRegular.setSelected(true);
    }

    private void clearForm() {
        txtId.setText("");
        txtName.setText("");
        cmbCourse.setSelectedIndex(0);
        txtSem.setText("");
        rbRegular.setSelected(true);
        list.clearSelection();
    }

    // ------------------------------------------------------------------
    // Persistence (plain JSON, no external libraries)
    // ------------------------------------------------------------------
    private void load() {
        data.clear();
        if (!Files.exists(DATA_FILE)) return;
        try {
            String text = new String(Files.readAllBytes(DATA_FILE), StandardCharsets.UTF_8);
            Object root = new Json(text).parse();
            if (!(root instanceof Map)) return;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) root).entrySet()) {
                if (!(entry.getValue() instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) entry.getValue();
                Object sem = m.get("semester");
                data.put(String.valueOf(entry.getKey()), new Enrollment(
                        String.valueOf(m.get("name")),
                        String.valueOf(m.get("course")),
                        (sem instanceof Number) ? ((Number) sem).intValue() : SEM_MIN,
                        String.valueOf(m.get("mode"))));
            }
        } catch (Exception ex) {
            data.clear();   // missing or corrupt file: start empty
        }
    }

    private boolean save() {
        StringBuilder sb = new StringBuilder("{\n");
        int n = 0;
        for (Map.Entry<String, Enrollment> entry : data.entrySet()) {
            Enrollment e = entry.getValue();
            sb.append("  ").append(quote(entry.getKey())).append(": {\n");
            sb.append("    \"name\": ").append(quote(e.name)).append(",\n");
            sb.append("    \"course\": ").append(quote(e.course)).append(",\n");
            sb.append("    \"semester\": ").append(e.semester).append(",\n");
            sb.append("    \"mode\": ").append(quote(e.mode)).append("\n");
            sb.append("  }").append(++n < data.size() ? ",\n" : "\n");
        }
        sb.append("}\n");
        try {
            Files.write(DATA_FILE, sb.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException ex) {
            String msg = "Could not save " + DATA_FILE + ": " + ex.getMessage();
            JOptionPane.showMessageDialog(this, msg, "Save Error", JOptionPane.ERROR_MESSAGE);
            notify(msg, false);
            return false;
        }
    }

    private static String quote(String v) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : v.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Minimal JSON reader: handles objects, strings and integers, which is all this app writes. */
    private static final class Json {
        private final String s;
        private int i = 0;

        Json(String s) {
            this.s = s;
        }

        Object parse() {
            Object v = value();
            ws();
            return v;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private void expect(char c) {
            ws();
            if (i >= s.length() || s.charAt(i) != c) {
                throw new IllegalStateException("Expected '" + c + "' at " + i);
            }
            i++;
        }

        private Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '"') return string();
            return number();
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            expect('{');
            ws();
            if (s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                String key = string();
                expect(':');
                m.put(key, value());
                ws();
                if (s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                expect('}');
                return m;
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char n = s.charAt(i++);
                    switch (n) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                            break;
                        default: sb.append(n);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Integer number() {
            int start = i;
            while (i < s.length() && "-0123456789".indexOf(s.charAt(i)) >= 0) i++;
            return Integer.parseInt(s.substring(start, i));
        }
    }

    // ------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CourseEnrollmentSystem().setVisible(true));
    }
}
