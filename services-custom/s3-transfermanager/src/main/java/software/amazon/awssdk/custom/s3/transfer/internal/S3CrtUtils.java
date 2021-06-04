/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.custom.s3.transfer.internal;

import com.amazonaws.s3.model.GetObjectOutput;
import com.amazonaws.s3.model.ObjectCannedACL;
import com.amazonaws.s3.model.ObjectLockLegalHoldStatus;
import com.amazonaws.s3.model.ObjectLockMode;
import com.amazonaws.s3.model.PutObjectOutput;
import com.amazonaws.s3.model.RequestPayer;
import com.amazonaws.s3.model.ServerSideEncryption;
import com.amazonaws.s3.model.StorageClass;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.core.internal.util.UserAgentUtils;
import software.amazon.awssdk.crt.auth.credentials.CredentialsProvider;
import software.amazon.awssdk.crt.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.crt.http.HttpHeader;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3ResponseMetadata;
import software.amazon.awssdk.utils.http.SdkHttpUtils;

@SdkInternalApi
public final class S3CrtUtils {
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String USER_AGENT_STRING = UserAgentUtils.getUserAgent() + " ft/s3-transfer";

    private S3CrtUtils() {
    }

    // TODO: Add more adapters if there are any new crt credentials providers.

    /**
     * Adapter between the sdk credentials provider and the crt credentials provider.
     */
    public static CredentialsProvider createCrtCredentialsProvider(AwsCredentialsProvider awsCredentialsProvider) {
        AwsCredentials sdkCredentials = awsCredentialsProvider.resolveCredentials();
        StaticCredentialsProvider.StaticCredentialsProviderBuilder builder =
            new StaticCredentialsProvider.StaticCredentialsProviderBuilder();

        if (sdkCredentials instanceof AwsSessionCredentials) {
            builder.withSessionToken(((AwsSessionCredentials) sdkCredentials).sessionToken().getBytes(StandardCharsets.UTF_8));
        }

        return builder.withAccessKeyId(sdkCredentials.accessKeyId().getBytes(StandardCharsets.UTF_8))
                      .withSecretAccessKey(sdkCredentials.secretAccessKey().getBytes(StandardCharsets.UTF_8))
                      .build();
    }

    // TODO: codegen and add tests
    public static com.amazonaws.s3.model.GetObjectRequest toCrtGetObjectRequest(GetObjectRequest request) {
        com.amazonaws.s3.model.GetObjectRequest.Builder getObjectBuilder =
            com.amazonaws.s3.model.GetObjectRequest.builder()
                                                   .key(request.key())
                                                   .bucket(request.bucket())
                                                   .expectedBucketOwner(request.expectedBucketOwner())
                                                   .ifMatch(request.ifMatch())
                                                   .ifModifiedSince(request.ifModifiedSince())
                                                   .ifNoneMatch(request.ifNoneMatch());

        processRequestOverrideConfiguration(request.overrideConfiguration().orElse(null),
                                            getObjectBuilder::customQueryParameters);

        addCustomHeaders(request.overrideConfiguration().orElse(null), getObjectBuilder::customHeaders);

        return getObjectBuilder.build();

    }

    // TODO: codegen and add tests
    public static GetObjectResponse adaptGetObjectOutput(GetObjectOutput response, SdkHttpResponse sdkHttpResponse) {
        S3ResponseMetadata s3ResponseMetadata = createS3ResponseMetadata(sdkHttpResponse);

        return (GetObjectResponse) GetObjectResponse.builder()
                                                    .bucketKeyEnabled(response.bucketKeyEnabled())
                                                    .acceptRanges(response.acceptRanges())
                                                    .contentDisposition(response.contentDisposition())
                                                    .cacheControl(response.cacheControl())
                                                    .contentEncoding(response.contentEncoding())
                                                    .contentLanguage(response.contentLanguage())
                                                    .contentRange(response.contentRange())
                                                    .contentLength(response.contentLength())
                                                    .contentType(response.contentType())
                                                    .deleteMarker(response.deleteMarker())
                                                    .eTag(response.eTag())
                                                    .expiration(response.expiration())
                                                    .expires(response.expires())
                                                    .lastModified(response.lastModified())
                                                    .metadata(response.metadata())
                                                    .responseMetadata(s3ResponseMetadata)
                                                    .sdkHttpResponse(sdkHttpResponse)
                                                    .build();
    }

