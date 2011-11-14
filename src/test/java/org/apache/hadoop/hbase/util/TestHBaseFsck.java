/**
 * Copyright 2010 The Apache Software Foundation
 *
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
package org.apache.hadoop.hbase.util;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HServerAddress;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.HBaseFsck.ErrorReporter.ERROR_CODE;
import org.apache.zookeeper.KeeperException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This tests HBaseFsck's ability to detect reasons for inconsistent tables.
 */
public class TestHBaseFsck {
  final Log LOG = LogFactory.getLog(getClass());
  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private final static Configuration conf = TEST_UTIL.getConfiguration();
  private final static byte[] FAM = Bytes.toBytes("fam");

  // for the instance, reset every test run
  private HTable tbl;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    TEST_UTIL.startMiniCluster(3);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  private List<ERROR_CODE> doFsck(boolean fix) throws Exception {
    HBaseFsck fsck = new HBaseFsck(conf);
    fsck.displayFullReport(); // i.e. -details
    fsck.setTimeLag(0);
    fsck.setFixErrors(fix);
    fsck.doWork();
    return fsck.getErrors().getErrorList();
  }

  private void assertNoErrors(List<ERROR_CODE> errs) throws Exception {
    assertEquals(0, errs.size());
  }

  private void assertErrors(List<ERROR_CODE> errs, ERROR_CODE[] expectedErrors) {
    assertEquals(Arrays.asList(expectedErrors), errs);
  }

  private HRegionInfo createRegion(Configuration conf,
      final HTableDescriptor htd, byte[] startKey, byte[] endKey)
      throws IOException {
    HTable meta = new HTable(conf, HConstants.META_TABLE_NAME);
    HRegionInfo hri = new HRegionInfo(htd, startKey, endKey);
    Put put = new Put(hri.getRegionName());
    put.add(HConstants.CATALOG_FAMILY, HConstants.REGIONINFO_QUALIFIER,
        Writables.getBytes(hri));
    meta.put(put);
    return hri;
  }

  public void dumpMeta(HTableDescriptor htd) throws IOException {
    List<byte[]> metaRows = TEST_UTIL.getMetaTableRows(htd.getName());
    for (byte[] row : metaRows) {
      LOG.info(Bytes.toString(row));
    }
  }

  private void deleteRegion(Configuration conf, final HTableDescriptor htd, 
      byte[] startKey, byte[] endKey) throws IOException {

    LOG.info("Before delete:");
    dumpMeta(htd);

    Map<HRegionInfo, HServerAddress> hris = tbl.getRegionsInfo();
    for (Entry<HRegionInfo, HServerAddress> e: hris.entrySet()) {
      HRegionInfo hri = e.getKey();
      HServerAddress hsa = e.getValue();
      if (Bytes.compareTo(hri.getStartKey(), startKey) == 0 
          && Bytes.compareTo(hri.getEndKey(), endKey) == 0) {

        LOG.info("RegionName: " +hri.getRegionNameAsString());
        byte[] deleteRow = hri.getRegionName();
        TEST_UTIL.getHBaseAdmin().unassign(deleteRow, true);

        LOG.info("deleting hdfs data: " + hri.toString() + hsa.toString());
        Path rootDir = new Path(conf.get(HConstants.HBASE_DIR));
        FileSystem fs = rootDir.getFileSystem(conf);
        Path p = new Path(rootDir + "/" + htd.getNameAsString(), hri.getEncodedName());
        fs.delete(p, true);

        HTable meta = new HTable(conf, HConstants.META_TABLE_NAME);
        Delete delete = new Delete(deleteRow);
        meta.delete(delete);
      }
      LOG.info(hri.toString() + hsa.toString());
    }

    TEST_UTIL.getMetaTableRows(htd.getName());
    LOG.info("After delete:");
    dumpMeta(htd);

  }

