/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.master;

import java.io.IOException;
import java.util.List;
import org.apache.hadoop.hbase.Server;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableDescriptors;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.TableNotDisabledException;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.hadoop.hbase.client.MasterSwitchType;
import org.apache.hadoop.hbase.client.NormalizeTableFilterParams;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.executor.ExecutorService;
import org.apache.hadoop.hbase.favored.FavoredNodesManager;
import org.apache.hadoop.hbase.master.assignment.AssignmentManager;
import org.apache.hadoop.hbase.master.janitor.CatalogJanitor;
import org.apache.hadoop.hbase.master.locking.LockManager;
import org.apache.hadoop.hbase.master.normalizer.RegionNormalizerManager;
import org.apache.hadoop.hbase.master.procedure.MasterProcedureEnv;
import org.apache.hadoop.hbase.master.replication.ReplicationPeerManager;
import org.apache.hadoop.hbase.master.replication.SyncReplicationReplayWALManager;
import org.apache.hadoop.hbase.master.snapshot.SnapshotManager;
import org.apache.hadoop.hbase.master.zksyncer.MetaLocationSyncer;
import org.apache.hadoop.hbase.procedure.MasterProcedureManagerHost;
import org.apache.hadoop.hbase.procedure2.LockedResource;
import org.apache.hadoop.hbase.procedure2.Procedure;
import org.apache.hadoop.hbase.procedure2.ProcedureEvent;
import org.apache.hadoop.hbase.procedure2.ProcedureExecutor;
import org.apache.hadoop.hbase.quotas.MasterQuotaManager;
import org.apache.hadoop.hbase.replication.ReplicationException;
import org.apache.hadoop.hbase.replication.ReplicationPeerConfig;
import org.apache.hadoop.hbase.replication.ReplicationPeerDescription;
import org.apache.hadoop.hbase.replication.SyncReplicationState;
import org.apache.hadoop.hbase.rsgroup.RSGroupInfoManager;
import org.apache.hadoop.hbase.security.access.AccessChecker;
import org.apache.hadoop.hbase.security.access.ZKPermissionWatcher;
import org.apache.yetus.audience.InterfaceAudience;

import org.apache.hbase.thirdparty.com.google.protobuf.Service;

/**
 * A curated subset of services provided by {@link HMaster}. For use internally only. Passed to
 * Managers, Services and Chores so can pass less-than-a full-on HMaster at test-time. Be judicious
 * adding API. Changes cause ripples through the code base.
 */
@InterfaceAudience.Private
public interface MasterServices extends Server {
  /** Returns the underlying snapshot manager */
  SnapshotManager getSnapshotManager();

  /** Returns the underlying MasterProcedureManagerHost */
  MasterProcedureManagerHost getMasterProcedureManagerHost();

  /** Returns Master's instance of {@link ClusterSchema} */
  ClusterSchema getClusterSchema();

  /** Returns Master's instance of the {@link AssignmentManager} */
  AssignmentManager getAssignmentManager();

  /** Returns Master's filesystem {@link MasterFileSystem} utility class. */
  MasterFileSystem getMasterFileSystem();

  /** Returns Master's WALs {@link MasterWalManager} utility class. */
  MasterWalManager getMasterWalManager();

  /** Returns Master's {@link ServerManager} instance. */
  ServerManager getServerManager();

  /** Returns Master's instance of {@link ExecutorService} */
  ExecutorService getExecutorService();

  /** Returns Master's instance of {@link TableStateManager} */
  TableStateManager getTableStateManager();

  /** Returns Master's instance of {@link MasterCoprocessorHost} */
  MasterCoprocessorHost getMasterCoprocessorHost();

  /** Returns Master's instance of {@link MasterQuotaManager} */
  MasterQuotaManager getMasterQuotaManager();

  /** Returns Master's instance of {@link RegionNormalizerManager} */
  RegionNormalizerManager getRegionNormalizerManager();

