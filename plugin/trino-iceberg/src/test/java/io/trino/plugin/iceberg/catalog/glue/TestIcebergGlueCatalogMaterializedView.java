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
package io.trino.plugin.iceberg.catalog.glue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.plugin.hive.FlociS3AndGlue;
import io.trino.plugin.iceberg.BaseIcebergMaterializedViewTest;
import io.trino.plugin.iceberg.IcebergQueryRunner;
import io.trino.plugin.iceberg.SchemaInitializer;
import io.trino.testing.DistributedQueryRunner;
import io.trino.testing.QueryRunner;
import software.amazon.awssdk.services.glue.GlueClient;

import java.io.File;
import java.nio.file.Files;

import static io.trino.plugin.base.util.Closables.closeAllSuppress;
import static io.trino.testing.TestingNames.randomNameSuffix;
import static org.apache.iceberg.BaseMetastoreTableOperations.METADATA_LOCATION_PROP;

public class TestIcebergGlueCatalogMaterializedView
        extends BaseIcebergMaterializedViewTest
{
    private final String schemaName = "test_iceberg_materialized_view_" + randomNameSuffix();

    private File schemaDirectory;
    private FlociS3AndGlue floci;

    @Override
    protected QueryRunner createQueryRunner()
            throws Exception
    {
        this.schemaDirectory = Files.createTempDirectory("test_iceberg").toFile();
        schemaDirectory.deleteOnExit();
        floci = closeAfterClass(new FlociS3AndGlue());

        DistributedQueryRunner queryRunner = IcebergQueryRunner.builder()
                .setIcebergProperties(
                        ImmutableMap.<String, String>builder()
                                .put("iceberg.catalog.type", "glue")
                                .put("hive.metastore.glue.default-warehouse-dir", schemaDirectory.getAbsolutePath())
                                .put("fs.hadoop.enabled", "true")
                                .putAll(floci.glueProperties())
                                .buildOrThrow())
                .setSchemaInitializer(
                        SchemaInitializer.builder()
                                .withClonedTpchTables(ImmutableList.of())
                                .withSchemaName(schemaName)
                                .build())
                .build();
        try {
            queryRunner.createCatalog("iceberg_legacy_mv", "iceberg", ImmutableMap.<String, String>builder()
                    .put("iceberg.catalog.type", "glue")
                    .put("hive.metastore.glue.default-warehouse-dir", schemaDirectory.getAbsolutePath())
                    .put("iceberg.materialized-views.hide-storage-table", "false")
                    .put("fs.hadoop.enabled", "true")
                    .putAll(floci.glueProperties())
                    .buildOrThrow());

            queryRunner.installPlugin(createMockConnectorPlugin());
            queryRunner.createCatalog("mock", "mock");
            return queryRunner;
        }
        catch (Throwable e) {
            closeAllSuppress(e, queryRunner);
            throw e;
        }
    }

    @Override
    protected String getSchemaDirectory()
    {
        return new File(schemaDirectory, schemaName + ".db").getPath();
    }

    @Override
    protected String getStorageMetadataLocation(String materializedViewName)
    {
        try (GlueClient glueClient = floci.createGlueClient()) {
            return glueClient.getTable(x -> x
                            .databaseName(schemaName)
                            .name(materializedViewName))
                    .table()
                    .parameters().get(METADATA_LOCATION_PROP);
        }
    }
}
