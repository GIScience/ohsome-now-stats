package org.heigit.ohsome.now.parquet;

import java.nio.file.Path;

public interface PartitionKey {
    Path toPath(Path root);
}