  /** Returns Master's instance of {@link CatalogJanitor} */
  CatalogJanitor getCatalogJanitor();

  /** Returns Master's instance of {@link ProcedureExecutor} */
  ProcedureExecutor<MasterProcedureEnv> getMasterProcedureExecutor();

  /** Returns Tripped when Master has finished initialization. */
  public ProcedureEvent<?> getInitializedEvent();

  /** Returns Master's instance of {@link MetricsMaster} */
  MetricsMaster getMasterMetrics();

  /**
   * Check table is modifiable; i.e. exists and is offline.
   * @param tableName Name of table to check.
   */
  // We actually throw the exceptions mentioned in the
  void checkTableModifiable(final TableName tableName)
    throws IOException, TableNotFoundException, TableNotDisabledException;

  /**
   * Create a table using the given table definition.
   * @param desc      The table definition
   * @param splitKeys Starting row keys for the initial table regions. If null a single region is
   *                  created.
   */
  long createTable(final TableDescriptor desc, final byte[][] splitKeys, final long nonceGroup,
    final long nonce) throws IOException;

  /**
   * Create a system table using the given table definition.
   * @param tableDescriptor The system table definition a single region is created.
   */
  long createSystemTable(final TableDescriptor tableDescriptor) throws IOException;

  /**
   * Delete a table
   * @param tableName The table name
   */
  long deleteTable(final TableName tableName, final long nonceGroup, final long nonce)
    throws IOException;

  /**
   * Truncate a table
   * @param tableName      The table name
   * @param preserveSplits True if the splits should be preserved
   */
  public long truncateTable(final TableName tableName, final boolean preserveSplits,
    final long nonceGroup, final long nonce) throws IOException;

  /**
   * Modify the descriptor of an existing table
   * @param tableName  The table name
   * @param descriptor The updated table descriptor
   */
  long modifyTable(final TableName tableName, final TableDescriptor descriptor,
    final long nonceGroup, final long nonce) throws IOException;

  /**
   * Modify the store file tracker of an existing table
   */
  long modifyTableStoreFileTracker(final TableName tableName, final String dstSFT,
    final long nonceGroup, final long nonce) throws IOException;

  /**
   * Enable an existing table
   * @param tableName The table name
   */
  long enableTable(final TableName tableName, final long nonceGroup, final long nonce)
    throws IOException;

  /**
   * Disable an existing table
   * @param tableName The table name
   */
  long disableTable(final TableName tableName, final long nonceGroup, final long nonce)
    throws IOException;

  /**
   * Add a new column to an existing table
   * @param tableName The table name
   * @param column    The column definition
   */
  long addColumn(final TableName tableName, final ColumnFamilyDescriptor column,
    final long nonceGroup, final long nonce) throws IOException;

  /**
   * Modify the column descriptor of an existing column in an existing table
   * @param tableName  The table name
   * @param descriptor The updated column definition
   */
  long modifyColumn(final TableName tableName, final ColumnFamilyDescriptor descriptor,
    final long nonceGroup, final long nonce) throws IOException;

  /**
   * Modify the store file tracker of an existing column in an existing table
   */
  long modifyColumnStoreFileTracker(final TableName tableName, final byte[] family,
    final String dstSFT, final long nonceGroup, final long nonce) throws IOException;

  /**
   * Delete a column from an existing table
   * @param tableName  The table name
   * @param columnName The column name
   */
  long deleteColumn(final TableName tableName, final byte[] columnName, final long nonceGroup,
    final long nonce) throws IOException;

  /**
   * Merge regions in a table.
   * @param regionsToMerge daughter regions to merge
   * @param forcible       whether to force to merge even two regions are not adjacent
   * @param nonceGroup     used to detect duplicate
   * @param nonce          used to detect duplicate
   * @return procedure Id
   */
  long mergeRegions(final RegionInfo[] regionsToMerge, final boolean forcible,
    final long nonceGroup, final long nonce) throws IOException;

