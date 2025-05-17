import java.io.*;
import java.util.*;

public class MiniOS {
    private static File currentDir = null; // 稍后初始化为PRTS Main目录
    private static File prevDir = null; // 新增：保存上一次目录
    private static String currentUser = null;
    private static final Map<String, String> users = new HashMap<>(); // 用户名->密码
    private static final Map<String, String> admins = new HashMap<>(); // 管理员用户名->密码

    // 文件所有者映射（文件绝对路径->用户名）
    private static final Map<String, String> fileOwners = new HashMap<>();

    // 强制users.txt和admins.txt在工作目录/PRTS/PRTS Main下
    private static final String BASE_DIR = System.getProperty("user.dir") + File.separator + "PRTS" + File.separator + "PRTS Main";
    private static final String USERS_FILE = new File(BASE_DIR, "users.txt").getAbsolutePath();
    private static final String ADMINS_FILE = new File(BASE_DIR, "admins.txt").getAbsolutePath();
    private static final String OWNERS_FILE = new File(BASE_DIR, "owners.txt").getAbsolutePath();

    public static void main(String[] args) throws IOException {
        // 强制进入PRTS/PRTS Main子目录
        String baseDir = System.getProperty("user.dir") + File.separator + "PRTS" + File.separator + "PRTS Workspace";
        currentDir = new File(baseDir).getCanonicalFile();
        
        loadUsers();
        loadAdmins();
        checkAdminUserConflict();
        loadOwners();

        Scanner scanner = new Scanner(System.in);
        System.out.println("PRTS 启动，输入 help 查看命令。");

        while (true) {
            if (currentUser == null) {
                System.out.print("未登录 > ");
                String input = scanner.nextLine();
                if (input.trim().isEmpty()) continue;
                String[] parts = input.trim().split("\\s+");
                if (parts.length == 0) continue;
                if ("login".equals(parts[0])) {
                    if (parts.length < 3) {
                        System.out.println("用法: login <用户名> <密码>");
                        continue;
                    }
                    String user = parts[1], pwd = parts[2];
                    if ((users.containsKey(user) && users.get(user).equals(pwd)) ||
                        (admins.containsKey(user) && admins.get(user).equals(pwd))) {
                        currentUser = user;
                        System.out.println("登录成功，当前用户: " + currentUser);
                    } else {
                        System.out.println("用户名或密码错误");
                    }
                } else if ("help".equals(parts[0])) {
                    System.out.println("请先登录。命令: login <用户名> <密码>");
                } else if ("exit".equals(parts[0])) {
                    System.out.println("退出 MiniOS");
                    scanner.close();
                    return;
                } else {
                    System.out.println("请先登录。");
                }
                continue;
            }

            System.out.print(currentUser + " @" + currentDir.getAbsolutePath() + " > ");
            String input = scanner.nextLine();
            if (input.trim().isEmpty()) continue;
            String[] parts = input.trim().split("\\s+");
            if (parts.length == 0) continue;
            String cmd = parts[0];
            switch (cmd) {
                case "ls":
                    File[] files = currentDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            // 获取相对路径（从PRTS开始）
                            String absPath = f.getAbsolutePath();
                            String basePath = BASE_DIR.substring(0, BASE_DIR.indexOf("PRTS"));
                            String relPath = absPath.startsWith(basePath) ? absPath.substring(basePath.length()) : absPath;
                            relPath = relPath.replace("\\", "/"); // 统一分隔符
                            String owner = null;
                            for (String key : fileOwners.keySet()) {
                                if (key.replace("\\", "/").equalsIgnoreCase(relPath)) {
                                    owner = fileOwners.get(key);
                                    break;
                                }
                            }
                            if (owner == null) owner = "未知";
                            System.out.println(f.getName() + " [owner: " + owner + "]");
                        }
                    }
                    break;
                case "cd":
                    if (parts.length < 2) break;
                    String cdTarget = input.trim().substring(2).trim();
                    if (cdTarget.startsWith("cd")) cdTarget = cdTarget.substring(2).trim();
                    if ((cdTarget.startsWith("\"") && cdTarget.endsWith("\"")) || (cdTarget.startsWith("'") && cdTarget.endsWith("'"))) {
                        cdTarget = cdTarget.substring(1, cdTarget.length() - 1);
                    }
                    if ("-".equals(cdTarget)) {
                        if (prevDir != null && prevDir.exists() && prevDir.isDirectory()) {
                            File temp = currentDir;
                            currentDir = prevDir.getCanonicalFile();
                            prevDir = temp;
                        } else {
                            System.out.println("没有上一级目录可返回");
                        }
                        break;
                    }
                    File next = new File(currentDir, cdTarget);
                    if (next.exists() && next.isDirectory()) {
                        prevDir = currentDir;
                        currentDir = next.getCanonicalFile();
                    } else {
                        System.out.println("目录不存在");
                    }
                    break;
                case "mkdir":
                    if (!admins.containsKey(currentUser)) {
                        System.out.println("无权限：只有管理员可以执行 mkdir。");
                        System.out.println("当前用户: " + currentUser);
                        for (String admin : admins.keySet()) {
                            System.out.println("管理员: " + admin);
                        }
                        break;
                    }
                    if (parts.length < 2) break;
                    // 支持带空格的目录名
                    String mkdirTarget = input.trim().substring(5).trim();
                    if ((mkdirTarget.startsWith("\"") && mkdirTarget.endsWith("\"")) ||
                        (mkdirTarget.startsWith("'") && mkdirTarget.endsWith("'"))) {
                        mkdirTarget = mkdirTarget.substring(1, mkdirTarget.length() - 1);
                    }
                    if (mkdirTarget.isEmpty()) break;
                    File dir = new File(currentDir, mkdirTarget);
                    if (dir.mkdir()) {
                        // 存储相对路径
                        String absPath = dir.getAbsolutePath();
                        String basePath = BASE_DIR.substring(0, BASE_DIR.indexOf("PRTS"));
                        String relPath = absPath.startsWith(basePath) ? absPath.substring(basePath.length()) : absPath;
                        relPath = relPath.replace("\\", "/");
                        fileOwners.put(relPath, currentUser);
                        saveOwners();
                    }
                    break;
                case "showpath":
                    System.out.println("users.txt 路径: " + USERS_FILE);
                    System.out.println("admins.txt 路径: " + ADMINS_FILE);
                    break;
                case "touch":
                    if (parts.length < 2) break;
                    File newFile = new File(currentDir, parts[1]);
                    try {
                        if (newFile.createNewFile()) {
                            String absPath = newFile.getAbsolutePath();
                            String basePath = BASE_DIR.substring(0, BASE_DIR.indexOf("PRTS"));
                            String relPath = absPath.startsWith(basePath) ? absPath.substring(basePath.length()) : absPath;
                            relPath = relPath.replace("\\", "/");
                            fileOwners.put(relPath, currentUser);
                            saveOwners();
                        }
                    } catch (IOException e) {
                        System.out.println("创建文件失败: " + e.getMessage());
                    }
                    break;
                case "cat":
                    if (parts.length < 2) break;
                    File file = new File(currentDir, parts[1]);
                    if (file.exists()) {
                        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = br.readLine()) != null)
                                System.out.println(line);
                        } catch (IOException e) {
                            System.out.println("读取文件失败: " + e.getMessage());
                        }
                    } else {
                        System.out.println("文件不存在");
                    }
                    break;
                case "echo":
                    if (parts.length < 3) break;
                    // 拼接文本内容，支持空格
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < parts.length - 1; i++) {
                        sb.append(parts[i]);
                        if (i != parts.length - 2) sb.append(" ");
                    }
                    File echoFile = new File(currentDir, parts[parts.length - 1]);
                    String owner = fileOwners.get(echoFile.getAbsolutePath());
                    if (owner == null || owner.equals(currentUser) || admins.containsKey(currentUser)) {
                        try (PrintWriter pw = new PrintWriter(new FileWriter(echoFile, true))) {
                            pw.println(sb.toString());
                        } catch (IOException e) {
                            System.out.println("写入文件失败: " + e.getMessage());
                        }
                        if (owner == null) {
                            String absPath = echoFile.getAbsolutePath();
                            String basePath = BASE_DIR.substring(0, BASE_DIR.indexOf("PRTS"));
                            String relPath = absPath.startsWith(basePath) ? absPath.substring(basePath.length()) : absPath;
                            relPath = relPath.replace("\\", "/");
                            fileOwners.put(relPath, currentUser);
                            saveOwners();
                        }
                    } else {
                        System.out.println("无权限写入该文件");
                    }
                    break;
                case "rm":
                    if (!admins.containsKey(currentUser)) {
                        System.out.println("无权限：只有管理员可以执行 rm。");
                        break;
                    }
                    // 管理员执行rm时要求输入密码验证
                    System.out.print("请输入管理员密码以确认删除操作: ");
                    String rmPwd = new Scanner(System.in).nextLine();
                    if (!admins.get(currentUser).equals(rmPwd)) {
                        System.out.println("密码错误，操作已取消。");
                        break;
                    }
                    if (parts.length < 2) break;
                    String rmTarget = input.trim().substring(2).trim();
                    if (rmTarget.startsWith("rm")) rmTarget = rmTarget.substring(2).trim();
                    if ((rmTarget.startsWith("\"") && rmTarget.endsWith("\"")) || (rmTarget.startsWith("'") && rmTarget.endsWith("'"))) {
                        rmTarget = rmTarget.substring(1, rmTarget.length() - 1);
                    }
                    File delFile = new File(currentDir, rmTarget);
                    // 检查是否为最基层目录（PRTS Workspace），禁止删除
                    File baseWorkspace = new File(System.getProperty("user.dir") + File.separator + "PRTS" + File.separator + "PRTS Workspace");
                    if (delFile.getCanonicalFile().equals(baseWorkspace.getCanonicalFile())) {
                        System.out.println("禁止删除最基层目录: " + baseWorkspace.getName());
                        break;
                    }
                    String delOwner = fileOwners.get(delFile.getAbsolutePath());
                    if (delOwner == null || delOwner.equals(currentUser) || admins.containsKey(currentUser)) {
                        boolean deleted = deleteRecursively(delFile);
                        if (deleted) {
                            String absPath = delFile.getAbsolutePath();
                            String basePath = BASE_DIR.substring(0, BASE_DIR.indexOf("PRTS"));
                            String relPath = absPath.startsWith(basePath) ? absPath.substring(basePath.length()) : absPath;
                            relPath = relPath.replace("\\", "/");
                            fileOwners.remove(relPath);
                            saveOwners();
                            System.out.println("删除成功");
                        } else {
                            System.out.println("删除失败");
                        }
                    } else {
                        System.out.println("无权限删除该文件/目录");
                    }
                    break;
                case "create":
                    // 用法: create <文件名>
                    if (parts.length < 2) {
                        System.out.println("用法: create <文件名>");
                        break;
                    }
                    String createTarget = input.trim().substring(6).trim();
                    if ((createTarget.startsWith("\"") && createTarget.endsWith("\"")) ||
                        (createTarget.startsWith("'") && createTarget.endsWith("'"))) {
                        createTarget = createTarget.substring(1, createTarget.length() - 1);
                    }
                    if (createTarget.isEmpty()) break;
                    File createFile = new File(currentDir, createTarget);
                    try {
                        if (createFile.createNewFile()) {
                            String absPath = createFile.getAbsolutePath();
                            String basePath = BASE_DIR.substring(0, BASE_DIR.indexOf("PRTS"));
                            String relPath = absPath.startsWith(basePath) ? absPath.substring(basePath.length()) : absPath;
                            relPath = relPath.replace("\\", "/");
                            fileOwners.put(relPath, currentUser);
                            saveOwners();
                            System.out.println("文件已创建: " + createTarget);
                        } else {
                            System.out.println("文件已存在: " + createTarget);
                        }
                    } catch (IOException e) {
                        System.out.println("创建文件失败: " + e.getMessage());
                    }
                    break;
                case "whoami":
                    System.out.println("当前用户: " + currentUser + (admins.containsKey(currentUser) ? " (管理员)" : ""));
                    break;
                case "logout":
                    currentUser = null;
                    System.out.println("已注销，请重新登录。");
                    break;
                case "show":
                    if (parts.length < 2) {
                        System.out.println("用法: show admin|user");
                        break;
                    }
                    if ("admin".equals(parts[1])) {
                        System.out.println("管理员列表：");
                        for (String admin : admins.keySet()) {
                            if (admin.equals(currentUser)) {
                                System.out.println(admin + " (online)");
                            } else {
                                System.out.println(admin);
                            }
                        }
                    } else if ("user".equals(parts[1])) {
                        System.out.println("用户列表：");
                        for (String user : users.keySet()) {
                            if (user.equals(currentUser)) {
                                System.out.println(user + " (online)");
                            } else {
                                System.out.println(user);
                            }
                        }
                    } else {
                        System.out.println("用法: show admin|user");
                    }
                    break;
                case "javac":
                    // 用法: javac <Java源文件>
                    if (parts.length < 2) {
                        System.out.println("用法: javac <Java源文件>");
                        break;
                    }
                    String javacTarget = input.trim().substring(5).trim();
                    if ((javacTarget.startsWith("\"") && javacTarget.endsWith("\"")) ||
                        (javacTarget.startsWith("'") && javacTarget.endsWith("'"))) {
                        javacTarget = javacTarget.substring(1, javacTarget.length() - 1);
                    }
                    if (javacTarget.isEmpty()) break;
                    File javacFile = new File(currentDir, javacTarget);
                    if (!javacFile.exists()) {
                        System.out.println("文件不存在: " + javacTarget);
                        break;
                    }
                    try {
                        Process proc = Runtime.getRuntime().exec(
                            "javac \"" + javacFile.getAbsolutePath() + "\"",
                            null,
                            currentDir
                        );
                        int code = proc.waitFor();
                        if (code == 0) {
                            System.out.println("编译成功: " + javacTarget);
                        } else {
                            System.out.println("编译失败，错误信息：");
                            try (BufferedReader err = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                                String line;
                                while ((line = err.readLine()) != null) {
                                    System.out.println(line);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("编译异常: " + e.getMessage());
                    }
                    break;
                case "g++":
                    // 用法: g++ <C++源文件>
                    if (parts.length < 2) {
                        System.out.println("用法: g++ <C++源文件>");
                        break;
                    }
                    String gppTarget = input.trim().substring(3).trim();
                    if ((gppTarget.startsWith("\"") && gppTarget.endsWith("\"")) ||
                        (gppTarget.startsWith("'") && gppTarget.endsWith("'"))) {
                        gppTarget = gppTarget.substring(1, gppTarget.length() - 1);
                    }
                    if (gppTarget.isEmpty()) break;
                    File gppFile = new File(currentDir, gppTarget);
                    if (!gppFile.exists()) {
                        System.out.println("文件不存在: " + gppTarget);
                        break;
                    }
                    // 输出文件名与源文件同名但无扩展名
                    String exeName = gppTarget;
                    int dotIdx = exeName.lastIndexOf('.');
                    if (dotIdx > 0) exeName = exeName.substring(0, dotIdx);
                    File exeFile = new File(currentDir, exeName + ".exe");
                    try {
                        Process proc = Runtime.getRuntime().exec(
                            "g++ \"" + gppFile.getAbsolutePath() + "\" -o \"" + exeFile.getAbsolutePath() + "\"",
                            null,
                            currentDir
                        );
                        int code = proc.waitFor();
                        if (code == 0) {
                            System.out.println("编译成功: " + exeFile.getName());
                        } else {
                            System.out.println("编译失败，错误信息：");
                            try (BufferedReader err = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                                String line;
                                while ((line = err.readLine()) != null) {
                                    System.out.println(line);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("编译异常: " + e.getMessage());
                    }
                    break;
                case "run":
                    // 用法: run <Java源文件|C++源文件>
                    if (parts.length < 2) {
                        System.out.println("用法: run <Java源文件|C++源文件>");
                        break;
                    }
                    String runTarget = input.trim().substring(3).trim();
                    if ((runTarget.startsWith("\"") && runTarget.endsWith("\"")) ||
                        (runTarget.startsWith("'") && runTarget.endsWith("'"))) {
                        runTarget = runTarget.substring(1, runTarget.length() - 1);
                    }
                    if (runTarget.isEmpty()) break;
                    File runFile = new File(currentDir, runTarget);
                    if (!runFile.exists()) {
                        System.out.println("文件不存在: " + runTarget);
                        break;
                    }
                    if (runTarget.endsWith(".java")) {
                        // Java编译并运行
                        String className = runFile.getName();
                        int classDotIdx = className.lastIndexOf('.');
                        if (classDotIdx > 0) className = className.substring(0, classDotIdx);
                        try {
                            Process compileProc = Runtime.getRuntime().exec(
                                "javac \"" + runFile.getAbsolutePath() + "\"",
                                null,
                                currentDir
                            );
                            int compileCode = compileProc.waitFor();
                            if (compileCode != 0) {
                                System.out.println("编译失败，错误信息：");
                                try (BufferedReader err = new BufferedReader(new InputStreamReader(compileProc.getErrorStream()))) {
                                    String line;
                                    while ((line = err.readLine()) != null) {
                                        System.out.println(line);
                                    }
                                }
                                break;
                            }
                            Process runProc = Runtime.getRuntime().exec(
                                "java -cp \"" + currentDir.getAbsolutePath() + "\" " + className,
                                null,
                                currentDir
                            );
                            try (BufferedReader out = new BufferedReader(new InputStreamReader(runProc.getInputStream()));
                                 BufferedReader err = new BufferedReader(new InputStreamReader(runProc.getErrorStream()))) {
                                String line;
                                while ((line = out.readLine()) != null) {
                                    System.out.println(line);
                                }
                                while ((line = err.readLine()) != null) {
                                    System.out.println(line);
                                }
                            }
                            runProc.waitFor();
                        } catch (Exception e) {
                            System.out.println("运行异常: " + e.getMessage());
                        }
                    } else if (runTarget.endsWith(".cpp") || runTarget.endsWith(".cc") || runTarget.endsWith(".cxx")) {
                        // C++编译并运行，支持交互输入
                        String runExeName = runFile.getName();
                        int runDotIdx = runExeName.lastIndexOf('.');
                        if (runDotIdx > 0) runExeName = runExeName.substring(0, runDotIdx);
                        File runExeFile = new File(currentDir, runExeName + ".exe");
                        try {
                            Process compileProc = Runtime.getRuntime().exec(
                                "g++ \"" + runFile.getAbsolutePath() + "\" -o \"" + runExeFile.getAbsolutePath() + "\"",
                                null,
                                currentDir
                            );
                            int compileCode = compileProc.waitFor();
                            if (compileCode != 0) {
                                System.out.println("编译失败，错误信息：");
                                try (BufferedReader err = new BufferedReader(new InputStreamReader(compileProc.getErrorStream()))) {
                                    String line;
                                    while ((line = err.readLine()) != null) {
                                        System.out.println(line);
                                    }
                                }
                                break;
                            }
                            // 支持交互输入
                            Process runProc = Runtime.getRuntime().exec(
                                "\"" + runExeFile.getAbsolutePath() + "\"",
                                null,
                                currentDir
                            );
                            Thread outThread = new Thread(() -> {
                                try (BufferedReader out = new BufferedReader(new InputStreamReader(runProc.getInputStream()))) {
                                    String line;
                                    while ((line = out.readLine()) != null) {
                                        System.out.println(line);
                                    }
                                } catch (IOException e) {}
                            });
                            Thread errThread = new Thread(() -> {
                                try (BufferedReader err = new BufferedReader(new InputStreamReader(runProc.getErrorStream()))) {
                                    String line;
                                    while ((line = err.readLine()) != null) {
                                        System.out.println(line);
                                    }
                                } catch (IOException e) {}
                            });
                            outThread.start();
                            errThread.start();
                            // 主线程转发用户输入到子进程
                            try (BufferedWriter procIn = new BufferedWriter(new OutputStreamWriter(runProc.getOutputStream()))) {
                                Scanner inputScanner = new Scanner(System.in);
                                while (runProc.isAlive()) {
                                    if (System.in.available() > 0) {
                                        String userInput = inputScanner.nextLine();
                                        procIn.write(userInput);
                                        procIn.newLine();
                                        procIn.flush();
                                    }
                                    Thread.sleep(50);
                                }
                            } catch (Exception e) {}
                            outThread.join();
                            errThread.join();
                            runProc.waitFor();
                        } catch (Exception e) {
                            System.out.println("运行异常: " + e.getMessage());
                        }
                    } else {
                        System.out.println("暂不支持该类型文件的运行: " + runTarget);
                    }
                    break;
                case "help":
                    System.out.println("支持命令: ls, cd <dir>, mkdir <dir>, touch <file>, cat <file>, echo <text> <file>, rm <file>, whoami, logout, exit");
                    break;
                case "exit":
                    System.out.println("退出 MiniOS");
                    scanner.close();
                    return;
                default:
                    System.out.println("未知命令");
            }
        }
    }

    // 加载用户信息
    private static void loadUsers() throws IOException {
        File userFile = new File(USERS_FILE);
        if (!userFile.exists()) {
            // 若不存在则在同级目录下创建 users.txt 文件
            userFile.createNewFile();
        }
        try (BufferedReader br = new BufferedReader(new FileReader(userFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || !line.contains(":")) continue;
                String[] arr = line.split(":", 2);
                users.put(arr[0], arr[1]);
            }
        }
    }

    // 加载管理员信息
    private static void loadAdmins() throws IOException {
        File adminFile = new File(ADMINS_FILE);
        if (!adminFile.exists()) {
            // 若不存在则在同级目录下创建 admins.txt 文件
            adminFile.createNewFile();
        }
        try (BufferedReader br = new BufferedReader(new FileReader(adminFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || !line.contains(":")) continue;
                String[] arr = line.split(":", 2);
                admins.put(arr[0], arr[1]);
            }
        }
    }

    // 加载owners信息
    private static void loadOwners() throws IOException {
        File ownersFile = new File(OWNERS_FILE);
        if (!ownersFile.exists()) {
            ownersFile.createNewFile();
        }
        try (BufferedReader br = new BufferedReader(new FileReader(ownersFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || !line.contains(":")) continue;
                // 只存储相对路径
                String[] arr = line.split(":", 2);
                fileOwners.put(arr[0], arr[1]);
            }
        }
    }

    // 保存owners信息
    private static void saveOwners() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(OWNERS_FILE, false))) {
            for (Map.Entry<String, String> entry : fileOwners.entrySet()) {
                pw.println(entry.getKey().replace("\\", "/") + ":" + entry.getValue());
            }
        } catch (IOException e) {
            System.out.println("owners.txt 保存失败: " + e.getMessage());
        }
    }

    // 检查管理员和用户是否有重名
    private static void checkAdminUserConflict() {
        for (String admin : admins.keySet()) {
            if (users.containsKey(admin)) {
                System.out.println("错误：管理员账户 '" + admin + "' 不能与普通用户账户重名！");
                System.exit(1);
            }
        }
    }

    // 递归删除文件/目录
    private static boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }
}