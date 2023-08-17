package com.diegocapape;

import com.diegocapape.util.Config;
import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Settings {

    private Shell shell;
    private List groupList;
    private Group optionGroup;
    private Config config;
    private Wini ini;
    private Map<String, Text> textWidgets = new HashMap<>();

    public Settings(Display display) throws IOException {
        shell = new Shell(display, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL );
        shell.setText("Settings");
        shell.setSize(700, 400);
        shell.setMinimumSize(700, 400);
        shell.setLayout(new GridLayout(2, false));

        // Cargar configuración
        config = new Config();
        ini = new Wini(new File("config.ini"));

        // Lista de grupos
        groupList = new List(shell, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        GridData gridData = new GridData(GridData.FILL_BOTH);
        gridData.widthHint = 150; // 1/4 of 500
        groupList.setLayoutData(gridData);
        for (String section : ini.keySet()) {
            groupList.add(section);
        }
        groupList.addListener(SWT.Selection, e -> {
            String selectedGroup = null;
            String[] selection = groupList.getSelection();
            if (selection.length > 0) {
                selectedGroup = selection[0];
            }
            displayOptions(selectedGroup);
        });

        // GroupBox para opciones
        optionGroup = new Group(shell, SWT.NONE);
        optionGroup.setLayout(new GridLayout(3, false));
        gridData = new GridData(GridData.FILL_BOTH);
        gridData.widthHint = 550; // 3/4 of 500
        optionGroup.setLayoutData(gridData);

        // Botones en la parte inferior
        Composite buttonComposite = new Composite(shell, SWT.NONE);
        buttonComposite.setLayout(new GridLayout(3, true));
        gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL);
        gridData.horizontalSpan = 2;
        gridData.horizontalAlignment = GridData.END;
        buttonComposite.setLayoutData(gridData);

        GridData btnGridData = new GridData(GridData.HORIZONTAL_ALIGN_END);
        btnGridData.widthHint = 120;
        btnGridData.horizontalAlignment = SWT.RIGHT;

        Button acceptButton = new Button(buttonComposite, SWT.PUSH);
        acceptButton.setText("Accept");
        acceptButton.setLayoutData(btnGridData);
        acceptButton.addListener(SWT.Selection, e -> {
            saveChanges();
            shell.dispose();
        });

        Button cancelButton = new Button(buttonComposite, SWT.PUSH);
        cancelButton.setText("Cancel");
        cancelButton.setLayoutData(btnGridData);
        cancelButton.addListener(SWT.Selection, e -> shell.dispose());

        Button applyButton = new Button(buttonComposite, SWT.PUSH);
        applyButton.setText("Apply");
        applyButton.setLayoutData(btnGridData);
        applyButton.addListener(SWT.Selection, e -> saveChanges());

        //shell.pack();
        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    private void displayOptions(String section) {
        // Limpia las opciones anteriores
        for (Control control : optionGroup.getChildren()) {
            control.dispose();
        }

        for (String key : ini.get(section).keySet()) {
            String formattedKey = key.replaceAll("([A-Z])", " $1").trim();
            Label label = new Label(optionGroup, SWT.NONE);
            label.setText(formattedKey);

            Text text = new Text(optionGroup, SWT.BORDER);
            text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            text.setText(ini.get(section, key));
            textWidgets.put(key, text);

            if (key.contains("Path")) {
                Button button = new Button(optionGroup, SWT.PUSH);
                button.setText("Choose File...");
                button.addListener(SWT.Selection, e -> {
                    FileDialog dialog = new FileDialog(shell, SWT.OPEN);
                    String path = dialog.open();
                    if (path != null) {
                        text.setText(path);
                    }
                });
            } else {
                new Label(optionGroup, SWT.NONE); // Placeholder
            }
        }
        optionGroup.layout();
    }

    private void saveChanges() {
        String selectedSection = groupList.getSelection()[0];
        for (String key : textWidgets.keySet()) {
            ini.put(selectedSection, key, textWidgets.get(key).getText());
        }
        try {
            ini.store();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        Display display = new Display();
        new Settings(display);
        display.dispose();
    }
}
