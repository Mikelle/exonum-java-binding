package com.exonum.binding.proxy;

/**
 * Represents an underlying Exonum Storage database.
 */
public abstract class Database extends AbstractNativeProxy {

  /**
   * @param nativeHandle a native handle: an implementation-specific reference to a native object.
   * @param owningHandle true if this proxy is responsible to release any native resources;
   */
  Database(long nativeHandle, boolean owningHandle) {
    super(nativeHandle, owningHandle);
  }

  /**
   * Creates a new snapshot of the database state.
   *
   * <p>A caller is responsible to close the snapshot (see {@link Connect#close()}).
   *
   *  @return a new snapshot of the database state.
   */
  public abstract Snapshot getSnapshot();

  /**
   * Creates a new database fork.
   *
   * <p>A fork allows to perform a transaction: a number of independent writes to a database,
   * which then may be <em>atomically</em> applied to the database.
   *
   * <p>A caller is responsible to close the fork (see {@link Connect#close()}).
   *
   * @return a new database fork.
   */
  public abstract Fork getFork();
}
