package com.example.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Thin wrapper around the Hadoop {@link FileSystem} API that exposes the
 * handful of operations needed to work with text files stored on HDFS.
 *
 * <p>The service owns its own {@code FileSystem} instance (it is not taken from
 * Hadoop's shared cache), so closing the service never affects other callers.</p>
 */
public final class HdfsFileService implements Closeable {

    private final FileSystem fileSystem;

    /**
     * Opens a connection to the file system located at the given URI.
     *
     * @param hdfsUri NameNode address, e.g. {@code hdfs://namenode:8020}
     */
    public HdfsFileService(String hdfsUri) throws IOException {
        Configuration configuration = new Configuration();
        configuration.set("fs.defaultFS", hdfsUri);
        this.fileSystem = FileSystem.newInstance(URI.create(hdfsUri), configuration);
    }

    /** Creates a directory (and any missing parents). */
    public void createDirectory(String directory) throws IOException {
        Path target = new Path(directory);
        if (!fileSystem.mkdirs(target)) {
            throw new IOException("Unable to create directory: " + target);
        }
    }

    /**
     * Writes the given lines to a UTF-8 text file. Missing parent directories
     * are created automatically by HDFS.
     *
     * @param overwrite when {@code false}, fails if the file already exists
     */
    public void writeLines(String file, Iterable<String> lines, boolean overwrite) throws IOException {
        Path target = new Path(file);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(fileSystem.create(target, overwrite), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /** Reads a UTF-8 text file and returns its lines. */
    public List<String> readLines(String file) throws IOException {
        Path source = new Path(file);
        List<String> lines = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(fileSystem.open(source), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** Lists the entries of a directory (or the single entry if a file is given). */
    public List<FileStatus> list(String path) throws IOException {
        return Arrays.asList(fileSystem.listStatus(new Path(path)));
    }

    public boolean exists(String path) throws IOException {
        return fileSystem.exists(new Path(path));
    }

    /**
     * Deletes a file or directory.
     *
     * @param recursive must be {@code true} to delete a non-empty directory
     * @return {@code true} if something was deleted
     */
    public boolean delete(String path, boolean recursive) throws IOException {
        return fileSystem.delete(new Path(path), recursive);
    }

    @Override
    public void close() throws IOException {
        fileSystem.close();
    }
}
