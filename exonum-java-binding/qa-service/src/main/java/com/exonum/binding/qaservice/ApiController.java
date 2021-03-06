/*
 * Copyright 2018 The Exonum Team
 *
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

package com.exonum.binding.qaservice;

import static com.exonum.binding.common.serialization.json.JsonSerializer.json;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCKCHAIN_BLOCKS_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCKCHAIN_BLOCK_HASHES_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCKCHAIN_BLOCK_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCKCHAIN_BLOCK_TRANSACTIONS_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCKCHAIN_HEIGHT_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCKCHAIN_LAST_BLOCK_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCKCHAIN_TRANSACTION_LOCATIONS_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCKCHAIN_TRANSACTION_LOCATION_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCKCHAIN_TRANSACTION_MESSAGES_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCKCHAIN_TRANSACTION_RESULTS_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCKCHAIN_TRANSACTION_RESULT_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCK_HEIGHT_PARAM;
import static com.exonum.binding.qaservice.ApiController.QaPaths.BLOCK_ID_PARAM;
import static com.exonum.binding.qaservice.ApiController.QaPaths.COUNTER_ID_PARAM;
import static com.exonum.binding.qaservice.ApiController.QaPaths.GET_ACTUAL_CONFIGURATION_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.GET_COUNTER_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.MESSAGE_HASH_PARAM;
import static com.exonum.binding.qaservice.ApiController.QaPaths.SUBMIT_CREATE_COUNTER_TX_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.SUBMIT_INCREMENT_COUNTER_TX_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.SUBMIT_UNKNOWN_TX_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.SUBMIT_VALID_ERROR_TX_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.SUBMIT_VALID_THROWING_TX_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.TIME_PATH;
import static com.exonum.binding.qaservice.ApiController.QaPaths.VALIDATORS_TIMES_PATH;
import static com.google.common.base.Preconditions.checkArgument;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import com.exonum.binding.blockchain.Block;
import com.exonum.binding.common.blockchain.TransactionLocation;
import com.exonum.binding.common.blockchain.TransactionResult;
import com.exonum.binding.common.configuration.StoredConfiguration;
import com.exonum.binding.common.crypto.PublicKey;
import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.common.message.TransactionMessage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class ApiController {

  private static final Logger logger = LogManager.getLogger(ApiController.class);

  private static final BaseEncoding HEX_ENCODING = BaseEncoding.base16().lowerCase();

  private final QaService service;

  ApiController(QaService service) {
    this.service = service;
  }

  void mountApi(Router router) {
    // Mount the body handler to process bodies of some POST queries, and the handler of failures.
    router.route()
        .handler(BodyHandler.create())
        .failureHandler(this::failureHandler);

    // Mount the handlers of each request
    ImmutableMap<String, Handler<RoutingContext>> handlers =
        ImmutableMap.<String, Handler<RoutingContext>>builder()
            .put(SUBMIT_CREATE_COUNTER_TX_PATH, this::submitCreateCounter)
            .put(SUBMIT_INCREMENT_COUNTER_TX_PATH, this::submitIncrementCounter)
            .put(SUBMIT_VALID_THROWING_TX_PATH, this::submitValidThrowingTx)
            .put(SUBMIT_VALID_ERROR_TX_PATH, this::submitValidErrorTx)
            .put(SUBMIT_UNKNOWN_TX_PATH, this::submitUnknownTx)
            .put(GET_COUNTER_PATH, this::getCounter)
            .put(BLOCKCHAIN_HEIGHT_PATH, this::getHeight)
            .put(BLOCKCHAIN_BLOCK_HASHES_PATH, this::getBlockHashes)
            .put(BLOCKCHAIN_BLOCKS_PATH, this::getBlocks)
            .put(BLOCKCHAIN_BLOCK_PATH, this::getBlock)
            .put(BLOCKCHAIN_LAST_BLOCK_PATH, this::getLastBlock)
            .put(BLOCKCHAIN_BLOCK_TRANSACTIONS_PATH, this::getBlockTransactions)
            .put(BLOCKCHAIN_TRANSACTION_MESSAGES_PATH, this::getTransactionMessages)
            .put(BLOCKCHAIN_TRANSACTION_RESULTS_PATH, this::getTransactionResults)
            .put(BLOCKCHAIN_TRANSACTION_RESULT_PATH, this::getTransactionResult)
            .put(BLOCKCHAIN_TRANSACTION_LOCATIONS_PATH, this::getTransactionLocations)
            .put(BLOCKCHAIN_TRANSACTION_LOCATION_PATH, this::getTransactionLocation)
            .put(GET_ACTUAL_CONFIGURATION_PATH, this::getActualConfiguration)
            .put(TIME_PATH, this::getTime)
            .put(VALIDATORS_TIMES_PATH, this::getValidatorsTimes)
            .build();

    handlers.forEach((path, handler) ->
        router.route(path).handler(handler)
    );
  }

  private void submitCreateCounter(RoutingContext rc) {
    MultiMap parameters = rc.request().params();
    String name = getRequiredParameter(parameters, "name");

    HashCode txHash = service.submitCreateCounter(name);
    replyTxSubmitted(rc, txHash);
  }

  private void submitIncrementCounter(RoutingContext rc) {
    MultiMap parameters = rc.request().params();
    long seed = getRequiredParameter(parameters, "seed", Long::parseLong);
    HashCode counterId = getRequiredParameter(parameters, COUNTER_ID_PARAM, HashCode::fromString);

    HashCode txHash = service.submitIncrementCounter(seed, counterId);
    replyTxSubmitted(rc, txHash);
  }

  private void submitValidThrowingTx(RoutingContext rc) {
    MultiMap parameters = rc.request().params();
    long seed = getRequiredParameter(parameters, "seed", Long::parseLong);

    HashCode txHash = service.submitValidThrowingTx(seed);
    replyTxSubmitted(rc, txHash);
  }

  private void submitValidErrorTx(RoutingContext rc) {
    MultiMap parameters = rc.request().params();
    long seed = getRequiredParameter(parameters, "seed", Long::parseLong);
    byte errorCode = getRequiredParameter(parameters, "errorCode", Byte::parseByte);
    String description = parameters.get("errorDescription");

    HashCode txHash = service.submitValidErrorTx(seed, errorCode, description);
    replyTxSubmitted(rc, txHash);
  }

  private void submitUnknownTx(RoutingContext rc) {
    HashCode txHash = service.submitUnknownTx();
    replyTxSubmitted(rc, txHash);
  }

  private void getCounter(RoutingContext rc) {
    HashCode counterId = getRequiredParameter(rc.request(), COUNTER_ID_PARAM, HashCode::fromString);

    Optional<Counter> counter = service.getValue(counterId);

    respondWithJson(rc, counter);
  }

  private void getHeight(RoutingContext rc) {
    try {
      Height height = service.getHeight();
      respondWithJson(rc, height);
    } catch (RuntimeException ex) {
      rc.response()
          .setStatusCode(HTTP_BAD_REQUEST)
          .end();
    }
  }

  private void getBlockHashes(RoutingContext rc) {
    List<HashCode> hashes = service.getBlockHashes();
    respondWithJson(rc, hashes);
  }

  private void getBlocks(RoutingContext rc) {
    Map<HashCode, Block> blocks = service.getBlocks();
    respondWithJson(rc, blocks);
  }

  private void getBlock(RoutingContext rc) {
    MultiMap queryParams = rc.queryParams();
    if (queryParams.contains(BLOCK_HEIGHT_PARAM)) {
      long blockHeight = Long.parseLong(queryParams.get(BLOCK_HEIGHT_PARAM));
      getBlockByHeight(rc, blockHeight);
    } else if (queryParams.contains(BLOCK_ID_PARAM)) {
      HashCode blockId = HashCode.fromString(queryParams.get(BLOCK_ID_PARAM));
      getBlockById(rc, blockId);
    } else {
      respondBadRequest(rc, "Get block request was invalid. Expected format is "
          + BLOCKCHAIN_BLOCK_PATH + "?blockId={blockId} or "
          + BLOCKCHAIN_BLOCK_PATH + "?blockHeight={blockHeight}");
    }
  }

  private void getBlockByHeight(RoutingContext rc, long blockHeight) {
    Block block = service.getBlockByHeight(blockHeight);
    respondWithJson(rc, block);
  }

  private void getBlockById(RoutingContext rc, HashCode blockId) {
    Optional<Block> block = service.getBlockById(blockId);
    respondWithJson(rc, block);
  }

  private void getLastBlock(RoutingContext rc) {
    Block block = service.getLastBlock();
    respondWithJson(rc, block);
  }

  private void getBlockTransactions(RoutingContext rc) {
    MultiMap queryParams = rc.queryParams();
    if (queryParams.contains(BLOCK_HEIGHT_PARAM)) {
      long blockHeight = Long.parseLong(queryParams.get(BLOCK_HEIGHT_PARAM));
      getBlockTransactionsByHeight(rc, blockHeight);
    } else if (queryParams.contains(BLOCK_ID_PARAM)) {
      HashCode blockId = HashCode.fromString(queryParams.get(BLOCK_ID_PARAM));
      getBlockTransactionsByBlockId(rc, blockId);
    } else {
      respondBadRequest(rc, "Get block transactions request was invalid. Expected format is "
          + BLOCKCHAIN_BLOCK_TRANSACTIONS_PATH + "?blockId={blockId} or "
          + BLOCKCHAIN_BLOCK_TRANSACTIONS_PATH + "?blockHeight={blockHeight}");
    }
  }

  private void getBlockTransactionsByHeight(RoutingContext rc, long blockHeight) {
    List<HashCode> hashes = service.getBlockTransactions(blockHeight);
    respondWithJson(rc, hashes);
  }

  private void getBlockTransactionsByBlockId(RoutingContext rc, HashCode blockId) {
    List<HashCode> hashes = service.getBlockTransactions(blockId);
    respondWithJson(rc, hashes);
  }

  private void getTransactionMessages(RoutingContext rc) {
    Map<HashCode, TransactionMessage> transactionMessages = service.getTxMessages();
    Map<HashCode, String> transactionMessagesEncoded =
        hexEncodeTransactionMessages(transactionMessages);

    respondWithJson(rc, transactionMessagesEncoded);
  }

  private void getTransactionResults(RoutingContext rc) {
    Map<HashCode, TransactionResult> txResults = service.getTxResults();
    respondWithJson(rc, txResults);
  }

  private void getTransactionResult(RoutingContext rc) {
    HashCode messageHash = getRequiredParameter(rc.request().params(), MESSAGE_HASH_PARAM,
        HashCode::fromString);

    Optional<TransactionResult> txResult = service.getTxResult(messageHash);
    respondWithJson(rc, txResult);
  }

  private void getTransactionLocations(RoutingContext rc) {
    Map<HashCode, TransactionLocation> txLocations = service.getTxLocations();
    respondWithJson(rc, txLocations);
  }

  private void getTransactionLocation(RoutingContext rc) {
    HashCode messageHash = getRequiredParameter(rc.request().params(), MESSAGE_HASH_PARAM,
        HashCode::fromString);

    Optional<TransactionLocation> txLocation = service.getTxLocation(messageHash);
    respondWithJson(rc, txLocation);
  }

  private void getActualConfiguration(RoutingContext rc) {
    StoredConfiguration configuration = service.getActualConfiguration();
    respondWithJson(rc, configuration);
  }

  private void getTime(RoutingContext rc) {
    Optional<TimeDto> time = service.getTime().map(TimeDto::new);
    respondWithJson(rc, time);
  }

  private void getValidatorsTimes(RoutingContext rc) {
    Map<PublicKey, ZonedDateTime> validatorsTimes = service.getValidatorsTimes();
    respondWithJson(rc, validatorsTimes);
  }

  private static String getRequiredParameter(MultiMap parameters, String key) {
    return getRequiredParameter(parameters, key, String::toString);
  }

  private static <T> T getRequiredParameter(HttpServerRequest request, String key,
      Function<String, T> converter) {
    return getRequiredParameter(request.params(), key, converter);
  }

  private static <T> T getRequiredParameter(MultiMap parameters, String key,
      Function<String, T> converter) {
    checkArgument(parameters.contains(key), "No required key (%s) in request parameters: %s",
        key, parameters);
    String parameter = parameters.get(key);
    try {
      return converter.apply(parameter);
    } catch (Exception e) {
      String message = String.format("Failed to convert parameter (%s): %s", key, e.getMessage());
      throw new IllegalArgumentException(message);
    }
  }

  private void replyTxSubmitted(RoutingContext rc, HashCode transactionHash) {
    rc.response()
        .setStatusCode(HTTP_CREATED)
        .putHeader("Content-Type", "text/plain")
        .putHeader("Location", transactionLocationPath(transactionHash))
        .end(String.valueOf(transactionHash));
  }

  private String transactionLocationPath(HashCode txHash) {
    return "/api/explorer/v1/transactions/" + txHash;
  }

  private void failureHandler(RoutingContext rc) {
    logger.info("An error whilst processing request {}", rc.normalisedPath());

    Throwable requestFailure = rc.failure();
    if (requestFailure != null) {
      Optional<String> badRequest = badRequestDescription(requestFailure);
      if (badRequest.isPresent()) {
        respondBadRequest(rc, badRequest.get());
      } else {
        logger.error("Request error:", requestFailure);
        rc.response()
            .setStatusCode(HTTP_INTERNAL_ERROR)
            .end();
      }
    } else {
      int failureStatusCode = rc.statusCode();
      rc.response()
          .setStatusCode(failureStatusCode)
          .end();
    }
  }

  private <T> void respondWithJson(RoutingContext rc, Optional<T> responseBody) {
    if (responseBody.isPresent()) {
      respondWithJson(rc, responseBody.get());
    } else {
      respondNotFound(rc);
    }
  }

  private void respondWithJson(RoutingContext rc, Object responseBody) {
    rc.response()
        .putHeader("Content-Type", "application/json")
        .end(json().toJson(responseBody));
  }

  private void respondNotFound(RoutingContext rc) {
    rc.response()
        .setStatusCode(HTTP_NOT_FOUND)
        .end();
  }

  private void respondBadRequest(RoutingContext rc, String description) {
    rc.response()
        .setStatusCode(HTTP_BAD_REQUEST)
        .putHeader("Content-Type", "text/plain")
        .end(description);
  }

  /**
   * If the passed throwable corresponds to a bad request — returns an error message,
   * or {@code Optional.empty()} otherwise.
   */
  private Optional<String> badRequestDescription(Throwable requestFailure) {
    // All IllegalArgumentExceptions (including NumberFormatException) and IndexOutOfBoundsException
    // are considered to be caused by a bad request.
    if (requestFailure instanceof IllegalArgumentException
        || requestFailure instanceof IndexOutOfBoundsException) {
      String message = Strings.nullToEmpty(requestFailure.getLocalizedMessage());
      return Optional.of(message);
    }

    // This throwable must correspond to an internal server error.
    return Optional.empty();
  }

  @VisibleForTesting
  static Map<HashCode, String> hexEncodeTransactionMessages(
      Map<HashCode, TransactionMessage> txMessages) {
    return Maps.transformValues(txMessages, ApiController::hexEncodeTransactionMessage);
  }

  private static String hexEncodeTransactionMessage(TransactionMessage transactionMessage) {
    return HEX_ENCODING.encode(transactionMessage.toBytes());
  }

  static class QaPaths {
    @VisibleForTesting
    static final String SUBMIT_CREATE_COUNTER_TX_PATH = "/submit-create-counter";
    @VisibleForTesting
    static final String SUBMIT_INCREMENT_COUNTER_TX_PATH = "/submit-increment-counter";
    @VisibleForTesting
    static final String SUBMIT_VALID_THROWING_TX_PATH = "/submit-valid-throwing";
    @VisibleForTesting
    static final String SUBMIT_VALID_ERROR_TX_PATH = "/submit-valid-error";
    @VisibleForTesting
    static final String SUBMIT_UNKNOWN_TX_PATH = "/submit-unknown";
    @VisibleForTesting
    static final String GET_ACTUAL_CONFIGURATION_PATH = "/actualConfiguration";
    static final String COUNTER_ID_PARAM = "counterId";
    static final String GET_COUNTER_PATH = "/counter/:" + COUNTER_ID_PARAM;

    private static final String BLOCKCHAIN_ROOT = "/blockchain";
    @VisibleForTesting
    static final String BLOCKCHAIN_HEIGHT_PATH = BLOCKCHAIN_ROOT + "/height";
    @VisibleForTesting
    static final String BLOCKCHAIN_BLOCK_HASHES_PATH = BLOCKCHAIN_ROOT + "/blocks";
    @VisibleForTesting
    static final String BLOCKCHAIN_BLOCKS_PATH = BLOCKCHAIN_ROOT + "/hashesToBlocks";
    @VisibleForTesting
    static final String BLOCK_HEIGHT_PARAM = "blockHeight";
    @VisibleForTesting
    static final String BLOCK_ID_PARAM = "blockId";
    @VisibleForTesting
    static final String BLOCKCHAIN_BLOCK_PATH = BLOCKCHAIN_ROOT + "/block";
    @VisibleForTesting
    static final String BLOCKCHAIN_LAST_BLOCK_PATH = BLOCKCHAIN_ROOT + "/block/last";
    @VisibleForTesting
    static final String BLOCKCHAIN_BLOCK_TRANSACTIONS_PATH = BLOCKCHAIN_ROOT + "/block"
        + "/transactions";
    @VisibleForTesting
    static final String BLOCKCHAIN_TRANSACTION_MESSAGES_PATH = BLOCKCHAIN_ROOT + "/txMessages";
    @VisibleForTesting
    static final String BLOCKCHAIN_TRANSACTION_RESULTS_PATH = BLOCKCHAIN_ROOT + "/txResults";
    @VisibleForTesting
    static final String MESSAGE_HASH_PARAM = "messageHash";
    @VisibleForTesting
    static final String BLOCKCHAIN_TRANSACTION_RESULT_PATH = BLOCKCHAIN_ROOT + "/txResult/:"
        + MESSAGE_HASH_PARAM;
    @VisibleForTesting
    static final String BLOCKCHAIN_TRANSACTION_LOCATIONS_PATH = BLOCKCHAIN_ROOT + "/txLocations";
    @VisibleForTesting
    static final String BLOCKCHAIN_TRANSACTION_LOCATION_PATH = BLOCKCHAIN_ROOT + "/txLocation/:"
        + MESSAGE_HASH_PARAM;
    @VisibleForTesting
    static final String TIME_PATH = "/time";
    @VisibleForTesting
    static final String VALIDATORS_TIMES_PATH = TIME_PATH + "/validators";
  }

}
