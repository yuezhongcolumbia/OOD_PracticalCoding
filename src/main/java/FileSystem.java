import javax.management.DescriptorKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//"Design an in-memory file system that supports creating files and directories," +
//        " navigating paths, and basic file operations.
//
//Requirements:
//1.create file/create directory;
//2.navigating absolute path
//
//4. what are basic file operations;
//    1. create;
//    2. delete;
//    3. list;
//    4. move;
//    5. reanme;
//5. file contains content
//
//Entity;
//1. FileSystem;
//2. folder;
//3. file;
//
//Entity Design;
//1. FileSystem;
//    - FileSystemEntity root;
//    action:
//        boolean createFile(String)
//        boolean createFolder (String "home/1/2/3")
//        boolean delete(string)
//        List<String> list(string)
//        boolean move(String src, String dest) edge case: when src is dest subtree
//        FileSystemEntity navigate(String)
//
//2. FileSystemEntity;
//        File;
//            - String name
//            - String content
//        Folder;
//            - String name
//            -Map<Name, FileSystemEntity>
//            addFile(File)
//            addFolder(Folder)
//            FileSystemEntity getEntity(String)
//            boolean deleteEntity(String)
//            List<FileSystemEntity> displayChildren()

public class FileSystem {
    private final Folder root = new Folder("/");

    public FileSystem() {

    }

    public static void main(String[] args) {
        FileSystem fileSystem = new FileSystem();
        //createFolder
        fileSystem.createFolder("/photo");
        fileSystem.createFolder("/photo");
        fileSystem.createFolder("/movie");
        fileSystem.createFolder("/document");
        fileSystem.createFolder("/photo/2025");
        fileSystem.createFolder("/photo/2025/2025_May");
        fileSystem.createFolder("/photo/2025/2025_April");
        //createfile
        fileSystem.createFile("/photo/2025/2025_April/photoList.txt");
        fileSystem.list("/photo/2025/2025_April");
        //move
        fileSystem.move("/photo/2025/2025_April/photoList.txt", "/photo/2025/2025_May");
        fileSystem.list("/photo/2025/2025_May");
//        fileSystem.move("/photo/2025", "/photo/2025/2025_May");
//        fileSystem.move("/photo/2025/2025_May", "/photo/2025/2025_May");
        //delete
//        fileSystem.delete("/photo/2025/2025_April/photoList.txt");
        fileSystem.delete("/photo/2025/2025_May/photoList.txt");
        fileSystem.list("/photo/2025/2025_May");
        //rename
        fileSystem.rename("/photo/2025/2025_May", "2025_June");
        fileSystem.list("/photo/2025/");
//        fileSystem.rename("/photo/2025/2025_June", "2025_June");

    }

    public boolean createFile(String path) {
        //validate fileName
        String fileName = resolveEntityName(path);
        String parentPath = resolveParentPath(path);
        if (parentPath.equals("/")) {
            throw new RuntimeException("path cannot be null or create file in root directory");
        }
        Folder targetFolder = navigatePath(parentPath);
        FileSystemEntity file = new File(fileName);
        return targetFolder.addChild(file);
    }

    public boolean createFolder(String path) {
        String folderName = resolveEntityName(path);
        if (folderName.endsWith(".txt")){
            throw new RuntimeException("not valid folderName");
        }
        String parentPath = resolveParentPath(path);
        Folder targetFolder = navigatePath(parentPath);
        FileSystemEntity folder = new Folder(folderName);
        return targetFolder.addChild(folder);
    }

    public boolean delete(String path) {
        if (path.equals("/")){
            throw new RuntimeException("cannot delete home directory");
        }
        String entityName = resolveEntityName(path);
        String parentPath = resolveParentPath(path);
        Folder targetFolder = navigatePath(parentPath);
        targetFolder.delete(entityName);
        return true;
    }

    public void move(String source, String destination){
//        a
//     a    b    c
//             d   e
//            f
//                     a/b/c  a/c/d
//       //detect if destination is a subfolder of source, we cannot traverse source to find destiantion
//        //name, there can be same name, one way would be a parent pointer, when compare
//        core logic: find destination parentFolder, add source, delete source from original parent
        String srcParentPath = resolveParentPath(source);
        Folder srcParentFolder = navigatePath(srcParentPath);
        String srcName = resolveEntityName(source);
        FileSystemEntity src = srcParentFolder.getChild(srcName);

//        String destParentPath = resolveParentPath(destination);
//        Folder destParentFolder = navigatePath(destParentPath);
//        FileSystemEntity dest = destParentFolder.getChild(destParentPath);
//        if (!dest.isDirectory()){
//            throw new RuntimeException("destination is not a directory");
//        }
        Folder destFolder = navigatePath(destination);
        //edge case 1: src is a folder and dest is subfolder of src
        if (src.isDirectory()){
            Folder curParent = destFolder;
            while(curParent!=null){
                if (curParent == (Folder) src){
                    throw new RuntimeException("destination is subFolder of source");
                }
                curParent = curParent.getParent();
            }
        }
        //edge case 2: duplicate name in destination
        if (destFolder.hasChild(src.getName())){
            throw new RuntimeException("destination already exists file or folder of same name");
        }
        srcParentFolder.delete(src.getName());
        destFolder.addChild(src);
    }

