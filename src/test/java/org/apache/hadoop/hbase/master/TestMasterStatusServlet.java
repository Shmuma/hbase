/**
 * Copyright 2011 The Apache Software Foundation
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
package org.apache.hadoop.hbase.master;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HServerAddress;
import org.apache.hadoop.hbase.HServerInfo;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.master.AssignmentManager.RegionState;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.zookeeper.ZooKeeperWatcher;
import org.apache.hbase.tmpl.master.AssignmentManagerStatusTmpl;
import org.apache.hbase.tmpl.master.MasterStatusTmpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.collect.Maps;

/**
 * Tests for the master status page and its template.
 */
public class TestMasterStatusServlet {
  
  private HMaster master;
  private Configuration conf;
  private HBaseAdmin admin;

  static final HTableDescriptor FAKE_TABLE =
    new HTableDescriptor("mytable");
  static final HRegionInfo FAKE_REGION =
    new HRegionInfo(FAKE_TABLE, Bytes.toBytes("a"), Bytes.toBytes("b"));
  
  @Before
  public void setupBasicMocks() {
    conf = HBaseConfiguration.create();
    
    master = Mockito.mock(HMaster.class);
    Mockito.doReturn(new HServerAddress("localhost:12345"))
      .when(master).getMasterAddress();
    Mockito.doReturn(conf).when(master).getConfiguration();
    
    // Fake serverManager
    ServerManager serverManager = Mockito.mock(ServerManager.class);
    Mockito.doReturn(1.0).when(serverManager).getAverageLoad();
    Mockito.doReturn(serverManager).when(master).getServerManager();

    // Fake AssignmentManager and RIT
    AssignmentManager am = Mockito.mock(AssignmentManager.class);
    NavigableMap<String, RegionState> regionsInTransition =
      Maps.newTreeMap();
    regionsInTransition.put("r1",
        new RegionState(FAKE_REGION, RegionState.State.CLOSING, 12345L, "localhost"));
    Mockito.doReturn(regionsInTransition).when(am).getRegionsInTransition();
    Mockito.doReturn(am).when(master).getAssignmentManager();
    
    // Fake ZKW
    ZooKeeperWatcher zkw = Mockito.mock(ZooKeeperWatcher.class);
    Mockito.doReturn("fakequorum").when(zkw).getQuorum();
    Mockito.doReturn(zkw).when(master).getZooKeeperWatcher();

    // Mock admin
    admin = Mockito.mock(HBaseAdmin.class);
  }
  

  private void setupMockTables() throws IOException {
    HTableDescriptor tables[] = new HTableDescriptor[] {
        new HTableDescriptor("foo"),
        new HTableDescriptor("bar")
    };
    Mockito.doReturn(tables).when(admin).listTables();
  }
  
  @Test
  public void testStatusTemplateNoTables() throws IOException {
    new MasterStatusTmpl().render(new StringWriter(),
        master, admin);
  }
  
  @Test
  public void testStatusTemplateRootAvailable() throws IOException {
    new MasterStatusTmpl()
    .setRootLocation(new HServerAddress("localhost:12345"))
      .render(new StringWriter(),
        master, admin);
  }
  
  @Test
  public void testStatusTemplateRootAndMetaAvailable() throws IOException {
    setupMockTables();
    
    new MasterStatusTmpl()
    .setRootLocation(new HServerAddress("localhost:12345"))
    .setMetaLocation(new HServerAddress("localhost:12345"))
      .render(new StringWriter(),
        master, admin);
  }

  @Test
  public void testStatusTemplateWithServers() throws IOException {
    setupMockTables();
    
    Map<String,HServerInfo> servers = new HashMap<String,HServerInfo>();
    servers.put("rootserver", new HServerInfo(new HServerAddress("localhost:12345"), HConstants.DEFAULT_REGIONSERVER_INFOPORT, null));
    servers.put("metaserver", new HServerInfo(new HServerAddress("localhost:12345"), HConstants.DEFAULT_REGIONSERVER_INFOPORT, null));

    Set<String> deadServers = new HashSet<String>();
    deadServers.add("localhost:23569");
    deadServers.add("hbase.apache.org,9454,1327103687194");

    new MasterStatusTmpl()
    .setRootLocation(new HServerAddress("localhost:12345"))
    .setMetaLocation(new HServerAddress("localhost:12345"))
      .setServers(servers)
      .setDeadServers(deadServers)
      .render(new StringWriter(),
        master, admin);
  }
  
  @Test
  public void testAssignmentManagerTruncatedList() throws IOException {
    AssignmentManager am = Mockito.mock(AssignmentManager.class);
    NavigableMap<String, RegionState> regionsInTransition =
      Maps.newTreeMap();
    for (byte i = 0; i < 100; i++) {
      String regionName = "r" + i;
      HRegionInfo hri = new HRegionInfo(FAKE_TABLE,
          new byte[]{i}, new byte[]{(byte) (i+1)});
      regionsInTransition.put(regionName,
          new RegionState(hri, RegionState.State.CLOSING, 12345L, "localhost"));
    }
    Mockito.doReturn(regionsInTransition).when(am).getRegionsInTransition();

    StringWriter sw = new StringWriter();
    new AssignmentManagerStatusTmpl()
      .setLimit(50)
      .render(sw, am);
    String result = sw.toString();
    int count = 0;
    Matcher matcher = Pattern.compile("CLOSING").matcher(result);
    while (matcher.find()) {
      count++;
    }
    assertEquals(50, count);
  }

}
