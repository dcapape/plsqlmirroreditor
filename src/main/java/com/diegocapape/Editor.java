package com.diegocapape;

import com.diegocapape.model.TableInfo;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.browser.*;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.diegocapape.model.ColumnInfo;
import com.diegocapape.model.ConnectionInfo;
import com.diegocapape.util.Config;


import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.io.*;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class Editor {

    private static final String APP_NAME = "PL/SQL Mirror Editor";
    private static org.eclipse.swt.widgets.List connectionsList;
    private static Menu connectionsMenu;
    private static Shell shell;

    private static SashForm sashForm;
    private static StyledText consoleArea;  // Para la consola
    private static boolean isAppModification = false;

    private static MenuItem consoleSubMenuItem;
    private static ConnectionInfo activeConnection;
    private static boolean isServiceConnection = true;
    private static List<TableInfo> tablesInfo = new ArrayList<>();

    private static Browser browser;

    private static Config config;

    public static void main(String[] args) {
        try {
            config = new Config();
        } catch (IOException e) {
            log.error("Error al leer el archivo de configuración: {}", e.getMessage());
            e.printStackTrace();
        }

        deploy("Microsoft.WebView2.FixedVersionRuntime.115.0.1901.200.x64");
        deploy("sqlplus");
        deploy("drivers");

        if (!new File("snippets.json").exists()){
            //copy from jar
            InputStream inwb = Editor.class.getResourceAsStream("/snippets.json");
            try {
                Files.copy(inwb, Paths.get("snippets.json"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!new File("suggestions.json").exists()){
            //copy from jar
            InputStream inwb = Editor.class.getResourceAsStream("/suggestions.json");
            try {
                Files.copy(inwb, Paths.get("suggestions.json"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //set java property to use webkit
        System.setProperty("org.eclipse.swt.browser.DefaultType", "edge");
        System.setProperty("org.eclipse.swt.browser.EdgeDir",  System.getProperty("user.dir") + "\\Microsoft.WebView2.FixedVersionRuntime.115.0.1901.200.x64");

        Display display = new Display();
        shell = new Shell(display);
        shell.setText(APP_NAME);
        shell.setLayout(new FillLayout());
        // Establecer el icono de la ventana
        InputStream in = Editor.class.getResourceAsStream("/images/logo.png");
        Image icon = new Image(display, in);
        shell.setImage(icon);
        /*URL imageUrl = Editor.class.getResource("/images/logo.png");
        if (imageUrl == null) {
            log.error("El recurso no se encuentra");
        } else {
            Image icon = new Image(display, imageUrl.getPath());
            shell.setImage(icon);
        }*/
        //Image icon = new Image(display, Editor.class.getResourceAsStream("logo.png"));
        //shell.setImage(icon);

        // Cambiar a GridLayout
        GridLayout mainLayout = new GridLayout(1, false); // Un único grid, verticalmente
        shell.setLayout(mainLayout);

        // Barra de herramientas
        ToolBar toolBar = new ToolBar(shell, SWT.FLAT);
        ToolItem playItem = new ToolItem(toolBar, SWT.PUSH);
        playItem.setText("▶\uFE0F");

        toolBar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        sashForm = new SashForm(shell, SWT.HORIZONTAL);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));


        // Crea un navegador en la ventana
        browser = new Browser(shell, SWT.EDGE);
        InputStream in2 = Editor.class.getResourceAsStream("/editor/editor.html");
        browser.setText(new BufferedReader(new InputStreamReader(in2)).lines().collect(Collectors.joining("\n")));
        //String url = Editor.class.getResource("/editor/editor.html").toExternalForm();
        //browser.setUrl(url);
        browser.setParent(sashForm);
        playItem.addListener(SWT.Selection, event -> executeCode(browser));
        browser.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                // Check if CTRL is held down and the key pressed is ENTER
                if ((e.stateMask & SWT.CTRL) != 0 && e.keyCode == SWT.CR) {
                    log.info("CTRL + ENTER pressed");
                    executeCode(browser);
                }
            }
        });
        browser.addLocationListener(new LocationAdapter() {
            @Override
            public void changing(LocationEvent event) {
                if (event.location.startsWith("java:content:")) {
                    event.doit = false; // Previne la navegación real
                    String contentToExecute = URLDecoder.decode(event.location.substring("java:content:".length()), StandardCharsets.UTF_8);
                    log.info(contentToExecute);
                    sendCommandToSqlPlus(contentToExecute);
                } else if ("java:executeCode".equals(event.location)) {
                    event.doit = false; // Previne la navegación real
                    executeCode(browser);
                } else if ("java:openFile".equals(event.location)) {
                    event.doit = false; // Previne la navegación real
                    openFile(shell, browser);
                } else if ("java:saveFile".equals(event.location)) {
                    event.doit = false; // Previne la navegación real
                    saveFile(shell, browser, false);
                } else if ("java:newFile".equals(event.location)) {
                    event.doit = false; // Previne la navegación real
                    newFile(shell, browser);
                }
            }
        });





        // Crear la barra de menú
        Menu menuBar = new Menu(shell, SWT.BAR);

        // Menú "File"
        MenuItem fileMenuHeader = new MenuItem(menuBar, SWT.CASCADE);
        fileMenuHeader.setText("File");
        Menu fileMenu = new Menu(shell, SWT.DROP_DOWN);
        fileMenuHeader.setMenu(fileMenu);

        // Opción "Open File"
        MenuItem newFileItem = new MenuItem(fileMenu, SWT.PUSH);
        newFileItem.setText("New\tCtrl+N"); // Agregar "\tCtrl+O" al texto
        newFileItem.setAccelerator(SWT.CTRL + 'N'); // Asignar el atajo de teclado
        newFileItem.addListener(SWT.Selection, event -> newFile(shell, browser));

        // Opción "Open File"
        MenuItem openFileItem = new MenuItem(fileMenu, SWT.PUSH);
        openFileItem.setText("Open\tCtrl+O"); // Agregar "\tCtrl+O" al texto
        openFileItem.setAccelerator(SWT.CTRL + 'O'); // Asignar el atajo de teclado
        openFileItem.addListener(SWT.Selection, event -> openFile(shell, browser));

        // Opción "Save File"
        MenuItem saveFileItem = new MenuItem(fileMenu, SWT.PUSH);
        saveFileItem.setText("Save\tCtrl+S");
        openFileItem.setAccelerator(SWT.CTRL + 'S'); // Asignar el atajo de teclado
        saveFileItem.addListener(SWT.Selection, event -> saveFile(shell, browser, false));

        // Opción "Save File"
        MenuItem saveAsFileItem = new MenuItem(fileMenu, SWT.PUSH);
        saveAsFileItem.setText("Save As...");
        saveAsFileItem.addListener(SWT.Selection, event -> saveFile(shell, browser, true));

        // separador
        new MenuItem(fileMenu, SWT.SEPARATOR);

        // Opción "Settings"
        MenuItem settingsItem = new MenuItem(fileMenu, SWT.PUSH);
        settingsItem.setText("Settings");
        settingsItem.addListener(SWT.Selection, event -> {
            try {
                new Settings(display);
            } catch (IOException e) {
                e.printStackTrace();
                log.error("Error al abrir las configuraciones: {}", e.getMessage());
            }
        });

        connectionsMenu = new Menu(shell, SWT.DROP_DOWN);
        MenuItem connectionsMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        connectionsMenuItem.setText("Connections");
        connectionsMenuItem.setMenu(connectionsMenu);
        updateConnectionsMenu();

        Menu windowMenu = new Menu(shell, SWT.DROP_DOWN);
        MenuItem windowMenuItem = new MenuItem(menuBar, SWT.CASCADE);
        windowMenuItem.setText("Window");
        windowMenuItem.setMenu(windowMenu);

        consoleSubMenuItem = new MenuItem(windowMenu, SWT.CHECK);
        consoleSubMenuItem.setText("Console");
        consoleSubMenuItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (consoleSubMenuItem.getSelection()) {
                    showConsole();
                } else {
                    hideConsole();
                }
            }
        });

        // Asignar la barra de menú a la shell
        shell.setMenuBar(menuBar);


        // Muestra la ventana
        shell.open();


        try {
            Thread.sleep(1000);
            if (args.length > 0) {
                if (new File(args[0]).exists()) {
                    openFile(args[0]);
                } else {
                    log.error("El archivo '{}' no existe", args[0]);
                }
            }else if (config.getCurrentFilePath() != null && !config.getCurrentFilePath().isEmpty()) {
                openFile(config.getCurrentFilePath());
            }
        } catch (SWTException e) {
            log.error("Error al ejecutar el script: {}", e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            // Leer el archivo 'suggestions.json'
            String jsonContent = new String(Files.readAllBytes(Paths.get(config.getSuggestionsPath())), StandardCharsets.UTF_8);

            log.info("jsonContent: {}", jsonContent);
            // Formatear el contenido JSON para la función de inyección
            String suggestionsScript = "injectSuggestions(" + jsonContent + ");";

            Thread.sleep(200);
            browser.evaluate(suggestionsScript);

        } catch (SWTException e) {
            log.error("Error al ejecutar el script: {}", e.getMessage());
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        try {
            // Leer el archivo 'suggestions.json'
            String jsonContent = new String(Files.readAllBytes(Paths.get(config.getSnippetsPath())), StandardCharsets.UTF_8);

            log.info("jsonContent: {}", jsonContent);
            // Formatear el contenido JSON para la función de inyección
            String snippetsScript = "injectSnippets(" + jsonContent + ");";

            Thread.sleep(200);
            browser.evaluate(snippetsScript);

        } catch (SWTException e) {
            log.error("Error al ejecutar el script: {}", e.getMessage());
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }



        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
    }

    private static void executeCode(Browser browser) {
        // Verifica si sqlplusProcess está en ejecución y si la consola está mostrándose
        if (sqlplusProcess != null && sqlplusProcess.isAlive()) {
            showConsole();
            // Primero, verifica si hay algo seleccionado en el editor
            String getSelectedTextScript = "var selectedContent = editor.getSelection();"
                    + "if (!selectedContent.trim()) {"
                    + "  selectedContent = editor.getValue();"
                    + "}"
                    + "window.location = 'java:content:' + encodeURIComponent(selectedContent);";

            browser.execute(getSelectedTextScript);


            /*String getSelectedTextScript = "return editor.getSelection();";

            String selectedContent = (String) browser.evaluate(getSelectedTextScript);

            String contentToExecute;

            if (selectedContent != null && !selectedContent.trim().isEmpty()) {
                // Si hay texto seleccionado, ejecuta solo ese texto
                contentToExecute = selectedContent;
            } else {
                // Si no hay texto seleccionado, obtiene todo el contenido del editor
                String getAllTextScript = "return editor.getValue();";
                contentToExecute = (String) browser.evaluate(getAllTextScript);
            }

            // Aquí ejecutas 'contentToExecute' en la consola SQL
            log.info(contentToExecute);
            sendCommandToSqlPlus(contentToExecute);*/
        } else {
            // Mensaje para el usuario indicando que debe haber una conexión y la consola debe estar mostrándose
            MessageBox messageBox = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
            messageBox.setMessage("Please ensure you have an active SQLPlus connection.");
            messageBox.open();
        }
    }




    private static void showConsole() {
        consoleSubMenuItem.setSelection(true);
        if ((consoleArea != null && !consoleArea.isDisposed())) {
            log.info("Showing console " + consoleArea.getVisible());
            if (!consoleArea.getVisible()){
                Display.getDefault().asyncExec(() -> {
                    consoleArea.setVisible(true);
                    consoleArea.redraw();
                    //consoleArea.append("\n");
                    //sashForm.redraw();
                    //consoleArea.setSize(consoleArea.getSize());
                    sashForm.setWeights(new int[]{50, 50}); // Divide la ventana en mitades iguales
                });
            }

        } else {
            if (consoleArea == null || consoleArea.isDisposed()) {
                consoleArea = new StyledText(sashForm, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
                consoleArea.setBackground(new Color(shell.getDisplay(), 0, 0, 0));
                consoleArea.setForeground(new Color(shell.getDisplay(), 255, 255, 255));
                consoleArea.setFont(new Font(shell.getDisplay(), "Consolas", 12, SWT.NONE));
                consoleArea.addVerifyKeyListener(new VerifyKeyListener() {
                    @Override
                    public void verifyKey(VerifyEvent e) {
                        if (e.character == SWT.CR || e.character == SWT.LF) { // Detecta la tecla "Enter"
                            int endOffset = consoleArea.getCaretOffset() - 1; // -1 para no incluir el Enter que se acaba de pulsar
                            int startOffset = endOffset;
                            while (startOffset > 0 && consoleArea.getText(startOffset - 1, startOffset).charAt(0) != '\n') {
                                startOffset--;
                            }
                            String command = consoleArea.getText(startOffset, endOffset);
                            log.info("Command: {}", command);
                            sendCommandToSqlPlus(command);
                        }
                    }
                });
                consoleArea.addVerifyListener(e -> {
                    if (!isAppModification) {
                        // Evita la inserción de saltos de línea que no estén al final
                        if (e.text.contains("\n") || e.text.contains("\r")) {
                            e.doit = false;
                            return;
                        }

                        int endOfText = consoleArea.getText().length();
                        if (e.start != endOfText) {
                            e.start = endOfText;
                            e.end = endOfText;
                        }
                    }
                });
                consoleArea.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        int endOfText = consoleArea.getText().length();
                        int currentCaretPosition = consoleArea.getCaretOffset();

                        // Permitir combinaciones de teclas para seleccionar texto
                        if (e.keyCode == SWT.CTRL) {
                            return;
                        }

                        // Si está seleccionando texto, permite hacerlo
                        if (consoleArea.getSelectionCount() > 0) {
                            int selectionStart = consoleArea.getSelectionRange().x;
                            int selectionEnd = selectionStart + consoleArea.getSelectionRange().y;
                            int lastLineStartOffset = consoleArea.getOffsetAtLine(consoleArea.getLineCount() - 1);

                            if (e.keyCode == SWT.DEL || e.keyCode == SWT.BS) {
                                if (selectionStart < lastLineStartOffset) {
                                    e.doit = false; // Cancela el evento de la tecla
                                    return;
                                }
                            }
                        }



                        if (currentCaretPosition < consoleArea.getOffsetAtLine(consoleArea.getLineCount() - 1)) {
                            consoleArea.setSelection(endOfText);
                            e.doit = false; // Cancela el evento de la tecla
                        }
                    }
                });

                consoleArea.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseUp(MouseEvent e) {
                        // Si el usuario está seleccionando texto, no hacemos nada
                        if (consoleArea.getSelectionCount() > 0) {
                            return;
                        }

                        int endOfText = consoleArea.getText().length();
                        int currentCaretPosition = consoleArea.getCaretOffset();

                        int lastLineStartOffset = consoleArea.getOffsetAtLine(consoleArea.getLineCount() - 1);
                        if (currentCaretPosition < lastLineStartOffset) {
                            consoleArea.setSelection(endOfText);
                        }
                    }
                });

            }
            sashForm.layout();
            sashForm.setWeights(new int[]{50, 50}); // Divide la ventana en mitades iguales
        }



    }

    private static Process sqlplusProcess; // Asume que esta es la instancia de tu proceso sqlplus

    private static void sendCommandToSqlPlus(String command) {
        int widgetWidth = consoleArea.getBounds().width; // Ancho del widget

        // Estimar el ancho de un carácter usando GC (Graphics Context) para medir
        GC gc = new GC(consoleArea);
        gc.setFont(new Font(consoleArea.getDisplay(), "Consolas", 12, SWT.NONE));
        int charWidth = gc.stringExtent("W").x;  // Usamos 'W' porque suele ser uno de los caracteres más anchos
        gc.dispose();

        int approxCharsPerLine = widgetWidth / charWidth;

        if (sqlplusProcess != null) {
            try {
                OutputStream os = sqlplusProcess.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
                writer.write("SET LINESIZE "+approxCharsPerLine+"\n");
                writer.newLine();
                //writer.write("SET PAGESIZE 0\n");
                //writer.newLine();
                writer.write("set sqlprompt ''\n");
                writer.newLine();
                writer.write("set sqlnumber off\n");
                writer.newLine();
                writer.write(command);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            Display.getDefault().asyncExec(() -> {
                isAppModification = true;
                consoleArea.setText("No active connection\n");
                isAppModification = false;
                consoleArea.setSelection(consoleArea.getText().length());
            });
        }
    }



    private static void hideConsole() {
        if (consoleArea != null && !consoleArea.isDisposed()) {
            consoleArea.setVisible(false);  // Oculta la consola
        }

        sashForm.layout();
    }



    private static void updateConnectionsMenu() {
        // Primero, remueve todos los ítems existentes
        for (MenuItem item : connectionsMenu.getItems()) {
            item.dispose();
        }

        // Luego, agrega las conexiones y el separador, si es necesario
        List<ConnectionInfo> connections = ConnectionManager.getConnections();
        for (ConnectionInfo connection : connections) {
            MenuItem connectionItem = new MenuItem(connectionsMenu, SWT.CHECK);
            connectionItem.setText(connection.toString());
            connectionItem.setSelection(false);

            if (activeConnection != null) {
                String connectionString = String.format("%s/%s@%s:%s/%s",
                        connection.getUser(),
                        connection.getPassword(),
                        connection.getHost(),
                        connection.getPort(),
                        connection.getServiceName());
                String activeConnectionString = String.format("%s/%s@%s:%s/%s",
                        activeConnection.getUser(),
                        activeConnection.getPassword(),
                        activeConnection.getHost(),
                        activeConnection.getPort(),
                        activeConnection.getServiceName());
                if (connectionString.equals(activeConnectionString))
                    connectionItem.setSelection(true); // Poner un tick si es la conexión activa

            }

            connectionItem.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    // Muestra la consola
                    showConsole();

                    // Ejecuta sqlplus con los datos de la conexión
                    executeSqlPlus(connection);

                    updateConnectionsMenu();
                }
            });
        }

        if (!connections.isEmpty()) {
            new MenuItem(connectionsMenu, SWT.SEPARATOR);
        }

        MenuItem manageConnectionsItem = new MenuItem(connectionsMenu, SWT.PUSH);
        manageConnectionsItem.setText("Manage Connections");
        manageConnectionsItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openManageConnectionsWindow(shell);
            }
        });

        shell.layout(true, true);
        while (Display.getCurrent().readAndDispatch()) ;
    }

    private static void executeSqlPlus(ConnectionInfo connection) {
        activeConnection = connection; // Establecer la conexión activa

        try {
            tablesInfo = getTablesInfo(connection);
            log.info("Tables: {}", tablesInfo);

            // Convertir la lista de TableInfo a un Map<String, List<String>>
            Map<String, List<String>> tableMap = new HashMap<>();
            for (TableInfo tableInfo : tablesInfo) {
                tableMap.put(tableInfo.getTableName(),
                        tableInfo.getColumns().stream().map(ColumnInfo::getColumnName).collect(Collectors.toList()));
            }

            // Convertir el Map a JSON usando Gson
            Gson gson = new Gson();
            String json = gson.toJson(tableMap);

            // Formatea el JSON para que se ajuste al formato esperado por tu función JavaScript
            String formattedJson = "injectTables(" + json + ");";

            log.info("formattedJson: {}", formattedJson);
            browser.execute(formattedJson);
        }catch (Exception e) {
            activeConnection = null;
            log.error("Error al conectar a la DB: {}", e.getMessage());
            Display.getDefault().asyncExec(() -> {
                isAppModification = true;
                consoleArea.append(e.getMessage() + "\n");
                isAppModification = false;
                consoleArea.setSelection(consoleArea.getText().length());
            });
            if (sqlplusProcess != null)
            sqlplusProcess.destroy();

            sqlplusProcess = null;
            return;
        }




        String command = String.format("sqlplus\\sqlplus.exe %s/%s@%s:%s/%s",
                connection.getUser(),
                connection.getPassword(),
                connection.getHost(),
                connection.getPort(),
                connection.getServiceName());
        if (!isServiceConnection){
            command = String.format("sqlplus\\sqlplus.exe %s/%s@%s:%s:%s",
                    connection.getUser(),
                    connection.getPassword(),
                    connection.getHost(),
                    connection.getPort(),
                    connection.getServiceName());
        }

        try {
            sqlplusProcess  = Runtime.getRuntime().exec(command);

            // Iniciar un hilo para leer la salida de sqlplus
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(sqlplusProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().equals("SQL>")) {  // Ignora líneas que solo contienen "SQL> "
                            String finalLine = line.replace("SQL> ", "");
                            Display.getDefault().asyncExec(() -> {
                                isAppModification = true;
                                consoleArea.append(finalLine + "\n");
                                isAppModification = false;
                                consoleArea.setSelection(consoleArea.getText().length());
                            });
                        }
                    }
                } catch (IOException e) {
                    Display.getDefault().asyncExec(() -> {
                        consoleArea.append("Error al leer la salida de sqlplus: " + e.getMessage() + "\n");
                    });
                }


            }).start();

            // Similarmente, podrías iniciar otro hilo para leer la salida de error si es necesario

        } catch (IOException e) {
            consoleArea.setText("Error al ejecutar sqlplus: " + e.getMessage());
        }
    }

    private static List<TableInfo> getTablesInfo(ConnectionInfo connection) {
        List<TableInfo> tablesInfo = new ArrayList<>();

        // 1. Cargar dinámicamente el archivo .jar del driver
        String driverFolderPath = "drivers/" + connection.getDriver();
        File folder = new File(driverFolderPath);
        File[] listOfFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

        if (listOfFiles == null || listOfFiles.length == 0) {
            throw new RuntimeException("No se encontró el archivo .jar en la carpeta del driver " + connection.getDriver());
        }

        String driverJarPath = listOfFiles[0].getAbsolutePath();
        try {
            //DriverLoader.loadDriver(driverJarPath);
            URL u = new URL("jar:file:" + driverJarPath + "!/");
            String classname = "oracle.jdbc.driver.OracleDriver";
            URLClassLoader ucl = new URLClassLoader(new URL[]{u});
            Driver d = (Driver) Class.forName(classname, true, ucl).newInstance();
            DriverManager.registerDriver(new DriverShim(d));
            Connection connect = null;
            try {
                isServiceConnection = false;
                connect = DriverManager.getConnection(String.format("jdbc:oracle:thin:@%s:%s:%s", connection.getHost(), connection.getPort(), connection.getServiceName()), connection.getUser(), connection.getPassword());
            } catch (java.sql.SQLException e) {
                if (e.getMessage().contains("ORA-01017")) {
                    log.error("Usuario o contraseña incorrectos. {}", e.getMessage());
                    throw new RuntimeException("Usuario o contraseña incorrectos");
                }
                if (e.getMessage().contains("ORA-12505")) {
                    isServiceConnection = true;
                    connect = DriverManager.getConnection(String.format("jdbc:oracle:thin:@%s:%s/%s", connection.getHost(), connection.getPort(), connection.getServiceName()), connection.getUser(), connection.getPassword());
                }
                //log.error("Error al conectar a la base de datos: {}", e.getMessage());
                //throw new RuntimeException("Error connecting to Database: " + e.getMessage());
            }


            log.info("Driver JDBC cargado exitosamente {}", driverJarPath);
            //use connect to retrieve tables names
            if (connect != null){
                log.info("Conexión exitosa");
                DatabaseMetaData metadata = connect.getMetaData();
                ResultSet tables = metadata.getTables(null, connection.getUser(), null, new String[]{"TABLE"});
                tablesInfo.clear();
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    TableInfo tableInfo = new TableInfo(tableName);

                    ResultSet columns = metadata.getColumns(null, null, tableName, null);
                    while (columns.next()) {
                        String columnName = columns.getString("COLUMN_NAME");
                        String columnType = columns.getString("TYPE_NAME");
                        tableInfo.addColumn(new ColumnInfo(columnName, columnType));
                    }

                    tablesInfo.add(tableInfo);
                }
                connect.close();
                log.info("tablesInfo: {}", tablesInfo);
                return tablesInfo;
            }else{
                throw new RuntimeException("Error connecting to database");
            }


        } catch (Exception e) {
            log.error("Error al cargar el driver JDBC: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }

    }




    private static void openManageConnectionsWindow(Shell parentShell) {
        Shell shell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        shell.setText("Manage Connections");
        shell.setSize(300, 200);

        // Layout para la ventana
        GridLayout layout = new GridLayout(2, false); // Dos columnas: una para la lista y otra para los botones
        shell.setLayout(layout);

        // Lista de conexiones
        connectionsList = new org.eclipse.swt.widgets.List(shell, SWT.BORDER | SWT.V_SCROLL);
        connectionsList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Actualizar la lista de conexiones
        updateConnectionsList();

        // Composite para agrupar los botones
        Composite buttonsComposite = new Composite(shell, SWT.NONE);
        buttonsComposite.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
        buttonsComposite.setLayout(new GridLayout(1, true));

        // Botón para añadir
        Button addButton = new Button(buttonsComposite, SWT.PUSH);
        addButton.setText("Add");
        addButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        addButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                openAddOrEditConnectionWindow(shell, null);

            }
        });


        // Botón para editar
        Button editButton = new Button(buttonsComposite, SWT.PUSH);
        editButton.setText("Edit");
        editButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        editButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int selectedIndex = connectionsList.getSelectionIndex();
                if (selectedIndex != -1) {
                    ConnectionInfo selectedConnection = ConnectionManager.getConnections().get(selectedIndex);
                    openAddOrEditConnectionWindow(shell, selectedConnection);
                }
            }
        });


