package org.heigit.ohsome.now.parquet;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.avro.Schema;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;
import org.jetbrains.annotations.NotNull;

public class AvroUtil {

    private AvroUtil() {
        // Utility class
    }

    public static <T> ParquetWriter<T> openWriter(Schema schema, Path root, PartitionKey partition) throws IOException {
            var path = partition.toPath(root);
            Files.createDirectories(path.getParent());
            return AvroParquetWriter.<T>builder(outputFile(path))
                    .withSchema(schema)
                    .withDictionaryEncoding(true)
                    .withCompressionCodec(CompressionCodecName.GZIP)
                    .withPageSize(ParquetWriter.DEFAULT_PAGE_SIZE)
                    .withRowGroupSize((long) ParquetWriter.DEFAULT_BLOCK_SIZE)
                    .withValidation(false)
                    .withConf(new Configuration())
                    .build();
    }

    public static Schema parseSchema(String avsc) {
        return new Schema.Parser().setValidate(true).parse(avsc);
    }

    public static Schema loadSchema(String avsc) throws IOException {
        try (var input = ClassLoader.getSystemResourceAsStream("avro/" + avsc)) {
            return new Schema.Parser().setValidate(true).parse(input);
        }
    }

    public static OutputFile outputFile(Path path) {
        return new LocalFSOutputFile(path);
    }

    private static class LocalFSOutputFile implements OutputFile {
        private final Path path;

        public LocalFSOutputFile(Path path) {
            this.path = path;
        }

        @Override
        public PositionOutputStream create(long blockSizeHint) throws IOException {
            return new LocalFSOutputFileStream(path, false);
        }

        @Override
        public PositionOutputStream createOrOverwrite(long blockSizeHint) throws IOException {
            return new LocalFSOutputFileStream(path, true);
        }

        @Override
        public boolean supportsBlockSize() {
            return false;
        }

        @Override
        public long defaultBlockSize() {
            return 0;
        }

        @Override
        public String getPath() {
            return path.toString();
        }
    }

    private static class LocalFSOutputFileStream extends PositionOutputStream {
        private static final int IO_BUF_SIZE = 16 * 1024;

        protected final OutputStream output;
        private long position = 0;

        public LocalFSOutputFileStream(Path path, boolean trunc) throws IOException {
            Files.createDirectories(path.getParent());
            this.output = new BufferedOutputStream(Files.newOutputStream(path, CREATE, trunc ? TRUNCATE_EXISTING : APPEND), IO_BUF_SIZE);
        }

        @Override
        public void write(int b) throws IOException {
            output.write(b);
            position++;
        }

        @Override
        public void write(@NotNull byte[] b) throws IOException {
            output.write(b);
            position += b.length;
        }

        @Override
        public void write(@NotNull byte[] b, int off, int len) throws IOException {
            output.write(b, off, len);
            position += len;
        }

        @Override
        public void flush() throws IOException {
            output.flush();
        }

        @Override
        public void close() throws IOException {
            output.close();
        }

        @Override
        public long getPos() {
            return position;
        }
    }
}
