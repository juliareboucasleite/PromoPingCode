package org.example.controllers;

import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.geometry.Point2D;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.reactfx.Subscription;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorController {

    private static final String THEME_LIGHT = "/org/example/editor-light.css";
    private static final String THEME_DARK = "/org/example/editor-dark.css";
    private static final String APP_NAME = "PromoPing CodePad";
    private static final String UPDATE_API = "https://api.github.com/repos/juliareboucasleite/PromoPing-CodePad/releases/latest";
    private static final String RELEASES_URL = "https://github.com/juliareboucasleite/PromoPing-CodePad/releases";
    private static final String RELEASE_ASSET_NAME = "PromoPingCodePad-Setup.exe";
    private static final String DRAFTS_DIR = "PromoPingCodePad";
    private static final String LEGACY_DRAFTS_DIR = "CodePad";
    private static final String DRAFTS_FILE = "drafts.dat";
    private static final int AUTO_SAVE_SECONDS = 30;
    private static final double BASE_FONT_SIZE = 13.0;
    private static final double MIN_FONT_SIZE = 10.0;
    private static final double MAX_FONT_SIZE = 24.0;
    private static final byte[] BOM_UTF8 = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] BOM_UTF16_LE = new byte[]{(byte) 0xFF, (byte) 0xFE};
    private static final byte[] BOM_UTF16_BE = new byte[]{(byte) 0xFE, (byte) 0xFF};

    private enum FileEncoding {
        ANSI("ANSI", java.nio.charset.Charset.defaultCharset(), null),
        UTF8("UTF-8", java.nio.charset.StandardCharsets.UTF_8, null),
        UTF8_BOM("UTF-8 BOM", java.nio.charset.StandardCharsets.UTF_8, BOM_UTF8),
        UTF16_LE_BOM("UTF-16 LE BOM", java.nio.charset.StandardCharsets.UTF_16LE, BOM_UTF16_LE),
        UTF16_BE_BOM("UTF-16 BE BOM", java.nio.charset.StandardCharsets.UTF_16BE, BOM_UTF16_BE);

        final String label;
        final java.nio.charset.Charset charset;
        final byte[] bom;

        FileEncoding(String label, java.nio.charset.Charset charset, byte[] bom) {
            this.label = label;
            this.charset = charset;
            this.bom = bom;
        }
    }

    private enum LineEnding {
        CRLF("Windows (CRLF)", "\r\n"),
        LF("Unix (LF)", "\n"),
        CR("Mac (CR)", "\r");

        final String label;
        final String sequence;

        LineEnding(String label, String sequence) {
            this.label = label;
            this.sequence = sequence;
        }
    }
    private static final String[] KEYWORDS = new String[]{
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while"
    };

    private static final String[] KEYWORDS_JS = new String[]{
            "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "export", "extends", "finally", "for", "function", "if",
            "import", "in", "instanceof", "let", "new", "return", "super", "switch", "this",
            "throw", "try", "typeof", "var", "void", "while", "with", "yield", "await"
    };

    private static final String[] KEYWORDS_PY = new String[]{
            "and", "as", "assert", "break", "class", "continue", "def", "del", "elif", "else",
            "except", "False", "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "None", "nonlocal", "not", "or", "pass", "raise", "return", "True",
            "try", "while", "with", "yield"
    };

    private static final Pattern PATTERN_JAVA = buildPattern(KEYWORDS,
            "//[^\\n]*|/\\*(.|\\R)*?\\*/",
            "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'");
    private static final Pattern PATTERN_JS = buildPattern(KEYWORDS_JS,
            "//[^\\n]*|/\\*(.|\\R)*?\\*/",
            "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'|`([^`\\\\]|\\\\.)*`");
    private static final Pattern PATTERN_PY = buildPattern(KEYWORDS_PY,
            "#[^\\n]*",
            "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'");

    private static final String CURSOR = "${cursor}";
    private static final Map<String, String> SNIPPETS = new LinkedHashMap<>();

    static {
        SNIPPETS.put("psvm", "public static void main(String[] args) {\n    " + CURSOR + "\n}");
        SNIPPETS.put("sout", "System.out.println(" + CURSOR + ");");
        SNIPPETS.put("fori", "for (int i = 0; i < " + CURSOR + "; i++) {\n    \n}");
        SNIPPETS.put("if", "if (" + CURSOR + ") {\n    \n}");
    }

    @FXML
    private BorderPane root;
    @FXML
    private TabPane tabPane;
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblStats;
    @FXML
    private Label lblSelection;
    @FXML
    private Label lblLineCol;
    @FXML
    private Label lblEol;
    @FXML
    private Label lblEncoding;
    @FXML
    private Label lblZoom;

    @FXML
    private MenuItem miNewTab;
    @FXML
    private MenuItem miOpen;
    @FXML
    private MenuItem miSave;
    @FXML
    private MenuItem miSaveAs;
    @FXML
    private MenuItem miCloseTab;
    @FXML
    private MenuItem miExit;

    @FXML
    private MenuItem miUndo;
    @FXML
    private MenuItem miRedo;
    @FXML
    private MenuItem miCut;
    @FXML
    private MenuItem miCopy;
    @FXML
    private MenuItem miPaste;
    @FXML
    private MenuItem miSelectAll;
    @FXML
    private MenuItem miFind;
    @FXML
    private MenuItem miReplace;

    @FXML
    private RadioMenuItem miThemeLight;
    @FXML
    private RadioMenuItem miThemeDark;
    @FXML
    private RadioMenuItem miModeText;
    @FXML
    private RadioMenuItem miModeCode;
    @FXML
    private RadioMenuItem miEncodingAnsi;
    @FXML
    private RadioMenuItem miEncodingUtf8;
    @FXML
    private RadioMenuItem miEncodingUtf8Bom;
    @FXML
    private RadioMenuItem miEncodingUtf16LeBom;
    @FXML
    private RadioMenuItem miEncodingUtf16BeBom;
    @FXML
    private MenuItem miConvertAnsi;
    @FXML
    private MenuItem miConvertUtf8;
    @FXML
    private MenuItem miConvertUtf8Bom;
    @FXML
    private MenuItem miConvertUtf16LeBom;
    @FXML
    private MenuItem miConvertUtf16BeBom;
    @FXML
    private RadioMenuItem miEolWindows;
    @FXML
    private RadioMenuItem miEolUnix;
    @FXML
    private RadioMenuItem miEolMac;
    @FXML
    private MenuItem miZoomIn;
    @FXML
    private MenuItem miZoomOut;
    @FXML
    private MenuItem miZoomReset;

    @FXML
    private MenuItem miAbout;

    private int untitledCount = 1;
    private String currentTheme = THEME_LIGHT;
    private Stage findStage;
    private TextField tfFind;
    private TextField tfReplace;
    private Label lblFindStatus;
    private ContextMenu suggestMenu;
    private String appVersion = "0.0.0";
    private boolean draftsDirty = false;
    private Timeline autosaveTimeline;
    private FileEncoding defaultEncoding = FileEncoding.UTF8;
    private LineEnding defaultLineEnding = LineEnding.CRLF;
    private double fontSize = BASE_FONT_SIZE;

    private static class TabData {
        CodeArea area;
        Path filePath;
        boolean dirty;
        boolean loading;
        Subscription highlightSubscription;
        boolean codeMode;
        String language;
        Pattern pattern;
        FileEncoding encoding;
        LineEnding lineEnding;
    }

    private static class DraftEntry {
        String title;
        String filePath;
        boolean codeMode;
        String language;
        String content;
        String encoding;
        String lineEnding;
    }

    @FXML
    public void initialize() {
        ToggleGroup themeGroup = new ToggleGroup();
        miThemeLight.setToggleGroup(themeGroup);
        miThemeDark.setToggleGroup(themeGroup);
        miThemeLight.setSelected(true);

        ToggleGroup modeGroup = new ToggleGroup();
        miModeText.setToggleGroup(modeGroup);
        miModeCode.setToggleGroup(modeGroup);
        miModeCode.setSelected(true);

        ToggleGroup encodingGroup = new ToggleGroup();
        miEncodingAnsi.setToggleGroup(encodingGroup);
        miEncodingUtf8.setToggleGroup(encodingGroup);
        miEncodingUtf8Bom.setToggleGroup(encodingGroup);
        miEncodingUtf16LeBom.setToggleGroup(encodingGroup);
        miEncodingUtf16BeBom.setToggleGroup(encodingGroup);
        miEncodingUtf8.setSelected(true);

        ToggleGroup eolGroup = new ToggleGroup();
        miEolWindows.setToggleGroup(eolGroup);
        miEolUnix.setToggleGroup(eolGroup);
        miEolMac.setToggleGroup(eolGroup);
        miEolWindows.setSelected(true);

        setupShortcuts();
        appVersion = loadAppVersion();
        if (!loadDrafts()) {
            createNewTab();
        }
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateStatus("Pronto");
            syncModeToggle(newTab);
            syncEncodingToggle(newTab);
            syncLineEndingToggle(newTab);
            updateStats();
            updateLineColStatus();
            updateSelectionStatus();
            updateEncodingStatus();
            updateLineEndingStatus();
            updateZoomStatus();
        });
        startAutoSave();
        checkForUpdatesAsync();
    }

    private void setupShortcuts() {
        miNewTab.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.N, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miOpen.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.O, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miSave.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.S, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miSaveAs.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.S, javafx.scene.input.KeyCombination.CONTROL_DOWN,
                javafx.scene.input.KeyCombination.SHIFT_DOWN));
        miFind.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.F, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miReplace.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.H, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miZoomIn.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.EQUALS, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miZoomOut.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.MINUS, javafx.scene.input.KeyCombination.CONTROL_DOWN));
        miZoomReset.setAccelerator(new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.DIGIT0, javafx.scene.input.KeyCombination.CONTROL_DOWN));
    }

    private void createNewTab() {
        String title = "Sem Titulo " + untitledCount++;
        Tab tab = new Tab(title);
        TabData data = buildCodeTab(tab, "");
        tab.setUserData(data);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        if (miModeText != null && miModeText.isSelected()) {
            setMode(data, false);
        }
        updateStats();
        syncEncodingToggle(tab);
        syncLineEndingToggle(tab);
        updateLineColStatus();
        updateSelectionStatus();
        updateEncodingStatus();
        updateLineEndingStatus();
        updateZoomStatus();
    }

    private TabData buildCodeTab(Tab tab, String content) {
        CodeArea area = new CodeArea();
        area.getStyleClass().add("code-area");
        area.setParagraphGraphicFactory(LineNumberFactory.get(area));

        TabData data = new TabData();
        data.area = area;
        data.loading = true;
        area.replaceText(content == null ? "" : content);
        data.loading = false;
        data.dirty = false;
        data.codeMode = true;
        data.language = "java";
        data.pattern = PATTERN_JAVA;
        data.encoding = defaultEncoding;
        data.lineEnding = defaultLineEnding;

        applyFontSize(area);
        attachHighlight(data);

        area.plainTextChanges().subscribe(ignore -> {
            if (!data.loading) {
                markDirty(tab, true);
                draftsDirty = true;
            }
            updateStats();
        });

        area.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCaretStatus(area));
        area.selectedTextProperty().addListener((obs, oldText, newText) -> updateSelectionStatus(area));

        setupEditorInteractions(data);
        VirtualizedScrollPane<CodeArea> scroller = new VirtualizedScrollPane<>(area);
        tab.setContent(scroller);
        tab.setOnCloseRequest(event -> {
            if (!confirmClose(tab)) {
                event.consume();
            }
        });
        tab.setOnClosed(event -> saveDrafts());

        applyHighlight(data);
        return data;
    }

    private void applyHighlight(TabData data) {
        if (data == null || data.pattern == null) {
            return;
        }
        StyleSpans<Collection<String>> spans = computeHighlighting(data.area.getText(), data.pattern);
        data.area.setStyleSpans(0, spans);
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                            matcher.group("TYPE") != null ? "type" :
                                    matcher.group("FUNCTION") != null ? "function" :
                                            matcher.group("IDENT") != null ? "identifier" :
                            matcher.group("PAREN") != null ? "paren" :
                                    matcher.group("BRACE") != null ? "brace" :
                                            matcher.group("BRACKET") != null ? "bracket" :
                                                    matcher.group("SEMICOLON") != null ? "semicolon" :
                                                            matcher.group("STRING") != null ? "string" :
                                                                    matcher.group("COMMENT") != null ? "comment" :
                                                                            matcher.group("NUMBER") != null ? "number" :
                                                                                    null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private CodeArea getCurrentArea() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) {
            return null;
        }
        TabData data = (TabData) tab.getUserData();
        return data == null ? null : data.area;
    }

    private TabData getCurrentData() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == null) {
            return null;
        }
        return (TabData) tab.getUserData();
    }

    private void updateCaretStatus(CodeArea area) {
        int line = area.getCurrentParagraph() + 1;
        int col = area.getCaretColumn() + 1;
        updateStatus("Linha " + line + ", Coluna " + col);
    }

    private void updateStatus(String message) {
        if (lblStatus != null) {
            lblStatus.setText(message);
        }
    }

    private void updateStats() {
        if (lblStats == null) {
            return;
        }
        TabData data = getCurrentData();
        if (data == null) {
            lblStats.setText("Palavras: 0 | Caracteres: 0");
            return;
        }
        String text = data.area.getText();
        int chars = text.length();
        int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        lblStats.setText("Palavras: " + words + " | Caracteres: " + chars);
    }

    private void syncModeToggle(Tab tab) {
        if (tab == null) {
            return;
        }
        TabData data = (TabData) tab.getUserData();
        if (data == null) {
            return;
        }
        if (data.codeMode) {
            miModeCode.setSelected(true);
        } else {
            miModeText.setSelected(true);
        }
    }

    private void attachHighlight(TabData data) {
        if (data.highlightSubscription != null) {
            data.highlightSubscription.unsubscribe();
        }
        data.highlightSubscription = data.area.multiPlainChanges()
                .successionEnds(Duration.ofMillis(200))
                .subscribe(ignore -> applyHighlight(data));
    }

    private void detachHighlight(TabData data) {
        if (data.highlightSubscription != null) {
            data.highlightSubscription.unsubscribe();
            data.highlightSubscription = null;
        }
    }

    private void clearStyles(CodeArea area) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        spansBuilder.add(Collections.emptyList(), area.getLength());
        area.setStyleSpans(0, spansBuilder.create());
    }

    private void setMode(TabData data, boolean codeMode) {
        data.codeMode = codeMode;
        data.area.getStyleClass().removeAll("code-area", "text-area");
        data.area.getStyleClass().add(codeMode ? "code-area" : "text-area");
        if (codeMode) {
            attachHighlight(data);
            applyHighlight(data);
        } else {
            detachHighlight(data);
            clearStyles(data.area);
        }
    }

    private void setupEditorInteractions(TabData data) {
        data.area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.SPACE) {
                showSuggestions(data);
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.TAB) {
                if (tryExpandSnippet(data)) {
                    event.consume();
                }
            }
        });

        data.area.addEventHandler(KeyEvent.KEY_TYPED, event -> {
            if (data.codeMode && event.getCharacter() != null && event.getCharacter().length() == 1) {
                char ch = event.getCharacter().charAt(0);
                if (handleAutoPair(data.area, ch)) {
                    event.consume();
                    return;
                }
            }
            if (suggestMenu != null && suggestMenu.isShowing()) {
                suggestMenu.hide();
            }
        });
    }

    private boolean handleAutoPair(CodeArea area, char ch) {
        String closing = switch (ch) {
            case '(' -> ")";
            case '[' -> "]";
            case '{' -> "}";
            case '"' -> "\"";
            case '\'' -> "'";
            default -> null;
        };
        if (closing == null) {
            return false;
        }
        String selected = area.getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            area.replaceSelection(ch + selected + closing);
            area.moveTo(area.getCaretPosition() - closing.length());
            return true;
        }
        int caret = area.getCaretPosition();
        area.insertText(caret, String.valueOf(ch) + closing);
        area.moveTo(caret + 1);
        return true;
    }

    private boolean tryExpandSnippet(TabData data) {
        String word = getCurrentWord(data.area);
        if (word == null) {
            return false;
        }
        String snippet = SNIPPETS.get(word);
        if (snippet == null) {
            return false;
        }
        replaceCurrentWordWithSnippet(data.area, snippet);
        return true;
    }

    private void replaceCurrentWordWithSnippet(CodeArea area, String snippet) {
        int[] range = getCurrentWordRange(area);
        if (range == null) {
            return;
        }
        int start = range[0];
        int end = range[1];
        int cursorIndex = snippet.indexOf(CURSOR);
        String cleanSnippet = snippet.replace(CURSOR, "");
        area.replaceText(start, end, cleanSnippet);
        if (cursorIndex >= 0) {
            area.moveTo(start + cursorIndex);
        } else {
            area.moveTo(start + cleanSnippet.length());
        }
    }

    private void showSuggestions(TabData data) {
        if (suggestMenu == null) {
            suggestMenu = new ContextMenu();
        }
        suggestMenu.getItems().clear();
        String prefix = getCurrentWord(data.area);
        for (String suggestion : buildSuggestions(data, prefix)) {
            MenuItem item = new MenuItem(suggestion);
            item.setOnAction(e -> replaceCurrentWord(data.area, suggestion));
            suggestMenu.getItems().add(item);
        }
        if (suggestMenu.getItems().isEmpty()) {
            return;
        }
        data.area.getCaretBounds().ifPresentOrElse(bounds -> {
            Point2D point = data.area.localToScreen(bounds.getMaxX(), bounds.getMaxY());
            suggestMenu.show(data.area, point.getX(), point.getY());
        }, () -> suggestMenu.show(data.area, 0, 0));
    }

    private Set<String> buildSuggestions(TabData data, String prefix) {
        Set<String> result = new LinkedHashSet<>();
        String norm = prefix == null ? "" : prefix.trim();
        if (data.codeMode) {
            for (String key : getKeywordsForLanguage(data.language)) {
                if (norm.isEmpty() || key.startsWith(norm)) {
                    result.add(key);
                }
            }
        }
        for (String key : SNIPPETS.keySet()) {
            if (norm.isEmpty() || key.startsWith(norm)) {
                result.add(key);
            }
        }
        String text = data.area.getText();
        Matcher matcher = Pattern.compile("\\b[a-zA-Z_][\\w]*\\b").matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            if (norm.isEmpty() || word.startsWith(norm)) {
                result.add(word);
            }
            if (result.size() > 80) {
                break;
            }
        }
        return result;
    }

    private String getCurrentWord(CodeArea area) {
        int[] range = getCurrentWordRange(area);
        if (range == null) {
            return null;
        }
        return area.getText(range[0], range[1]);
    }

    private int[] getCurrentWordRange(CodeArea area) {
        int caret = area.getCaretPosition();
        String text = area.getText();
        if (text.isEmpty() || caret < 0) {
            return null;
        }
        int start = caret;
        int end = caret;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
            end++;
        }
        if (start == end) {
            return null;
        }
        return new int[]{start, end};
    }

    private void replaceCurrentWord(CodeArea area, String replacement) {
        int[] range = getCurrentWordRange(area);
        if (range == null) {
            return;
        }
        area.replaceText(range[0], range[1], replacement);
    }

    private void markDirty(Tab tab, boolean dirty) {
        TabData data = (TabData) tab.getUserData();
        if (data != null && data.dirty != dirty) {
            data.dirty = dirty;
            String baseName = tab.getText();
            if (baseName.endsWith("*")) {
                baseName = baseName.substring(0, baseName.length() - 1);
            }
            tab.setText(dirty ? baseName + "*" : baseName);
        }
    }

    private boolean confirmClose(Tab tab) {
        TabData data = (TabData) tab.getUserData();
        if (data == null || !data.dirty) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Alterações não salvas");
        alert.setHeaderText("Salvar alterações antes de fechar?");
        alert.setContentText(tab.getText().replace("*", ""));
        ButtonType btnSave = new ButtonType("Salvar");
        ButtonType btnDont = new ButtonType("Não salvar");
        ButtonType btnCancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnSave, btnDont, btnCancel);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == btnCancel) {
            return false;
        }
        if (result.get() == btnSave) {
            tabPane.getSelectionModel().select(tab);
            return handleSaveInternal(false);
        }
        return true;
    }

    private void setCurrentFile(TabData data, Tab tab, Path path) {
        data.filePath = path;
        String name = path == null ? "Sem Titulo" : path.getFileName().toString();
        tab.setText(name + (data.dirty ? "*" : ""));
    }

    @FXML
    public void handleNewTab() {
        createNewTab();
    }

    @FXML
    public void handleOpen() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir arquivo");
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Tab tab = new Tab(file.getName());
            TabData data = buildCodeTab(tab, content);
            data.filePath = file.toPath();
            String language = detectLanguage(file.toPath());
            if ("text".equals(language)) {
                setMode(data, false);
            } else {
                data.language = language;
                data.pattern = patternForLanguage(language);
                applyHighlight(data);
            }
            tab.setUserData(data);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            markDirty(tab, false);
            if (miModeText != null && miModeText.isSelected()) {
                setMode(data, false);
            }
            updateStatus("Arquivo aberto: " + file.getName());
            updateStats();
        } catch (IOException ex) {
            showError("Não foi possível abrir o arquivo.", ex.getMessage());
        }
    }

    @FXML
    public void handleSave() {
        handleSaveInternal(false);
    }

    @FXML
    public void handleSaveAs() {
        handleSaveInternal(true);
    }

    private boolean handleSaveInternal(boolean forceSaveAs) {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        TabData data = getCurrentData();
        if (tab == null || data == null) {
            return false;
        }
        Path path = data.filePath;
        if (forceSaveAs || path == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Salvar arquivo");
            File file = chooser.showSaveDialog(root.getScene().getWindow());
            if (file == null) {
                return false;
            }
            path = file.toPath();
        }
        try {
            Files.writeString(path, data.area.getText(), StandardCharsets.UTF_8);
            data.filePath = path;
            markDirty(tab, false);
            setCurrentFile(data, tab, path);
            String language = detectLanguage(path);
            if (data.codeMode && !"text".equals(language)) {
                data.language = language;
                data.pattern = patternForLanguage(language);
                applyHighlight(data);
            }
            updateStatus("Arquivo salvo: " + path.getFileName());
            return true;
        } catch (IOException ex) {
            showError("Não foi possível salvar o arquivo.", ex.getMessage());
            return false;
        }
    }

    @FXML
    public void handleCloseTab() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab != null && confirmClose(tab)) {
            tabPane.getTabs().remove(tab);
            if (tabPane.getTabs().isEmpty()) {
                createNewTab();
            }
            saveDrafts();
        }
    }

    @FXML
    public void handleExit() {
        requestExit();
    }

    public void requestExit() {
        for (Tab tab : tabPane.getTabs()) {
            if (!confirmClose(tab)) {
                return;
            }
        }
        saveDrafts();
        Platform.exit();
    }

    @FXML
    public void handleUndo() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            area.undo();
        }
    }

    @FXML
    public void handleRedo() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            area.redo();
        }
    }

    @FXML
    public void handleCut() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(area.getSelectedText());
            Clipboard.getSystemClipboard().setContent(content);
            area.replaceSelection("");
        }
    }

    @FXML
    public void handleCopy() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(area.getSelectedText());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    @FXML
    public void handlePaste() {
        CodeArea area = getCurrentArea();
        if (area != null && Clipboard.getSystemClipboard().hasString()) {
            area.replaceSelection(Clipboard.getSystemClipboard().getString());
        }
    }

    @FXML
    public void handleSelectAll() {
        CodeArea area = getCurrentArea();
        if (area != null) {
            area.selectAll();
        }
    }

    @FXML
    public void handleFind() {
        showFindReplace(false);
    }

    @FXML
    public void handleReplace() {
        showFindReplace(true);
    }

    @FXML
    public void handleModeText() {
        TabData data = getCurrentData();
        if (data != null) {
            setMode(data, false);
        }
    }

    @FXML
    public void handleModeCode() {
        TabData data = getCurrentData();
        if (data != null) {
            if (data.language == null) {
                data.language = "java";
                data.pattern = PATTERN_JAVA;
            }
            setMode(data, true);
        }
    }

    private void showFindReplace(boolean focusReplace) {
        if (findStage == null) {
            buildFindDialog();
        }
        findStage.show();
        findStage.toFront();
        if (focusReplace) {
            tfReplace.requestFocus();
        } else {
            tfFind.requestFocus();
        }
    }

    private void buildFindDialog() {
        findStage = new Stage();
        findStage.setTitle("Buscar e Substituir");
        findStage.initModality(Modality.NONE);
        findStage.initOwner(root.getScene().getWindow());

        tfFind = new TextField();
        tfReplace = new TextField();
        lblFindStatus = new Label();

        Button btnNext = new Button("Próximo");
        Button btnPrev = new Button("Anterior");
        Button btnReplace = new Button("Substituir");
        Button btnReplaceAll = new Button("Substituir Tudo");

        btnNext.setOnAction(e -> findNext(true));
        btnPrev.setOnAction(e -> findNext(false));
        btnReplace.setOnAction(e -> replaceOnce());
        btnReplaceAll.setOnAction(e -> replaceAll());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("Buscar:"), 0, 0);
        grid.add(tfFind, 1, 0);
        grid.add(new Label("Substituir:"), 0, 1);
        grid.add(tfReplace, 1, 1);
        GridPane.setHgrow(tfFind, Priority.ALWAYS);
        GridPane.setHgrow(tfReplace, Priority.ALWAYS);

        HBox actions = new HBox(8, btnPrev, btnNext, btnReplace, btnReplaceAll);
        VBox rootBox = new VBox(10, grid, actions, lblFindStatus);
        rootBox.setStyle("-fx-padding: 12;");

        findStage.setScene(new Scene(rootBox, 420, 150));
    }

    private void findNext(boolean forward) {
        CodeArea area = getCurrentArea();
        if (area == null) {
            return;
        }
        String query = tfFind.getText();
        if (query == null || query.isEmpty()) {
            lblFindStatus.setText("Digite algo para buscar.");
            return;
        }
        String text = area.getText();
        int start = area.getCaretPosition();
        int index = forward ? text.indexOf(query, start) : text.lastIndexOf(query, Math.max(0, start - 1));
        if (index == -1 && start != 0) {
            index = forward ? text.indexOf(query) : text.lastIndexOf(query);
        }
        if (index == -1) {
            lblFindStatus.setText("Nenhuma ocorrência encontrada.");
            return;
        }
        area.selectRange(index, index + query.length());
        area.requestFollowCaret();
        lblFindStatus.setText("Ocorrência em " + (index + 1));
    }

    private void replaceOnce() {
        CodeArea area = getCurrentArea();
        if (area == null) {
            return;
        }
        String query = tfFind.getText();
        String replacement = tfReplace.getText();
        if (query == null || query.isEmpty()) {
            lblFindStatus.setText("Digite algo para buscar.");
            return;
        }
        if (area.getSelectedText().equals(query)) {
            area.replaceSelection(replacement == null ? "" : replacement);
        }
        findNext(true);
    }

    private void replaceAll() {
        CodeArea area = getCurrentArea();
        if (area == null) {
            return;
        }
        String query = tfFind.getText();
        String replacement = tfReplace.getText();
        if (query == null || query.isEmpty()) {
            lblFindStatus.setText("Digite algo para buscar.");
            return;
        }
        String text = area.getText();
        String replaced = text.replace(query, replacement == null ? "" : replacement);
        area.replaceText(replaced);
        lblFindStatus.setText("Substituição concluída.");
    }

    @FXML
    public void handleThemeLight() {
        switchTheme(THEME_LIGHT);
    }

    @FXML
    public void handleThemeDark() {
        switchTheme(THEME_DARK);
    }

    private void switchTheme(String theme) {
        if (root == null) {
            return;
        }
        root.getStylesheets().clear();
        java.net.URL url = getClass().getResource(theme);
        if (url == null) {
            showError("Tema não encontrado", "Não foi possível carregar " + theme);
            return;
        }
        root.getStylesheets().add(url.toExternalForm());
        currentTheme = theme;
    }

    @FXML
    public void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sobre");
        alert.setHeaderText(APP_NAME);
        alert.setContentText("Editor simples com abas, destaque de sintaxe e temas.");
        alert.showAndWait();
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private static Pattern buildPattern(String[] keywords, String commentRegex, String stringRegex) {
        String keywordPattern = "\\b(" + String.join("|", keywords) + ")\\b";
        String typePattern = "\\b[A-Z][\\w]*\\b";
        String functionPattern = "\\b[a-zA-Z_][\\w]*(?=\\s*\\()";
        String identPattern = "\\b[a-zA-Z_][\\w]*\\b";
        String numberPattern = "\\b\\d+(\\.\\d+)?\\b";
        return Pattern.compile(
                "(?<KEYWORD>" + keywordPattern + ")"
                        + "|(?<TYPE>" + typePattern + ")"
                        + "|(?<FUNCTION>" + functionPattern + ")"
                        + "|(?<IDENT>" + identPattern + ")"
                        + "|(?<PAREN>\\(|\\))"
                        + "|(?<BRACE>\\{|\\})"
                        + "|(?<BRACKET>\\[|\\])"
                        + "|(?<SEMICOLON>;)"
                        + "|(?<STRING>" + stringRegex + ")"
                        + "|(?<COMMENT>" + commentRegex + ")"
                        + "|(?<NUMBER>" + numberPattern + ")"
        );
    }

    private static String detectLanguage(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".java")) {
            return "java";
        }
        if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".ts")) {
            return "js";
        }
        if (name.endsWith(".py")) {
            return "py";
        }
        if (name.endsWith(".html") || name.endsWith(".css")) {
            return "text";
        }
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            return "text";
        }
        return "java";
    }

    private static Pattern patternForLanguage(String language) {
        if ("js".equals(language)) {
            return PATTERN_JS;
        }
        if ("py".equals(language)) {
            return PATTERN_PY;
        }
        return PATTERN_JAVA;
    }

    private static String[] getKeywordsForLanguage(String language) {
        if ("js".equals(language)) {
            return KEYWORDS_JS;
        }
        if ("py".equals(language)) {
            return KEYWORDS_PY;
        }
        return KEYWORDS;
    }

    private String loadAppVersion() {
        try (InputStream in = getClass().getResourceAsStream("/org/example/app.properties")) {
            if (in == null) {
                return "0.0.0";
            }
            Properties props = new Properties();
            props.load(in);
            String v = props.getProperty("app.version");
            return v == null ? "0.0.0" : v.trim();
        } catch (IOException ex) {
            return "0.0.0";
        }
    }

    private void checkForUpdatesAsync() {
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(6))
                        .build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(UPDATE_API))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", APP_NAME)
                        .build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    return;
                }
                String body = res.body();
                String tag = extractJsonString(body, "tag_name");
                String url = extractJsonString(body, "html_url");
                String downloadUrl = extractLatestDownloadUrl(body);
                if (tag == null || url == null) {
                    return;
                }
                if (compareVersions(tag, appVersion) > 0) {
                    Platform.runLater(() -> showUpdateDialog(tag, url, downloadUrl));
                }
            } catch (Exception ignored) {
            }
        }, "update-check").start();
    }

    private void showUpdateDialog(String latest, String releaseUrl, String downloadUrl) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Atualização disponível");
        alert.setHeaderText("Nova versão " + latest + " disponível");
        alert.setContentText("Sua versão atual: " + appVersion + "\nDeseja atualizar agora?");
        ButtonType btnDownload = downloadUrl == null ? null : new ButtonType("Baixar e instalar");
        ButtonType btnUpdate = new ButtonType("Atualizar");
        ButtonType btnLater = new ButtonType("Mais tarde", ButtonBar.ButtonData.CANCEL_CLOSE);
        if (btnDownload != null) {
            alert.getButtonTypes().setAll(btnDownload, btnUpdate, btnLater);
        } else {
            alert.getButtonTypes().setAll(btnUpdate, btnLater);
        }
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == btnDownload) {
            try {
                java.awt.Desktop.getDesktop().browse(URI.create(downloadUrl));
            } catch (Exception ex) {
                showError("Falha ao abrir o navegador", ex.getMessage());
            }
        } else if (result.isPresent() && result.get() == btnUpdate) {
            try {
                java.awt.Desktop.getDesktop().browse(URI.create(releaseUrl));
            } catch (Exception ex) {
                showError("Falha ao abrir o navegador", ex.getMessage());
            }
        }
    }

    private int compareVersions(String a, String b) {
        List<Integer> va = parseVersionNumbers(a);
        List<Integer> vb = parseVersionNumbers(b);
        int max = Math.max(va.size(), vb.size());
        for (int i = 0; i < max; i++) {
            int ai = i < va.size() ? va.get(i) : 0;
            int bi = i < vb.size() ? vb.get(i) : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    private List<Integer> parseVersionNumbers(String v) {
        List<Integer> out = new ArrayList<>();
        if (v == null) {
            return out;
        }
        Matcher m = Pattern.compile("\\d+").matcher(v);
        while (m.find()) {
            try {
                out.add(Integer.parseInt(m.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private String extractJsonString(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return null;
        }
        return unescapeJsonString(m.group(1));
    }

    private String extractLatestDownloadUrl(String json) {
        if (json == null) {
            return null;
        }
        Pattern p = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"(.*?)\"");
        Matcher m = p.matcher(json);
        List<String> urls = new ArrayList<>();
        while (m.find()) {
            urls.add(unescapeJsonString(m.group(1)));
        }
        if (urls.isEmpty()) {
            return null;
        }
        for (String u : urls) {
            if (u == null) {
                continue;
            }
            String lu = u.toLowerCase();
            String target = RELEASE_ASSET_NAME.toLowerCase();
            if (lu.endsWith("/" + target) || lu.endsWith(target)) {
                return u;
            }
        }
        return null;
    }

    private String unescapeJsonString(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("\\/", "/").replace("\\\"", "\"");
    }

    private Path getDraftFile() {
        return buildDraftPath(DRAFTS_DIR);
    }

    private Path getLegacyDraftFile() {
        return buildDraftPath(LEGACY_DRAFTS_DIR);
    }

    private Path buildDraftPath(String dirName) {
        String base = System.getenv("LOCALAPPDATA");
        if (base == null || base.isBlank()) {
            base = System.getProperty("user.home");
        }
        return Paths.get(base, dirName, DRAFTS_FILE);
    }

    private void saveDrafts() {
        try {
            List<DraftEntry> entries = new ArrayList<>();
            for (Tab tab : tabPane.getTabs()) {
                TabData data = (TabData) tab.getUserData();
                if (data == null) {
                    continue;
                }
                String text = data.area.getText();
                if ((text == null || text.isEmpty()) && data.filePath == null) {
                    continue;
                }
                DraftEntry e = new DraftEntry();
                e.title = tab.getText();
                e.filePath = data.filePath == null ? "" : data.filePath.toString();
                e.codeMode = data.codeMode;
                e.language = data.language == null ? "java" : data.language;
                e.content = text == null ? "" : text;
                entries.add(e);
            }

            Path file = getDraftFile();
            if (entries.isEmpty()) {
                Files.deleteIfExists(file);
                draftsDirty = false;
                return;
            }
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("DRAFTS_V1").append("\n");
            for (DraftEntry e : entries) {
                sb.append(b64(e.title)).append("\n");
                sb.append(b64(e.filePath)).append("\n");
                sb.append(e.codeMode ? "1" : "0").append("\n");
                sb.append(b64(e.language)).append("\n");
                sb.append(b64(e.content)).append("\n");
                sb.append("---").append("\n");
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            draftsDirty = false;
        } catch (IOException ignored) {
        }
    }

    private boolean loadDrafts() {
        Path file = getDraftFile();
        if (!Files.exists(file)) {
            Path legacy = getLegacyDraftFile();
            if (!Files.exists(legacy)) {
                return false;
            }
            file = legacy;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !"DRAFTS_V1".equals(lines.get(0))) {
                return false;
            }
            boolean created = false;
            int i = 1;
            while (i + 4 < lines.size()) {
                String title = fromB64(lines.get(i++));
                String path = fromB64(lines.get(i++));
                String codeMode = lines.get(i++);
                String language = fromB64(lines.get(i++));
                String content = fromB64(lines.get(i++));
                if (i < lines.size() && "---".equals(lines.get(i))) {
                    i++;
                }
                Tab tab = new Tab(title == null || title.isBlank() ? "Sem Titulo" : title);
                TabData data = buildCodeTab(tab, content == null ? "" : content);
                data.filePath = (path == null || path.isBlank()) ? null : Paths.get(path);
                data.language = (language == null || language.isBlank()) ? "java" : language;
                data.pattern = patternForLanguage(data.language);
                if ("0".equals(codeMode)) {
                    setMode(data, false);
                } else {
                    setMode(data, true);
                }
                tab.setUserData(data);
                tabPane.getTabs().add(tab);
                markDirty(tab, data.filePath == null && content != null && !content.isEmpty());
                created = true;
            }
            if (created) {
                tabPane.getSelectionModel().selectFirst();
                updateStats();
            }
            draftsDirty = false;
            return created;
        } catch (IOException ex) {
            return false;
        }
    }

    private void startAutoSave() {
        autosaveTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(AUTO_SAVE_SECONDS), event -> {
            if (draftsDirty) {
                saveDrafts();
            }
        }));
        autosaveTimeline.setCycleCount(Timeline.INDEFINITE);
        autosaveTimeline.play();
    }

    private String b64(String s) {
        if (s == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private String fromB64(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }
}
