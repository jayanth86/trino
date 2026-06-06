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
package io.trino.testing.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

public final class FlociContainer
        extends GenericContainer<FlociContainer>
{
    public static final String FLOCI_IMAGE = "floci/floci:1.5.22";
    public static final String FLOCI_ACCESS_KEY = "test";
    public static final String FLOCI_SECRET_KEY = "test";
    public static final String FLOCI_REGION = "us-east-1";

    private static final int FLOCI_PORT = 4566;

    public FlociContainer()
    {
        super(DockerImageName.parse(FLOCI_IMAGE));
        addExposedPort(FLOCI_PORT);
        waitingFor(Wait.forHttp("/_floci/init").forPort(FLOCI_PORT));
    }

    public URI endpoint()
    {
        return URI.create("http://%s:%s".formatted(getHost(), getMappedPort(FLOCI_PORT)));
    }

    public void updateClient(AwsClientBuilder<?, ?> client)
    {
        client.endpointOverride(endpoint());
        client.region(Region.of(FLOCI_REGION));
        client.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(FLOCI_ACCESS_KEY, FLOCI_SECRET_KEY)));
        if (client instanceof S3ClientBuilder s3) {
            s3.forcePathStyle(true);
        }
    }

    public S3Client createS3Client()
    {
        return S3Client.builder().applyMutation(this::updateClient).build();
    }

    public void createBucket(String bucketName)
    {
        try (S3Client s3 = createS3Client()) {
            s3.createBucket(builder -> builder.bucket(bucketName));
        }
    }
}
