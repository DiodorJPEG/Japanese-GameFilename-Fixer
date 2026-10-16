import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

public class FilenameFixerGUI extends JFrame {

    private JTextField pathField;
    private JComboBox<String> sourceEncCombo;
    private JComboBox<String> targetEncCombo;
    private JCheckBox dryRunCheck;
    private JCheckBox replaceIllegalCheck; // Новая галочка
    private JTextArea logArea;
    private JButton startButton;

    public FilenameFixerGUI() {
        setTitle("Исправление кодировки имен файлов (v2.0)");
        setSize(750, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Верхняя панель (Настройки)
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Выбор папки
        JPanel folderPanel = new JPanel(new BorderLayout(5, 5));
        folderPanel.add(new JLabel("Папка с игрой:"), BorderLayout.WEST);
        pathField = new JTextField();
        pathField.setEditable(false);
        folderPanel.add(pathField, BorderLayout.CENTER);
        JButton browseButton = new JButton("Обзор...");
        browseButton.addActionListener(e -> chooseFolder());
        folderPanel.add(browseButton, BorderLayout.EAST);
        topPanel.add(folderPanel);
        topPanel.add(Box.createVerticalStrut(10));

        // Настройки кодировок
        JPanel encPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        encPanel.add(new JLabel("Из какой кодировки:"));
        sourceEncCombo = new JComboBox<>(new String[]{"windows-1251", "CP866", "KOI8-R", "ISO-8859-1"});
        encPanel.add(sourceEncCombo);

        encPanel.add(new JLabel("В какую:"));
        targetEncCombo = new JComboBox<>(new String[]{"Shift_JIS", "EUC-JP", "UTF-8"});
        encPanel.add(targetEncCombo);
        topPanel.add(encPanel);
        topPanel.add(Box.createVerticalStrut(10));

        // Кнопки управления
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        dryRunCheck = new JCheckBox("Сухой прогон", true);
        actionPanel.add(dryRunCheck);

        replaceIllegalCheck = new JCheckBox("Заменять запрещённые символы Windows (|, ?, * и т.д.) на _", true);
        actionPanel.add(replaceIllegalCheck);

        startButton = new JButton("Запустить");
        startButton.setFont(new Font("Arial", Font.BOLD, 14));
        startButton.addActionListener(e -> startProcessing());
        actionPanel.add(startButton);

        JButton clearButton = new JButton("Очистить лог");
        clearButton.addActionListener(e -> logArea.setText(""));
        actionPanel.add(clearButton);

        topPanel.add(actionPanel);

        add(topPanel, BorderLayout.NORTH);

        // Центральная панель (Лог)
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Результаты работы"));
        add(scrollPane, BorderLayout.CENTER);

        // Предупреждение
        JLabel warningLabel = new JLabel("ВНИМАНИЕ: Не запускайте повторно на уже исправленных файлах. Заранее сделайте бэкап");
        warningLabel.setForeground(Color.RED);
        warningLabel.setHorizontalAlignment(SwingConstants.CENTER);
        warningLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        add(warningLabel, BorderLayout.SOUTH);
    }

    private void chooseFolder() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Выберите папку с игрой");

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            pathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void startProcessing() {
        String folderPath = pathField.getText();
        if (folderPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Пожалуйста, выберите папку!", "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File rootDir = new File(folderPath);
        String sourceEnc = (String) sourceEncCombo.getSelectedItem();
        String targetEnc = (String) targetEncCombo.getSelectedItem();
        boolean isDryRun = dryRunCheck.isSelected();
        boolean replaceIllegal = replaceIllegalCheck.isSelected();

        startButton.setEnabled(false);
        logArea.setText("");
        log("Начало сканирования: " + rootDir.getAbsolutePath());
        log("Кодировки: " + sourceEnc + " -> " + targetEnc);
        log("Режим: " + (isDryRun ? "СУХОЙ ПРОГОН" : "РЕАЛЬНОЕ ПЕРЕИМЕНОВАНИЕ"));
        log("--------------------------------------------------");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            int countFiles = 0;
            int countRenamed = 0;
            int countErrors = 0;

            @Override
            protected Void doInBackground() {
                processDirectory(rootDir, sourceEnc, targetEnc, isDryRun, replaceIllegal);
                return null;
            }

            private void processDirectory(File dir, String src, String tgt, boolean dry, boolean replaceIll) {
                File[] files = dir.listFiles();
                if (files == null) return;

                for (File file : files) {
                    if (file.isDirectory()) {
                        processDirectory(file, src, tgt, dry, replaceIll);
                    } else {
                        countFiles++;
                        renameFile(file, src, tgt, dry, replaceIll);
                    }
                }
            }

            private void renameFile(File file, String src, String tgt, boolean dry, boolean replaceIll) {
                String oldName = file.getName();
                try {
                    byte[] bytes = oldName.getBytes(src);
                    String newName = new String(bytes, tgt);

                    // Проверка на запрещённые символы Windows
                    boolean hasIllegal = newName.matches(".*[\\\\/:*?\"<>|].*");
                    if (hasIllegal) {
                        if (replaceIll) {
                            newName = newName.replaceAll("[\\\\/:*?\"<>|]", "_");
                        } else {
                            publish("  [ПРОПУСК] " + oldName + " -> содержит запрещённые символы: " + newName);
                            countErrors++;
                            return;
                        }
                    }

                    if (!oldName.equals(newName)) {
                        if (dry) {
                            publish("  [СУХОЙ] " + oldName + "  ->  " + newName);
                            countRenamed++;
                        } else {
                            File newFile = new File(file.getParentFile(), newName);
                            if (newFile.exists()) {
                                publish("  [КОНФЛИКТ] " + newName + " уже существует! Пропущено: " + oldName);
                                countErrors++;
                            } else if (file.renameTo(newFile)) {
                                publish("  [OK] " + oldName + "  ->  " + newName);
                                countRenamed++;
                            } else {
                                publish("  [ОШИБКА] Не удалось переименовать: " + oldName + " (возможно, файл занят)");
                                countErrors++;
                            }
                        }
                    }
                } catch (Exception e) {
                    publish("  [ОШИБКА] " + oldName + " : " + e.getMessage());
                    countErrors++;
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    logArea.append(msg + "\n");
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                }
            }

            @Override
            protected void done() {
                log("--------------------------------------------------");
                log("Всего файлов проверено: " + countFiles);
                log("Файлов, требующих переименования: " + countRenamed);
                log("Ошибок/Пропущено: " + countErrors);
                log("ГОТОВО!");
                startButton.setEnabled(true);
                JOptionPane.showMessageDialog(FilenameFixerGUI.this,
                        "Обработка завершена!\nПроверьте лог.",
                        "Успех", JOptionPane.INFORMATION_MESSAGE);
            }
        };

        worker.execute();
    }

    private void log(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            new FilenameFixerGUI().setVisible(true);
        });
    }
}