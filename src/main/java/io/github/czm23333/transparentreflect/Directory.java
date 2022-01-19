package io.github.czm23333.transparentreflect;

import java.util.HashMap;
import java.util.Objects;

public class Directory {
    private final String realPath;
    private final HashMap<String, Directory> subDirectories = new HashMap<>();

    public Directory() {
        this.realPath = "";
    }

    public Directory(String realPath) {
        this.realPath = realPath;
    }

    public boolean isMark() {
        return Objects.equals(realPath, "");
    }

    public String getRealPath() {
        return realPath;
    }

    public boolean hasSubDirectory(String path) {
        return subDirectories.containsKey(path);
    }

    public Directory getSubDirectory(String path) {
        return subDirectories.get(path);
    }

    public void makeSubDirectory(String path, String realPath) {
        if (hasSubDirectory(path))
            throw new IllegalStateException("Sub directory already exists");
        subDirectories.put(path, new Directory(realPath));
    }
}
