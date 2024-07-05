package libs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FileClass {
    public FileClass parent;
    public FileClass topDir;
    public File file;
    public List<FileClass> subFiles;
    public boolean isDirectory;
    public String type;
    public String name;
    public long lastModified;
    public long fileSize;
    private List<FileClass> filesAdded;
    private List<FileClass> filesDeleted;


    public FileClass(File file, FileClass parent) {
        if (parent == null) topDir = this;
        else this.topDir = parent.topDir;
        this.parent = parent;
        this.file = file;
        subFiles = new ArrayList<>();
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) subFiles.add(new FileClass(f, this));
        }
        isDirectory = file.isDirectory();
        if (isDirectory || !file.getName().contains(".") || file.getName().lastIndexOf('.') == 0) {
            name = file.getName();
            type = null;
        }
        else {
            type = file.getName().substring(file.getName().lastIndexOf('.') + 1);
            name = file.getName().substring(0, file.getName().lastIndexOf('.') - 1);
        }
        lastModified = file.lastModified();
        fileSize = file.length();
    }

    /**
     *
     * @return list of files that were added
     */

    public Pair<List<File>, List<File>> getAdded() {
        List<File> folder = new ArrayList<>(), files = new ArrayList<>();
        for (FileClass f : filesAdded) {
            if (f.isDirectory) folder.add(f.file);
            else files.add(f.file);
        }
        return new Pair<>(folder, files);
    }

    /**
     *
     * @return List of files that were deleted
     */

    public List<File> getDeleted() {
        List<File> temp = new ArrayList<>();
        for (FileClass f : filesDeleted) temp.add(f.file);
        return temp;
    }

    /**
     * Must be called before getAdded and getDeleted
     * Finds all files added and deleted and those files that are equivalent are considered
     * renamed
     *
     * @param newFileList Alters newFileList
     * @return HashMap to from old file to new file
     */
    public HashMap<File, File> getRenamed(FileClass newFileList) {
        filesDeleted = FindDeleted(newFileList);
        filesAdded = FindAdded(newFileList);
        HashMap<File, File> renamed = new HashMap<>();
        List<FileClass> filesRemoveAdded = new ArrayList<>(), fileRemoveDeleted = new ArrayList<>();
        FileClass newFile;
        for (FileClass f : filesDeleted) {
            if (!f.isDirectory && (newFile = FindRenamed(f, filesAdded)) != null) {
                renamed.put(f.file, newFile.file);
                filesRemoveAdded.add(newFile);
                fileRemoveDeleted.add(f);
            }
        }
        filesAdded.removeAll(filesRemoveAdded);
        filesDeleted.removeAll(fileRemoveDeleted);
        return renamed;
    }

    private FileClass FindRenamed(FileClass search, List<FileClass> filesAdded) {
        for (FileClass f : filesAdded) {
            if (!f.isDirectory && f.type.equals(search.type) && f.lastModified == search.lastModified && f.fileSize == search.fileSize) return f;
        }
        return null;
    }

    private List<FileClass> FindAdded(FileClass newFileList) {
        List<FileClass> addedList = new ArrayList<>();
        for (FileClass f : newFileList.subFiles) {
            if (!this.subFiles.contains(f)) {
                addedList.add(f);
                if (f.isDirectory) addedList.addAll(AddRest(f));
            }
            else if (f.isDirectory) addedList.addAll(this.subFiles.get(this.subFiles.indexOf(f)).FindAdded(f));
        }
        return addedList;
    }

    private List<FileClass> AddRest(FileClass newFileList) {
        List<FileClass> added = new ArrayList<>();
        for (FileClass f : newFileList.subFiles) {
            added.add(f);
            if (f.isDirectory) added.addAll(AddRest(f));
        }
        return added;
    }

    private List<FileClass> FindDeleted(FileClass newFileList) {
        List<FileClass> deletedList = new ArrayList<>();
        for (FileClass f : this.subFiles) {
            if (!newFileList.subFiles.contains(f)) {
                deletedList.add(f);
                if (f.isDirectory) deletedList.addAll(AddRest(f));
            }
            else if (f.isDirectory) deletedList.addAll(f.FindDeleted(newFileList.subFiles.get(newFileList.subFiles.indexOf(f))));
        }
        return deletedList;
    }

    public boolean Difference(FileClass newFileList) {
        if (this.subFiles.size() != newFileList.subFiles.size()) return false;
        else {
            for (FileClass f : this.subFiles) {
                if (!newFileList.subFiles.contains(f)) return false;
                else if (f.isDirectory && !f.Difference(newFileList.subFiles.get(newFileList.subFiles.indexOf(f)))) return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        else if (o.getClass() != this.getClass()) return false;
        else {
            FileClass file = (FileClass) o;
            if (file.isDirectory != this.isDirectory) return false;
            else {
                if (file.isDirectory) {
                    return file.file.equals(this.file);
                }
                else {
                    if (this.type == null && file.type != null) return false;
                    else if (this.type == null) return file.lastModified == this.lastModified && file.fileSize == this.fileSize && file.name.equals(this.name);
                    else return file.lastModified == this.lastModified && file.type.equals(this.type) && file.fileSize == this.fileSize && file.name.equals(this.name);
                }
            }
        }
    }

    @Override
    public int hashCode() {
        return this.file.hashCode();
    }
}
