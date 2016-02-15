/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.storageengine.api;

import java.util.Collection;
import java.util.stream.Stream;

import org.neo4j.io.pagecache.IOLimiter;
import org.neo4j.kernel.api.exceptions.TransactionFailureException;
import org.neo4j.kernel.api.exceptions.schema.ConstraintValidationKernelException;
import org.neo4j.kernel.api.exceptions.schema.CreateConstraintFailureException;
import org.neo4j.kernel.impl.util.DependencySatisfier;
import org.neo4j.kernel.info.DiagnosticsManager;
import org.neo4j.storageengine.api.lock.ResourceLocker;
import org.neo4j.storageengine.api.txstate.ReadableTransactionState;

/**
 * A StorageEngine provides the functionality to durably store data, and read it back.
 */
public interface StorageEngine
{
    /**
     * @return an interface for accessing data previously
     * {@link #apply(CommandsToApply, TransactionApplicationMode) applied} to this storage.
     */
    StoreReadLayer storeReadLayer();

    /**
     * Generates a list of {@link StorageCommand commands} representing the changes in the given transaction state
     * ({@code state} and {@code legacyIndexTransactionState}.
     * The returned commands can be used to form {@link CommandsToApply} batches, which can be applied to this
     * storage using {@link #apply(CommandsToApply, TransactionApplicationMode)}.
     * The reason this is separated like this is that the generated commands can be used for other things
     * than applying to storage, f.ex replicating to another storage engine.
     *
     * @param target {@link Collection} to put {@link StorageCommand commands} into.
     * @param state {@link ReadableTransactionState} representing logical store changes to generate commands for.
     * @param storageStatement {@link StorageStatement} to use for reading store state during creation of commands.
     * @param locks {@link ResourceLocker} can grab additional locks.
     * This locks client still have the potential to acquire more locks at this point.
     * TODO we should try to get rid of this locking mechanism during creation of commands
     * The reason it's needed is that some relationship changes in the record storage engine
     * needs to lock prev/next relationships and these changes happens when creating commands
     * The EntityLocker interface is a subset of Locks.Client interface, just to fit in while it's here.
     * @param lastTransactionIdWhenStarted transaction id which was seen as last committed when this
     * transaction started, i.e. before any changes were made and before any data was read.
     * TODO Transitional (Collection), might be {@link Stream} or whatever.
     * @throws TransactionFailureException if command generation fails or some prerequisite of some command
     * didn't validate, for example if trying to delete a node that still has relationships.
     * @throws CreateConstraintFailureException if this transaction was set to create a constraint and that failed.
     * @throws ConstraintValidationKernelException if this transaction was set to create a constraint
     * and some data violates that constraint.
     */
    void createCommands(
            Collection<StorageCommand> target,
            ReadableTransactionState state,
            StorageStatement storageStatement,
            ResourceLocker locks,
            long lastTransactionIdWhenStarted )
            throws TransactionFailureException, CreateConstraintFailureException, ConstraintValidationKernelException;

    /**
     * Apply a batch of groups of commands to this storage.
     *
     * @param batch batch of groups of commands to apply to storage.
     * @param mode {@link TransactionApplicationMode} when applying.
     * @throws Exception if an error occurs during application.
     */
    void apply( CommandsToApply batch, TransactionApplicationMode mode ) throws Exception;

    /**
     * @return a {@link CommandReaderFactory} capable of returning {@link CommandReader commands readers}
     * for specific log entry versions.
     */
    CommandReaderFactory commandReaderFactory();

    /**
     * Flushes and forces all changes down to underlying storage. This is a blocking call and when it returns
     * all changes applied to this storage engine will be durable.
     * @param limiter The {@link IOLimiter} used to moderate the rate of IO caused by the flush process.
     */
    void flushAndForce( IOLimiter limiter );

    /**
     * Registers diagnostics about the storage onto {@link DiagnosticsManager}.
     *
     * @param diagnosticsManager {@link DiagnosticsManager} to register diagnostics at.
     */
    void registerDiagnostics( DiagnosticsManager diagnosticsManager );

    /**
     * Force close all opened resources. This may be called during startup if there's a failure
     * during recovery or similar.
     */
    void forceClose();

    /**
     * Startup process have reached the conclusion that recovery is required. Make the necessary
     * preparations to be ready for recovering transactions.
     */
    void prepareForRecoveryRequired();

    // ====================================================================
    // All these methods below are temporary while in the process of
    // creating this API, take little notice to them, as they will go away
    // ====================================================================

    @Deprecated
    void loadSchemaCache();

    /**
     * TEMPORARY method as this potentially exposes internal services which shouldn't really be accessible
     * outside the boundaries of this API. This is here as a transitional method while there still
     * exists abstraction leaks.
     *
     * @param satisfier {@link DependencySatisfier} to satisfy with internal service which still are needed
     * outside this interface.
     * @deprecated since accessing internal services like this shouldn't be required if all abstractions
     * are correct and doesn't leak.
     */
    @Deprecated
    void satisfyDependencies( DependencySatisfier satisfier );
}
