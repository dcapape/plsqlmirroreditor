package com.diegocapape.util;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;

public class Config {
    private static final String CONFIG_FILE = "config.ini";
    private static final String GROUP_EDITOR = "Editor";
    private static final String GROUP_GENERAL = "General";

    private Wini ini;

    public Config() throws IOException {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            generateDefaultConfig(file);
        }
        ini = new Wini(file);
    }

    private void generateDefaultConfig(File file) throws IOException {
        Wini ini = new Wini();
        ini.put(GROUP_EDITOR, "snippetsPath", "snippets.json");
        ini.put(GROUP_EDITOR, "suggestionsPath", "suggestions.json");
        ini.put(GROUP_GENERAL, "CurrentFilePath", null);
        ini.store(file);
    }

    public String getSnippetsPath() {
        return ini.get(GROUP_EDITOR, "snippetsPath", String.class);
    }

    public void setSnippetsPath(String path) throws IOException {
        ini.put(GROUP_EDITOR, "snippetsPath", path);
        ini.store();
    }

    public String getSuggestionsPath() {
        return ini.get(GROUP_EDITOR, "suggestionsPath", String.class);
    }

    public void setSuggestionsPath(String path) throws IOException {
        ini.put(GROUP_EDITOR, "suggestionsPath", path);
        ini.store();
    }

    public String getCurrentFilePath() {
        return ini.get(GROUP_GENERAL, "CurrentFilePath", String.class);
    }

    public void setCurrentFilePath(String path) throws IOException {
        ini.put(GROUP_GENERAL, "CurrentFilePath", path);
        ini.store();
    }

    public static void main(String[] args) throws IOException {
        // Ejemplo de uso
        Config config = new Config();
        System.out.println("SnippetsPath: " + config.getSnippetsPath());
        System.out.println("SuggestionsPath: " + config.getSuggestionsPath());

        config.setSnippetsPath("snippets.json");
        config.setSuggestionsPath("suggestions.json");

        System.out.println("Updated SnippetsPath: " + config.getSnippetsPath());
        System.out.println("Updated SuggestionsPath: " + config.getSuggestionsPath());
    }
}
