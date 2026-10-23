package com.example.hdfs;

import org.apache.hadoop.fs.FileStatus;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Command-line entry point.
 *
 * <pre>
 * java -jar hdfs-java-api.jar [--uri hdfs://host:port] [--overwrite] &lt;command&gt; [args]
 * </pre>
 *
 * The NameNode address is resolved in this order: {@code --uri} option,
 * {@code HDFS_URI} environment variable, then {@link #DEFAULT_URI}.
 */
public final class HdfsCli {

    static final String DEFAULT_URI = "hdfs://namenode:8020";
    private static final String DEMO_FILE = "/demo/hello.txt";

    private static final int EXIT_OK = 0;
    private static final int EXIT_ERROR = 1;
    private static final int EXIT_USAGE = 2;

    private HdfsCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        String uri = firstNonEmpty(System.getenv("HDFS_URI"), DEFAULT_URI);
        boolean overwrite = false;
        List<String> positional = new ArrayList<String>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--uri".equals(arg)) {
                if (++i >= args.length) {
                    return usage("--uri requires a value");
                }
                uri = args[i];
            } else if ("--overwrite".equals(arg)) {
                overwrite = true;
            } else {
                positional.add(arg);
            }
        }

        if (positional.isEmpty()) {
            return usage(null);
        }

        String command = positional.get(0);
        List<String> operands = positional.subList(1, positional.size());

        try (HdfsFileService hdfs = new HdfsFileService(uri)) {
            switch (command) {
                case "mkdir":
                    return requireOperands(operands, 1) ? mkdir(hdfs, operands.get(0)) : usage("mkdir <path>");
                case "write":
                    return requireOperands(operands, 2)
                            ? write(hdfs, operands.get(0), operands.subList(1, operands.size()), overwrite)
                            : usage("write <path> <line>...");
                case "cat":
                    return requireOperands(operands, 1) ? cat(hdfs, operands.get(0)) : usage("cat <path>");
                case "ls":
                    return ls(hdfs, operands.isEmpty() ? "/" : operands.get(0));
                case "rm":
                    return rm(hdfs, operands);
                case "demo":
                    return demo(hdfs);
                default:
                    return usage("unknown command: " + command);
            }
        } catch (IOException e) {
            System.err.println("HDFS error: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    // ------------------------------------------------------------------ commands

    private static int mkdir(HdfsFileService hdfs, String path) throws IOException {
        hdfs.createDirectory(path);
        System.out.println("Created directory " + path);
        return EXIT_OK;
    }

    private static int write(HdfsFileService hdfs, String path, List<String> lines, boolean overwrite)
            throws IOException {
        hdfs.writeLines(path, lines, overwrite);
        System.out.println("Wrote " + lines.size() + " line(s) to " + path);
        return EXIT_OK;
    }

    private static int cat(HdfsFileService hdfs, String path) throws IOException {
        for (String line : hdfs.readLines(path)) {
            System.out.println(line);
        }
        return EXIT_OK;
    }

    private static int ls(HdfsFileService hdfs, String path) throws IOException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (FileStatus status : hdfs.list(path)) {
            System.out.println(String.format("%s%s %-8s %10d %s %s",
                    status.isDirectory() ? "d" : "-",
                    status.getPermission(),
                    status.getOwner(),
                    status.getLen(),
                    format.format(new Date(status.getModificationTime())),
                    status.getPath().toUri().getPath()));
        }
        return EXIT_OK;
    }

    private static int rm(HdfsFileService hdfs, List<String> operands) throws IOException {
        boolean recursive = operands.contains("-r");
        List<String> paths = new ArrayList<String>(operands);
        paths.remove("-r");
        if (paths.size() != 1) {
            return usage("rm [-r] <path>");
        }
        String path = paths.get(0);
        if (hdfs.delete(path, recursive)) {
            System.out.println("Deleted " + path);
            return EXIT_OK;
        }
        System.err.println("Nothing to delete at " + path);
        return EXIT_ERROR;
    }

    /** Writes a small file, then reads it back to prove the round trip works. */
    private static int demo(HdfsFileService hdfs) throws IOException {
        List<String> content = Arrays.asList(
                "Hello from the HDFS Java API",
                "This file was written through a FileSystem client",
                "End of file");
        hdfs.writeLines(DEMO_FILE, content, true);
        System.out.println("Wrote " + DEMO_FILE);
        System.out.println("--- content read back ---");
        return cat(hdfs, DEMO_FILE);
    }

    // ------------------------------------------------------------------- helpers

    private static boolean requireOperands(List<String> operands, int minimum) {
        return operands.size() >= minimum;
    }

    private static String firstNonEmpty(String first, String fallback) {
        return first == null || first.trim().isEmpty() ? fallback : first.trim();
    }

    private static int usage(String problem) {
        if (problem != null) {
            System.err.println("Error: " + problem);
        }
        System.err.println("Usage: hdfs-java-api [--uri hdfs://host:port] [--overwrite] <command> [args]");
        System.err.println("Commands:");
        System.err.println("  mkdir <path>              create a directory");
        System.err.println("  write <path> <line>...    write lines to a file (--overwrite to replace)");
        System.err.println("  cat <path>                print a file");
        System.err.println("  ls [path]                 list a directory (default: /)");
        System.err.println("  rm [-r] <path>            delete a file or directory");
        System.err.println("  demo                      write " + DEMO_FILE + " and read it back");
        return EXIT_USAGE;
    }
}