  /**
   * Split a region.
   * @param regionInfo region to split
   * @param splitRow   split point
   * @param nonceGroup used to detect duplicate
   * @param nonce      used to detect duplicate
   * @return procedure Id
   */
  long splitRegion(final RegionInfo regionInfo, final byte[] splitRow, final long nonceGroup,
    final long nonce) throws IOException;

  /** Returns Return table descriptors implementation. */
  TableDescriptors getTableDescriptors();

  /**
   * Registers a new protocol buffer {@link Service} subclass as a master coprocessor endpoint.
   * <p/>
   * Only a single instance may be registered for a given {@link Service} subclass (the instances
   * are keyed on
   * {@link org.apache.hbase.thirdparty.com.google.protobuf.Descriptors.ServiceDescriptor#getFullName()}.
   * After the first registration, subsequent calls with the same service name will fail with a
   * return value of {@code false}.
   * @param instance the {@code Service} subclass instance to expose as a coprocessor endpoint
   * @return {@code true} if the registration was successful, {@code false} otherwise
   */
  boolean registerService(Service instance);

  /** Returns true if master is the active one */
  boolean isActiveMaster();

  /** Returns true if master is initialized */
  boolean isInitialized();

  /**
   * @return true if master is in maintanceMode
   * @throws IOException if the inquiry failed due to an IO problem
   */
  boolean isInMaintenanceMode();

  /**
   * Checks master state before initiating action over region topology.
   * @param action the name of the action under consideration, for logging.
   * @return {@code true} when the caller should exit early, {@code false} otherwise.
   */
  boolean skipRegionManagementAction(final String action);

  /**
   * Abort a procedure.
   * @param procId                ID of the procedure
   * @param mayInterruptIfRunning if the proc completed at least one step, should it be aborted?
   * @return true if aborted, false if procedure already completed or does not exist
   */
  public boolean abortProcedure(final long procId, final boolean mayInterruptIfRunning)
    throws IOException;

  /**
   * Get procedures
   * @return procedure list
   */
  public List<Procedure<?>> getProcedures() throws IOException;

  /**
   * Get locks
   * @return lock list
   */
  public List<LockedResource> getLocks() throws IOException;

  /**
   * Get list of table descriptors by namespace
   * @param name namespace name
   */
  public List<TableDescriptor> listTableDescriptorsByNamespace(String name) throws IOException;

  /**
   * Get list of table names by namespace
   * @param name namespace name
   * @return table names
   */
  public List<TableName> listTableNamesByNamespace(String name) throws IOException;

  /**
   * @param table the table for which last successful major compaction time is queried
   * @return the timestamp of the last successful major compaction for the passed table, or 0 if no
   *         HFile resulting from a major compaction exists
   */
  public long getLastMajorCompactionTimestamp(TableName table) throws IOException;

  /**
   * Returns the timestamp of the last successful major compaction for the passed region or 0 if no
   * HFile resulting from a major compaction exists
   */
  public long getLastMajorCompactionTimestampForRegion(byte[] regionName) throws IOException;

  /** Returns load balancer */
  public LoadBalancer getLoadBalancer();

  boolean isSplitOrMergeEnabled(MasterSwitchType switchType);

  /** Returns Favored Nodes Manager */
  public FavoredNodesManager getFavoredNodesManager();

  /**
   * Add a new replication peer for replicating data to slave cluster
   * @param peerId     a short name that identifies the peer
   * @param peerConfig configuration for the replication slave cluster
   * @param enabled    peer state, true if ENABLED and false if DISABLED
   */
  long addReplicationPeer(String peerId, ReplicationPeerConfig peerConfig, boolean enabled)
    throws ReplicationException, IOException;

  /**
   * Removes a peer and stops the replication
   * @param peerId a short name that identifies the peer
   */
  long removeReplicationPeer(String peerId) throws ReplicationException, IOException;

