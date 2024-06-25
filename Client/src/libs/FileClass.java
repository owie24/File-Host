package libs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileClass {
    public final File file;
    public long modified;
    public String name;
    public long dataSize;
    public boolean isDir;
    public String type;
    List<FileClass> fileList;

    public FileClass(File file) {
        this.file = file;
        modified = file.lastModified();
        name = file.getName();
        dataSize = file.length();
        isDir = file.isDirectory();
        fileList = new ArrayList<>();
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) fileList.add(new FileClass(f));
        }
        if (isDir) type = null;
        else {
            String temp;
            if (file.getName().contains(".") && file.getName().substring(file.getName().lastIndexOf(".") + 1).equals("deleted")) {
                temp = file.getName().substring(0, file.getName().lastIndexOf(".") - 1);
                type = temp.substring(temp.lastIndexOf('.') + 1);
            }
            else {
                temp = file.getName();
                type = temp.substring(temp.lastIndexOf('.') + 1);
            }
        }
    }

    public File file() { return file;}

    public boolean isDir() {return isDir;}

    public long getModified() {return modified;}


    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        else if (!o.getClass().equals(this.getClass())) return false;
        else {
            FileClass otherFile = (FileClass) o;
            if (otherFile.isDir != this.isDir) return false;
            else if (this.isDir) {
                if (this.fileList.size() != otherFile.fileList.size()) return false;
                else {
                    for (FileClass f : this.fileList) {
                        if (!otherFile.fileList.contains(f)) return false;
                    }
                    return this.name.equals(otherFile.name);
                }
            }
            else return this.name.equals(otherFile.name) && this.dataSize == otherFile.dataSize && this.modified == otherFile.modified && this.type.equals(otherFile.type);
        }
    }
}