    public void rename(String path, String newName) {
        if (path.equals("/")) {
            throw new IllegalArgumentException("Cannot rename root");
        }

        if (newName == null || newName.isEmpty() || newName.contains("/")) {
            throw new IllegalArgumentException("Invalid name");
        }
        //find the entity, set its new Name, check for duplicate name
        String parentPath = resolveParentPath(path);
        Folder parentFolder = navigatePath(parentPath);
        String targetName = resolveEntityName(path);
        FileSystemEntity target = parentFolder.getChild(targetName);
        parentFolder.renameChild(target, newName);

    }

    public List<String> list(String path){
        Folder targetFolder = navigatePath(path);
        List<String> list =  targetFolder.listChildren();
        System.out.println(list);
        return list;
    }

    public void writeFile(String path, String content){
        String fileName = resolveEntityName(path);
        String parentPath = resolveParentPath(path);
        Folder targetFolder = navigatePath(parentPath);
        FileSystemEntity entity = targetFolder.getChild(fileName);
        if (!(entity instanceof File)){
            throw new RuntimeException("file is not a file");
        }
        entity.setName(content);
    }



    public String resolveParentPath(String path) {
        int idx = path.lastIndexOf("/");
        return path.substring(0, idx).isEmpty() ? "/" : path.substring(0, idx) ;
    }

    public String resolveEntityName(String path){
        int idx = path.lastIndexOf("/");

        String subStr = path.substring(idx+1);
        if (subStr.isEmpty()) {
            throw new RuntimeException("Entity name is Empty");
        }
        return subStr;
    }

    public Folder navigatePath(String path) {
        if (!path.startsWith("/")) {
            throw new RuntimeException("Path must start with '/'");
        }

        if (path.equals("/")) {
            return root;
        }
        String[] pathArr = path.split("/");
        Folder cur = root;
        for (int i = 1; i < pathArr.length; i++) {
            String children = pathArr[i];
            //doesnt have children or is file, we have a problem, logic contains in helper method
            if (children.isEmpty()) {
                throw new RuntimeException("double slash");
            }
            FileSystemEntity childEntity = cur.getChild(children);
            if (!childEntity.isDirectory()) {
                throw new RuntimeException("component in the path is not a directory");
            }
            cur = (Folder) childEntity;
        }
        //last
        return cur;

    }


}

interface FileSystemEntity {
    String getName();
    void setName(String name);
    boolean isDirectory();
    Folder getParent();
    void setParent(Folder parent);
}

class File implements FileSystemEntity {
    private String name;
    private String content;
    private Folder parent;

    public File(String name) {
        this.name = name;
        content = "";
    }
    @Override
    public Folder getParent() {
        return parent;
    }
    @Override
    public void setParent(Folder parent) {
        this.parent = parent;
    }


    @Override
    public String getName() {
        return name ;
    }
    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

class Folder implements FileSystemEntity {
    private Folder parent;
    private String name;
    private final Map<String, FileSystemEntity> children = new HashMap<>();

    public Folder(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }


    @Override
    public boolean isDirectory() {
        return true;
    }
    @Override
    public Folder getParent(){
        return parent;
    }
    @Override
    public void setParent(Folder parent){
        this.parent = parent;
    }

    public boolean addChild(FileSystemEntity child) {
        if (child == null){
            return false;
        }
        String name = child.getName();
        if (children.containsKey(name)){
            return false;
        }
        children.put(name, child);
        child.setParent(this);
        return true;
    }

    public FileSystemEntity getChild(String name) {
        if (!children.containsKey(name)) {
            throw new RuntimeException("Child does not exist");
        }
        return children.get(name);
    }

    public boolean hasChild(String name){
        if (name == null || name.isEmpty()){
            throw new RuntimeException("Child name is null or empty");
        }
        return children.containsKey(name);
    }


    public FileSystemEntity delete(String childName) {
        if (!children.containsKey(childName)) {
            throw new RuntimeException("Child does not exist");
        }
        FileSystemEntity child = children.get(childName);
        children.remove(childName);
        child.setParent(null);
        return child;
    }

    public void renameChild(FileSystemEntity child, String newName) {
        if (!children.containsKey(child.getName())) {
            throw new RuntimeException("entity does not exist in this folder");
        }
        if (children.containsKey(newName)) {
            throw new RuntimeException("this folder already exists entity with same name");
        }
        children.remove(child.getName());
        child.setName(newName);
        children.put(newName, child);
    }

    public List<String> listChildren(){
        List<String> childrenList = new ArrayList<>();
        for(String name: children.keySet()){
            childrenList.add(name);
        }
        return childrenList;
    }

}






