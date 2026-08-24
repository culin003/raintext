package com.raintext.ui;

import com.raintext.core.LineIndex;
import com.raintext.core.MappedFileReader;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ScrollbarGeometryTest extends Application {
    private EditableEditorView editor;
    private ScrollBar bar;

    @Override
    public void start(Stage stage) throws Exception {
        File tmp = File.createTempFile("raintext_scrollbar", ".txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20000; i++) sb.append("line ").append(i).append(" padding padding padding padding padding\n");
        Files.write(tmp.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));

        BorderPane root = new BorderPane();
        MappedFileReader reader = new MappedFileReader(tmp.toPath());
        LineIndex index = new LineIndex(reader);
        editor = new EditableEditorView(reader, index);
        root.setCenter(editor);

        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.show();

        // Let layout settle
        PauseTransition p1 = new PauseTransition(Duration.millis(1000));
        p1.setOnFinished(e -> {
            bar = editor.getVirtualScrollBar();
            System.out.println("[geom] init max=" + bar.getMax() + " min=" + bar.getMin()
                    + " value=" + bar.getValue() + " visibleAmount=" + bar.getVisibleAmount()
                    + " thumbHeight=" + bar.getHeight() + " barHeight=" + bar.getHeight());
            dumpGeometry("init");

            // Scroll all the way to the physical bottom in steps
            scrollDown(0);
        });
        p1.play();
    }

    private void scrollDown(int depth) {
        double physBottom = editor.debugActualMaxScrollTop();
        editor.getTextArea().setScrollTop(physBottom);
        javafx.scene.input.ScrollEvent down = new javafx.scene.input.ScrollEvent(
                javafx.scene.input.ScrollEvent.SCROLL,
                0, 0, 0, 0, false, false, false, false, true, true,
                0, -40, 0, 0,
                javafx.scene.input.ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                javafx.scene.input.ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null);
        editor.getTextArea().getOnScroll().handle(down);

        PauseTransition p = new PauseTransition(Duration.millis(80));
        p.setOnFinished(e -> {
            if (depth < 40 && editor.debugLoadedFromLine() < editor.debugTotalLines() - 1) {
                scrollDown(depth + 1);
            } else {
                dumpGeometry("bottom");
                // Now check thumb geometry
                try {
                    Object flow = editor.getTextArea().lookup(".virtual-flow");
                    if (flow != null) {
                        java.lang.reflect.Method m = flow.getClass().getMethod("getCellLength", int.class);
                        m.setAccessible(true);
                        System.out.println("[geom] cellLength(0)=" + m.invoke(flow, 0));
                        int cells = ((Number) flow.getClass().getMethod("getCellCount").invoke(flow)).intValue();
                        System.out.println("[geom] cellCount=" + cells);
                    }
                } catch (Exception ex) {
                    System.out.println("[geom] flow err: " + ex);
                }
                dumpThumbGeometry();
                Platform.exit();
            }
        });
        p.play();
    }

    private void dumpGeometry(String tag) {
        System.out.println("[geom] " + tag + " loadedFromLine=" + editor.debugLoadedFromLine()
                + " loadedCount=" + editor.debugLoadedCount()
                + " totalLines=" + editor.debugTotalLines()
                + " scrollTop=" + editor.debugScrollTop()
                + " actualMax=" + editor.debugActualMaxScrollTop()
                + " barValue=" + bar.getValue()
                + " barVisible=" + bar.getVisibleAmount()
                + " visibleLines=" + editor.debugVisibleCount());
    }

    private void dumpThumbGeometry() {
        bar.lookupAll(".thumb").forEach(node -> {
            System.out.println("[geom] THUMB layoutY=" + node.getLayoutY()
                    + " height=" + node.getLayoutBounds().getHeight()
                    + " layoutBounds=" + node.getLayoutBounds()
                    + " parentH=" + node.getParent().getLayoutBounds().getHeight());
        });
        bar.lookupAll(".track").forEach(node -> {
            System.out.println("[geom] TRACK height=" + node.getLayoutBounds().getHeight()
                    + " bounds=" + node.getLayoutBounds());
        });
        System.out.println("[geom] bar height=" + bar.getHeight()
                + " value=" + bar.getValue()
                + " max=" + bar.getMax()
                + " visible=" + bar.getVisibleAmount());
    }

    public static void main(String[] args) {
        launch(args);
    }
}