  /**
   * Setup a clean table before we start mucking with it.
   * 
   * @throws IOException
   * @throws InterruptedException
   * @throws KeeperException
   */
  void setupTable(String tablename) throws Exception {
    byte[][] startKeys = new byte[][] { Bytes.toBytes("A"), Bytes.toBytes("B"),
        Bytes.toBytes("C") };
    HTableDescriptor desc = new HTableDescriptor(tablename);
    HColumnDescriptor hcd = new HColumnDescriptor(Bytes.toString(FAM));
    desc.addFamily(hcd); // If a table has no CF's it doesn't get checked
    TEST_UTIL.getHBaseAdmin().createTable(desc, startKeys);
    tbl = new HTable(TEST_UTIL.getConfiguration(), tablename);
  }

  /**
   * delete table in preparation for next test
   * 
   * @param tablename
   * @throws IOException
   */
  void deleteTable(String tablename) throws IOException {
    HBaseAdmin admin = TEST_UTIL.getHBaseAdmin();
    byte[] tbytes = Bytes.toBytes(tablename);
    admin.disableTable(tbytes);
    admin.deleteTable(tbytes);
  }


  
  /**
   * This creates a clean table and confirms that the table is clean.
   */
  @Test
  public void testHBaseFsckClean() throws Exception {
    assertNoErrors(doFsck(false));
    String table = "tableClean";
    try {
      setupTable(table);

      // We created 1 table, should be fine
      assertNoErrors(doFsck(false));
    } finally {
      deleteTable(table);
    }
  }

  /**
   * This creates a bad table with regions that have a duplicate start key
   */
  @Test
  public void testDupeStartKey() throws Exception {
    assertNoErrors(doFsck(false));
    String table = "tableDupeStartKey";
    try {
      setupTable(table);
      assertNoErrors(doFsck(false));

      // Now let's mess it up, by adding a region with a duplicate startkey
      HRegionInfo hriDupe = createRegion(conf, tbl.getTableDescriptor(),
          Bytes.toBytes("A"), Bytes.toBytes("A2"));
      TEST_UTIL.getHBaseCluster().getMaster().assignRegion(hriDupe);
      TEST_UTIL.getHBaseCluster().getMaster().getAssignmentManager()
          .waitForAssignment(hriDupe);

      assertErrors(doFsck(false),
          new ERROR_CODE[] { ERROR_CODE.DUPE_STARTKEYS,
            ERROR_CODE.DUPE_STARTKEYS});
    } finally {
      deleteTable(table);
    }
  }

  /**
   * This creates a bad table where a start key contained in another region.
   */
  @Test
  public void testCoveredStartKey() throws Exception {
    String table = "tableCoveredStartKey";
    try {
      setupTable(table);

      // Mess it up by creating an overlap in the metadata
      HRegionInfo hriOverlap = createRegion(conf, tbl.getTableDescriptor(),
          Bytes.toBytes("A2"), Bytes.toBytes("B2"));
      TEST_UTIL.getHBaseCluster().getMaster().assignRegion(hriOverlap);
      TEST_UTIL.getHBaseCluster().getMaster().getAssignmentManager()
          .waitForAssignment(hriOverlap);
      assertErrors(doFsck(false), new ERROR_CODE[] {
          ERROR_CODE.OVERLAP_IN_REGION_CHAIN,
          ERROR_CODE.OVERLAP_IN_REGION_CHAIN });
    } finally {
      deleteTable(table);
    }
  }

  /**
   * This creates a bad table with a hole in meta.
   */
  @Test
  public void testMetaHole() throws Exception {
    String table = "tableMetaHole";
    try {
      setupTable(table);

      // Mess it up by leaving a hole in the meta data
      HRegionInfo hriHole = createRegion(conf, tbl.getTableDescriptor(),
          Bytes.toBytes("D"), Bytes.toBytes(""));
      TEST_UTIL.getHBaseCluster().getMaster().assignRegion(hriHole);
      TEST_UTIL.getHBaseCluster().getMaster().getAssignmentManager()
          .waitForAssignment(hriHole);

      TEST_UTIL.getHBaseAdmin().disableTable(table);
      deleteRegion(conf, tbl.getTableDescriptor(), Bytes.toBytes("C"), Bytes.toBytes(""));
      TEST_UTIL.getHBaseAdmin().enableTable(table);
      assertErrors(doFsck(false),
          new ERROR_CODE[] { ERROR_CODE.HOLE_IN_REGION_CHAIN });
    } finally {
      deleteTable(table);
    }
  }

}
