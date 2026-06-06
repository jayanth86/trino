/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.hive;

import com.google.common.collect.ImmutableMap;
import io.trino.plugin.hive.metastore.glue.GlueCache;
import io.trino.plugin.hive.metastore.glue.GlueHiveMetastore;
import io.trino.plugin.hive.metastore.glue.GlueHiveMetastore.TableKind;
import io.trino.plugin.hive.metastore.glue.GlueHiveMetastoreConfig;
import io.trino.plugin.hive.metastore.glue.GlueMetastoreStats;
import io.trino.spi.catalog.CatalogName;
import io.trino.testing.containers.FlociContainer;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Consumer;

import static io.trino.hdfs.HdfsTestUtils.HDFS_FILE_SYSTEM_FACTORY;

public final class FlociS3AndGlue
        implements AutoCloseable
{
    private final FlociContainer floci = new FlociContainer();

    public FlociS3AndGlue()
    {
        floci.start();
    }

    public void createBucket(String bucketName)
    {
        floci.createBucket(bucketName);
    }

    public S3Client createS3Client()
    {
        return S3Client.builder()
                .applyMutation(floci::updateClient)
                .build();
    }

    public GlueClient createGlueClient()
    {
        return GlueClient.builder()
                .applyMutation(floci::updateClient)
                .build();
    }

    public Map<String, String> glueProperties()
    {
        return ImmutableMap.<String, String>builder()
                .put("hive.metastore.glue.endpoint-url", floci.endpoint().toString())
                .put("hive.metastore.glue.region", FlociContainer.FLOCI_REGION)
                .put("hive.metastore.glue.aws-access-key", FlociContainer.FLOCI_ACCESS_KEY)
                .put("hive.metastore.glue.aws-secret-key", FlociContainer.FLOCI_SECRET_KEY)
                .buildOrThrow();
    }

    public Map<String, String> s3AndGlueProperties()
    {
        return ImmutableMap.<String, String>builder()
                .putAll(glueProperties())
                .put("s3.region", FlociContainer.FLOCI_REGION)
                .put("s3.endpoint", floci.endpoint().toString())
                .put("s3.aws-access-key", FlociContainer.FLOCI_ACCESS_KEY)
                .put("s3.aws-secret-key", FlociContainer.FLOCI_SECRET_KEY)
                .put("s3.path-style-access", "true")
                .buildOrThrow();
    }

    public void configureGlueHiveMetastore(GlueHiveMetastoreConfig config)
    {
        config.setGlueEndpointUrl(floci.endpoint())
                .setGlueRegion(FlociContainer.FLOCI_REGION)
                .setAwsAccessKey(FlociContainer.FLOCI_ACCESS_KEY)
                .setAwsSecretKey(FlociContainer.FLOCI_SECRET_KEY);
    }

    public GlueHiveMetastore createGlueHiveMetastore(URI warehouseUri, Consumer<AutoCloseable> registerResource, boolean assumeCanonicalPartitionKeys)
    {
        GlueHiveMetastoreConfig glueConfig = new GlueHiveMetastoreConfig()
                .setDefaultWarehouseDir(warehouseUri.toString())
                .setAssumeCanonicalPartitionKeys(assumeCanonicalPartitionKeys);
        configureGlueHiveMetastore(glueConfig);
        GlueClient glueClient = createGlueClient();
        registerResource.accept(glueClient);
        return new GlueHiveMetastore(
                glueClient,
                GlueCache.NOOP,
                new GlueMetastoreStats(),
                HDFS_FILE_SYSTEM_FACTORY,
                glueConfig,
                new CatalogName("test"),
                EnumSet.allOf(TableKind.class));
    }

    @Override
    public void close()
    {
        floci.close();
    }
}
