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

package software.amazon.awssdk.awscore.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.Collections;
import org.junit.Test;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpResponse;

public class AwsServiceExceptionSerializationTest {

    @Test
    public void serializeServiceException() throws IOException, ClassNotFoundException {
        AwsServiceException expectedException = createException();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
        objectOutputStream.writeObject(expectedException);
        objectOutputStream.flush();
        objectOutputStream.close();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(outputStream.toByteArray()));
        AwsServiceException resultException = (AwsServiceException) ois.readObject();

        assertSameValues(resultException, expectedException);
    }

    // The AwsServiceException and it's members do not implement equals
    private void assertSameValues(AwsServiceException resultException, AwsServiceException expectedException) {
        assertThat(resultException.statusCode()).isEqualTo(expectedException.statusCode());
        assertThat(resultException.getMessage()).isEqualTo(expectedException.getMessage());
        assertThat(resultException.requestId()).isEqualTo(expectedException.requestId());
        assertThat(resultException.extendedRequestId()).isEqualTo(expectedException.extendedRequestId());
        assertThat(resultException.toBuilder().clockSkew()).isEqualTo(expectedException.toBuilder().clockSkew());
        assertThat(resultException.toBuilder().cause().getMessage()).isEqualTo(expectedException.toBuilder().cause().getMessage());

        AwsErrorDetails resultErrorDetails = resultException.awsErrorDetails();
        AwsErrorDetails expectedErrorDetails = expectedException.awsErrorDetails();
        assertThat(resultErrorDetails.errorCode()).isEqualTo(expectedErrorDetails.errorCode());
        assertThat(resultErrorDetails.serviceName()).isEqualTo(expectedErrorDetails.serviceName());
        assertThat(resultErrorDetails.errorMessage()).isEqualTo(expectedErrorDetails.errorMessage());
        assertThat(resultErrorDetails.rawResponse()).isEqualTo(expectedErrorDetails.rawResponse());

        SdkHttpResponse httpResponse = resultErrorDetails.sdkHttpResponse();
        SdkHttpResponse expectedHttpResponse = expectedErrorDetails.sdkHttpResponse();
        assertThat(httpResponse.statusCode()).isEqualTo((expectedHttpResponse.statusCode()));
        assertThat(httpResponse.headers()).containsEntry("sample", Collections.singletonList("value"));
    }


    private AwsServiceException createException() {
        SdkHttpResponse httpResponse = SdkHttpFullResponse.builder().statusCode(403)
                                                          .putHeader("sample", "value")
                                                          .build();

        AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                                                      .errorCode("someCode")
                                                      .errorMessage("message")
                                                      .serviceName("someService")
                                                      .sdkHttpResponse(httpResponse)
                                                      .build();

        return AwsServiceException.builder()
                                  .awsErrorDetails(errorDetails)
                                  .statusCode(403)
                                  .cause(new RuntimeException("someThrowable"))
                                  .clockSkew(Duration.ofSeconds(2))
                                  .requestId("requestId")
                                  .extendedRequestId("extendedRequestId")
                                  .message("message")
                                  .build();
    }
}