    // TODO: codegen and add tests
    public static com.amazonaws.s3.model.PutObjectRequest toCrtPutObjectRequest(PutObjectRequest sdkPutObject) {
        com.amazonaws.s3.model.PutObjectRequest.Builder putObjectBuilder =
            com.amazonaws.s3.model.PutObjectRequest.builder()
                                                   .contentLength(sdkPutObject.contentLength())
                                                   .aCL(ObjectCannedACL.fromValue(sdkPutObject.aclAsString()))
                                                   .bucket(sdkPutObject.bucket())
                                                   .key(sdkPutObject.key())
                                                   .bucketKeyEnabled(sdkPutObject.bucketKeyEnabled())
                                                   .cacheControl(sdkPutObject.cacheControl())
                                                   .contentDisposition(sdkPutObject.contentDisposition())
                                                   .contentEncoding(sdkPutObject.contentEncoding())
                                                   .contentLanguage(sdkPutObject.contentLanguage())
                                                   .contentMD5(sdkPutObject.contentMD5())
                                                   .contentType(sdkPutObject.contentType())
                                                   .expectedBucketOwner(sdkPutObject.expectedBucketOwner())
                                                   .expires(sdkPutObject.expires())
                                                   .grantFullControl(sdkPutObject.grantFullControl())
                                                   .grantRead(sdkPutObject.grantRead())
                                                   .grantReadACP(sdkPutObject.grantReadACP())
                                                   .grantWriteACP(sdkPutObject.grantWriteACP())
                                                   .metadata(sdkPutObject.metadata())
                                                   .objectLockLegalHoldStatus(ObjectLockLegalHoldStatus.fromValue(
                                                       sdkPutObject.objectLockLegalHoldStatusAsString()))
                                                   .objectLockMode(ObjectLockMode.fromValue(
                                                       sdkPutObject.objectLockModeAsString()))
                                                   .objectLockRetainUntilDate(sdkPutObject.objectLockRetainUntilDate())
                                                   .requestPayer(RequestPayer.fromValue(sdkPutObject.requestPayerAsString()))
                                                   .serverSideEncryption(ServerSideEncryption.fromValue(
                                                       sdkPutObject.requestPayerAsString()))
                                                   .sSECustomerAlgorithm(sdkPutObject.sseCustomerAlgorithm())
                                                   .sSECustomerKey(sdkPutObject.sseCustomerKey())
                                                   .sSECustomerKeyMD5(sdkPutObject.sseCustomerKeyMD5())
                                                   .sSEKMSEncryptionContext(sdkPutObject.ssekmsEncryptionContext())
                                                   .sSEKMSKeyId(sdkPutObject.ssekmsKeyId())
                                                   .storageClass(StorageClass.fromValue(sdkPutObject.storageClassAsString()))
                                                   .tagging(sdkPutObject.tagging())
                                                   .websiteRedirectLocation(sdkPutObject.websiteRedirectLocation());


        processRequestOverrideConfiguration(sdkPutObject.overrideConfiguration().orElse(null),
                                            putObjectBuilder::customQueryParameters);

        addCustomHeaders(sdkPutObject.overrideConfiguration().orElse(null), putObjectBuilder::customHeaders);

        return putObjectBuilder.build();
    }

