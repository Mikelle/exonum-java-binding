package com.exonum.binding.service;

import static com.google.common.base.Preconditions.checkNotNull;

import com.exonum.binding.messages.BinaryMessage;
import com.exonum.binding.messages.Transaction;
import com.exonum.binding.storage.database.Fork;
import com.exonum.binding.storage.database.Snapshot;
import java.util.List;

/**
 * An adapter of a user-facing interface {@link Service} to an interface with a native code:
 *   - Separates user-facing interface and the framework implementation,
 *     to enable us to change them independently.
 *   - Provides the native code with a convenient interface (simpler, faster, more reliable).
 */
class UserServiceAdapter {

  private final Service service;

  UserServiceAdapter(Service service) {
    this.service = checkNotNull(service, "service");
  }

  short getId() {
    return service.getId();
  }

  String getName() {
    return service.getName();
  }

  /**
   * Converts a transaction messages into an executable transaction of this service.
   *
   * <p>The callee must handle the declared exceptions.
   *
   * @param transactionMessage a transaction message to be converted
   * @return an executable transaction of this service
   *         todo: exception(-s) is to be revised when we (a) design the native part and
   *         (b) implement a certain serialization format
   * @throws NullPointerException if transactionMessage is null, or a user service returns
   *     a null transaction
   * @throws IllegalArgumentException if message is not a valid transaction message of this service
   */
  Transaction convertTransaction(byte[] transactionMessage) {
    BinaryMessage message = BinaryMessage.fromBytes(transactionMessage);
    assert message.getServiceId() == getId() :
        "Message id is distinct from the service id";

    Transaction transaction = service.convertToTransaction(message);
    checkNotNull(transaction, "Invalid service implementation: "
            + "Service#convertToTransaction must never return null.\n"
            + "Throw an exception if your service does not recognize this message id (%s)",
        message.getMessageType());  // todo: consider moving this check to the native code?
    return transaction;
  }

  /**
   * Returns the state hashes of the service.
   *
   * <p>The method does not destroy a native snapshot object corresponding to the passed handle.</p>
   *
   * @param snapshotHandle a handle to a native snapshot object
   * @return an array of state hashes
   * @see Service#getStateHashes(Snapshot)
   */
  // todo: if the native code is better of with a flattened array, change the signature
  byte[][] getStateHashes(long snapshotHandle) {
    // fixme: Although this code and #initialize below close the snapshot proxy,
    // making it impossible to create new indices, a user may still have live references
    // to the indices created during the method execution (e.g., a ProofMapIndex).
    // Isn't it problematic (= actually, unsafe) if a user tries to use an index
    // when the corresponding snapshot is already destroyed?
    // Maybe we shall finally introduce an implicit graph of 'child' native proxies and
    // check that the children of a proxy are 'live' each time a native object is used
    // (e.g., ANP#getNativeHandle)?
    assert snapshotHandle != 0;
    try (Snapshot snapshot = new Snapshot(snapshotHandle, false)) {
      List<byte[]> stateHashes = service.getStateHashes(snapshot);
      return stateHashes.toArray(new byte[0][]);
    }
  }

  /**
   * Returns the service initial global configuration.
   *
   * <p>The method does not destroy a native fork object corresponding to the passed handle.</p>
   *
   * @param forkHandle a handle to a native fork object
   * @return the service global configuration as a JSON string or null if it does not have any
   * @see Service#initialize(Fork)
   */
  String initalize(long forkHandle) {
    assert forkHandle != 0;
    try (Fork fork = new Fork(forkHandle, false)) {
      return service.initialize(fork)
          .orElse(null);
    }
  }

  void mountPublicApiHandler() {
    //todo:
    service.createPublicApiHandlers();
  }

  void mountPrivateApiHandler() {
    //todo:
    service.createPrivateApiHandlers();
  }
}
