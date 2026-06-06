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
package io.trino.plugin.iceberg;

import com.google.common.reflect.ClassPath;
import io.trino.testing.containers.FlociContainer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Pattern;

import static java.util.regex.Matcher.quoteReplacement;

final class FlociIcebergTestUtils
{
    private FlociIcebergTestUtils() {}

    static void copyResources(FlociContainer floci, String resourcePath, String bucketName, String target)
    {
        try (S3Client s3 = floci.createS3Client()) {
            for (ClassPath.ResourceInfo resourceInfo : ClassPath.from(FlociIcebergTestUtils.class.getClassLoader()).getResources()) {
                if (resourceInfo.getResourceName().startsWith(resourcePath)) {
                    String fileName = resourceInfo.getResourceName().replaceFirst("^" + Pattern.quote(resourcePath), quoteReplacement(target));
                    s3.putObject(
                            builder -> builder.bucket(bucketName).key(fileName),
                            RequestBody.fromBytes(resourceInfo.asByteSource().read()));
                }
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> listObjects(FlociContainer floci, String bucketName, String prefix)
    {
        try (S3Client s3 = floci.createS3Client()) {
            return s3.listObjectsV2Paginator(builder -> builder.bucket(bucketName).prefix(prefix))
                    .contents()
                    .stream()
                    .map(object -> object.key())
                    .toList();
        }
    }

    static void deleteObjects(FlociContainer floci, String bucketName, String prefix)
    {
        try (S3Client s3 = floci.createS3Client()) {
            List<String> keys = s3.listObjectsV2Paginator(builder -> builder.bucket(bucketName).prefix(prefix))
                    .contents()
                    .stream()
                    .map(object -> object.key())
                    .toList();
            keys.forEach(key -> s3.deleteObject(builder -> builder.bucket(bucketName).key(key)));
        }
    }
}