// Botón para eliminar
        Button deleteButton = new Button(buttonsComposite, SWT.PUSH);
        deleteButton.setText("Delete");
        deleteButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        deleteButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int selectedIndex = connectionsList.getSelectionIndex();
                if (selectedIndex != -1) {
                    MessageBox messageBox = new MessageBox(shell, SWT.YES | SWT.NO | SWT.ICON_WARNING);
                    messageBox.setMessage("Are you sure you want to delete this connection?");
                    int response = messageBox.open();
                    if (response == SWT.YES) {
                        String userToDelete = connectionsList.getItem(selectedIndex);
                        ConnectionManager.deleteConnection(userToDelete);
                        updateConnectionsList();
                        updateConnectionsMenu();
                    }
                }
            }
        });

        // En la ventana "Manage Connections", donde defines tus componentes...
        Button btnConnect = new Button(shell, SWT.NONE);
        btnConnect.setText("Connect");
        btnConnect.setBounds(260, 270, 80, 25);

        btnConnect.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                List<ConnectionInfo> allConnections = ConnectionManager.getConnections();

                // Obtener la conexión seleccionada del listado
                int selectedIndex = connectionsList.getSelectionIndex();
                if (selectedIndex != -1) {
                    ConnectionInfo selectedConnection = allConnections.get(selectedIndex);
                    showConsole();
                    executeSqlPlus(selectedConnection);

                    updateConnectionsMenu();

                    // Cierra la ventana de Manage Connections
                    shell.close();
                } else {
                    // Puedes mostrar un mensaje indicando que se debe seleccionar una conexión primero.
                    MessageBox messageBox = new MessageBox(shell, SWT.ICON_WARNING | SWT.OK);
                    messageBox.setMessage("Please select a connection from the list.");
                    messageBox.open();
                }
            }
        });




        shell.open();
    }

    private static void openAddOrEditConnectionWindow(Shell parentShell, ConnectionInfo existingConnection) {

        Shell shell = new Shell(parentShell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        shell.setText("Add Connection");
        shell.setSize(300, 250);

        shell.setLayout(new GridLayout(2, false));

        Label userLabel = new Label(shell, SWT.NONE);
        userLabel.setText("User:");
        Text userText = new Text(shell, SWT.BORDER);
        userText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label passwordLabel = new Label(shell, SWT.NONE);
        passwordLabel.setText("Password:");
        Text passwordText = new Text(shell, SWT.BORDER | SWT.PASSWORD);
        passwordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label hostLabel = new Label(shell, SWT.NONE);
        hostLabel.setText("Host:");
        Text hostText = new Text(shell, SWT.BORDER);
        hostText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label portLabel = new Label(shell, SWT.NONE);
        portLabel.setText("Port:");
        Text portText = new Text(shell, SWT.BORDER);
        portText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label serviceNameLabel = new Label(shell, SWT.NONE);
        serviceNameLabel.setText("Service Name:");
        Text serviceNameText = new Text(shell, SWT.BORDER);
        serviceNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label dialectLabel = new Label(shell, SWT.NONE);
        dialectLabel.setText("Dialect:");
        Combo dialectCombo = new Combo(shell, SWT.DROP_DOWN | SWT.READ_ONLY);
        dialectCombo.setItems("org.hibernate.dialect.OracleDialect",
                "org.hibernate.dialect.Oracle9Dialect",
                "org.hibernate.dialect.Oracle9iDialect",
                "org.hibernate.dialect.Oracle10gDialect",
                "org.hibernate.dialect.Oracle12cDialect");
        dialectCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Label driverLabel = new Label(shell, SWT.NONE);
        driverLabel.setText("Driver:");
        Combo driverCombo = new Combo(shell, SWT.DROP_DOWN | SWT.READ_ONLY);
        //get folder names from drivers folder
        File folder = new File("drivers");
        File[] listOfFiles = folder.listFiles();
        String[] items = new String[listOfFiles.length];
        for (int i = 0; i < listOfFiles.length; i++) {
            items[i] = listOfFiles[i].getName();
        }
        driverCombo.setItems(items);
        driverCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));


        Button saveButton = new Button(shell, SWT.PUSH);
        saveButton.setText("Save");
        saveButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        saveButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (existingConnection != null) {
                    ConnectionManager.deleteConnection(existingConnection.getUser());
                }

                ConnectionInfo newConnection = new ConnectionInfo();
                newConnection.setUser(userText.getText());
                newConnection.setPassword(passwordText.getText());
                newConnection.setHost(hostText.getText());
                newConnection.setPort(portText.getText());
                newConnection.setServiceName(serviceNameText.getText());
                newConnection.setDialect(dialectCombo.getText());
                newConnection.setDriver(driverCombo.getText());

                // Guardar la nueva conexión
                ConnectionManager.saveConnection(newConnection);

                try {
                    Thread.sleep(500);  // esperar 500 milisegundos
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

                updateConnectionsList();
                updateConnectionsMenu();

                // Cerrar la ventana después de guardar
                shell.close();
            }
        });

        if (existingConnection != null) {
            userText.setText(existingConnection.getUser());
            passwordText.setText(existingConnection.getPassword());
            hostText.setText(existingConnection.getHost());
            portText.setText(existingConnection.getPort());
            serviceNameText.setText(existingConnection.getServiceName());
            if (existingConnection.getDialect() != null)
                dialectCombo.setText(existingConnection.getDialect());
            if (existingConnection.getDriver() != null)
                driverCombo.setText(existingConnection.getDriver());
        }


        shell.pack();
        shell.open();
    }

    private static void updateConnectionsList() {
        connectionsList.removeAll();
        for (ConnectionInfo connection : ConnectionManager.getConnections()) {
            connectionsList.add(connection.getUser());
        }
        connectionsList.redraw();
        while (Display.getCurrent().readAndDispatch()) ;
    }


    private static void newFile(Shell shell, Browser browser) {


        try {
            config.setCurrentFilePath("");
            shell.setText(APP_NAME);
            String script = "editor.setValue(\"\");";
            browser.execute(script);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Método para abrir un archivo y mostrar su contenido en el editor
    private static void openFile(Shell shell, Browser browser) {
        FileDialog fileDialog = new FileDialog(shell, SWT.OPEN);
        String path = fileDialog.open();
        if (path != null) {
            openFile(path);
        }
    }

    private static void openFile(String path){
        File file = new File(path);
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            log.info("Reading file: " + file.getAbsolutePath());
            while ((line = reader.readLine()) != null) {
                log.info(line);
                content.append(line).append("\n");
            }
            config.setCurrentFilePath(file.getAbsolutePath());
            String fileName = file.getName();
            shell.setText(fileName + " - " + APP_NAME);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Escapar el contenido para ser utilizado en una cadena JavaScript
        String escapedContent = content.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");

        // Utilizar JavaScript para establecer el contenido en el editor
        String script = "editor.setValue(\"" + escapedContent + "\");"; // Asegúrate de que 'editor' sea la variable que contiene tu instancia de CodeMirror
        browser.execute(script);

    }

    // Método para guardar el contenido del editor en un archivo
    private static void saveFile(Shell shell, Browser browser, Boolean saveAs) {
        File file = null;
        if (!saveAs && !config.getCurrentFilePath().isEmpty()){
            file = new File(config.getCurrentFilePath());
        }else{
            FileDialog fileDialog = new FileDialog(shell, SWT.SAVE);
            fileDialog.setOverwrite(true); // Pide confirmación si el archivo ya existe
            String path = fileDialog.open();
            if (path != null) {
                file = new File(path);
            }else {
                return;
            }
        }

        try {
            // Obtener el contenido del editor a través de JavaScript
            String script = "return editor.getValue();"; // Asegúrate de que 'editor' sea la variable que contiene tu instancia de CodeMirror
            String content = (String) browser.evaluate(script);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                writer.write(content);
            }
            config.setCurrentFilePath(file.getAbsolutePath());
        } catch (IOException e) {
            // Manejar la excepción aquí
            e.printStackTrace();
        }

    }

    private static void deploy(String fileName){
        if (!new File(fileName).exists()){
            //copy from jar
            InputStream inwb = Editor.class.getResourceAsStream("/"+fileName+".zip");
            try {
                Files.copy(inwb, Paths.get(fileName+".zip"));
                //unzip
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "Expand-Archive -Path "+fileName+".zip -DestinationPath .\\"+fileName);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info(line);
                }
                p.waitFor();
                //delete zip
                Files.delete(Paths.get(fileName+".zip"));
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
