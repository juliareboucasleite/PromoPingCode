package org.example.controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.reactfx.Subscription;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditorController {

    private static final String THEME_LIGHT = "/org/example/editor-light.css";
    private static final String THEME_DARK = "/org/example/editor-dark.css";
    private static final String[] KEYWORDS = new String[]{
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while"
    };

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>\\b(" + String.join("|", KEYWORDS) + ")\\b)"
                    + "|(?<PAREN>\\(|\\))"
                    + "|(?<BRACE>\\{|\\})"
                    + "|(?<BRACKET>\\[|\\])"
                    + "|(?<SEMICOLON>;)"
                    + "|(?<STRING>\"([^\"\\\\]|\\\\.)*\")"
                    + "|(?<COMMENT>//[^\\n]*|/\\*(.|\\R)*?\\*/)"
                    + "|(?<NUMBER>\\b\\d+(\\.\\d+)?\\b)"
    );

    @FXML
    private BorderPane root;
    @FXML
    private TabPane tabPane;
    @FXML
    private Label lblStatus;
    @FXML
    private Label lblStats;

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
    private MenuItem miAbout;

    private int untitledCount = 1;
    private String currentTheme = THEME_LIGHT;
    private Stage findStage;
    private TextField tfFind;
    private TextField tfReplace;
    private Label lblFindStatus;

    private static class TabData {
        CodeArea area;
        Path filePath;
        boolean dirty;
        boolean loading;
        Subscription highlightSubscription;
        boolean codeMode;
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

        setupShortcuts();
        createNewTab();
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            updateStatus("Pronto");
            syncModeToggle(newTab);
            updateStats();
        });
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
    }

    private void createNewTab() {
        String title = "Sem Título " + untitledCount++;
        Tab tab = new Tab(title);
        TabData data = buildCodeTab(tab, "");
        tab.setUserData(data);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        updateStats();
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

        attachHighlight(data);

        area.plainTextChanges().subscribe(ignore -> {
            if (!data.loading) {
                markDirty(tab, true);
            }
            updateStats();
        });

        area.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCaretStatus(area));

        VirtualizedScrollPane<CodeArea> scroller = new VirtualizedScrollPane<>(area);
        tab.setContent(scroller);
        tab.setOnCloseRequest(event -> {
            if (!confirmClose(tab)) {
                event.consume();
            }
        });

        applyHighlight(area);
        return data;
    }

    private void applyHighlight(CodeArea area) {
        StyleSpans<Collection<String>> spans = computeHighlighting(area.getText());
        area.setStyleSpans(0, spans);
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
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
                .subscribe(ignore -> applyHighlight(data.area));
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
            applyHighlight(data.area);
        } else {
            detachHighlight(data);
            clearStyles(data.area);
        }
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
        String name = path == null ? "Sem Título" : path.getFileName().toString();
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
            tab.setUserData(data);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            markDirty(tab, false);
            updateStatus("Arquivo aberto: " + file.getName());
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
        }
    }

    @FXML
    public void handleExit() {
        for (Tab tab : tabPane.getTabs()) {
            if (!confirmClose(tab)) {
                return;
            }
        }
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
        alert.setHeaderText("PromoPing CodePad");
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
}