    // TODO: codegen and add tests
    public static PutObjectResponse fromCrtPutObjectOutput(PutObjectOutput crtPutObjectOutput) {
        // TODO: Provide the HTTP request-level data (e.g. response metadata, HTTP response)
        PutObjectResponse.Builder builder = PutObjectResponse.builder()
                                                             .bucketKeyEnabled(crtPutObjectOutput.bucketKeyEnabled())
                                                             .eTag(crtPutObjectOutput.eTag())
                                                             .expiration(crtPutObjectOutput.expiration())
                                                             .sseCustomerAlgorithm(crtPutObjectOutput.sSECustomerAlgorithm())
                                                             .sseCustomerKeyMD5(crtPutObjectOutput.sSECustomerKeyMD5())
                                                             .ssekmsEncryptionContext(
                                                                 crtPutObjectOutput.sSEKMSEncryptionContext())
                                                             .ssekmsKeyId(crtPutObjectOutput.sSEKMSKeyId())
                                                             .versionId(crtPutObjectOutput.versionId());

        if (crtPutObjectOutput.requestCharged() != null) {
            builder.requestCharged(crtPutObjectOutput.requestCharged().value());
        }

        if (crtPutObjectOutput.serverSideEncryption() != null) {
            builder.serverSideEncryption(crtPutObjectOutput.serverSideEncryption().value());
        }

        return builder.build();
    }

    private static S3ResponseMetadata createS3ResponseMetadata(SdkHttpResponse sdkHttpResponse) {
        Map<String, String> metadata = new HashMap<>();
        sdkHttpResponse.headers().forEach((key, value) -> metadata.put(key, value.get(0)));
        return S3ResponseMetadata.create(DefaultAwsResponseMetadata.create(metadata));
    }

    private static void throwExceptionForUnsupportedConfigurations(AwsRequestOverrideConfiguration overrideConfiguration) {
        if (!overrideConfiguration.metricPublishers().isEmpty()) {
            throw new UnsupportedOperationException("Metric publishers are not supported");
        }

        if (overrideConfiguration.signer().isPresent()) {
            throw new UnsupportedOperationException("signer are not supported");
        }

        if (!overrideConfiguration.apiNames().isEmpty()) {
            throw new UnsupportedOperationException("apiNames are not supported");
        }

        if (overrideConfiguration.apiCallAttemptTimeout().isPresent()) {
            throw new UnsupportedOperationException("apiCallAttemptTimeout is not supported");
        }

        if (overrideConfiguration.apiCallTimeout().isPresent()) {
            throw new UnsupportedOperationException("apiCallTimeout is not supported");
        }

        if (overrideConfiguration.credentialsProvider().isPresent()) {
            throw new UnsupportedOperationException("credentialsProvider is not supported");
        }
    }

    private static void addRequestCustomHeaders(List<HttpHeader> crtHeaders, Map<String, List<String>> headers) {
        headers.forEach((key, value) -> {
            value.stream().map(val -> new HttpHeader(key, val)).forEach(crtHeaders::add);
        });
    }

    private static String encodedQueryString(Map<String, List<String>> rawQueryParameters) {
        return SdkHttpUtils.encodeAndFlattenQueryParameters(rawQueryParameters)
                           .map(value -> "?" + value)
                           .orElse("");
    }

    private static void processRequestOverrideConfiguration(AwsRequestOverrideConfiguration requestOverrideConfiguration,
                                                            Consumer<String> queryParametersConsumer) {
        if (requestOverrideConfiguration != null) {
            throwExceptionForUnsupportedConfigurations(requestOverrideConfiguration);

            if (!requestOverrideConfiguration.rawQueryParameters().isEmpty()) {
                String encodedQueryString = encodedQueryString(requestOverrideConfiguration.rawQueryParameters());
                queryParametersConsumer.accept(encodedQueryString);
            }
        }
    }

    private static void addCustomHeaders(AwsRequestOverrideConfiguration requestOverrideConfiguration,
                                         Consumer<HttpHeader[]> headersConsumer) {

        List<HttpHeader> crtHeaders = new ArrayList<>();
        crtHeaders.add(new HttpHeader(HEADER_USER_AGENT, USER_AGENT_STRING));

        if (requestOverrideConfiguration != null && !requestOverrideConfiguration.headers().isEmpty()) {
            addRequestCustomHeaders(crtHeaders, requestOverrideConfiguration.headers());
        }

        headersConsumer.accept(crtHeaders.toArray(new HttpHeader[0]));
    }
}