  /**
   * Restart the replication stream to the specified peer
   * @param peerId a short name that identifies the peer
   */
  long enableReplicationPeer(String peerId) throws ReplicationException, IOException;

  /**
   * Stop the replication stream to the specified peer
   * @param peerId a short name that identifies the peer
   */
  long disableReplicationPeer(String peerId) throws ReplicationException, IOException;

  /**
   * Returns the configured ReplicationPeerConfig for the specified peer
   * @param peerId a short name that identifies the peer
   * @return ReplicationPeerConfig for the peer
   */
  ReplicationPeerConfig getReplicationPeerConfig(String peerId)
    throws ReplicationException, IOException;

  /**
   * Returns the {@link ReplicationPeerManager}.
   */
  ReplicationPeerManager getReplicationPeerManager();

  /**
   * Returns the {@link SyncReplicationReplayWALManager}.
   */
  SyncReplicationReplayWALManager getSyncReplicationReplayWALManager();

  /**
   * Update the peerConfig for the specified peer
   * @param peerId     a short name that identifies the peer
   * @param peerConfig new config for the peer
   */
  long updateReplicationPeerConfig(String peerId, ReplicationPeerConfig peerConfig)
    throws ReplicationException, IOException;

  /**
   * Return a list of replication peers.
   * @param regex The regular expression to match peer id
   * @return a list of replication peers description
   */
  List<ReplicationPeerDescription> listReplicationPeers(String regex)
    throws ReplicationException, IOException;

  /**
   * Set current cluster state for a synchronous replication peer.
   * @param peerId       a short name that identifies the peer
   * @param clusterState state of current cluster
   */
  long transitReplicationPeerSyncReplicationState(String peerId, SyncReplicationState clusterState)
    throws ReplicationException, IOException;

  /** Returns {@link LockManager} to lock namespaces/tables/regions. */
  LockManager getLockManager();

  public String getRegionServerVersion(final ServerName sn);

  /**
   * Called when a new RegionServer is added to the cluster. Checks if new server has a newer
   * version than any existing server and will move system tables there if so.
   */
  public void checkIfShouldMoveSystemRegionAsync();

  String getClientIdAuditPrefix();

  /** Returns True if cluster is up; false if cluster is not up (we are shutting down). */
  boolean isClusterUp();

  /** Returns return null if current is zk-based WAL splitting */
  default SplitWALManager getSplitWALManager() {
    return null;
  }

  /** Returns the {@link AccessChecker} */
  AccessChecker getAccessChecker();

  /** Returns the {@link ZKPermissionWatcher} */
  ZKPermissionWatcher getZKPermissionWatcher();

  /**
   * Execute region plans with throttling
   * @param plans to execute
   * @return succeeded plans
   */
  List<RegionPlan> executeRegionPlansWithThrottling(List<RegionPlan> plans);

  /**
   * Run the ReplicationBarrierChore.
   */
  void runReplicationBarrierCleaner();

  /** Returns the {@link RSGroupInfoManager} */
  RSGroupInfoManager getRSGroupInfoManager();

  /**
   * Queries the state of the {@code LoadBalancerStateStore}. If the balancer is not initialized,
   * false is returned.
   * @return The state of the load balancer, or false if the load balancer isn't defined.
   */
  boolean isBalancerOn();

  /**
   * Perform normalization of cluster.
   * @param ntfp           Selection criteria for identifying which tables to normalize.
   * @param isHighPriority {@code true} when these requested tables should skip to the front of the
   *                       queue.
   * @return {@code true} when the request was submitted, {@code false} otherwise.
   */
  boolean normalizeRegions(final NormalizeTableFilterParams ntfp, final boolean isHighPriority)
    throws IOException;

  /**
   * Get the meta location syncer.
   * <p/>
   * We need to get this in MTP to tell the syncer the new meta replica count.
   */
  MetaLocationSyncer getMetaLocationSyncer();

  /**
   * Flush master local region
   */
  void flushMasterStore() throws IOException;
}
