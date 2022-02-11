/*
 * Copyright The Stargate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.stargate.sgv2.common.grpc;

import io.grpc.Channel;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

public interface StargateBridgeClientFactory {

  static StargateBridgeClientFactory newInstance(
      Channel channel, String adminToken, ScheduledExecutorService executor) {
    return new DefaultStargateBridgeClientFactory(channel, adminToken, executor);
  }

  StargateBridgeClient newClient(String authToken, Optional<String> tenantId);

  StargateBridgeSchema getSchema();
}